package app.phacteur.android.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal object MobileAuthProtocol {
    private val urlSafeValue = Regex("^[A-Za-z0-9_-]{16,256}$")

    fun randomUrlSafe(bytes: Int, random: SecureRandom = SecureRandom()): String {
        require(bytes > 0)
        return encodeUrlSafe(ByteArray(bytes).also(random::nextBytes))
    }

    fun codeChallenge(verifier: String): String = encodeUrlSafe(
        MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
    )

    fun isValidUrlSafeValue(value: String): Boolean = urlSafeValue.matches(value)

    fun isFresh(createdAt: Long, now: Long, ttlMillis: Long): Boolean =
        ttlMillis >= 0 && now - createdAt in 0..ttlMillis

    fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
        left.toByteArray(Charsets.UTF_8),
        right.toByteArray(Charsets.UTF_8),
    )

    internal fun encodeUrlSafe(value: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)
}
