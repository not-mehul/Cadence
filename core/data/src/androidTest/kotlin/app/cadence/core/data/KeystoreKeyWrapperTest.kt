package app.cadence.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cadence.core.data.security.KeyUnavailableException
import app.cadence.core.data.security.KeystoreKeyWrapper
import app.cadence.core.data.security.WrappedKey
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class KeystoreKeyWrapperTest {
    private val alias = "cadence_test_kek"
    private val wrapper = KeystoreKeyWrapper(alias)

    @After
    fun deleteKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test
    fun wrapAndUnwrapRoundTrip() {
        val secret = ByteArray(32) { it.toByte() }

        assertContentEquals(secret, wrapper.unwrap(wrapper.wrap(secret)))
    }

    @Test
    fun eachWrapUsesAFreshIv() {
        val secret = ByteArray(32)

        assertFalse(wrapper.wrap(secret).iv.contentEquals(wrapper.wrap(secret).iv))
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val wrapped = wrapper.wrap(ByteArray(32))
        val tampered = WrappedKey(wrapped.iv, wrapped.ciphertext.copyOf().also { it[0] = (it[0] + 1).toByte() })

        assertFailsWith<KeyUnavailableException> { wrapper.unwrap(tampered) }
    }

    @Test
    fun missingKeystoreKeyIsReported() {
        val wrapped = wrapper.wrap(ByteArray(32))
        deleteKey()

        assertFailsWith<KeyUnavailableException> { wrapper.unwrap(wrapped) }
    }
}
