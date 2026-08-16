package app.phacteur.android.auth

import androidx.activity.ComponentActivity
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialException
import app.phacteur.android.data.Passkey
import app.phacteur.android.data.ApiException
import app.phacteur.android.data.PhacteurApi
import app.phacteur.android.data.User
import kotlinx.coroutines.CancellationException

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
        } catch (_: GetCredentialUnsupportedException) {
            error("Le gestionnaire d’identifiants ne prend pas en charge les passkeys sur ce téléphone")
        } catch (_: GetCredentialException) {
            error("Échec de la connexion par passkey")
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            error(
                passkeyErrorMessage(
                    action = "connexion",
                    error = error,
                ),
            )
        }
        return try {
            api.verifyPasskeyAuthentication(credential.authenticationResponseJson)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            error(passkeyErrorMessage(action = "vérification", error = error))
        }
    }

    suspend fun register(activity: ComponentActivity, name: String): Passkey {
        val optionsJson = api.passkeyRegistrationOptions()
        val response = try {
            CredentialManager.create(activity).createCredential(
                context = activity,
                request = CreatePublicKeyCredentialRequest(requestJson = optionsJson),
            ) as? CreatePublicKeyCredentialResponse
                ?: error("Le gestionnaire d’identifiants n’a pas créé de passkey")
        } catch (_: CreateCredentialException) {
            error("Impossible d’enregistrer une passkey sur ce téléphone")
        } catch (_: GetCredentialException) {
            error("La création d’une passkey n’est pas prise en charge par ce téléphone")
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            error(passkeyErrorMessage(action = "création", error = error))
        }
        return try {
            api.verifyPasskeyRegistration(name, response.registrationResponseJson)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            error(passkeyErrorMessage(action = "enregistrement", error = error))
        }
    }

    private fun passkeyErrorMessage(action: String, error: Throwable): String = when {
        error is ApiException && error.message?.contains("not supported by this app", ignoreCase = true) == true ->
            "Les passkeys ne sont pas activées pour cette version de l’application. Ouvrez le site web pour continuer."
        error is GetPublicKeyCredentialException ->
            "La vérification par passkey a échoué avec ce gestionnaire d’identifiants"
        error is CreateCredentialException ->
            "L’enregistrement par passkey n’est pas pris en charge par ce terminal"
        else ->
            error.message?.ifBlank { "Une erreur est survenue pendant la $action passkey" }
                ?: "Une erreur est survenue pendant la $action passkey"
    }
}
