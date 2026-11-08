package app.cadence.core.domain.log

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogCatalogTest {
    @Test
    fun `ids are unique and storage-safe`() {
        val ids = LogCatalog.symptoms + LogCatalog.moods
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue(Regex("^[a-z_]+$").matches(it), it) }
    }

    @Test
    fun `everything but flow can be hidden`() {
        assertFalse(LogCategory.FLOW.canBeHidden)
        LogCategory.entries.filter { it != LogCategory.FLOW }.forEach { assertTrue(it.canBeHidden) }
    }

    @Test
    fun `hidden categories round trip`() {
        val hidden = setOf(LogCategory.SEX, LogCategory.MOOD)
        assertEquals("MOOD,SEX", LogCategory.formatSet(hidden))
        assertEquals(hidden, LogCategory.parseSet(LogCategory.formatSet(hidden)))
    }

    @Test
    fun `parsing ignores flow, blanks and unknown names`() {
        assertEquals(emptySet(), LogCategory.parseSet(""))
        assertEquals(setOf(LogCategory.NOTES), LogCategory.parseSet("FLOW,NOTES,FUTURE_THING"))
        assertEquals("", LogCategory.formatSet(setOf(LogCategory.FLOW)))
    }
}
