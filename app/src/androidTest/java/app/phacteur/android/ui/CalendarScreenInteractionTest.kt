package app.phacteur.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.phacteur.android.data.MailCalendarEvent
import app.phacteur.android.ui.screens.CalendarScreen
import app.phacteur.android.ui.theme.PhacteurTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class CalendarScreenInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sourceMailActionKeepsTheExactServerEvent() {
        val selections = mutableListOf<MailCalendarEvent>()
        compose.setContent {
            TestCalendar(events = listOf(calendarEvent), onOpenSourceMail = { selections += it })
        }

        compose.onNodeWithText("Mail source").performScrollTo().performClick()

        compose.runOnIdle { assertEquals(listOf(calendarEvent), selections) }
    }

    @Test
    fun monthNavigationAndTodayDispatchSeparateActions() {
        val months = mutableListOf<YearMonth>()
        var todayCalls = 0
        compose.setContent {
            TestCalendar(onMonthChange = { months += it }, onToday = { todayCalls++ })
        }

        compose.onNodeWithContentDescription("Mois suivant").performClick()
        compose.onNodeWithContentDescription("Mois précédent").performClick()
        compose.onNodeWithText("Aujourd’hui").performClick()

        compose.runOnIdle {
            assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 9)), months)
            assertEquals(1, todayCalls)
        }
    }

    @Test
    fun downwardSwipeRefreshesAnEmptyCalendar() {
        var calls = 0
        var refreshing by mutableStateOf(false)
        compose.setContent {
            TestCalendar(refreshing = refreshing, onRefresh = { calls++; refreshing = true })
        }

        swipeCalendarDown()

        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test
    fun loadingAndRefreshBlockDuplicateSwipeRequests() {
        var loading by mutableStateOf(true)
        var refreshing by mutableStateOf(false)
        var calls = 0
        compose.setContent {
            TestCalendar(loading = loading, refreshing = refreshing, onRefresh = { calls++ })
        }

        swipeCalendarDown()
        compose.runOnIdle { loading = false; refreshing = true }
        swipeCalendarDown()

        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test
    fun failedLoadShowsRetryInsteadOfAnEmptyAgenda() {
        var calls = 0
        compose.setContent {
            TestCalendar(error = "Calendrier indisponible", onRefresh = { calls++ })
        }

        compose.onNodeWithText("Réessayer").performScrollTo().performClick()
        compose.onNodeWithText("Une journée libre").assertDoesNotExist()

        compose.runOnIdle { assertEquals(1, calls) }
    }

    private fun swipeCalendarDown() {
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToIndex(0)
            .performTouchInput { swipeDown(durationMillis = 800) }
        compose.waitForIdle()
    }
}

@Composable
private fun TestCalendar(
    events: List<MailCalendarEvent> = emptyList(),
    loading: Boolean = false,
    refreshing: Boolean = false,
    error: String? = null,
    onMonthChange: (YearMonth) -> Unit = {},
    onToday: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onOpenSourceMail: (MailCalendarEvent) -> Unit = {},
) {
    PhacteurTheme {
        Box(Modifier.width(400.dp).fillMaxHeight()) {
            CalendarScreen(
                events = events,
                visibleMonth = YearMonth.of(2026, 10),
                selectedDate = calendarEvent.date() ?: LocalDate.of(2026, 10, 2),
                loading = loading,
                refreshing = refreshing,
                error = error,
                onMonthChange = onMonthChange,
                onSelectDate = {},
                onToday = onToday,
                onRefresh = onRefresh,
                onOpenSourceMail = onOpenSourceMail,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val calendarEvent = MailCalendarEvent(
    id = "tracking:server-fixture",
    kind = "flight",
    title = "Vol pour Paris",
    description = null,
    status = "confirmed",
    startsAt = "2026-10-02T12:00:00Z",
    endsAt = "2026-10-02T14:00:00Z",
    location = "Lyon → Paris",
    source = "Compagnie",
    confirmationCode = "ABCD",
    externalUrl = null,
    relatedEmailId = 42,
    relatedThreadId = "flight-thread",
    relatedSubject = "Confirmation de vol",
)
