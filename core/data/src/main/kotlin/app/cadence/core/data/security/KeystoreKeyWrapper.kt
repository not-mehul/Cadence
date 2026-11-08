package app.cadence.core.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps secrets with an AES-256-GCM key held in the Android Keystore. The key is
 * non-exportable and, where the device supports it, hardware-backed.
 */
class KeystoreKeyWrapper(
    private val alias: String = DEFAULT_ALIAS,
) : KeyWrapper {
    override fun wrap(plaintext: ByteArray): WrappedKey {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return WrappedKey(iv = cipher.iv, ciphertext = cipher.doFinal(plaintext))
    }

    override fun unwrap(wrapped: WrappedKey): ByteArray {
        val key = existingKey() ?: throw KeyUnavailableException("Keystore key '$alias' is missing")
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, wrapped.iv))
            cipher.doFinal(wrapped.ciphertext)
        } catch (e: GeneralSecurityException) {
            throw KeyUnavailableException("Could not unwrap the database key", e)
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(alias, null) as SecretKey?

    private fun getOrCreateKey(): SecretKey =
        existingKey() ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(KEY_SIZE_BITS)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
            }.generateKey()

    companion object {
        const val DEFAULT_ALIAS = "cadence_database_kek"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val KEY_SIZE_BITS = 256
    }
}
