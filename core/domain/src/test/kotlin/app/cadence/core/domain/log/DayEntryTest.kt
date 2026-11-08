package app.cadence.core.domain.log

import app.cadence.core.domain.d
import app.cadence.core.domain.model.MenstrualFlow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DayEntryTest {
    private val day = d(2026, 3, 1)

    @Test
    fun `an entry with nothing set is empty`() {
        assertTrue(DayEntry(day).isEmpty)
        assertFalse(DayEntry(day).hasDetails)
    }

    @Test
    fun `flow alone is not a detail`() {
        val entry = DayEntry(day, flow = MenstrualFlow.HEAVY)
        assertFalse(entry.isEmpty)
        assertFalse(entry.hasDetails)
    }

    @Test
    fun `any other field counts as a detail`() {
        listOf(
            DayEntry(day, symptoms = mapOf("cramps" to Severity.MILD)),
            DayEntry(day, moods = setOf("calm")),
            DayEntry(day, painLevel = 0),
            DayEntry(day, energy = 3),
            DayEntry(day, sleep = 1),
            DayEntry(day, stress = 5),
            DayEntry(day, sex = SexActivity.NONE),
            DayEntry(day, note = "x"),
        ).forEach { assertTrue(it.hasDetails, it.toString()) }
    }

    @Test
    fun `scales match the log sheet`() {
        assertEquals(0..10, DayEntry.PAIN_RANGE)
        assertEquals(1..5, DayEntry.RATING_RANGE)
    }

    @Test
    fun `values outside their scales are rejected`() {
        assertThrows<IllegalArgumentException> { DayEntry(day, painLevel = 11) }
        assertThrows<IllegalArgumentException> { DayEntry(day, painLevel = -1) }
        assertThrows<IllegalArgumentException> { DayEntry(day, energy = 0) }
        assertThrows<IllegalArgumentException> { DayEntry(day, sleep = 6) }
        assertThrows<IllegalArgumentException> { DayEntry(day, stress = 0) }
        assertThrows<IllegalArgumentException> { DayEntry(day, note = "x".repeat(DayEntry.MAX_NOTE_LENGTH + 1)) }
    }

    @Test
    fun `values at the ends of their scales are accepted`() {
        DayEntry(day, painLevel = 0, energy = 1, sleep = 5, stress = 1, note = "x".repeat(DayEntry.MAX_NOTE_LENGTH))
        DayEntry(day, painLevel = 10, energy = 5, sleep = 1, stress = 5)
    }

    @Test
    fun `tapping a symptom cycles its severity`() {
        val steps =
            generateSequence(
                DayEntry(day),
            ) { it.cycleSymptom("cramps") }.take(5).map { it.symptoms["cramps"] }.toList()
        assertEquals(listOf(null, Severity.MILD, Severity.MODERATE, Severity.SEVERE, null), steps)
    }

    @Test
    fun `cycling one symptom leaves others alone`() {
        val entry = DayEntry(day, symptoms = mapOf("acne" to Severity.SEVERE)).cycleSymptom("cramps")
        assertEquals(mapOf("acne" to Severity.SEVERE, "cramps" to Severity.MILD), entry.symptoms)
    }

    @Test
    fun `moods toggle`() {
        val on = DayEntry(day).toggleMood("calm")
        assertEquals(setOf("calm"), on.moods)
        assertEquals(emptySet(), on.toggleMood("calm").moods)
    }
}

class CustomTagTest {
    @Test
    fun `custom tags toggle and appear among pattern items`() {
        val entry =
            DayEntry(d(2026, 3, 1), symptoms = mapOf("cramps" to Severity.MILD), moods = setOf("calm"))
                .toggleTag(4)
        assertEquals(setOf(4L), entry.tags)
        assertEquals(setOf("cramps", "calm", "tag:4"), entry.patternItems())
        assertEquals(emptySet(), entry.toggleTag(4).tags)
        assertEquals(4L, CustomTag.fromItemId("tag:4"))
        assertEquals(null, CustomTag.fromItemId("cramps"))
        assertEquals(null, CustomTag.fromItemId("tag:x"))
    }

    @Test
    fun `labels are validated`() {
        assertThrows<IllegalArgumentException> { CustomTag(1, " ") }
        assertThrows<IllegalArgumentException> { CustomTag(1, "x".repeat(31)) }
        assertEquals("Travel", CustomTag(1, "Travel").label)
    }
}
