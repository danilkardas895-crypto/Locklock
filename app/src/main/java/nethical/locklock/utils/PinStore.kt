package nethical.locklock.utils

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.core.content.edit

private const val PREFS_NAME = "pin"

/**
 * Single place that reads/writes the PIN and the recovery-question answer.
 *
 * SECURITY FIX: the PIN and the recovery answer used to be stored as plain
 * strings in SharedPreferences and compared with `==`. Anyone with a rooted
 * device, an adb backup, or access to the app's data directory could read
 * them directly. They are now stored as a salted SHA-256 hash (see
 * [SecretHasher]) and only ever compared, never read back out.
 *
 * A device upgrading from the old version will still have the legacy
 * plaintext "pin"/"recovery_answer" entries. [verifyPin] and
 * [verifyRecoveryAnswer] transparently check against those once, and if they
 * match, silently re-save them as a hash — so nobody gets locked out of
 * their own app by this fix.
 */
object PinStore {

    fun isPinSet(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sp.contains("pin_hash") || sp.contains("pin")
    }

    fun isRecoveryQuestionSet(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sp.contains("recovery_question")
    }

    fun setPin(context: Context, pin: String) {
        val sp = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val (hash, salt) = SecretHasher.hash(pin)
        sp.edit(commit = true) {
            putString("pin_hash", hash)
            putString("pin_salt", salt)
            remove("pin") // drop the old plaintext copy, if any
        }
    }

    fun verifyPin(context: Context, candidate: String): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val hash = sp.getString("pin_hash", null)
        val salt = sp.getString("pin_salt", null)
        if (hash != null && salt != null) {
            return SecretHasher.verify(candidate, salt, hash)
        }
        // Legacy plaintext PIN (or the factory default "0000" if none was ever set)
        val legacyPin = sp.getString("pin", "0000")
        val matches = candidate == legacyPin
        if (matches) setPin(context, candidate) // upgrade to a hash now that we know it's correct
        return matches
    }

    fun setRecoveryQuestion(context: Context, question: String, answer: String) {
        val sp = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val (hash, salt) = SecretHasher.hash(answer.trim().lowercase())
        sp.edit(commit = true) {
            putString("recovery_question", question)
            putString("recovery_answer_hash", hash)
            putString("recovery_answer_salt", salt)
            remove("recovery_answer") // drop the old plaintext copy, if any
        }
    }

    fun getRecoveryQuestion(context: Context): String {
        val sp = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sp.getString("recovery_question", "The default pin is 0000").orEmpty()
    }

    fun verifyRecoveryAnswer(context: Context, candidate: String): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val normalized = candidate.trim().lowercase()
        val hash = sp.getString("recovery_answer_hash", null)
        val salt = sp.getString("recovery_answer_salt", null)
        if (hash != null && salt != null) {
            return SecretHasher.verify(normalized, salt, hash)
        }
        // Legacy plaintext answer
        val legacyAnswer = sp.getString("recovery_answer", null)?.trim()?.lowercase()
        val matches = legacyAnswer != null && normalized == legacyAnswer
        if (matches) setRecoveryQuestion(context, getRecoveryQuestion(context), candidate)
        return matches
    }

    /** Milliseconds left before another recovery-answer attempt is allowed (0 if none). */
    fun recoveryCooldownRemainingMs(context: Context): Long {
        val sp = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val end = sp.getLong("sq_cooldown_end", 0L)
        val remaining = end - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }

    /**
     * SECURITY FIX: sq_failed_attempts / sq_cooldown_end used to be written
     * on a wrong answer but never actually checked anywhere, so the cooldown
     * had no real effect and the recovery answer could be brute-forced with
     * unlimited attempts. This is now called on every attempt and
     * [recoveryCooldownRemainingMs] is checked before allowing the next one.
     */
    fun recordRecoveryAttempt(context: Context, success: Boolean) {
        val sp = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (success) {
            sp.edit(commit = true) {
                remove("sq_failed_attempts")
                remove("sq_cooldown_end")
            }
            return
        }
        val failedAttempts = sp.getInt("sq_failed_attempts", 0) + 1
        val cooldown = if (failedAttempts >= 3) failedAttempts * 30 * 1000L else 0L
        sp.edit(commit = true) {
            putInt("sq_failed_attempts", failedAttempts)
            if (cooldown > 0) putLong("sq_cooldown_end", System.currentTimeMillis() + cooldown)
        }
    }
}
