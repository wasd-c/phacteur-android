package app.phacteur.android.data

import org.json.JSONObject

class SessionCookieStore(private val secureStorage: SecureStorage) {
    private data class StoredCookie(val value: String, val expiresAt: Long)

    @Synchronized
    fun cookieHeader(now: Long = System.currentTimeMillis()): String? {
        val cookies = readCookies().filterValues { it.expiresAt > now }.toMutableMap()
        persist(cookies)
        return cookies.entries.joinToString("; ") { (name, cookie) -> "$name=${cookie.value}" }
            .takeIf(String::isNotBlank)
    }

    @Synchronized
    fun update(setCookieHeaders: List<String>, now: Long = System.currentTimeMillis()) {
        if (setCookieHeaders.isEmpty()) return
        val cookies = readCookies().toMutableMap()

        setCookieHeaders.forEach { header ->
            val nameValue = header.substringBefore(';')
            val separator = nameValue.indexOf('=')
            if (separator <= 0) return@forEach
            val name = nameValue.substring(0, separator).trim()
            if (name !in ALLOWED_COOKIE_NAMES) return@forEach
            val value = nameValue.substring(separator + 1).trim()
            val maxAge = MAX_AGE.find(header)?.groupValues?.getOrNull(1)?.toLongOrNull()

            if (value.isEmpty() || (maxAge != null && maxAge <= 0)) {
                cookies.remove(name)
            } else {
                val lifetimeSeconds = maxAge ?: DEFAULT_LIFETIME_SECONDS
                cookies[name] = StoredCookie(
                    value = value,
                    expiresAt = now + lifetimeSeconds.coerceAtMost(MAX_LIFETIME_SECONDS) * 1_000,
                )
            }
        }

        persist(cookies)
    }

    @Synchronized
    fun hasSession(): Boolean = readCookies()[SESSION_COOKIE]?.expiresAt?.let {
        it > System.currentTimeMillis()
    } == true

    @Synchronized
    fun clear() = secureStorage.remove(STORAGE_KEY)

    private fun readCookies(): Map<String, StoredCookie> {
        val raw = secureStorage.getString(STORAGE_KEY) ?: return emptyMap()
        return runCatching {
            val objectValue = JSONObject(raw)
            buildMap {
                objectValue.keys().forEach { name ->
                    if (name !in ALLOWED_COOKIE_NAMES) return@forEach
                    val cookie = objectValue.optJSONObject(name) ?: return@forEach
                    val value = cookie.optString("value")
                    val expiresAt = cookie.optLong("expiresAt")
                    if (value.isNotBlank() && expiresAt > 0) put(name, StoredCookie(value, expiresAt))
                }
            }
        }.getOrElse {
            secureStorage.remove(STORAGE_KEY)
            emptyMap()
        }
    }

    private fun persist(cookies: Map<String, StoredCookie>) {
        if (cookies.isEmpty()) {
            secureStorage.remove(STORAGE_KEY)
            return
        }
        val value = JSONObject()
        cookies.forEach { (name, cookie) ->
            value.put(name, JSONObject().put("value", cookie.value).put("expiresAt", cookie.expiresAt))
        }
        secureStorage.putString(STORAGE_KEY, value.toString())
    }

    private companion object {
        const val STORAGE_KEY = "http_cookies"
        const val SESSION_COOKIE = "user-session"
        const val DEFAULT_LIFETIME_SECONDS = 5 * 60L
        const val MAX_LIFETIME_SECONDS = 7 * 24 * 60 * 60L
        val MAX_AGE = Regex("(?:^|;)\\s*Max-Age=(-?\\d+)", RegexOption.IGNORE_CASE)
        val ALLOWED_COOKIE_NAMES = setOf(
            SESSION_COOKIE,
            "passkey-authentication",
            "passkey-registration",
        )
    }
}
