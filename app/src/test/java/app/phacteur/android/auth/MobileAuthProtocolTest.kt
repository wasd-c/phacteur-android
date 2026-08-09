package app.phacteur.android.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileAuthProtocolTest {
    @Test
    fun `creates the RFC 7636 S256 challenge`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            MobileAuthProtocol.codeChallenge(verifier),
        )
    }

    @Test
    fun `accepts only bounded base64url callback values`() {
        assertTrue(MobileAuthProtocol.isValidUrlSafeValue("abcdEFGHijklMNOP_123-456"))
        assertFalse(MobileAuthProtocol.isValidUrlSafeValue("too-short"))
        assertFalse(MobileAuthProtocol.isValidUrlSafeValue("abcdefghijklmnop="))
        assertFalse(MobileAuthProtocol.isValidUrlSafeValue("abcdefghijklmnop/"))
    }

    @Test
    fun `rejects future and expired authorization state`() {
        val now = 1_000_000L
        val ttl = 300_000L

        assertTrue(MobileAuthProtocol.isFresh(now - ttl, now, ttl))
        assertFalse(MobileAuthProtocol.isFresh(now - ttl - 1, now, ttl))
        assertFalse(MobileAuthProtocol.isFresh(now + 1, now, ttl))
    }

    @Test
    fun `compares returned state exactly`() {
        assertTrue(MobileAuthProtocol.constantTimeEquals("same-state-value", "same-state-value"))
        assertFalse(MobileAuthProtocol.constantTimeEquals("same-state-value", "other-state-valu"))
    }
}
