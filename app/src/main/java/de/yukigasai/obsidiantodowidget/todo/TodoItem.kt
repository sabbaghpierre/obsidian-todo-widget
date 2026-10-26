package de.yukigasai.obsidiantodowidget.todo

import de.yukigasai.obsidiantodowidget.WidgetConfig
import de.yukigasai.obsidiantodowidget.util.FsHelper
import java.nio.ByteBuffer
import java.security.MessageDigest

data class TodoItem(
    val id: Int,
    val name: String,
    val state: String,
    val isChecked: Boolean,
    val offSet: String,
    val count: Int = 0,
    val repeats: Int?,
    val sourcePath: String? = null,
    val sourceRevision: String? = null,
    val stateOffset: Int = -1,
    val sourceHeading: String? = null
) {
    fun updateInFile(config: WidgetConfig, checked: Boolean? = null): TodoItem? {
        val replacement = when {
            checked != null -> if (checked) "x" else " "
            isChecked -> " "
            repeats != null && count.toLong() + 1 < repeats -> (count + 1).toString()
            else -> "x"
        }
        val revision = FsHelper.updateTask(config, this, replacement) ?: return null
        return copy(
            state = replacement,
            isChecked = replacement == "x",
            count = replacement.toIntOrNull() ?: 0,
            sourceRevision = revision
        )
    }

    fun widgetItemId(): Long {
        val identity = "$sourcePath\n$sourceRevision\n$stateOffset\n$id"
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        return ByteBuffer.wrap(hash).long and Long.MAX_VALUE
    }

    fun getStateEmoji(): String {
        if(this.state.lowercase() == "x") {
            return ""
        }

        if(this.repeats != null) {
            return getEmojiForNumber(this.count)
        }

        when (this.state) {
            "/" -> {
                return "🔃"
            }
            "-" -> {
                return "✖️"
            }
            ">" -> {
                return "➡️"
            }
            "<" -> {
                return "📅"
            }
            "?" -> {
                return "❓"
            }
            "!" -> {
                return "❗"
            }
            "\"" -> {
                return "💬"
            }
            "l" -> {
                return "📍"
            }
            "b" -> {
                return "🔖"
            }
            "i" -> {
                return "ℹ️"
            }
            "S" -> {
                return "💲"
            }
            "*" -> {
                return "⭐"
            }
            "I" -> {
                return "💡"
            }
            "p" -> {
                return "👍"
            }
            "c" -> {
                return "👎"
            }
            "f" -> {
                return "🔥"
            }
            "k" -> {
                return "🔑"
            }
            "w" -> {
                return "🎂"
            }
            "u" -> {
                return "📈"
            }
            "d" -> {
                return "📉"
            }
        }

        return ""
    }

    private fun getEmojiForNumber(number: Int): String {
        when (number) {
            0 -> return "0️⃣"
            1 -> return "1️⃣"
            2 -> return "2️⃣"
            3 -> return "3️⃣"
            4 -> return "4️⃣"
            5 -> return "5️⃣"
            6 -> return "6️⃣"
            7 -> return "7️⃣"
            8 -> return "8️⃣"
            9 -> return "9️⃣"
        }
        return number.toString()
    }

    fun getTodoText(): String = MarkdownLabel.format(name)
}
