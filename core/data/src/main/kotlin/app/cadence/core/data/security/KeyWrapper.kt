package app.cadence.core.data.security

/** Encrypts small secrets (the database key) with a key that never leaves secure hardware. */
interface KeyWrapper {
    fun wrap(plaintext: ByteArray): WrappedKey

    /** @throws KeyUnavailableException if the wrapping key is gone or the data was tampered with. */
    fun unwrap(wrapped: WrappedKey): ByteArray
}

class WrappedKey(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

/**
 * The database key can't be recovered, for example after the Keystore was cleared by
 * a device reset. Phase 5 surfaces this to the user with a restore-from-export path
 * (docs/PLAN.md §8).
 */
class KeyUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
