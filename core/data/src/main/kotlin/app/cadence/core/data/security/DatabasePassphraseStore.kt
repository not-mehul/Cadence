package app.cadence.core.data.security

import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.SecureRandom

/**
 * Creates the database key on first launch and stores it wrapped by [KeyWrapper].
 *
 * The stored file lives in `noBackupFilesDir` and is useless without the device's
 * Keystore, so it never leaves the phone (docs/PLAN.md §2.7).
 */
class DatabasePassphraseStore(
    private val file: File,
    private val wrapper: KeyWrapper,
    private val random: SecureRandom = SecureRandom(),
) {
    /**
     * Returns the SQLCipher passphrase, creating and persisting a new random key if none exists.
     *
     * @throws KeyUnavailableException if a key file exists but can't be read or unwrapped.
     */
    @Synchronized
    fun getOrCreate(): ByteArray {
        val rawKey = if (file.exists()) read() else create()
        return rawKeyPassphrase(rawKey).also { rawKey.fill(0) }
    }

    private fun create(): ByteArray {
        val rawKey = ByteArray(KEY_BYTES).also(random::nextBytes)
        val wrapped = wrapper.wrap(rawKey)
        val atomicFile = AtomicFile(file)
        val out = atomicFile.startWrite()
        try {
            out.write(encode(wrapped))
            atomicFile.finishWrite(out)
        } catch (e: IOException) {
            atomicFile.failWrite(out)
            throw e
        }
        return rawKey
    }

    private fun read(): ByteArray {
        val wrapped =
            try {
                decode(AtomicFile(file).readFully())
            } catch (e: IOException) {
                throw KeyUnavailableException("Database key file is unreadable", e)
            }
        return wrapper.unwrap(wrapped).also {
            if (it.size != KEY_BYTES) throw KeyUnavailableException("Database key has the wrong length")
        }
    }

    internal companion object {
        const val KEY_BYTES = 32
        private const val MAGIC = 0x43444B31 // "CDK1"

        /**
         * Formats a raw 256-bit key as SQLCipher's raw-key literal `x'<64 hex>'`. This skips
         * the passphrase KDF, which is unnecessary for a random key and slows every open.
         */
        fun rawKeyPassphrase(rawKey: ByteArray): ByteArray {
            val hex = rawKey.joinToString(separator = "") { "%02x".format(it) }
            return "x'$hex'".toByteArray(Charsets.US_ASCII)
        }

        fun encode(wrapped: WrappedKey): ByteArray =
            ByteArrayOutputStream().use { bytes ->
                DataOutputStream(bytes).use { out ->
                    out.writeInt(MAGIC)
                    out.writeByte(wrapped.iv.size)
                    out.write(wrapped.iv)
                    out.writeInt(wrapped.ciphertext.size)
                    out.write(wrapped.ciphertext)
                }
                bytes.toByteArray()
            }

        fun decode(data: ByteArray): WrappedKey =
            DataInputStream(ByteArrayInputStream(data)).use { input ->
                if (input.readInt() != MAGIC) throw IOException("Not a Cadence key file")
                val iv = ByteArray(input.readUnsignedByte()).also(input::readFully)
                val ciphertext = ByteArray(input.readInt()).also(input::readFully)
                WrappedKey(iv, ciphertext)
            }
    }
}
