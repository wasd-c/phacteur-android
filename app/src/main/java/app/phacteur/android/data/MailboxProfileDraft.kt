package app.phacteur.android.data

data class MailboxProfileDraft(val displayName: String = "", val replyTo: String = "") {
    fun normalized() = copy(displayName = displayName.trim(), replyTo = replyTo.trim())

    fun validationError(): String? {
        val value = normalized()
        if (value.displayName.length > 200 || value.displayName.any { it == '\r' || it == '\n' }) {
            return "Le nom doit tenir sur une ligne de 200 caractères maximum."
        }
        if (value.replyTo.isNotEmpty() && (value.replyTo.length > 320 ||
                !Regex("^[^\\s<>,;@]+@[^\\s<>,;@]+\\.[^\\s<>,;@]+$").matches(value.replyTo))) {
            return "Saisissez une adresse de réponse valide."
        }
        return null
    }
}
