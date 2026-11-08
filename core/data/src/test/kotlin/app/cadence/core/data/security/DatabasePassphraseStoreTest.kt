package app.cadence.core.data.security

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.SecureRandom
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Robolectric provides android.util.AtomicFile.
@RunWith(RobolectricTestRunner::class)
class DatabasePassphraseStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val wrapper = FakeKeyWrapper()

    private fun store(file: File = File(tmp.root, "db.key")) = DatabasePassphraseStore(file, wrapper, SecureRandom())

    @Test
    fun `first call creates and persists a key`() {
        val file = File(tmp.root, "db.key")
        assertFalse(file.exists())

        store(file).getOrCreate()

        assertTrue(file.exists())
        assertEquals(1, wrapper.wrapCount)
    }

    @Test
    fun `later calls return the same passphrase without creating a new key`() {
        val file = File(tmp.root, "db.key")
        val first = store(file).getOrCreate()
        val second = store(file).getOrCreate()

        assertContentEquals(first, second)
        assertEquals(1, wrapper.wrapCount)
    }

    @Test
    fun `the raw key is never written to disk in the clear`() {
        val file = File(tmp.root, "db.key")
        val passphrase = String(store(file).getOrCreate(), Charsets.US_ASCII)
        val hexKey = passphrase.removePrefix("x'").removeSuffix("'")
        val fileHex = file.readBytes().joinToString("") { "%02x".format(it) }

        assertFalse(fileHex.contains(hexKey))
    }

    @Test
    fun `passphrase is a SQLCipher raw key literal of 256 bits`() {
        val passphrase = String(store().getOrCreate(), Charsets.US_ASCII)

        assertTrue(Regex("^x'[0-9a-f]{64}'$").matches(passphrase), passphrase)
    }

    @Test
    fun `different installs get different keys`() {
        val a = store(File(tmp.root, "a.key")).getOrCreate()
        val b = store(File(tmp.root, "b.key")).getOrCreate()

        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `a lost wrapping key is reported, not replaced`() {
        val file = File(tmp.root, "db.key")
        store(file).getOrCreate()
        wrapper.available = false

        assertFailsWith<KeyUnavailableException> { store(file).getOrCreate() }
        assertEquals(1, wrapper.wrapCount, "must not silently create a new key and orphan the database")
    }

    @Test
    fun `a corrupt key file is reported`() {
        val file = File(tmp.root, "db.key").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertFailsWith<KeyUnavailableException> { store(file).getOrCreate() }
    }

    @Test
    fun `encode and decode round trip`() {
        val wrapped = WrappedKey(iv = ByteArray(12) { it.toByte() }, ciphertext = ByteArray(48) { (it * 3).toByte() })

        val decoded = DatabasePassphraseStore.decode(DatabasePassphraseStore.encode(wrapped))

        assertContentEquals(wrapped.iv, decoded.iv)
        assertContentEquals(wrapped.ciphertext, decoded.ciphertext)
    }
}
