package app.phacteur.android.data

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.IDN
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Locale

data class EmailMetadata(
    val sender: String? = null,
    val senderName: String? = null,
    val replyTo: String? = null,
    /** The receiving mailbox, not a reconstructed To header. */
    val recipient: String? = null,
    val receivedAt: String? = null,
    val recordedAt: String? = null,
    val direction: String? = null,
    val mailedBy: String? = null,
    val signedBy: String? = null,
    val authenticationHeaders: List<EmailMetadataHeader> = emptyList(),
    val publicEncryptionKey: EmailPublicEncryptionKey? = null,
)

data class EmailMetadataHeader(val name: String, val value: String, val source: String)

/** A key supplied by the message; its presence proves neither sender identity nor encryption. */
data class EmailPublicEncryptionKey(val format: String, val value: String, val source: String)

fun parseEmailMetadata(value: JSONObject): EmailMetadata = parseEmailMetadataFields(
    metadataFields.associateWith { name -> value.opt(name).takeUnless { it == JSONObject.NULL } } +
        ("metadata" to value.optJSONObject("metadata")?.let { metadata ->
            mapOf(
                "headerProvenance" to metadata.optString("headerProvenance"),
                "mailedBy" to metadata.opt("mailedBy"),
                "signedBy" to metadata.opt("signedBy"),
                "authenticationHeaders" to metadata.optJSONArray("authenticationHeaders")?.let { headers ->
                    (0 until minOf(headers.length(), 8)).mapNotNull { index ->
                        headers.optJSONObject(index)?.let { header ->
                            mapOf("name" to header.opt("name"), "value" to header.opt("value"), "source" to header.opt("source"))
                        }
                    }
                },
                "publicEncryptionKey" to metadata.optJSONObject("publicEncryptionKey")?.let { key ->
                    mapOf("format" to key.opt("format"), "value" to key.opt("value"), "source" to key.opt("source"))
                },
            )
        }),
)

/** Pure counterpart for fixture tests and older responses with missing metadata. */
fun parseEmailMetadataFields(fields: Map<String, Any?>): EmailMetadata {
    val metadata = fields["metadata"] as? Map<*, *>
    val declaredHeaders = metadata?.get("headerProvenance") in setOf("provided_header", "trusted_reception")
    val headers = (metadata?.get("authenticationHeaders") as? List<*>)?.take(8).orEmpty()
        .mapNotNull { item ->
            val header = item as? Map<*, *> ?: return@mapNotNull null
            val name = cleanMetadataText(header["name"], 80) ?: return@mapNotNull null
            if (name.lowercase(Locale.ROOT) !in authenticationHeaderNames) return@mapNotNull null
            val value = cleanMetadataText(header["value"], 8_192) ?: return@mapNotNull null
            val source = header["source"] as? String
            if (source !in setOf("provided_header", "trusted_reception")) return@mapNotNull null
            EmailMetadataHeader(name, value, source!!)
        }
    val key = (metadata?.get("publicEncryptionKey") as? Map<*, *>)?.let { key ->
        val value = key["value"] as? String ?: return@let null
        val format = key["format"] as? String ?: return@let null
        val source = key["source"] as? String ?: return@let null
        parseEmailPublicEncryptionKey(value, format, source)
    }
    return EmailMetadata(
        sender = cleanMetadataText(fields["sender"], 512),
        senderName = cleanMetadataText(fields["senderName"], 512),
        replyTo = cleanMetadataText(fields["replyTo"], 1_024),
        recipient = cleanMetadataText(fields["recipient"], 512),
        receivedAt = cleanMetadataText(fields["receivedAt"], 80),
        recordedAt = cleanMetadataText(fields["recordedAt"], 80),
        direction = (fields["direction"] as? String)?.takeIf { it in setOf("RECEIVED", "SENT") },
        // These domains are declarations, never authentication or signature verification.
        mailedBy = if (declaredHeaders) metadataDomain(metadata?.get("mailedBy")) else null,
        signedBy = if (declaredHeaders) metadataDomain(metadata?.get("signedBy")) else null,
        authenticationHeaders = headers,
        publicEncryptionKey = key,
    )
}

/** No extraction from body, filenames, DKIM keys, account certificates or owner vault descriptors. */
fun parseEmailPublicEncryptionKey(value: String, format: String, source: String): EmailPublicEncryptionKey? {
    if (source !in setOf("sender_header", "sender_attachment") || value.length !in 32..65_536 ||
        value.any { it.code !in 32..126 && it !in "\t\r\n" } ||
        privateKeyMarker.containsMatchIn(value)) return null
    val valid = when (format) {
        "OPENPGP" -> isPublicOpenPgpBlock(value)
        "SMIME" -> isEmailEncryptionCertificate(value)
        else -> false
    }
    return if (valid) EmailPublicEncryptionKey(format, value, source) else null
}

private fun cleanMetadataText(value: Any?, limit: Int): String? = (value as? String)?.takeIf { it.length <= limit }
    ?.replace(metadataControls, " ")?.trim()?.takeIf(String::isNotEmpty)

