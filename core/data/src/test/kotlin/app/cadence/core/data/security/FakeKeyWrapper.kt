package app.cadence.core.data.security

/** Reversible stand-in for the Keystore, which Robolectric/JVM tests can't use. */
internal class FakeKeyWrapper : KeyWrapper {
    var available = true
    var wrapCount = 0
        private set

    override fun wrap(plaintext: ByteArray): WrappedKey {
        wrapCount++
        val iv = ByteArray(12) { (it + wrapCount).toByte() }
        return WrappedKey(iv, xor(plaintext, iv))
    }

    override fun unwrap(wrapped: WrappedKey): ByteArray {
        if (!available) throw KeyUnavailableException("fake key gone")
        return xor(wrapped.ciphertext, wrapped.iv)
    }

    private fun xor(
        data: ByteArray,
        iv: ByteArray,
    ) = ByteArray(data.size) { (data[it].toInt() xor iv[it % iv.size].toInt() xor 0x5A).toByte() }
}
