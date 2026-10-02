package app.phacteur.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.phacteur.android.data.MailCalendarEvent
import app.phacteur.android.data.calendarDays
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    events: List<MailCalendarEvent>,
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    loading: Boolean,
    refreshing: Boolean,
    error: String?,
    onMonthChange: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onToday: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSourceMail: (MailCalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoneId = ZoneId.systemDefault()
    val eventsByDay = remember(events, zoneId) {
        events.filter { it.date(zoneId) != null }
            .sortedBy(MailCalendarEvent::startsAt)
            .groupBy { it.date(zoneId)!! }
    }
    val selectedEvents = eventsByDay[selectedDate].orEmpty()
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("DATES TROUVÉES DANS VOS MAILS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("Toutes vos boîtes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onToday) { Text("Aujourd’hui") }
            IconButton(onClick = onRefresh, enabled = !loading && !refreshing) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Actualiser le calendrier")
            }
        }
        if (loading && events.isNotEmpty()) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { if (!loading && !refreshing) onRefresh() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth >= 840.dp) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        LazyColumn(
                            modifier = Modifier.weight(1.25f),
                            contentPadding = PaddingValues(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            item {
                                CalendarMonth(visibleMonth, selectedDate, eventsByDay, onMonthChange, onSelectDate)
                            }
                            if (error != null) item { CalendarError(error, onRefresh) }
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            agendaItems(selectedDate, selectedEvents, loading, error, onOpenSourceMail)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { CalendarMonth(visibleMonth, selectedDate, eventsByDay, onMonthChange, onSelectDate) }
                        if (error != null) item { CalendarError(error, onRefresh) }
                        agendaItems(selectedDate, selectedEvents, loading, error, onOpenSourceMail)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarMonth(
    month: YearMonth,
    selectedDate: LocalDate,
    eventsByDay: Map<LocalDate, List<MailCalendarEvent>>,
    onMonthChange: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val locale = Locale.getDefault()
    val days = remember(month) { calendarDays(month) }
    val today = LocalDate.now()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)).replaceFirstChar { it.titlecase(locale) },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Mois précédent", Modifier.size(20.dp))
                }
                IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Mois suivant", Modifier.size(20.dp))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim").forEach { label ->
                    Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                days.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        week.forEach { day ->
                            CalendarDay(
                                day = day,
                                inMonth = YearMonth.from(day) == month,
                                isToday = day == today,
                                isSelected = day == selectedDate,
                                eventCount = eventsByDay[day].orEmpty().size,
                                onClick = { onSelectDate(day) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    eventCount: Int,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val description = buildString {
        append(day.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault())))
        if (isToday) append(", aujourd’hui")
        if (eventCount > 0) append(", $eventCount ${if (eventCount == 1) "événement" else "événements"}")
    }
    Surface(
        onClick = onClick,
        modifier = modifier.height(58.dp).semantics { selected = isSelected; contentDescription = description },
        shape = MaterialTheme.shapes.small,
        color = when {
            isSelected -> colors.primaryContainer
            !inMonth -> colors.surfaceContainerLow
            else -> colors.surface
        },
        border = if (isToday) BorderStroke(1.dp, colors.primary) else null,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    isSelected -> colors.onPrimaryContainer
                    isToday -> colors.primary
                    !inMonth -> colors.onSurfaceVariant
                    else -> colors.onSurface
                },
            )
            if (eventCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(minOf(eventCount, 3)) {
                        Surface(Modifier.padding(top = 4.dp).size(4.dp), color = colors.primary, shape = CircleShape) {}
                    }
                }
            } else Spacer(Modifier.height(8.dp))
        }
    }
}

private fun LazyListScope.agendaItems(
    selectedDate: LocalDate,
    events: List<MailCalendarEvent>,
    loading: Boolean,
    error: String?,
    onOpenSourceMail: (MailCalendarEvent) -> Unit,
) {
    item {
        Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
            Text("AGENDA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                selectedDate.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())).replaceFirstChar { it.titlecase(Locale.getDefault()) },
                style = MaterialTheme.typography.titleLarge,
            )
            if (events.isNotEmpty()) {
                Text("${events.size} ${if (events.size == 1) "événement détecté" else "événements détectés"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (loading && events.isEmpty()) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Lecture des dates de vos mails…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else if (events.isEmpty() && error == null) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Text("Une journée libre", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Les livraisons, voyages, réservations et billets détectés dans vos mails apparaîtront ici.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    } else {
        items(events, key = MailCalendarEvent::id) { event -> CalendarEventCard(event, onOpenSourceMail) }
    }
}

@Composable
private fun CalendarError(error: String, onRefresh: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(error, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRefresh) { Text("Réessayer") }
        }
    }
}

@Composable
private fun CalendarEventCard(event: MailCalendarEvent, onOpenSourceMail: (MailCalendarEvent) -> Unit) {
    val uriHandler = LocalUriHandler.current
    var externalError by remember(event.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val tone = when (event.kind) {
        "package" -> colors.tertiaryContainer to colors.onTertiaryContainer
        "flight", "train", "bus", "hotel" -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.primaryContainer to colors.onPrimaryContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Surface(color = tone.first, shape = MaterialTheme.shapes.small) {
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        Icon(calendarEventIcon(event.kind), null, tint = tone.second, modifier = Modifier.size(20.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(event.categoryLabel, style = MaterialTheme.typography.labelSmall, color = tone.second)
                    Text(
                        listOf(event.source, event.status.replace('_', ' ')).filter(String::isNotBlank).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            CalendarEventMeta(Icons.Outlined.Schedule, event.timeLabel())
            event.location?.let { CalendarEventMeta(Icons.Outlined.LocationOn, it) }
            event.confirmationCode?.let { CalendarEventMeta(Icons.Outlined.ConfirmationNumber, "Référence : $it") }
            event.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis) }
            if (event.relatedEmailId != null || event.safeExternalUrl != null) {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (event.relatedEmailId != null) {
                        TextButton(onClick = { onOpenSourceMail(event) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Icon(Icons.Outlined.MailOutline, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Mail source", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    event.safeExternalUrl?.let { url ->
                        TextButton(
                            onClick = { externalError = runCatching { uriHandler.openUri(url) }.isFailure },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Détails", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (externalError) Text("Impossible d’ouvrir ce lien.", style = MaterialTheme.typography.bodySmall, color = colors.error)
        }
    }
}

@Composable
private fun CalendarEventMeta(icon: ImageVector, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun calendarEventIcon(kind: String): ImageVector = when (kind) {
    "flight" -> Icons.Outlined.Flight
    "package" -> Icons.Outlined.Inventory2
    "train" -> Icons.Outlined.Train
    "bus" -> Icons.Outlined.DirectionsBus
    "hotel", "reservation" -> Icons.Outlined.Hotel
    "concert", "sports", "theater", "conference" -> Icons.Outlined.ConfirmationNumber
    else -> Icons.Outlined.Event
}
