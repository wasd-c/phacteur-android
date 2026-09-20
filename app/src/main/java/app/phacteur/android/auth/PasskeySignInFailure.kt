package app.phacteur.android.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import kotlinx.coroutines.CancellationException

class PasskeySignInUnavailableException(message: String, cause: Throwable) :
    IllegalStateException(message, cause)

internal fun passkeySignInFailure(error: GetCredentialException): Exception = when (error) {
    // Dismissing the provider is not a failure and must never open another login flow.
    is GetCredentialCancellationException -> CancellationException("Connexion annulée", error)
    is NoCredentialException -> PasskeySignInUnavailableException(
        "Aucune clé d’accès Phacteur n’est disponible dans ce gestionnaire. " +
            "Vous pouvez continuer sur le site, puis revenir dans l’application.",
        error,
    )
    is GetCredentialUnsupportedException -> PasskeySignInUnavailableException(
        "Votre gestionnaire ne permet pas la connexion par clé d’accès dans cette application. " +
            "Continuez sur le site avec votre clé d’accès.",
        error,
    )
    else -> PasskeySignInUnavailableException(
        "Votre gestionnaire de clés d’accès a refusé la connexion dans l’application. " +
            "Continuez sur le site avec votre clé d’accès, puis revenez dans l’application.",
        error,
    )
}
