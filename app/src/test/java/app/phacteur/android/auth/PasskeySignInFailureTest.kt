package app.phacteur.android.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.domerrors.SecurityError
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PasskeySignInFailureTest {
    @Test
    fun cancellationDoesNotSuggestAnotherSignInFlow() {
        val original = GetCredentialCancellationException("Dismissed")
        val failure = passkeySignInFailure(original)
        assertTrue(failure is CancellationException)
        assertFalse(failure is PasskeySignInUnavailableException)
        assertSame(original, failure.cause)
    }

    @Test
    fun providerOriginRejectionOffersBrowserWithoutDiscardingCause() {
        val original = GetPublicKeyCredentialDomException(
            SecurityError(),
            "Passkeys not supported for this app",
        )
        val failure = passkeySignInFailure(original)
        assertTrue(failure is PasskeySignInUnavailableException)
        assertTrue(failure.message!!.contains("Continuez sur le site"))
        assertSame(original, failure.cause)
    }

    @Test
    fun missingUnsupportedAndCustomProviderErrorsAllowBrowserRecovery() {
        val errors = listOf(
            NoCredentialException(),
            GetCredentialUnsupportedException(),
            GetCredentialCustomException("provider-error", "Provider-specific failure"),
        )
        errors.forEach { original ->
            val failure = passkeySignInFailure(original)
            assertTrue(failure is PasskeySignInUnavailableException)
            assertSame(original, failure.cause)
            assertFalse(failure.message.isNullOrBlank())
        }
    }
}
