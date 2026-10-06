package app.persora.android.core.util

import app.persora.android.data.model.VaultItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

object Dates {
    private val dayFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val shortFmt = DateTimeFormatter.ofPattern("MMM d")
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
    private val weekdayFmt = DateTimeFormatter.ofPattern("EEE, MMM d")

    fun today(): String = LocalDate.now().toString()
    fun nowIso(): String = Instant.now().toString()

    fun parseLocalDate(value: String?): LocalDate? = value?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
    fun parseInstant(value: String?): ZonedDateTime? = value?.takeIf { it.isNotBlank() }?.let { raw ->
        runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()) }.getOrNull()
            ?: parseLocalDate(raw)?.atStartOfDay(ZoneId.systemDefault())
    }

    /** formatDate() on the web: "12 Mar 2026"; falls back to the raw value. */
    fun formatDate(value: String?): String = parseLocalDate(value)?.format(dayFmt) ?: parseInstant(value)?.format(dayFmt) ?: value.orEmpty()
    fun formatShort(value: String?): String = parseLocalDate(value)?.format(shortFmt) ?: value.orEmpty()
    fun formatTime(value: ZonedDateTime?): String = value?.format(timeFmt) ?: ""
    fun formatDateTime(value: String?): String = parseInstant(value)?.let { "${it.format(weekdayFmt)} · ${it.format(timeFmt)}" } ?: value.orEmpty()
    fun formatRelative(value: String?): String {
        val then = parseInstant(value) ?: return ""
        val minutes = ChronoUnit.MINUTES.between(then, ZonedDateTime.now())
        return when {
            minutes < 1 -> "Just now"; minutes < 60 -> "${minutes}m ago"; minutes < 60 * 24 -> "${minutes / 60}h ago"
            minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"; else -> then.format(dayFmt)
        }
    }
    fun formatClock(hhmm: String): String = runCatching { LocalTime.parse(hhmm).format(timeFmt) }.getOrDefault(hhmm)

    /** Days until a YYYY-MM-DD date (negative when past). */
    fun daysUntil(value: String?): Long? = parseLocalDate(value)?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }

    /** Port of nextScheduleDate() in Workspace.tsx — the next fire time for a reminder or alarm. */
    fun nextScheduleDate(item: VaultItem, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        val m = item.metadata
        parseInstant(m["snoozedUntil"])?.let { if (!it.isBefore(now.minusDays(1))) return it }
        if (item.recordType == "reminder") return parseInstant(m["reminderAt"])
        if (item.recordType != "alarm") return null
        val alarmTime = m["alarmTime"]?.takeIf { it.isNotBlank() } ?: return null
        val time = runCatching { LocalTime.parse(alarmTime) }.getOrNull() ?: return null
        val repeat = m["repeatDays"].orEmpty().split(",").filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }
        if (repeat.isEmpty()) {
            val date = parseLocalDate(m["alarmDate"]) ?: return null
            return date.atTime(time).atZone(ZoneId.systemDefault())
        }
        for (offset in 0..7) {
            val candidate = now.toLocalDate().plusDays(offset.toLong()).atTime(time).atZone(ZoneId.systemDefault())
            val jsDay = candidate.dayOfWeek.value % 7 // JS getDay(): Sunday = 0
            if (jsDay in repeat && !candidate.isBefore(now)) return candidate
        }
        return null
    }

    /** Date that drives "Coming up soon" on the dashboard (dashboardItemDate in Workspace.tsx). */
    fun dashboardDate(item: VaultItem, dateKey: String?): ZonedDateTime? {
        if (item.isSchedule) return nextScheduleDate(item)
        val raw = if (item.isTodo) item.metadata["dueDate"] else dateKey?.let { item.metadata[it] }
        return parseLocalDate(raw)?.atStartOfDay(ZoneId.systemDefault())
    }

    val weekdayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    fun repeatLabel(repeatDays: String?): String = repeatDays.orEmpty().split(",").mapNotNull { it.toIntOrNull() }.mapNotNull { weekdayLabels.getOrNull(it) }.joinToString(" · ")
}
