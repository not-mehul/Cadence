# Cadence — Product & Implementation Plan

Cadence is a simple, private period-tracking companion app **for Android only**. This document covers:

1. [Product principles](#1-product-principles)
2. [Feature inventory](#2-feature-inventory): what a good period tracker offers, and which ones Cadence will build
3. [Recommended architecture](#3-recommended-architecture)
4. [Data model](#4-data-model)
5. [Prediction engine](#5-prediction-engine)
6. [Screens & UX](#6-screens--ux)
7. [Phased delivery plan](#7-phased-delivery-plan)
8. [Risks & mitigations](#8-risks--mitigations)
9. [Open decisions](#9-open-decisions)

---

## 1. Product principles

| Principle | What it means in practice |
|---|---|
| **Private by default** | Data lives on the device. No account, no server, no ads, no third-party analytics SDKs. Encrypted at rest. Nothing leaves the phone unless the user exports it. |
| **Fast to log** | Logging today takes 1–2 taps from the home screen. Detailed logging is optional. |
| **Honest predictions** | Show predictions as ranges with a confidence level, never as certainties. Learn from the user's own history. |
| **Not a medical device** | Cadence is not contraception and does not diagnose. It points out patterns worth raising with a clinician and says so clearly. |
| **Inclusive** | Neutral language ("people who menstruate", no assumed gender or relationship). Works for irregular cycles, hormonal birth control, PCOS, perimenopause, and more. |
| **The user owns the data** | Full export, full delete, and a backup/restore path, all one tap away. |

---

## 2. Feature inventory

Priority key: **P0** = MVP, must have · **P1** = core, expected by users · **P2** = valuable, comes later · **P3** = nice to have or future.

### 2.1 Cycle & period logging
| Feature | Priority | Notes |
|---|---|---|
| Mark period start / end | P0 | One-tap "Period started" and "Period ended" on the Today screen. |
| Edit past periods from the calendar | P0 | Tap any day to toggle a period day. Backfilling history matters for good predictions. |
| Flow intensity per day (spotting / light / medium / heavy) | P1 | Spotting does not count toward period length. |
| Bulk-add past periods during onboarding | P1 | "When did your last 3 periods start?" improves predictions from day one. |

### 2.2 Predictions & cycle awareness
| Feature | Priority | Notes |
|---|---|---|
| Next period date and countdown | P0 | "Period in 5 days" or "Period is 2 days late". |
| Current cycle day and phase | P0 | Menstrual, follicular, ovulatory or luteal, each with a short explanation. |
| Predicted period length | P0 | Used to pre-fill predicted days on the calendar. |
| Estimated ovulation day and fertile window | P1 | Clearly labelled as an estimate. Hidden when hormonal birth control is on. |
| PMS window estimate | P2 | Learned from symptoms the user logs in the luteal phase. |
| Confidence ranges ("likely Mar 3–6") | P1 | Driven by how variable the user's cycles are. |
| Late or missed period detection | P1 | Prompts the user to log, take a test, or note a skipped cycle. |

### 2.3 Daily logging (symptoms & body)
| Feature | Priority | Notes |
|---|---|---|
| Symptoms with severity (cramps, headache, bloating, acne, breast tenderness, back pain, nausea, fatigue, cravings, etc.) | P1 | Built-in catalogue of about 20 items. |
| Mood (happy, calm, irritable, anxious, sad, sensitive, etc.) | P1 | |
| Energy, sleep quality, stress (1–5 scale) | P1 | |
| Pain level (0–10) | P1 | Useful for spotting possible endometriosis or dysmenorrhea patterns. |
| Sexual activity (protected / unprotected), libido | P1 | Optional. Can be hidden in settings. |
| Discharge / cervical mucus | P2 | Dry, sticky, creamy, watery, egg-white. |
| Medications and contraception taken | P2 | Pill check-off, painkillers, supplements. |
| Basal body temperature (BBT) | P2 | Charted. Used to confirm ovulation after the fact. |
| Ovulation test (LH) and pregnancy test results | P2 | |
| Weight | P3 | Opt-in only. |
| Free-text notes | P1 | |
| Custom tags and symptoms | P2 | |
| Hide categories the user doesn't care about | P1 | Keeps the log sheet short. |

### 2.4 Calendar & history
| Feature | Priority | Notes |
|---|---|---|
| Month calendar: logged period, predicted period, fertile window, ovulation, "has log" dot | P0 | Needs a colour-blind-safe palette plus non-colour cues such as patterns and icons. |
| Cycle history list (each cycle's start, length, period length) | P1 | |
| Day detail view | P1 | Everything logged on a given day. |

### 2.5 Insights & statistics
| Feature | Priority | Notes |
|---|---|---|
| Average cycle length, average period length, variation | P1 | |
| Cycle length chart over time | P1 | Bar chart per cycle, with a band for the normal range. |
| Symptom patterns by cycle day ("you often log headaches 2 days before your period") | P2 | |
| Health flags: cycles < 24 or > 38 days, variation > 9 days, period > 8 days, no period in 90 days | P1 | Based on FIGO normal ranges. Gentle wording plus "consider talking to a clinician". |
| BBT chart with cover line | P2 | |

### 2.6 Reminders & notifications
| Feature | Priority | Notes |
|---|---|---|
| Upcoming period (N days before) | P1 | |
| Period is late | P1 | |
| Daily log reminder | P1 | |
| Pill / contraception reminder (daily pill, weekly patch, monthly ring) | P2 | |
| Fertile window start | P2 | Only in modes where it makes sense. |
| **Discreet notification text** | P1 | Default to neutral text, e.g. "Time to check in", so nothing sensitive shows on the lock screen. |

### 2.7 Privacy & security
| Feature | Priority | Notes |
|---|---|---|
| Local-only storage, no account | P0 | |
| Encrypted database at rest | P0 | Much cheaper to do from day one than to retrofit later. |
| App lock (PIN plus biometrics) | P1 | |
| Delete all data | P0 | |
| No third-party analytics or ads | P0 | |
| Plain-language privacy screen | P1 | Explains exactly what is stored and where. |
| Block screenshots and the recents preview (`FLAG_SECURE`) | P1 | |
| Keep data out of Android cloud backup | P0 | The app turns Android Auto Backup off. The encryption key can't be restored on another device anyway, so a cloud copy would be useless and would also be a leak. |
| Discreet app icon and name option | P3 | Done with launcher `activity-alias` entries. |

### 2.8 Data portability
| Feature | Priority | Notes |
|---|---|---|
| Export JSON (full backup) | P1 | |
| Import / restore from backup | P1 | Because data is local, losing a phone without a backup means losing everything. |
| Export CSV | P2 | |
| Clinician report (PDF): cycle table, averages, symptom summary, flags | P2 | |
| Encrypted backup file (password-protected) | P2 | |
| Optional end-to-end-encrypted sync across devices | P3 | Needs a server, so it is deliberately deferred. |

### 2.9 Goals / life-stage modes
| Feature | Priority | Notes |
|---|---|---|
| Track my cycle (default) | P0 | |
| Hormonal birth control mode | P1 | Turns off ovulation and fertile predictions, which don't apply, and adds pill reminders. |
| Trying to conceive (TTC) mode | P2 | Puts the fertile window, LH tests, BBT and mucus first. |
| Pregnancy mode | P3 | Pauses predictions and shows week count. |
| Perimenopause mode | P3 | Tolerates irregular cycles and tracks hot flashes and night sweats. |
| ~~Avoid pregnancy / contraception mode~~ | **Not offered** | Using an app as contraception requires regulatory clearance and clinical validation. We show a clear disclaimer instead. |

### 2.10 Experience & platform
| Feature | Priority | Notes |
|---|---|---|
| Onboarding (last period, typical lengths, goal) | P0 | Every question is skippable, with sensible defaults. |
| Light and dark theme (Material 3), with an optional Material You dynamic colour | P1 | |
| Accessibility: TalkBack labels, font scaling up to 200%, 48dp touch targets, WCAG AA contrast | P1 | |
| Educational cards per phase | P2 | Short, factual, sourced. |
| Localization (dates, first day of week, translations) | P2 | |
| Home-screen widget (cycle day / next period) | P3 | Built with Jetpack Glance. |
| Health Connect sync (menstruation, cervical mucus, BBT, ovulation tests) | P3 | Health Connect has native record types for all of these. |
| Partner sharing (read-only) | P3 | Needs a backend, so it is deferred. |

### 2.11 Explicit non-goals
- Social feeds, community forums, gamification, ads, or selling data.
- Contraceptive claims or diagnosis.
- Mandatory accounts or cloud storage.

---

## 3. Recommended architecture

**Recommendation: a native Android app in Kotlin with Jetpack Compose, local-first, no backend.**

Since the app only targets Android, a cross-platform framework adds nothing and costs us direct access to the platform. Going native gives first-class access to the Android Keystore, BiometricPrompt, notification scheduling, Glance widgets and Health Connect, all without bridging layers.

| Concern | Choice | Why |
|---|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 | The current standard Android toolkit. |
| SDK levels | `minSdk 26` (Android 8.0), `targetSdk` = latest that Google Play requires | API 26 covers about 97% of active devices and gives `java.time` without desugaring. |
| App structure | Single activity, Navigation Compose, MVVM (`ViewModel` + `StateFlow`) | Simple, idiomatic, and easy to test. |
| Dependency injection | Hilt | |
| Async | Kotlin Coroutines + Flow | Room queries come back as `Flow`, so the UI updates live. |
| Storage | Room on SQLCipher (`net.zetetic:sqlcipher-android` through `SupportOpenHelperFactory`) | Typed queries, versioned migrations, encrypted at rest. |
| Encryption key | A random 256-bit database passphrase, wrapped by an AES-GCM key held in the **Android Keystore** and stored in DataStore | The Keystore key never leaves secure hardware. `EncryptedSharedPreferences` is deprecated, so we don't use it. |
| Settings | Jetpack DataStore (Proto or Preferences) | |
| Notifications | `AlarmManager` (`setAndAllowWhileIdle`) for timed reminders, plus `WorkManager` for the daily rescheduling pass | Everything is local, with no push server. The app re-arms alarms on boot, on time or timezone changes, and after app updates. |
| App lock | `androidx.biometric` (`BiometricPrompt`, with device credential as the fallback) plus an optional app PIN | |
| Calendar UI | `com.kizitonwose.calendar:compose`, or a custom `LazyVerticalGrid` | |
| Charts | Vico (Compose charts) or a hand-drawn Compose `Canvas` | |
| PDF report | `android.graphics.pdf.PdfDocument`, shared through the share sheet | No third-party library needed. |
| Export / import | Storage Access Framework (`CreateDocument` / `OpenDocument`) | The user picks the location, so no storage permission is needed. |
| Dates | `java.time.LocalDate`, stored in Room as `epochDay: Long` | Avoids timezone and DST bugs. A "day" is a calendar date, not an instant. |
| Testing | JUnit 5 + kotlin.test for the domain module; Turbine for Flows; in-memory Room tests; Compose UI tests; Robolectric | |
| Quality | ktlint, detekt, Android Lint | |
| CI | GitHub Actions: `./gradlew lint detekt test assembleDebug` | |

### Module layout
```
:app                  # Android application: Activity, navigation, Hilt setup, theme
:core:domain          # PURE Kotlin/JVM module (no Android dependency) — fully unit-tested
    cycles/           #   derive periods & cycles from day logs
    predict/          #   next period, ovulation, fertile window, confidence
    flags/            #   health-pattern flags
    phases/           #   cycle-day → phase
:core:data            # Room entities, DAOs, migrations, SQLCipher, Keystore, DataStore, repositories
:core:ui              # design system: theme, tokens, shared composables
:feature:onboarding
:feature:today
:feature:calendar
:feature:log          # the daily log bottom sheet
:feature:insights
:feature:settings     # incl. export/import, lock, reminders config
:feature:reminders    # alarm scheduling, receivers, notification channels
```
The key rule: **all cycle maths lives in `:core:domain`, a plain Kotlin JVM module.** Gradle itself stops it from importing Android code, so the logic stays pure, fast to test, and independent of the UI and storage.

---

## 4. Data model

The **source of truth is the per-day log.** Periods and cycles are *derived*, not stored, so editing any day keeps everything consistent automatically.

```kotlin
// Domain types (in :core:domain). Days are java.time.LocalDate; Room stores them as epochDay.

enum class Flow { SPOTTING, LIGHT, MEDIUM, HEAVY }
enum class Mucus { DRY, STICKY, CREAMY, WATERY, EGG_WHITE }
enum class SexActivity { NONE, PROTECTED, UNPROTECTED }
enum class TestResult { NEGATIVE, POSITIVE }

data class DayLog(
    val date: LocalDate,                // primary key
    val flow: Flow? = null,             // LIGHT/MEDIUM/HEAVY = period day
    val painLevel: Int? = null,         // 0–10
    val energy: Int? = null,            // 1–5
    val sleep: Int? = null,             // 1–5
    val stress: Int? = null,            // 1–5
    val mucus: Mucus? = null,
    val sex: SexActivity? = null,
    val bbtCelsius: Double? = null,
    val weightKg: Double? = null,
    val lhTest: TestResult? = null,
    val pregnancyTest: TestResult? = null,
    val note: String? = null,
    val updatedAt: Instant,             // for future sync/merge
)

enum class TagKind { SYMPTOM, MOOD, CUSTOM }

data class TagDefinition(               // symptoms, moods, custom tags
    val id: String,
    val kind: TagKind,
    val label: String,                  // string-resource key for built-ins (localizable)
    val builtIn: Boolean,
    val hidden: Boolean,
)

data class DayTag(                      // many-to-many: which tags on which day
    val date: LocalDate,
    val tagId: String,
    val severity: Int? = null,          // 1 mild, 2 moderate, 3 severe
)

enum class MedKind { PILL, PATCH, RING, OTHER }
enum class Recurrence { DAILY, WEEKLY, MONTHLY }

data class Medication(
    val id: String,
    val name: String,
    val kind: MedKind,
    val recurrence: Recurrence,
    val time: LocalTime,                // e.g. 21:00
    val active: Boolean,
)

data class MedicationDose(val medicationId: String, val date: LocalDate, val taken: Boolean)

enum class Goal { TRACK, BIRTH_CONTROL, TTC, PREGNANT, PERIMENOPAUSE }

data class Settings(                    // stored in DataStore, not Room
    val goal: Goal = Goal.TRACK,
    val defaultCycleLength: Int = 28,
    val defaultPeriodLength: Int = 5,
    val lutealPhaseLength: Int = 14,    // user-adjustable, or learned from BBT/LH
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY, // default from locale
    val hiddenCategories: Set<String> = emptySet(),
    val reminders: ReminderSettings = ReminderSettings(),
    val lockEnabled: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val onboardingComplete: Boolean = false,
)

// Optional: the user marks a cycle as unusual (illness, travel, postpartum…)
// so it's left out of predictions.
data class CycleAnnotation(val startDate: LocalDate, val excludeFromPredictions: Boolean, val reason: String?)
```

**Deriving periods:** consecutive days with flow `LIGHT | MEDIUM | HEAVY` form a period. A gap of ≤ 1 day without flow is bridged. Spotting alone never *starts* a period.
**Deriving cycles:** a cycle runs from one period's start to the day before the next period's start. The current cycle is open-ended.

---

## 5. Prediction engine

All functions are pure: `(dayLogs, settings, annotations, today) → result`.

### 5.1 Next period
1. Take completed cycles, excluding user-excluded cycles and outliers (< 15 or > 60 days, which are likely logging gaps).
2. Use the **last 6** remaining cycles. Compute a **weighted average** that favours recent cycles (weights 1…6) → `predictedCycleLength`.
3. If there are fewer than 2 completed cycles, fall back to `settings.defaultCycleLength`.
4. `nextStart = currentPeriodStart + round(predictedCycleLength)`.
5. **Confidence range** = ± `max(1, round(stdDev))` days, capped at ± 7. Labels:
   - **High**: ≥ 3 cycles and SD ≤ 2
   - **Medium**: SD ≤ 4
   - **Low**: otherwise, or when running on defaults

### 5.2 Period length
Weighted average of the last 6 period lengths, falling back to `defaultPeriodLength`.

### 5.3 Ovulation & fertile window
- `ovulation ≈ nextStart − lutealPhaseLength` (default 14 days)
- `fertileWindow = [ovulation − 5, ovulation + 1]`
- Skipped entirely when goal is `birth_control` or `pregnant`.
- In a later phase, a positive LH test or a sustained BBT rise (3 readings ≥ 0.2 °C above the previous 6) *confirms* ovulation for that cycle and feeds back into a learned luteal length.

### 5.4 Phases (for the Today screen)
| Phase | Days |
|---|---|
| Menstrual | Period days |
| Follicular | After the period until the fertile window starts |
| Ovulatory | Fertile window |
| Luteal | After ovulation until the next period |

### 5.5 Late period
If today is past `nextStart + rangeHigh` with no flow logged, the state becomes **Late by N days**. Predictions for later cycles shift off today rather than staying in the past.

### 5.6 Health flags (FIGO-based, informational only)
- Average cycle < 24 or > 38 days
- Cycle-to-cycle variation (max − min over the last 6) > 9 days
- Any period lasting > 8 days
- No period for ≥ 90 days (when not pregnant or on birth control)
- Pain level ≥ 7 on multiple period days across 3+ cycles

### 5.7 Testing strategy
Table-driven unit tests with fixture histories: perfectly regular 28-day cycles, irregular cycles, a single cycle, no data, outliers, an excluded cycle, DST boundaries, leap years, a late period, an ongoing period, and spotting-only days. These run as plain JVM tests (no emulator) in milliseconds. Target **100% branch coverage of `:core:domain`**, measured with Kover.

---

## 6. Screens & UX

```
┌─ Material 3 NavigationBar ──────────────────────────┐
│  Today   │   Calendar   │   Insights   │   Settings │
└─────────────────────────────────────────────────────┘
```

The app supports edge-to-edge display and predictive back, and adapts to tablets and foldables with a `NavigationRail` on wide screens.

1. **Onboarding**: welcome and privacy promise → last period start → typical cycle and period length ("I don't know" allowed) → goal → optional app lock → done.
2. **Today**: a large cycle ring showing cycle day N of ~M with the current phase highlighted; the headline ("Period in 4 days", "Day 2 of period", "3 days late"); a primary action button that changes with context ("Period started" / "Period ended" / "Log today"); chips for what's already logged today; and a short phase card.
3. **Log sheet** (Material 3 `ModalBottomSheet`, opened from Today or any calendar day): sections for Flow, Symptoms, Mood, Body (pain, energy, sleep, stress), Sex, Discharge, Tests, Meds, and Notes. Sections are collapsible, the user can hide them, and changes save automatically.
4. **Calendar**: month grid, swipe between months. Legend:
   - Solid fill = logged period
   - Dashed outline = predicted period
   - Tinted = fertile window
   - Dot = ovulation estimate
   - Small dot = has log

   Tapping a day opens the log sheet for that date.
5. **Insights**: summary cards (average cycle, average period, variation, confidence), a cycle length chart, the cycle history list, symptom patterns, and health flags.
6. **Settings**: goal, cycle defaults, reminders, categories shown, app lock, theme, export / import / delete all, privacy explainer, about and disclaimer.

**Visual direction:** calm and warm, not stereotypically pink. Primary terracotta or rose, a teal accent for the fertile window, and neutral warm greys. Every state has a non-colour cue (pattern, icon or label) for colour-blind users.

---

## 7. Phased delivery plan

Each phase ends with a **working, shippable app**. Phases are sized for one developer at a few days to two weeks each.

### Phase 0: Foundation
**Goal:** the skeleton everything else builds on.
- Gradle multi-module project (Kotlin DSL, version catalog `libs.versions.toml`) with the modules from §3; Hilt, Compose and Navigation wired up.
- ktlint, detekt and Android Lint configured; Kover for coverage.
- Material 3 theme in `:core:ui`: colour scheme (light and dark), type scale, spacing tokens, plus base composables (primary button, chip, card, section header).
- Room on SQLCipher; passphrase generated on first launch and wrapped by an Android Keystore key; schema export on, with a migration test harness.
- `android:allowBackup="false"` and data-extraction rules that exclude the database and preferences.
- `:core:domain` scaffold with JUnit 5.
- GitHub Actions CI: `lint detekt test assembleDebug`, uploading the debug APK as an artifact.
- Bottom navigation with placeholder screens.

**Done when:** the debug APK installs and runs on an API 26 emulator and a current API emulator, CI is green, the database file is unreadable without the key, and a migration test passes.

### Phase 1: MVP (log periods, see predictions)
**Goal:** the smallest app that is actually useful.
- Onboarding: last period date, typical lengths, goal (track / birth control).
- Today screen: cycle day, phase, next-period countdown, and a contextual "Period started / ended" button.
- Calendar: month view showing logged and predicted period days; tap a day to toggle a period day.
- Domain: derive periods and cycles; basic prediction (average plus defaults); phases.
- Settings: cycle defaults, goal, theme, **delete all data**, and the disclaimer.

**Done when:** a new user can onboard in under 60 seconds, log a period in 1 tap, see the next prediction, and edit history on the calendar. The domain has full unit tests.

### Phase 2: Daily logging
**Goal:** a rich but fast daily check-in.
- Log sheet with flow intensity, symptoms with severity, mood, pain, energy, sleep, stress, sex, and notes.
- Built-in tag catalogue; hide or show categories.
- Calendar "has log" dots; day detail view.
- Chips on the Today screen summarising today's log.

**Done when:** a typical day can be logged in under 10 seconds, every field persists and is editable, and categories can be hidden.

### Phase 3: Smarter predictions & insights
**Goal:** predictions people can trust, plus useful statistics.
- Weighted-average prediction, outlier filtering, confidence ranges and labels.
- Ovulation and fertile window on Today and Calendar (hidden in birth control mode).
- Late period state; "exclude this cycle" annotation.
- Insights tab: summary cards, cycle length chart, cycle history, and FIGO-based health flags.

**Done when:** prediction fixtures cover all edge cases in §5.7, Insights renders correctly with 0, 1 and many cycles, and flag wording has been reviewed.

### Phase 4: Reminders & notifications
**Goal:** the app reaches out at the right moments without giving anything away.
- Local notifications for upcoming period, late period, and a daily log reminder at a chosen time.
- Discreet notification text on by default.
- Notifications rescheduled automatically whenever predictions change.
- Notification channels (Period, Daily check-in, Medication) so users can tune each one in system settings.
- `POST_NOTIFICATIONS` runtime permission (Android 13+), asked in context when the user turns on the first reminder, not at launch.
- Inexact alarms (`setAndAllowWhileIdle`) by default, so the restricted `SCHEDULE_EXACT_ALARM` permission isn't needed. A reminder arriving a few minutes late is fine.
- Alarms re-armed on `BOOT_COMPLETED`, `TIMEZONE_CHANGED`, `TIME_SET` and `MY_PACKAGE_REPLACED`.
- Notification visibility set to `VISIBILITY_PRIVATE`, with a neutral public version for the lock screen.
- An in-app hint for OEMs with aggressive battery management (Xiaomi, Samsung, etc.), linking to the battery-optimisation setting.

**Done when:** reminders fire correctly after a reboot, a timezone change, an app update, Doze, and edits to period history, and no sensitive words appear on the lock screen by default.

### Phase 5: Privacy hardening & data portability
**Goal:** the user fully controls and can move their data.
- App lock: `BiometricPrompt` (with device-credential fallback) or an app PIN; auto-lock after N minutes in the background, handled with a `ProcessLifecycleOwner` observer.
- `FLAG_SECURE` on the activity, so there are no screenshots, screen recordings or recents previews (on by default, can be turned off).
- JSON export and import through the Storage Access Framework (versioned format, validated on import, merge or replace).
- CSV export.
- Clinician PDF report (`PdfDocument`): date range, cycle table, averages, top symptoms, flags; shared through the share sheet.
- Optional password-encrypted backup file.
- Privacy explainer screen.

**Done when:** an export → delete all → import round trip restores identical data, the lock can't be bypassed via deep links, notifications or the recents screen, and the PDF renders correctly.

### Phase 6: Goals, fertility signals & medications
**Goal:** support different life stages.
- TTC mode: BBT logging and chart, cervical mucus, LH tests, and confirmed ovulation feeding back into a learned luteal length.
- Birth control mode: medication or contraceptive schedule (pill / patch / ring), dose check-off, and reminders.
- Pregnancy mode (pauses predictions, shows week count) and perimenopause mode (relaxed irregularity flags, extra symptoms).
- Symptom pattern insights ("you often log X around cycle day Y").
- Custom tags.

**Done when:** each mode changes predictions, the Today screen and reminders as specified, and switching modes never loses data.

### Phase 7: Polish & ecosystem
**Goal:** release quality and nice-to-haves.
- Accessibility audit (TalkBack, Switch Access, font scaling up to 200%, contrast, Accessibility Scanner) and fixes.
- Localization: string resources for everything, per-app language (Android 13+), RTL layout check, locale-based first day of week and units.
- Educational phase cards.
- Home-screen widget built with Jetpack Glance, discreet by default.
- Health Connect read and write, opt-in (`MenstruationPeriodRecord`, `MenstruationFlowRecord`, `CervicalMucusRecord`, `BasalBodyTemperatureRecord`, `OvulationTestRecord`).
- Optional discreet launcher icon (`activity-alias`).
- Baseline Profiles for fast startup; R8 minification and shrinking.
- Google Play readiness: release signing with Play App Signing, an Android App Bundle, store listing and screenshots, a **Data safety form** (no data collected or shared), the **Health apps declaration**, and a privacy policy URL. Roll out through internal → closed → production tracks.
- *(Stretch, needs a backend)*: E2E-encrypted sync, partner sharing.

### Phase summary

| Phase | Theme | Key output |
|---|---|---|
| 0 | Foundation | Encrypted DB, design system, CI |
| 1 | MVP | Log periods → see predictions |
| 2 | Daily logging | Symptoms, mood, flow, notes |
| 3 | Insights | Confidence-ranged predictions, fertile window, stats, flags |
| 4 | Reminders | Discreet local notifications |
| 5 | Privacy & portability | App lock, export/import, clinician PDF |
| 6 | Modes | TTC, birth control, pregnancy, perimenopause |
| 7 | Polish | a11y, i18n, Glance widget, Health Connect, Play Store release |

---

## 8. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Users rely on fertile window predictions as contraception | A persistent disclaimer on the fertile window UI, no "safe days" wording, and no contraception mode. |
| Sensitive data exposure (legal requests, a lost device, a shoulder-surfer) | Local-only storage, SQLCipher, app lock, discreet notifications, `FLAG_SECURE`, Auto Backup off, and no server to subpoena. |
| Data loss because nothing is in the cloud (Auto Backup is off on purpose) | Prominent backup and export (Phase 5), plus a periodic "back up your data" nudge. |
| Reminders delayed or killed by OEM battery managers | Inexact alarms plus re-arming in WorkManager, and an in-app guide to battery-optimisation exemptions. |
| Keystore key lost (for example after some device resets), leaving the DB unreadable | Detect it on launch, explain it plainly, and offer to restore from an export file. This is another reason to nudge users to back up. |
| Timezone or DST bugs shifting days | `java.time.LocalDate` (stored as `epochDay`) everywhere, no `Instant` maths on stored days, and dedicated tests. |
| Poor predictions for irregular cycles | Confidence ranges, a Low-confidence label, the exclude-cycle feature, and never presenting a single date as certain. |
| Medical inaccuracy in flags or education | Base thresholds on FIGO/ACOG guidance, cite sources in-app, and use informational wording only. |
| Regulations and store policy (GDPR special-category data, FTC Health Breach Notification Rule, Google Play health-app policy) | Collect nothing server-side, keep a clear privacy policy, and fill in the Data safety form and Health apps declaration accurately. Revisit if sync or a backend is ever added. |

---

## 9. Open decisions

These choices affect the plan. Defaults are in **bold**.

Decided: **Android only**, as a native Kotlin + Jetpack Compose app.

1. **Distribution:** **Google Play**, or sideloaded APK / F-Droid only? An F-Droid build would also need us to avoid proprietary dependencies.
2. **Minimum Android version:** **API 26 (Android 8.0)**, or higher to simplify things?
3. **Sync:** **local-only for now**, or plan a backend for E2E-encrypted sync earlier?
4. **Initial modes:** **track + birth control in MVP**, or also TTC?
5. **Visual identity:** **warm terracotta / teal brand palette**, or Material You dynamic colour as the default?
