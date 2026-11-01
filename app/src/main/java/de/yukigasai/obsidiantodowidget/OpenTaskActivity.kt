package de.yukigasai.obsidiantodowidget

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import de.yukigasai.obsidiantodowidget.todo.TodoFactory
import de.yukigasai.obsidiantodowidget.todo.TodoItem
import de.yukigasai.obsidiantodowidget.util.FsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OpenTaskActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SOURCE = "open_task_source"
        const val EXTRA_TASK = "open_task_item"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gson = Gson()
        val source = gson.fromJson(intent.getStringExtra(EXTRA_SOURCE), WidgetConfig::class.java)
        val item = gson.fromJson(intent.getStringExtra(EXTRA_TASK), TodoItem::class.java)
        if (source == null || item == null || !source.isConfigured()) {
            finish()
            return
        }
        lifecycleScope.launch {
            val url = withContext(Dispatchers.IO) { taskUrl(source, item) }
                ?: source.getFullObsidianURL(item.sourceHeading)
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .setPackage("md.obsidian")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this@OpenTaskActivity, R.string.obsidian_not_installed, Toast.LENGTH_LONG).show()
            } finally {
                finish()
            }
        }
    }

    private fun taskUrl(source: WidgetConfig, item: TodoItem): String? {
        val snapshot = FsHelper.readNote(source) ?: return null
        if (snapshot.path != item.sourcePath) return null
        val tasks = TodoFactory(source).getTodos(snapshot)
        val current = if (snapshot.revision == item.sourceRevision) {
            tasks.singleOrNull { it.stateOffset == item.stateOffset }
        } else {
            tasks.singleOrNull {
                it.name == item.name && it.offSet == item.offSet && it.sourceHeading == item.sourceHeading
            }
        } ?: return null
        val block = Regex("\\s\\^([A-Za-z0-9-]+)\\s*$").find(current.name)?.groupValues?.get(1)
        if (block != null) return source.getFullObsidianURL("^$block")
        val lineEnd = snapshot.text.indexOf('\n', current.stateOffset)
            .takeIf { it >= 0 } ?: snapshot.text.length
        val offset = snapshot.text.take(lineEnd).trimEnd('\r').replace("\r\n", "\n").length
        return source.getTaskObsidianURL(offset)
    }
}