private fun metadataDomain(value: Any?): String? = runCatching {
    val text = cleanMetadataText(value, 253) ?: return null
    val domain = IDN.toASCII(text, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
    domain.takeIf { it.contains('.') && !it.endsWith('.') && !it.contains('@') && it.split('.').all { part -> part.isNotEmpty() && part.length <= 63 } }
}.getOrNull()

private fun isPublicOpenPgpBlock(value: String): Boolean = runCatching {
    val match = openPgpArmor.matchEntire(value.trim()) ?: return false
    val body = match.groupValues[1].lineSequence().map(String::trim).filter(String::isNotEmpty)
        .filterNot { it.startsWith("Version:") || it.startsWith("Comment:") || it.startsWith('=') }.joinToString("")
    val data = Base64.getDecoder().decode(body)
    if (data.size > 24_576) return false
    var offset = 0
    var first = true
    var hasEncryptionKey = false
    while (offset < data.size) {
        val tagByte = data[offset++].toInt() and 0xff
        if (tagByte and 0x80 == 0) return false
        val newFormat = tagByte and 0x40 != 0
        val tag = if (newFormat) tagByte and 0x3f else tagByte shr 2 and 0x0f
        if (tag in setOf(5, 7) || first && tag != 6 || tag !in setOf(2, 6, 10, 12, 13, 14, 17, 21)) return false
        first = false
        if (offset >= data.size) return false
        val size: Long
        if (newFormat) {
            val firstLength = data[offset++].toInt() and 0xff
            size = when {
                firstLength < 192 -> firstLength.toLong()
                firstLength < 224 -> {
                    if (offset >= data.size) return false
                    ((firstLength - 192) shl 8).toLong() + (data[offset++].toInt() and 0xff) + 192
                }
                firstLength == 255 -> {
                    if (offset + 4 > data.size) return false
                    readPacketLength(data, offset, 4).also { offset += 4 }
                }
                else -> return false // Partial packet lengths are not needed for bounded public keys.
            }
        } else {
            val sizeBytes = when (tagByte and 3) { 0 -> 1; 1 -> 2; 2 -> 4; else -> return false }
            if (offset + sizeBytes > data.size) return false
            size = readPacketLength(data, offset, sizeBytes)
            offset += sizeBytes
        }
        if (size <= 0 || size > data.size - offset) return false
        if (tag in setOf(6, 14)) {
            if (!validPublicKeyMaterial(data.copyOfRange(offset, offset + size.toInt()))) return false
            if ((data[offset + 5].toInt() and 0xff) in setOf(1, 2, 16, 18, 25, 26)) hasEncryptionKey = true
        }
        offset += size.toInt()
    }
    !first && hasEncryptionKey
}.getOrDefault(false)

private fun readPacketLength(data: ByteArray, offset: Int, size: Int): Long =
    (offset until offset + size).fold(0L) { length, index -> (length shl 8) or (data[index].toInt() and 0xff).toLong() }

private fun validPublicKeyMaterial(data: ByteArray): Boolean {
    if (data.size < 6 || (data[0].toInt() and 0xff) !in setOf(4, 5, 6)) return false
    val algorithm = data[5].toInt() and 0xff
    var offset = 6
    if (data[0].toInt() != 4) {
        if (data.size < 10 || readPacketLength(data, 6, 4) != (data.size - 10).toLong()) return false
        offset = 10
    }
    fun mpi(): Boolean {
        if (offset + 2 > data.size) return false
        val bits = readPacketLength(data, offset, 2).toInt()
        offset += 2
        val length = (bits + 7) / 8
        if (bits !in 1..16_384 || offset + length > data.size) return false
        val first = data[offset].toInt() and 0xff
        if (first == 0 || 32 - Integer.numberOfLeadingZeros(first) != (bits - 1) % 8 + 1) return false
        offset += length
        return true
    }
    val count = mapOf(1 to 2, 2 to 2, 3 to 2, 16 to 3, 17 to 4)[algorithm]
    if (count != null) {
        repeat(count) { if (!mpi()) return false }
    } else if (algorithm in setOf(18, 19, 22)) {
        if (offset >= data.size) return false
        val curveLength = data[offset++].toInt() and 0xff
        if (curveLength == 0 || offset + curveLength > data.size) return false
        offset += curveLength
        if (!mpi()) return false
        if (algorithm == 18) {
            if (offset >= data.size) return false
            val kdfLength = data[offset++].toInt() and 0xff
            if (kdfLength != 3 || offset + kdfLength > data.size) return false
            offset += kdfLength
        }
    } else {
        val length = mapOf(25 to 32, 26 to 56, 27 to 32, 28 to 57)[algorithm] ?: return false
        if (data[0].toInt() != 6) return false
        offset += length
    }
    return offset == data.size
}

private fun isEmailEncryptionCertificate(value: String): Boolean = runCatching {
    if (!certificateArmor.matches(value.trim())) return false
    val certificate = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(value.toByteArray())) as X509Certificate
    val usage = certificate.keyUsage
    val canEncrypt = usage == null || listOf(2, 3, 4).any { index -> usage.getOrNull(index) == true }
    val purposes = certificate.extendedKeyUsage
    canEncrypt && (purposes == null || "1.3.6.1.5.5.7.3.4" in purposes || "2.5.29.37.0" in purposes) &&
        certificate.publicKey.algorithm in setOf("RSA", "EC", "XDH", "X25519", "X448")
}.getOrDefault(false)

private val metadataFields = setOf("sender", "senderName", "replyTo", "recipient", "receivedAt", "recordedAt", "direction")
private val authenticationHeaderNames = setOf("authentication-results", "received-spf", "dkim-signature", "return-path")
private val metadataControls = Regex("[\\u0000-\\u001f\\u007f]+")
private val privateKeyMarker = Regex("PRIVATE KEY|SECRET KEY|-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE", RegexOption.IGNORE_CASE)
private val openPgpArmor = Regex("-----BEGIN PGP PUBLIC KEY BLOCK-----\\s*([\\s\\S]+?)\\s*-----END PGP PUBLIC KEY BLOCK-----")
private val certificateArmor = Regex("-----BEGIN CERTIFICATE-----\\s*[A-Za-z0-9+/=\\s]+\\s*-----END CERTIFICATE-----")
