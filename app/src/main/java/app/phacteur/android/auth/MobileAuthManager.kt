package app.phacteur.android.auth

import android.net.Uri
import app.phacteur.android.BuildConfig
import app.phacteur.android.data.PhacteurApi
import app.phacteur.android.data.SecureStorage
import app.phacteur.android.data.User
import org.json.JSONObject

class MobileAuthManager(
    private val secureStorage: SecureStorage,
    private val api: PhacteurApi,
) {
    fun createAuthorizationUri(): Uri {
        val state = MobileAuthProtocol.randomUrlSafe(24)
        val verifier = MobileAuthProtocol.randomUrlSafe(32)
        val challenge = MobileAuthProtocol.codeChallenge(verifier)
        secureStorage.putString(
            PENDING_AUTH_KEY,
            JSONObject()
                .put("state", state)
                .put("verifier", verifier)
                .put("createdAt", System.currentTimeMillis())
                .toString(),
        )

        return Uri.parse(BuildConfig.PHACTEUR_BASE_URL).buildUpon()
            .appendPath("mobile-auth")
            .appendQueryParameter("client_id", BuildConfig.MOBILE_AUTH_CLIENT_ID)
            .appendQueryParameter("redirect_uri", BuildConfig.MOBILE_AUTH_REDIRECT_URI)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build()
    }

    suspend fun completeCallback(uri: Uri): User {
        require(uri.scheme == "phacteur" && uri.host == "auth" && uri.path == "/callback") {
            "Lien de connexion invalide"
        }
        val code = uri.getQueryParameter("code").orEmpty()
        val returnedState = uri.getQueryParameter("state").orEmpty()
        require(
            MobileAuthProtocol.isValidUrlSafeValue(code) &&
                MobileAuthProtocol.isValidUrlSafeValue(returnedState)
        ) {
            "Réponse de connexion invalide"
        }

        val pending = secureStorage.getString(PENDING_AUTH_KEY)?.let(::JSONObject)
            ?: error("Cette tentative de connexion n’est plus disponible")
        val createdAt = pending.optLong("createdAt")
        val expectedState = pending.optString("state")
        val verifier = pending.optString("verifier")
        require(MobileAuthProtocol.isFresh(createdAt, System.currentTimeMillis(), AUTH_TTL_MILLIS)) {
            "Cette tentative de connexion a expiré"
        }
        require(MobileAuthProtocol.constantTimeEquals(returnedState, expectedState)) {
            "La réponse de connexion ne correspond pas à cette application"
        }

        return try {
            api.exchangeMobileGrant(
                code = code,
                codeVerifier = verifier,
                clientId = BuildConfig.MOBILE_AUTH_CLIENT_ID,
                redirectUri = BuildConfig.MOBILE_AUTH_REDIRECT_URI,
            )
        } finally {
            secureStorage.remove(PENDING_AUTH_KEY)
        }
    }

    private companion object {
        const val PENDING_AUTH_KEY = "pending_mobile_auth"
        const val AUTH_TTL_MILLIS = 5 * 60 * 1_000L
    }
}
