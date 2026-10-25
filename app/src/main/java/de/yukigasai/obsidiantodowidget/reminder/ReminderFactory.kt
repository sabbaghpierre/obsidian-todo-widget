package de.yukigasai.obsidiantodowidget.reminder

import de.yukigasai.obsidiantodowidget.WidgetConfig
import de.yukigasai.obsidiantodowidget.todo.TodoItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

class ReminderFactory () {
    fun createFromTodo(todo: TodoItem, config: WidgetConfig): Reminder? {
        if (todo.isChecked) return null

        // Get The dateTime from the name
        val getDateRegex =
            Regex("(?:@|📅)[\\t ]*\\{?(?:(\\d{4}-\\d{2}-\\d{2})(?:[\\t ]+(\\d{2}:\\d{2}))?|(\\d{2}:\\d{2}))\\}?")
        // Find date match or return
        val match: MatchResult = getDateRegex.find(todo.name) ?: return null

        val reminderDateTime: LocalDateTime
        try {
            val day = match.groups[1]?.value?.let { LocalDate.parse(it) } ?: LocalDate.now()
            val time = LocalTime.parse(match.groups[2]?.value ?: match.groups[3]?.value ?: "12:00")
            reminderDateTime = LocalDateTime.of(day, time)
        } catch (error: DateTimeParseException) {
            System.err.println(error.cause)
            return null
        }

        val todoTileWithoutDate = todo.name.replace(match.groupValues[0], "")

        return Reminder(
            todo = todo,
            title = todoTileWithoutDate,
            date = reminderDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            dateText = match.groupValues[0],
            config = config
        )
    }
}
