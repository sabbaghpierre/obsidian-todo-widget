package de.yukigasai.obsidiantodowidget.util

import android.os.Environment
import android.util.Base64
import de.yukigasai.obsidiantodowidget.WidgetConfig
import de.yukigasai.obsidiantodowidget.todo.TodoItem
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class NoteSnapshot(val path: String, val text: String, val revision: String)

object FsHelper {
    private val fileLocks = ConcurrentHashMap<String, Any>()

    private fun noteFile(config: WidgetConfig): File =
        File(Environment.getExternalStorageDirectory(), config.getFullPath()).canonicalFile

    private fun decode(bytes: ByteArray): String =
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()

    private fun revision(bytes: ByteArray): String = Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(bytes), Base64.NO_WRAP
    )

    fun readNote(
        config: WidgetConfig,
        cache: MutableMap<String, NoteSnapshot?> = mutableMapOf()
    ): NoteSnapshot? {
        var path: String? = null
        return try {
            val file = noteFile(config)
            path = file.path
            if (cache.containsKey(file.path)) return cache[file.path]
            val bytes = file.readBytes()
            NoteSnapshot(file.path, decode(bytes), revision(bytes)).also { cache[file.path] = it }
        } catch (error: Exception) {
            path?.let { cache[it] = null }
            WidgetLogger.error("FsHelper.readNote error: ${error.message}")
            null
        }
    }

    fun updateTask(config: WidgetConfig, item: TodoItem, replacement: String): String? {
        var temporaryFile: File? = null
        try {
            val file = noteFile(config)
            if (file.path != item.sourcePath || item.sourceRevision == null) return null

            synchronized(fileLocks.computeIfAbsent(file.path) { Any() }) {
                val original = file.readBytes()
                if (revision(original) != item.sourceRevision) return null
                val text = decode(original)
                val offset = item.stateOffset
                if (offset < 0 || offset > text.length - item.state.length ||
                    text.substring(offset, offset + item.state.length) != item.state
                ) return null
                if (replacement == item.state) return item.sourceRevision

                val updated = text.replaceRange(offset, offset + item.state.length, replacement)
                    .toByteArray(Charsets.UTF_8)
                val staged = File.createTempFile(".${file.name}.", ".tmp", file.parentFile)
                temporaryFile = staged
                staged.writeBytes(updated)
                if (!file.readBytes().contentEquals(original)) return null
                Files.move(
                    staged.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                )
                return revision(updated)
            }
        } catch (error: Exception) {
            WidgetLogger.error("FsHelper.updateTask error: ${error.message}")
            return null
        } finally {
            temporaryFile?.delete()
        }
    }
}
