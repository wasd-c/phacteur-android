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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.phacteur.android.data.MailboxEmail
import app.phacteur.android.data.MailboxScope
import app.phacteur.android.ui.screens.MailboxScreen
import app.phacteur.android.ui.theme.PhacteurTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class MailboxScreenInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun downwardSwipeRefreshesAnEmptyInbox() {
        var refreshCalls = 0
        var refreshing by mutableStateOf(false)
        compose.setContent {
            TestMailbox(
                emails = emptyList(),
                refreshing = refreshing,
                onRefresh = {
                    refreshCalls++
                    refreshing = true
                },
            )
        }

        compose.onNodeWithText("Vous êtes à jour").assertIsDisplayed()
        swipeInboxDown()

        compose.runOnIdle { assertEquals(1, refreshCalls) }
        compose.onNodeWithText("Actualisation…").assertIsDisplayed()
    }

    @Test
    fun downwardSwipeRefreshesExistingMailWithoutHidingItOrSelectingAnotherMessage() {
        var refreshCalls = 0
        var refreshing by mutableStateOf(false)
        val selections = mutableListOf<MailboxEmail?>()
        compose.setContent {
            TestMailbox(
                emails = listOf(MailboxFixtures.email),
                refreshing = refreshing,
                onRefresh = {
                    refreshCalls++
                    refreshing = true
                },
                onSelect = { selections += it },
            )
        }

        compose.onNodeWithText(MailboxFixtures.email.subject).performClick()
        compose.runOnIdle { assertEquals(listOf(MailboxFixtures.email), selections) }
        swipeInboxDown()

        compose.runOnIdle {
            assertEquals(1, refreshCalls)
            assertEquals(listOf(MailboxFixtures.email), selections)
        }
        compose.onNodeWithText(MailboxFixtures.email.subject).assertIsDisplayed()
        compose.onNodeWithText("Actualisation…").assertIsDisplayed()
    }

    @Test
    fun loadingAndRefreshingBlockDuplicateRefreshAndPagination() {
        var busy by mutableStateOf(Busy.LOADING)
        var refreshCalls = 0
        var loadMoreCalls = 0
        compose.setContent {
            TestMailbox(
                emails = listOf(MailboxFixtures.email),
                loading = busy == Busy.LOADING,
                refreshing = busy == Busy.REFRESHING,
                loadingMore = busy == Busy.LOADING_MORE,
                hasMore = true,
                onRefresh = { refreshCalls++ },
                onLoadMore = { loadMoreCalls++ },
            )
        }

        for (state in listOf(Busy.LOADING, Busy.REFRESHING)) {
            compose.runOnIdle { busy = state }
            swipeInboxDown()
            compose.onNodeWithText("Charger la suite")
                .performScrollTo()
                .assertIsNotEnabled()
                .performTouchInput { click() }
        }
        compose.runOnIdle {
            assertEquals(0, refreshCalls)
            assertEquals(0, loadMoreCalls)
            busy = Busy.LOADING_MORE
        }
        compose.onNodeWithText("Chargement…")
            .performScrollTo()
            .assertIsNotEnabled()
            .performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(0, loadMoreCalls)
            busy = Busy.IDLE
        }

        compose.onNodeWithText("Charger la suite").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, loadMoreCalls) }
    }

    @Test
    fun refreshErrorCanBeRetriedWithTheSamePullGesture() {
        var refreshCalls = 0
        compose.setContent {
            TestMailbox(
                emails = emptyList(),
                loadError = "Connexion interrompue",
                onRefresh = { refreshCalls++ },
            )
        }

        compose.onNodeWithText("Connexion interrompue").assertIsDisplayed()
        swipeInboxDown()

        compose.runOnIdle { assertEquals(1, refreshCalls) }
    }

    @Test
    fun partialSearchResultsRemainReadableWhileTheLimitIsAnnounced() {
        var searchLimited by mutableStateOf(true)
        compose.setContent {
            TestMailbox(emails = listOf(MailboxFixtures.email), searchLimited = searchLimited)
        }

        compose.onNodeWithText("La recherche couvre jusqu’à 1 000 messages.", substring = true).assertIsDisplayed()
        compose.onNodeWithText(MailboxFixtures.email.subject).assertIsDisplayed()
        compose.runOnIdle { searchLimited = false }
        compose.onNodeWithText("La recherche couvre jusqu’à 1 000 messages.", substring = true).assertDoesNotExist()
        compose.onNodeWithText(MailboxFixtures.email.subject).assertIsDisplayed()
    }

    @Test
    fun mailRowWithMeetingActionMeasuresAndItsSubjectRemainsSelectable() {
        val meetingEmail = MailboxFixtures.email.copy(
            subject = "Invitation à une réunion",
            body = "Rejoignez-nous : https://meet.google.com/abc-defg-hij",
            htmlBody = null,
            receivedAt = Instant.now().toString(),
            direction = "RECEIVED",
        )
        val selections = mutableListOf<MailboxEmail?>()
        compose.setContent {
            TestMailbox(emails = listOf(meetingEmail), onSelect = { selections += it })
        }

        compose.onNodeWithText("Rejoindre la réunion").assertIsDisplayed()
        compose.onNodeWithText("meet.google.com").assertIsDisplayed()
        compose.onNodeWithText(meetingEmail.subject).performTouchInput { click() }

        compose.runOnIdle { assertEquals(listOf(meetingEmail), selections) }
    }

    private fun swipeInboxDown() {
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToIndex(0)
            .performTouchInput { swipeDown(durationMillis = 800) }
        compose.waitForIdle()
    }

    private enum class Busy { IDLE, LOADING, REFRESHING, LOADING_MORE }
}

@Composable
private fun TestMailbox(
    emails: List<MailboxEmail>,
    loading: Boolean = false,
    refreshing: Boolean = false,
    loadingMore: Boolean = false,
    hasMore: Boolean = false,
    searchLimited: Boolean = false,
    loadError: String? = null,
    onRefresh: () -> Unit = {},
    onSelect: (MailboxEmail?) -> Unit = {},
    onLoadMore: () -> Unit = {},
) {
    PhacteurTheme {
        MailboxScreen(
            emails = emails,
            selectedEmail = null,
            accounts = MailboxFixtures.accounts,
            groups = MailboxFixtures.groups,
            scope = MailboxScope.All,
            search = "",
            status = null,
            category = null,
            hasMore = hasMore,
            loading = loading,
            refreshing = refreshing,
            loadingMore = loadingMore,
            totalCount = emails.size,
            searchLimited = searchLimited,
            loadError = loadError,
            wide = false,
            onScopeChange = {},
            onSearch = {},
            onStatus = {},
            onCategory = {},
            onRefresh = onRefresh,
            onSelect = onSelect,
            onLoadMore = onLoadMore,
            onStatusUpdate = { _, _ -> },
            onReply = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
