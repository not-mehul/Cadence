package app.cadence.core.data.backup

import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.contraception.PackSupply
import app.cadence.core.domain.log.CustomTag
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.log.Severity
import app.cadence.core.domain.log.SexActivity
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.medication.MedicationUse
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.reminders.ReminderSettings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Everything a backup restores. Security settings (app lock, screenshots) stay per device. */
data class BackupData(
    val settings: UserSettings,
    val entries: List<DayEntry>,
    val excludedCycleStarts: Set<LocalDate>,
    val contraceptiveDoses: Map<LocalDate, DoseStatus> = emptyMap(),
    val medications: List<Medication> = emptyList(),
    /** Medication ids taken each day; ids match [medications]. */
    val medicationDoses: Map<LocalDate, Set<Long>> = emptyMap(),
    /** Ids match the entries' [DayEntry.tags]. */
    val customTags: List<CustomTag> = emptyList(),
)

/**
 * Turns [BackupData] into backup file bytes and back, optionally password-encrypted with
 * AES-256-GCM using a PBKDF2-HMAC-SHA256 key.
 */
object BackupCodec {
    const val ENCRYPTED_FORMAT = "cadence-backup-encrypted"

    /** OWASP's 2023 recommendation for PBKDF2-HMAC-SHA256. */
    const val DEFAULT_ITERATIONS = 600_000
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            // Always write format and version, even though they're default values.
            encodeDefaults = true
            // Leave out fields that weren't logged instead of writing nulls.
            explicitNulls = false
        }

    fun encode(
        data: BackupData,
        exportedAt: Instant,
        password: CharArray? = null,
        iterations: Int = DEFAULT_ITERATIONS,
    ): ByteArray {
        val plain =
            json
                .encodeToString(
                    BackupDocument.serializer(),
                    toDocument(data, exportedAt),
                ).toByteArray(Charsets.UTF_8)
        return if (password == null || password.isEmpty()) plain else encrypt(plain, password, iterations)
    }

    /** @throws BackupException when the file can't be read. */
    fun decode(
        bytes: ByteArray,
        password: CharArray? = null,
    ): BackupData {
        val root = parseObject(bytes)
        val plain =
            when (root["format"]?.jsonPrimitive?.content) {
                ENCRYPTED_FORMAT -> {
                    if (password == null ||
                        password.isEmpty()
                    ) {
                        throw BackupException(BackupException.Reason.PASSWORD_REQUIRED)
                    }
                    decrypt(root, password)
                }

                BackupDocument.FORMAT -> {
                    bytes
                }

                else -> {
                    throw BackupException(BackupException.Reason.NOT_A_BACKUP)
                }
            }
        val document =
            try {
                json.decodeFromString(BackupDocument.serializer(), plain.toString(Charsets.UTF_8))
            } catch (e: SerializationException) {
                throw BackupException(BackupException.Reason.INVALID, e)
            } catch (e: IllegalArgumentException) {
                throw BackupException(BackupException.Reason.INVALID, e)
            }
        if (document.format != BackupDocument.FORMAT) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        if (document.version > BackupDocument.VERSION) throw BackupException(BackupException.Reason.NEWER_VERSION)
        return try {
            fromDocument(document)
        } catch (e: RuntimeException) {
            // Bad dates, unknown enum names, out-of-range values (DayEntry validates its own fields).
            throw BackupException(BackupException.Reason.INVALID, e)
        }
    }

    fun isEncrypted(bytes: ByteArray): Boolean =
        runCatching {
            parseObject(bytes)["format"]?.jsonPrimitive?.content ==
                ENCRYPTED_FORMAT
        }.getOrDefault(false)

    private fun parseObject(bytes: ByteArray): JsonObject =
        try {
            json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        } catch (e: SerializationException) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, e)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, e)
        }

    // --- Encryption ----------------------------------------------------------------------

    private fun key(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun encrypt(
        plain: ByteArray,
        password: CharArray,
        iterations: Int,
    ): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        val b64 = Base64.getEncoder()
        val envelope =
            """
            {
              "format": "$ENCRYPTED_FORMAT",
              "version": 1,
              "kdf": "$KDF",
              "iterations": $iterations,
              "salt": "${b64.encodeToString(salt)}",
              "iv": "${b64.encodeToString(iv)}",
              "ciphertext": "${b64.encodeToString(cipher.doFinal(plain))}"
            }
            """.trimIndent()
        return envelope.toByteArray(Charsets.UTF_8)
    }

    private class Envelope(
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray,
        val iterations: Int,
    )

    private fun decrypt(
        envelope: JsonObject,
        password: CharArray,
    ): ByteArray {
        val parts =
            try {
                val b64 = Base64.getDecoder()
                Envelope(
                    salt = b64.decode(envelope.getValue("salt").jsonPrimitive.content),
                    iv = b64.decode(envelope.getValue("iv").jsonPrimitive.content),
                    ciphertext = b64.decode(envelope.getValue("ciphertext").jsonPrimitive.content),
                    iterations = envelope.getValue("iterations").jsonPrimitive.int,
                )
            } catch (e: RuntimeException) {
                throw BackupException(BackupException.Reason.INVALID, e)
            }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(password, parts.salt, parts.iterations),
                GCMParameterSpec(TAG_BITS, parts.iv),
            )
            cipher.doFinal(parts.ciphertext)
        } catch (e: AEADBadTagException) {
            throw BackupException(BackupException.Reason.WRONG_PASSWORD, e)
        }
    }

    // --- Mapping -------------------------------------------------------------------------

    private fun toDocument(
        data: BackupData,
        exportedAt: Instant,
    ): BackupDocument {
        val s = data.settings
        return BackupDocument(
            exportedAt = exportedAt.toString(),
            settings =
                BackupSettings(
                    goal = s.goal.name,
                    cycleLength = s.cycleDefaults.cycleLength,
                    periodLength = s.cycleDefaults.periodLength,
                    lutealPhaseLength = s.lutealPhaseLength,
                    theme = s.theme.name,
                    hiddenCategories = s.hiddenCategories.map { it.name }.sorted(),
                    periodReminderEnabled = s.reminders.periodEnabled,
                    periodReminderDaysBefore = s.reminders.periodDaysBefore,
                    periodReminderTime = s.reminders.periodTime.toString(),
                    lateReminderEnabled = s.reminders.lateEnabled,
                    dailyReminderEnabled = s.reminders.dailyEnabled,
                    dailyReminderTime = s.reminders.dailyTime.toString(),
                    discreetNotifications = s.reminders.discreet,
                    contraception =
                        s.contraception?.let { c ->
                            BackupContraception(
                                method = c.method.name,
                                packStart = c.packStart.toString(),
                                activeDays = c.activeDays,
                                breakDays = c.breakDays,
                                placebo = c.placebo,
                                since = c.since.toString(),
                                reminderEnabled = c.reminderEnabled,
                                reminderTime = c.reminderTime.toString(),
                                supplyPacks = c.supply?.packs,
                                supplyCountedOn = c.supply?.countedOn?.toString(),
                            )
                        },
                ),
            days =
                data.entries.filterNot { it.isEmpty }.sortedBy { it.date }.map { e ->
                    BackupDay(
                        date = e.date.toString(),
                        flow = e.flow?.name,
                        symptoms = e.symptoms.mapValues { it.value.name }.toSortedMap(),
                        moods = e.moods.sorted(),
                        painLevel = e.painLevel,
                        energy = e.energy,
                        sleep = e.sleep,
                        stress = e.stress,
                        sex = e.sex?.name,
                        note = e.note,
                        tags = e.tags.sorted(),
                    )
                },
            excludedCycleStarts = data.excludedCycleStarts.sorted().map { it.toString() },
            contraceptiveDoses =
                data.contraceptiveDoses.toSortedMap().map { (date, status) ->
                    BackupDose(date.toString(), status.name)
                },
            medications =
                data.medications.sortedBy { it.id }.map { m ->
                    BackupMedication(m.id, m.name, m.use.name, m.reminderTime?.toString(), m.active)
                },
            medicationDoses =
                data.medicationDoses.toSortedMap().flatMap { (date, ids) ->
                    ids.sorted().map { BackupMedicationDose(date.toString(), it) }
                },
            customTags = data.customTags.sortedBy { it.id }.map { BackupTag(it.id, it.label) },
        )
    }

    private fun fromDocument(doc: BackupDocument): BackupData {
        val s = doc.settings
        val settings =
            UserSettings(
                goal = Goal.valueOf(s.goal),
                cycleDefaults = CycleDefaults(s.cycleLength, s.periodLength),
                lutealPhaseLength = s.lutealPhaseLength,
                theme = ThemePreference.valueOf(s.theme),
                onboardingComplete = true,
                hiddenCategories = LogCategory.parseSet(s.hiddenCategories.joinToString(",")),
                reminders =
                    ReminderSettings(
                        periodEnabled = s.periodReminderEnabled,
                        periodDaysBefore = s.periodReminderDaysBefore,
                        periodTime = LocalTime.parse(s.periodReminderTime),
                        lateEnabled = s.lateReminderEnabled,
                        dailyEnabled = s.dailyReminderEnabled,
                        dailyTime = LocalTime.parse(s.dailyReminderTime),
                        discreet = s.discreetNotifications,
                    ),
                contraception =
                    s.contraception?.let { c ->
                        ContraceptiveSchedule(
                            method = ContraceptiveMethod.valueOf(c.method),
                            packStart = LocalDate.parse(c.packStart),
                            activeDays = c.activeDays,
                            breakDays = c.breakDays,
                            placebo = c.placebo,
                            since = LocalDate.parse(c.since),
                            reminderEnabled = c.reminderEnabled,
                            reminderTime = LocalTime.parse(c.reminderTime),
                            supply =
                                c.supplyPacks?.let { packs ->
                                    c.supplyCountedOn?.let { PackSupply(packs, LocalDate.parse(it)) }
                                },
                        )
                    },
            )
        val entries =
            doc.days.map { d ->
                DayEntry(
                    date = LocalDate.parse(d.date),
                    flow = d.flow?.let(MenstrualFlow::valueOf),
                    symptoms = d.symptoms.mapValues { Severity.valueOf(it.value) },
                    moods = d.moods.toSet(),
                    painLevel = d.painLevel,
                    energy = d.energy,
                    sleep = d.sleep,
                    stress = d.stress,
                    sex = d.sex?.let(SexActivity::valueOf),
                    note = d.note,
                    tags = d.tags.toSet(),
                )
            }
        require(entries.map { it.date }.toSet().size == entries.size) { "Duplicate days" }
        val tags = doc.customTags.map { CustomTag(it.id, it.label) }
        val tagIds = tags.map { it.id }.toSet()
        require(tagIds.size == tags.size) { "Duplicate tag ids" }
        require(entries.all { tagIds.containsAll(it.tags) }) { "A day uses a tag that isn't in the backup" }
        val medications =
            doc.medications.map { m ->
                Medication(m.id, m.name, MedicationUse.valueOf(m.use), m.reminderTime?.let(LocalTime::parse), m.active)
            }
        val medicationIds = medications.map { it.id }.toSet()
        require(medicationIds.size == medications.size) { "Duplicate medication ids" }
        require(
            doc.medicationDoses.all {
                it.medicationId in medicationIds
            },
        ) { "A dose is for a medication that isn't in the backup" }
        require(
            doc.contraceptiveDoses
                .map { it.date }
                .toSet()
                .size == doc.contraceptiveDoses.size,
        ) { "Duplicate doses" }
        return BackupData(
            settings = settings,
            entries = entries,
            excludedCycleStarts = doc.excludedCycleStarts.map(LocalDate::parse).toSet(),
            contraceptiveDoses =
                doc.contraceptiveDoses.associate {
                    LocalDate.parse(it.date) to
                        DoseStatus.valueOf(it.status)
                },
            medications = medications,
            medicationDoses =
                doc.medicationDoses
                    .groupBy({ LocalDate.parse(it.date) }, { it.medicationId })
                    .mapValues { it.value.toSet() },
            customTags = tags,
        )
    }
}
