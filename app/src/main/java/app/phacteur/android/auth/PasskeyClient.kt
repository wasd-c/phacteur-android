package app.phacteur.android.auth

import androidx.activity.ComponentActivity
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.NoCredentialException
import app.phacteur.android.data.Passkey
import app.phacteur.android.data.PhacteurApi
import app.phacteur.android.data.User

class PasskeyClient(private val api: PhacteurApi) {
    suspend fun signIn(activity: ComponentActivity): User {
        val optionsJson = api.passkeyAuthenticationOptions()
        val request = GetCredentialRequest(
            credentialOptions = listOf(GetPublicKeyCredentialOption(optionsJson)),
        )
        val credential = try {
            CredentialManager.create(activity)
                .getCredential(context = activity, request = request)
                .credential as? PublicKeyCredential
                ?: error("Le gestionnaire d’identifiants n’a pas renvoyé de passkey")
        } catch (_: NoCredentialException) {
            error("Aucune passkey Phacteur n’est disponible sur cet appareil")
        }
        return api.verifyPasskeyAuthentication(credential.authenticationResponseJson)
    }

    suspend fun register(activity: ComponentActivity, name: String): Passkey {
        val optionsJson = api.passkeyRegistrationOptions()
        val response = CredentialManager.create(activity).createCredential(
            context = activity,
            request = CreatePublicKeyCredentialRequest(requestJson = optionsJson),
        ) as? CreatePublicKeyCredentialResponse
            ?: error("Le gestionnaire d’identifiants n’a pas créé de passkey")
        return api.verifyPasskeyRegistration(name, response.registrationResponseJson)
    }
}
