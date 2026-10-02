package app.phacteur.android.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.phacteur.android.data.EmailAccount
import app.phacteur.android.data.MailboxGroup
import app.phacteur.android.data.MailboxGroupDraft
import app.phacteur.android.data.MailboxGroupMember
import app.phacteur.android.ui.screens.GroupsScreen
import app.phacteur.android.ui.theme.PhacteurTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupsScreenInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun creationSavesChosenMailboxOrderIncludingPausedAccountsAndExcludesRelays() {
        val relay = MailboxFixtures.personal.copy(id = 44, email = "relay@example.test", accountType = "RELAY")
        var savedId: String? = "unexpected"
        var savedDraft: MailboxGroupDraft? = null
        compose.setContent {
            TestGroups(accounts = MailboxFixtures.accounts + relay, onSave = { id, draft ->
                savedId = id
                savedDraft = draft
            })
        }

        compose.onNodeWithText("Nouveau groupe").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("  Mon groupe  ")
        closeSoftKeyboard()
        compose.onNodeWithContentDescription("Ajouter ${MailboxFixtures.work.email}").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Ajouter ${MailboxFixtures.inactive.email}").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Monter ${MailboxFixtures.work.email}").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Couleur Prune").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Ajouter ${relay.email}").assertDoesNotExist()
        compose.onNodeWithText("Créer").performScrollTo().performClick()

        compose.runOnIdle {
            assertNull(savedId)
            assertEquals("Mon groupe", savedDraft?.name)
            assertEquals("#765f7c", savedDraft?.color)
            assertEquals(listOf(22, 11, 33), savedDraft?.memberAccountIds)
        }
    }

    @Test
    fun failedSaveKeepsDraftAndSuccessfulRetryClosesTheEditor() {
        var groups by mutableStateOf<List<MailboxGroup>>(emptyList())
        var error by mutableStateOf<String?>(null)
        var mutationVersion by mutableStateOf(0L)
        val attempts = mutableListOf<MailboxGroupDraft>()
        compose.setContent {
            TestGroups(groups = groups, actionError = error, mutationVersion = mutationVersion, onSave = { _, draft ->
                attempts += draft
                if (attempts.size == 1) {
                    error = "Connexion interrompue"
                } else {
                    groups = listOf(groupFromDraft(draft))
                    error = null
                    mutationVersion++
                }
            })
        }

        compose.onNodeWithText("Nouveau groupe").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Mon groupe")
        closeSoftKeyboard()
        compose.onNodeWithText("Créer").performScrollTo().performClick()
        compose.onNodeWithText("Connexion interrompue").performScrollTo().assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("Mon groupe")
        compose.onNodeWithText("Créer").performScrollTo().performClick()

        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Mon groupe").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, attempts.size)
            assertEquals(attempts.first(), attempts.last())
        }
    }

    @Test
    fun deletingAGroupRequiresExplicitConfirmation() {
        var groups by mutableStateOf(listOf(MailboxFixtures.project))
        var mutationVersion by mutableStateOf(0L)
        val deleted = mutableListOf<MailboxGroup>()
        compose.setContent {
            TestGroups(groups = groups, mutationVersion = mutationVersion, onDelete = { group ->
                deleted += group
                groups = emptyList()
                mutationVersion++
            })
        }

        compose.onNodeWithContentDescription("Supprimer ${MailboxFixtures.project.name}").performClick()
        compose.onNodeWithText("Les boîtes mail et leurs messages seront conservés.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, deleted.size) }
        compose.onNodeWithText("Annuler").performClick()
        compose.runOnIdle { assertEquals(0, deleted.size) }
        compose.onNodeWithContentDescription("Supprimer ${MailboxFixtures.project.name}").performClick()
        compose.onNodeWithText("Supprimer").performClick()

        compose.onNodeWithText("Aucun groupe pour le moment").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(MailboxFixtures.project), deleted) }
    }

    @Test
    fun downwardSwipeCanRetryAnEmptyFailedGroupList() {
        var refreshCalls = 0
        var refreshing by mutableStateOf(false)
        compose.setContent {
            TestGroups(loadError = "Connexion interrompue", refreshing = refreshing, onRefresh = {
                refreshCalls++
                refreshing = true
            })
        }

        compose.onNodeWithText("Connexion interrompue").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToIndex(0)
            .performTouchInput { swipeDown(durationMillis = 800) }

        compose.runOnIdle { assertEquals(1, refreshCalls) }
    }

    private fun groupFromDraft(draft: MailboxGroupDraft) = MailboxGroup(
        id = "99", name = draft.name, color = draft.color,
        members = draft.memberAccountIds.map { accountId ->
            val account = MailboxFixtures.accounts.first { it.id == accountId }
            MailboxGroupMember(account.id, account.email, account.displayName, account.isActive)
        },
    )
}

@Composable
private fun TestGroups(
    groups: List<MailboxGroup> = emptyList(),
    accounts: List<EmailAccount> = MailboxFixtures.accounts,
    refreshing: Boolean = false,
    loadError: String? = null,
    actionError: String? = null,
    mutationVersion: Long = 0,
    onRefresh: () -> Unit = {},
    onSave: (String?, MailboxGroupDraft) -> Unit = { _, _ -> },
    onDelete: (MailboxGroup) -> Unit = {},
) {
    PhacteurTheme {
        GroupsScreen(
            groups = groups,
            accounts = accounts,
            loading = false,
            refreshing = refreshing,
            saving = false,
            loadError = loadError,
            actionError = actionError,
            mutationVersion = mutationVersion,
            onRefresh = onRefresh,
            onSave = onSave,
            onDelete = onDelete,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
