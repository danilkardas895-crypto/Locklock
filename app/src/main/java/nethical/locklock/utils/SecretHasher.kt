package nethical.locklock.utils

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Small, dependency-free salted-hash helper used to store secrets (the PIN,
 * the recovery-question answer) without ever keeping them in plain text in
 * SharedPreferences.
 *
 * Deliberately NOT using androidx.security:security-crypto here: that pulls
 * in Google Tink and adds several hundred KB to the APK for something a
 * ~30-line SHA-256 helper already solves well enough for a local PIN lock.
 * Keeps the app lighter while still fixing the real problem (plaintext
 * secrets on disk).
 */
object SecretHasher {

    private const val ALGORITHM = "SHA-256"
    private const val ITERATIONS = 10_000 // slows down brute-force if the prefs file is extracted
    private const val SALT_SIZE = 16

    /** Hashes [secret] with a freshly generated random salt. Returns hash + salt, both hex-encoded. */
    fun hash(secret: String): Pair<String, String> {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        return hashWithSalt(secret, salt) to salt.toHex()
    }

    /** Recomputes the hash of [secret] with the stored [saltHex] and compares it in constant time. */
    fun verify(secret: String, saltHex: String, expectedHashHex: String): Boolean {
        val salt = saltHex.fromHex()
        val computed = hashWithSalt(secret, salt)
        return constantTimeEquals(computed, expectedHashHex)
    }

    private fun hashWithSalt(secret: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        var bytes = secret.toByteArray(Charsets.UTF_8) + salt
        repeat(ITERATIONS) {
            bytes = digest.digest(bytes)
            digest.reset()
        }
        return bytes.toHex()
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray =
        ByteArray(length / 2) { i ->
            ((Character.digit(this[i * 2], 16) shl 4) + Character.digit(this[i * 2 + 1], 16)).toByte()
        }
}
