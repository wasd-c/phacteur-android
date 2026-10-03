package app.phacteur.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.phacteur.android.data.EmailAccount
import app.phacteur.android.data.MailboxGroup
import app.phacteur.android.data.MailboxScope
import app.phacteur.android.ui.components.MailboxScopeSelector
import app.phacteur.android.ui.theme.PhacteurTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MailboxGroupSelectorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun groupsAppearBeforeIndividualMailboxes() {
        setSelector(accounts = listOf(MailboxFixtures.personal), groups = listOf(MailboxFixtures.project))

        compose.onNodeWithText("Toutes les boîtes").performClick()
        compose.onNodeWithText("GROUPES").assertIsDisplayed()
        compose.onNodeWithText(MailboxFixtures.project.name).assertIsDisplayed()
        compose.onNodeWithText("MES BOÎTES").assertIsDisplayed()

        val groupHeader = compose.onNodeWithText("GROUPES").fetchSemanticsNode().boundsInRoot
        val groupRow = compose.onNodeWithText(MailboxFixtures.project.name).fetchSemanticsNode().boundsInRoot
        val accountHeader = compose.onNodeWithText("MES BOÎTES").fetchSemanticsNode().boundsInRoot
        assertTrue(groupHeader.top < groupRow.top)
        assertTrue(groupRow.top < accountHeader.top)
    }

    @Test
    fun creatingAGroupClosesTheSelectorWithoutChangingMailboxScope() {
        var scope by mutableStateOf<MailboxScope>(MailboxScope.All)
        var createCalls = 0
        compose.setContent {
            PhacteurTheme {
                Column(Modifier.fillMaxSize()) {
                    MailboxScopeSelector(
                        scope = scope,
                        accounts = MailboxFixtures.accounts,
                        groups = MailboxFixtures.groups,
                        onScopeChange = { scope = it },
                        onCreateGroup = { createCalls++ },
                    )
                }
            }
        }

        compose.onNodeWithText("Toutes les boîtes").performClick()
        compose.onNodeWithContentDescription("Créer un groupe").assertIsDisplayed().performClick()

        compose.onNodeWithText("Vos boîtes & groupes").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, createCalls)
            assertEquals(MailboxScope.All, scope)
        }
    }

    @Test
    fun creationRemainsAvailableWithNoGroupsOrMatchingSearchResults() {
        var createCalls = 0
        setSelector(accounts = emptyList(), groups = emptyList(), onCreateGroup = { createCalls++ })

        compose.onNodeWithText("Toutes les boîtes").performClick()
        compose.onNodeWithText("Aucun groupe pour le moment").assertIsDisplayed()
        compose.onNodeWithContentDescription("Créer un groupe").assertIsDisplayed()
        compose.onNodeWithContentDescription("Rechercher une boîte ou un groupe").performTextInput("aucun résultat")
        closeSoftKeyboard()
        compose.onNodeWithText("Aucun groupe correspondant").assertIsDisplayed()
        compose.onNodeWithContentDescription("Créer un groupe").assertIsDisplayed().performClick()

        compose.onNodeWithText("Vos boîtes & groupes").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, createCalls) }
    }

    private fun setSelector(
        accounts: List<EmailAccount>,
        groups: List<MailboxGroup>,
        onCreateGroup: () -> Unit = {},
    ) {
        compose.setContent {
            PhacteurTheme {
                Column(Modifier.fillMaxSize()) {
                    MailboxScopeSelector(
                        scope = MailboxScope.All,
                        accounts = accounts,
                        groups = groups,
                        onScopeChange = {},
                        onCreateGroup = onCreateGroup,
                    )
                }
            }
        }
    }
}
