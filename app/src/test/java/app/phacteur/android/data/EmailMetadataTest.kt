package app.phacteur.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class EmailMetadataTest {
    // Public RSA material only (tiny deterministic test n/e); no private key is generated.
    private val publicPacket = byteArrayOf(0xc6.toByte(), 13, 4, 0, 0, 0, 0, 1, 0, 9, 1, 67, 0, 5, 17)
    private val publicArmor = armor(publicPacket)

    @Test
    fun `older responses retain known identities and do not invent technical fields`() {
        val metadata = parseEmailMetadataFields(mapOf("sender" to "sender@example.com", "senderName" to "Sender", "receivedAt" to "2026-10-03T10:00:00Z"))
        assertEquals("sender@example.com", metadata.sender)
        assertEquals("Sender", metadata.senderName)
        assertNull(metadata.recipient)
        assertNull(metadata.mailedBy)
        assertNull(metadata.signedBy)
        assertNull(metadata.publicEncryptionKey)
        assertTrue(metadata.authenticationHeaders.isEmpty())
    }

    @Test
    fun `mailbox recipient and recorded date preserve their distinct meaning`() {
        val metadata = parseEmailMetadataFields(mapOf(
            "sender" to "sender@example.com", "replyTo" to "reply@example.com", "recipient" to "mailbox@phacteur.app",
            "receivedAt" to "2026-10-03T09:00:00Z", "recordedAt" to "2026-10-03T10:00:00Z", "direction" to "SENT",
        ))
        assertEquals("reply@example.com", metadata.replyTo)
        assertEquals("mailbox@phacteur.app", metadata.recipient)
        assertEquals("2026-10-03T09:00:00Z", metadata.receivedAt)
        assertEquals("2026-10-03T10:00:00Z", metadata.recordedAt)
        assertEquals("SENT", metadata.direction)
    }

    @Test
    fun `declared domains stay usable without turning result text into verification`() {
        val metadata = parseEmailMetadataFields(mapOf("metadata" to mapOf(
            "headerProvenance" to "provided_header", "mailedBy" to "Mail.Example.com", "signedBy" to "sender.example.com",
            "authenticationHeaders" to listOf(mapOf("name" to "Authentication-Results", "value" to "mx.example; dkim=pass header.d=sender.example.com", "source" to "provided_header")),
        )))
        assertEquals("mail.example.com", metadata.mailedBy)
        assertEquals("sender.example.com", metadata.signedBy)
        assertEquals("provided_header", metadata.authenticationHeaders.single().source)
        assertEquals("mx.example; dkim=pass header.d=sender.example.com", metadata.authenticationHeaders.single().value)
        assertNull(metadata.publicEncryptionKey)
        assertNull(parseEmailMetadataFields(mapOf("isVerified" to true, "metadata" to mapOf("signedBy" to "sender.example.com"))).signedBy)
    }

    @Test
    fun `header parser excludes unknown fields and bounds supplied text`() {
        val metadata = parseEmailMetadataFields(mapOf("senderName" to "Sender\nName", "metadata" to mapOf(
            "headerProvenance" to "provided_header", "signedBy" to "https://example.com/auth",
            "authenticationHeaders" to listOf(
                mapOf("name" to "X-Private-Key", "value" to "private", "source" to "provided_header"),
                mapOf("name" to "DKIM-Signature", "value" to "x".repeat(8_193), "source" to "provided_header"),
                mapOf("name" to "Return-Path", "value" to "<sender@example.com>", "source" to "inferred_verified"),
            ),
        )))
        assertEquals("Sender Name", metadata.senderName)
        assertNull(metadata.signedBy)
        assertTrue(metadata.authenticationHeaders.isEmpty())
    }

    @Test
    fun `explicit sender public material is kept byte for byte for copy`() {
        val key = parseEmailPublicEncryptionKey(publicArmor, "OPENPGP", "sender_header")
        assertEquals(publicArmor, key?.value)
        assertEquals("OPENPGP", key?.format)
        assertEquals("sender_header", key?.source)
        val metadata = parseEmailMetadataFields(mapOf("metadata" to mapOf(
            "publicEncryptionKey" to mapOf("format" to "OPENPGP", "value" to publicArmor, "source" to "sender_header"),
        )))
        assertEquals(key, metadata.publicEncryptionKey)
    }

    @Test
    fun `private secret or sign only packets cannot masquerade as encryption public keys`() {
        assertNull(parseEmailPublicEncryptionKey(publicArmor.replace("PUBLIC KEY", "PRIVATE KEY"), "OPENPGP", "sender_header"))
        val secretPacket = publicPacket.copyOf().apply { this[0] = 0xc5.toByte() }
        assertNull(parseEmailPublicEncryptionKey(armor(secretPacket), "OPENPGP", "sender_header"))
        assertNull(parseEmailPublicEncryptionKey(armor(publicPacket + byteArrayOf(0xc7.toByte(), 1, 1)), "OPENPGP", "sender_header"))
        val signingOnly = publicPacket.copyOf().apply { this[7] = 3 }
        assertNull(parseEmailPublicEncryptionKey(armor(signingOnly), "OPENPGP", "sender_header"))
        assertNull(parseEmailPublicEncryptionKey(armor(publicPacket + byteArrayOf(0)), "OPENPGP", "sender_header"))
    }

    @Test
    fun `malformed truncated oversized and mislabeled public material is absent`() {
        assertNull(parseEmailPublicEncryptionKey(armor(publicPacket.copyOf(10)), "OPENPGP", "sender_header"))
        assertNull(parseEmailPublicEncryptionKey(publicArmor.replace("xg0E", "not base64!"), "OPENPGP", "sender_header"))
        assertNull(parseEmailPublicEncryptionKey("x".repeat(65_537), "OPENPGP", "sender_header"))
        assertNull(parseEmailPublicEncryptionKey(publicArmor, "DKIM", "sender_header"))
        assertNull(parseEmailPublicEncryptionKey(publicArmor, "OPENPGP", "owner_vault"))
        assertNull(parseEmailPublicEncryptionKey(publicArmor, "OPENPGP", "dkim_dns"))
        assertNull(parseEmailPublicEncryptionKey("-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----", "SMIME", "sender_attachment"))
    }

    @Test
    fun `filename MIME labels body keys and vault keys are not automatically inferred`() {
        val metadata = parseEmailMetadataFields(mapOf(
            "body" to publicArmor, "htmlBody" to publicArmor,
            "encryptionPublicKey" to mapOf("kty" to "EC", "crv" to "P-256"),
            "attachments" to listOf(mapOf("filename" to "key.asc", "mimeType" to "application/pgp-keys")),
        ))
        assertNull(metadata.publicEncryptionKey)
    }

    private fun armor(data: ByteArray) = "-----BEGIN PGP PUBLIC KEY BLOCK-----\n${Base64.getEncoder().encodeToString(data)}\n-----END PGP PUBLIC KEY BLOCK-----"
}
