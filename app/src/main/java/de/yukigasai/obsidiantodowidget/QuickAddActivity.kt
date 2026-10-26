package de.yukigasai.obsidiantodowidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import de.yukigasai.obsidiantodowidget.todo.TaskUndo
import de.yukigasai.obsidiantodowidget.ui.theme.ObsidianTodoWidgetTheme
import de.yukigasai.obsidiantodowidget.util.WidgetLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuickAddActivity : ComponentActivity() {
    companion object {
        const val EXTRA_CONFIG = "quick_add_config"
        const val EXTRA_SOURCE = "quick_add_source"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val gson = Gson()
        val config = gson.fromJson(intent.getStringExtra(EXTRA_CONFIG), WidgetConfig::class.java)
        val source = gson.fromJson(intent.getStringExtra(EXTRA_SOURCE), WidgetConfig::class.java)
        if (config == null || source == null || !source.isConfigured() ||
            widgetId !in WidgetConfig.getWidgetIds(this) ||
            WidgetConfig.loadFromPrefs(this, widgetId) != config
        ) {
            finish()
            return
        }
        val model = ViewModelProvider(this)[QuickAddViewModel::class.java]
        setContent {
            ObsidianTodoWidgetTheme {
                val focusRequester = remember { FocusRequester() }
                val keyboard = LocalSoftwareKeyboardController.current
                LaunchedEffect(model.saved) {
                    if (model.saved) finish()
                }
                val save = { model.save(applicationContext, widgetId, config, source) }
                AlertDialog(
                    onDismissRequest = { if (!model.saving) finish() },
                    title = { Text(stringResource(R.string.quick_add_title)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(source.getArticleFileName())
                            if (source.header.isNotEmpty()) Text(source.header)
                            OutlinedTextField(
                                value = model.task,
                                onValueChange = { model.task = it.replace('\r', ' ').replace('\n', ' ') },
                                label = { Text(stringResource(R.string.quick_add_task)) },
                                singleLine = true,
                                enabled = !model.saving,
                                modifier = Modifier.focusRequester(focusRequester),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { save() })
                            )
                            LaunchedEffect(Unit) {
                                if (!model.saving && !model.saved) {
                                    focusRequester.requestFocus()
                                    keyboard?.show()
                                }
                            }
                            if (model.failed) {
                                Text(
                                    stringResource(R.string.quick_add_failed),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = save, enabled = !model.saving && model.task.isNotBlank()) {
                            Text(stringResource(
                                if (model.saving) R.string.quick_add_saving else R.string.quick_add_save
                            ))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { finish() }, enabled = !model.saving) {
                            Text(stringResource(R.string.quick_add_cancel))
                        }
                    }
                )
            }
        }
    }
}

class QuickAddViewModel : ViewModel() {
    var task by mutableStateOf("")
    var saving by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    fun save(context: Context, widgetId: Int, config: WidgetConfig, source: WidgetConfig) {
        if (saving || saved || task.isBlank()) return
        val text = task
        saving = true
        failed = false
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                TaskUndo.append(context, widgetId, config, source, text)
            }
            saved = success
            failed = !success
            saving = false
            if (success) {
                try {
                    WidgetRefreshWorker.enqueue(context)
                } catch (error: Exception) {
                    WidgetLogger.error("Quick-add refresh scheduling failed: ${error.message}")
                }
            }
        }
    }
}
