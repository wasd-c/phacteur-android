package app.phacteur.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.phacteur.android.data.MailboxScope
import app.phacteur.android.ui.components.MailboxScopeSelector
import app.phacteur.android.ui.theme.PhacteurTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MailboxScopeSelectorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun choosingMailboxThenGroupReturnsTheirIdsAndClosesSelector() {
        val selections = mutableListOf<MailboxScope>()
        setSelector { selections += it }

        compose.onNodeWithText("Toutes les boîtes").performClick()
        compose.onNodeWithText(MailboxFixtures.inactive.email).assertDoesNotExist()
        scrollToChoice(MailboxFixtures.work.email)
        compose.onNodeWithText(MailboxFixtures.work.email).performClick()

        compose.runOnIdle {
            assertEquals(listOf(MailboxScope.Account(MailboxFixtures.work.id)), selections)
        }
        compose.onNodeWithText("Vos boîtes & groupes").assertDoesNotExist()
        compose.onNodeWithText(MailboxFixtures.work.email).assertIsDisplayed()

        compose.onNodeWithText("Travail").performClick()
        scrollToChoice(MailboxFixtures.project.name)
        compose.onNodeWithText(MailboxFixtures.project.name).performClick()

        compose.runOnIdle {
            assertEquals(
                listOf(MailboxScope.Account(MailboxFixtures.work.id), MailboxScope.Group(MailboxFixtures.project.id)),
                selections,
            )
        }
        compose.onNodeWithText("Vos boîtes & groupes").assertDoesNotExist()
        compose.onNodeWithText(MailboxFixtures.project.name).assertIsDisplayed()
    }

    @Test
    fun searchingByMemberAddressFindsItsGroupAndFiltersUnrelatedIdentities() {
        val selections = mutableListOf<MailboxScope>()
        setSelector { selections += it }

        compose.onNodeWithText("Toutes les boîtes").performClick()
        compose.onNodeWithContentDescription("Rechercher une boîte ou un groupe")
            .performTextInput(MailboxFixtures.work.email)
        closeSoftKeyboard()

        compose.onNodeWithText(MailboxFixtures.personal.email).assertDoesNotExist()
        compose.onNodeWithText(MailboxFixtures.emptyGroup.name).assertDoesNotExist()
        scrollToChoice(MailboxFixtures.project.name)
        compose.onNodeWithText(MailboxFixtures.project.name).assertIsDisplayed().performClick()

        compose.runOnIdle {
            assertEquals(listOf(MailboxScope.Group(MailboxFixtures.project.id)), selections)
        }
    }

    @Test
    fun emptyGroupRemainsSelectableAndDoesNotResolveToAllMailboxes() {
        val selections = mutableListOf<MailboxScope>()
        setSelector { selections += it }

        compose.onNodeWithText("Toutes les boîtes").performClick()
        scrollToChoice(MailboxFixtures.emptyGroup.name)
        compose.onNodeWithText(MailboxFixtures.emptyGroup.name).performClick()

        compose.runOnIdle {
            assertEquals(listOf(MailboxScope.Group(MailboxFixtures.emptyGroup.id)), selections)
        }
        compose.onNodeWithText("Aucune boîte active dans ce groupe").assertIsDisplayed()
    }

    private fun setSelector(onSelection: (MailboxScope) -> Unit) {
        var scope by mutableStateOf<MailboxScope>(MailboxScope.All)
        compose.setContent {
            PhacteurTheme {
                Column(Modifier.fillMaxSize()) {
                    MailboxScopeSelector(
                        scope = scope,
                        accounts = MailboxFixtures.accounts,
                        groups = MailboxFixtures.groups,
                        onScopeChange = {
                            scope = it
                            onSelection(it)
                        },
                    )
                }
            }
        }
    }

    private fun scrollToChoice(label: String) {
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText(label))
    }
}
