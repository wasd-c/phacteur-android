package app.phacteur.android.data

import java.text.Normalizer

const val DEFAULT_MAILBOX_GROUP_COLOR = "#1f467d"

/** Ordered mailbox IDs are part of the group, including accounts that are paused. */
data class MailboxGroupDraft(
    val name: String = "",
    val color: String? = DEFAULT_MAILBOX_GROUP_COLOR,
    val memberAccountIds: List<Int> = emptyList(),
) {
    fun normalized(): MailboxGroupDraft = copy(
        name = Normalizer.normalize(name.trim(), Normalizer.Form.NFC),
        color = color?.lowercase(),
    )

    fun validationError(ownedAccountIds: Set<Int>? = null): String? {
        val normalizedName = normalized().name
        return when {
            normalizedName.isBlank() -> "Donnez un nom à ce groupe."
            normalizedName.length > 80 -> "Le nom doit contenir au maximum 80 caractères."
            normalizedName.any { it.code in 0..31 || it.code in 127..159 } -> "Le nom doit tenir sur une seule ligne."
            color != null && !color.matches(Regex("#[0-9a-fA-F]{6}")) -> "Choisissez une couleur valide."
            memberAccountIds.isEmpty() -> "Sélectionnez au moins une boîte mail."
            memberAccountIds.size > 100 -> "Un groupe peut contenir au maximum 100 boîtes."
            memberAccountIds.any { it <= 0 } -> "Une boîte mail de ce groupe est invalide."
            memberAccountIds.distinct().size != memberAccountIds.size -> "Une boîte mail ne peut apparaître qu’une fois dans un groupe."
            ownedAccountIds != null && memberAccountIds.any { it !in ownedAccountIds } -> "Une boîte mail de ce groupe n’est plus disponible."
            else -> null
        }
    }

    fun toggleAccount(accountId: Int): MailboxGroupDraft {
        if (accountId <= 0) return this
        return copy(memberAccountIds = if (accountId in memberAccountIds) {
            memberAccountIds.filterNot { it == accountId }
        } else {
            memberAccountIds + accountId
        })
    }

    fun moveAccount(accountId: Int, direction: Int): MailboxGroupDraft {
        if (direction != -1 && direction != 1) return this
        val index = memberAccountIds.indexOf(accountId)
        val target = index + direction
        if (index < 0 || target !in memberAccountIds.indices) return this
        val reordered = memberAccountIds.toMutableList()
        reordered[index] = reordered[target].also { reordered[target] = reordered[index] }
        return copy(memberAccountIds = reordered)
    }

    companion object {
        fun fromGroup(group: MailboxGroup, ownedAccountIds: Set<Int>): MailboxGroupDraft = MailboxGroupDraft(
            name = group.name,
            color = group.color,
            memberAccountIds = group.members.map(MailboxGroupMember::emailAccountId)
                .filter { it in ownedAccountIds }.distinct(),
        )
    }
}
