package de.yukigasai.obsidiantodowidget

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.ToggleableStateKey
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.google.gson.Gson
import de.yukigasai.obsidiantodowidget.reminder.ReminderEndReceiver
import de.yukigasai.obsidiantodowidget.reminder.ReminderStartReceiver
import de.yukigasai.obsidiantodowidget.todo.CompletionUndo
import de.yukigasai.obsidiantodowidget.todo.TaskUndo
import de.yukigasai.obsidiantodowidget.todo.TodoItem
import de.yukigasai.obsidiantodowidget.util.ActionsConstants
import de.yukigasai.obsidiantodowidget.util.KeysConstants
import de.yukigasai.obsidiantodowidget.util.OnScreenReceiver
import de.yukigasai.obsidiantodowidget.util.WidgetLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TodoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent { Content(appWidgetId) }
    }

    private fun GlanceModifier.appWidgetBackgroundCornerRadius(): GlanceModifier {
        if (Build.VERSION.SDK_INT >= 31) {
            return cornerRadius(android.R.dimen.system_app_widget_background_radius)
        }

        return background(
            ImageProvider(R.drawable.background_widget), ContentScale.Fit
        )
    }

    @Composable
    fun Content(appWidgetId: Int) {
        val context = LocalContext.current
        val preferences = currentState<Preferences>()
        val configJson = preferences[WidgetRefresher.sourceKey] ?: preferences[WidgetRefresher.configKey]
        if (configJson == null) {
            GlanceTheme { Text("Loading…") }
            return
        }
        val gson = Gson()
        val config = gson.fromJson(configJson, WidgetConfig::class.java) ?: WidgetConfig()
        val todos = gson.fromJson(preferences[WidgetRefresher.todosKey], Array<TodoItem>::class.java)?.toList().orEmpty()
        val undo = gson.fromJson(preferences[WidgetRefresher.undoKey], CompletionUndo::class.java)

        GlanceTheme {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .padding(4.dp)
                    .background(GlanceTheme.colors.background)
                    .appWidgetBackgroundCornerRadius()
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(4.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    val modifier = GlanceModifier.defaultWeight()
                    Text(
                        text = config.getArticleFileName().dropLast(3),
                        // text = "${Counter(context, true).count} ${Counter(context, false).count} ${config.getArticleFileName().dropLast(3)}",
                        modifier = modifier.then(
                            GlanceModifier.clickable(actionStartActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(config.getFullObsidianURL()))
                                    .addFlags(FLAG_ACTIVITY_NEW_TASK)
                            ))
                        ),
                        maxLines = 1,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = GlanceTheme.colors.primary
                        )
                    )
                    Image(
                        provider = ImageProvider(R.drawable.add),
                        contentDescription = context.getString(R.string.quick_add_title),
                        modifier = GlanceModifier
                            .clickable(actionStartActivity(
                                Intent(context, QuickAddActivity::class.java)
                                    .setData(Uri.parse("obsidiantodowidget://quick-add/$appWidgetId"))
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                    .putExtra(QuickAddActivity.EXTRA_CONFIG, preferences[WidgetRefresher.configKey])
                                    .putExtra(QuickAddActivity.EXTRA_SOURCE, configJson)
                                    .addFlags(FLAG_ACTIVITY_NEW_TASK)
                            ))
                            .padding(8.dp)
                            .width(24.dp)
                    )
                    Image(
                        provider = ImageProvider(R.drawable.refresh),
                        contentDescription = "Refresh",
                        modifier = modifier
                            .clickable(actionRunCallback<RefreshButtonHandler>())
                            .padding(0.dp)
                            .width(24.dp)
                    )
                }
                if (undo != null) {
                    Text(
                        text = context.getString(R.string.undo_completion, undo.item.getTodoText()),
                        modifier = GlanceModifier
                            .clickable(actionRunCallback<UndoCompletionAction>(
                                actionParametersOf(undoTokenKey to undo.token)
                            ))
                            .padding(8.dp),
                        style = TextStyle(color = GlanceTheme.colors.primary),
                        maxLines = 1
                    )
                }
                if (todos.isEmpty()) {
                    Text(
                        text = "No todos",
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground
                        )
                    )
                } else {
                    LazyColumn {
                        items(todos, itemId = { it.widgetItemId() }) { todoItem ->
                            if(!todoItem.isChecked || !config.hideDoneTasks) {
                                CheckBoxItem(todoItem, config)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckBoxItem(item: TodoItem, config: WidgetConfig) {

    fun GlanceModifier.leftPad(config: WidgetConfig, item: TodoItem): GlanceModifier {
        if (!config.hideDoneTasks) {
            return this.then(GlanceModifier.padding(
                start = (12 * (item.offSet.length - 1) + 8).dp,
                top = 4.dp,
                end = 4.dp,
                bottom = 4.dp,
            ))
        }
        return this.then(GlanceModifier.padding(4.dp))
    }

    if(item.getStateEmoji() == "") {

        val prefs = currentState<Preferences>()
        val checked = prefs[booleanPreferencesKey(item.id.toString())] ?: item.isChecked
        CheckBox(
            text = item.getTodoText(),
            style = TextStyle(GlanceTheme.colors.onBackground),
            checked = checked,
            colors = CheckboxDefaults.colors(),
            onCheckedChange = actionRunCallback<CheckboxClickAction>(
                actionParametersOf(
                    toggledItemKey to Gson().toJson(item)
                ),
            ),
            modifier = GlanceModifier.leftPad(config, item)
        )
    } else {
        Row (
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ){
            Text(
                text = item.getStateEmoji(),
                style = TextStyle(
                    fontSize = 18.sp,
                    textAlign = TextAlign.Right,
                    textDecoration = TextDecoration.None,
                    color = GlanceTheme.colors.onSurface,
                ),
                modifier = GlanceModifier.leftPad(config, item).then(
                GlanceModifier.clickable(actionRunCallback<CheckboxClickAction>(
                    actionParametersOf(
                        toggledItemKey to Gson().toJson(item)
                    ),
                ))).then(GlanceModifier.width(30.dp))
            )
            Text(
                text = item.getTodoText(),
                style = TextStyle(
                    fontSize = 15.sp,
                    color = GlanceTheme.colors.onSurface,
                ),
                modifier = GlanceModifier.clickable(actionRunCallback<CheckboxClickAction>(
                    actionParametersOf(
                        toggledItemKey to Gson().toJson(item)
                    ),
                ))
            )
        }
    }
}

private val toggledItemKey = ActionParameters.Key<String>(KeysConstants.TOGGLE_ITEM_KEY)
private val undoTokenKey = ActionParameters.Key<String>(KeysConstants.UNDO_TOKEN_KEY)

class CheckboxClickAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {

        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        if (appWidgetId !in WidgetConfig.getWidgetIds(context)) return
        val config = WidgetConfig.loadFromPrefs(context, appWidgetId)
        val taskJson = parameters[toggledItemKey]
        val checked = parameters[ToggleableStateKey]
        val state = TodoWidget().getAppWidgetState<Preferences>(context, glanceId)
        val toggledItem = taskJson?.takeIf { it.startsWith("{") }?.let {
            Gson().fromJson(it, TodoItem::class.java)
        }
        val displayedConfig = Gson().fromJson(state[WidgetRefresher.configKey], WidgetConfig::class.java)
        val source = Gson().fromJson(
            state[WidgetRefresher.sourceKey] ?: state[WidgetRefresher.configKey], WidgetConfig::class.java
        )
        if (toggledItem == null || displayedConfig != config || source == null) {
            WidgetRefresher.refresh(context)
            return
        }

        val updated = withContext(Dispatchers.IO) {
            TaskUndo.update(context, appWidgetId, config, source, toggledItem, checked)
        }
        if (updated == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.task_edit_failed, Toast.LENGTH_LONG).show()
            }
        }

        WidgetRefresher.refresh(context)
    }
}

class UndoCompletionAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val token = parameters[undoTokenKey] ?: return
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val undone = withContext(Dispatchers.IO) { TaskUndo.undo(context, widgetId, token) }
        if (!undone) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.undo_completion_failed, Toast.LENGTH_LONG).show()
            }
        }
        WidgetRefresher.refresh(context)
    }
}

class RefreshButtonHandler : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        WidgetRefresher.refresh(context)
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetConfig.delete(context, appWidgetIds)
        super.onDeleted(context, appWidgetIds)
        OnScreenReceiver.updateRegistration(context)
        WidgetRefreshWorker.enqueue(context)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        WidgetConfig.restore(context, oldWidgetIds, newWidgetIds)
        val manager = AppWidgetManager.getInstance(context)
        newWidgetIds.forEach { id ->
            manager.updateAppWidgetOptions(id, Bundle().apply {
                putBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true)
            })
        }
        super.onRestored(context, oldWidgetIds, newWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            ActionsConstants.START_NOTIFICATION -> launchAsync {
                ReminderStartReceiver.onReceive(appContext, intent)
            }
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
                ) {
                    WidgetRefreshWorker.enqueue(appContext)
                }
            }
            ActionsConstants.END_NOTIFICATION_CHECK -> launchAsync {
                ReminderEndReceiver.onReceive(appContext, intent)
                WidgetRefresher.refresh(appContext)
            }
            ActionsConstants.UPDATE_WIDGET -> WidgetRefreshWorker.enqueue(appContext)
            else -> super.onReceive(context, intent)
        }
    }

    private fun launchAsync(block: suspend () -> Unit) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                block()
            } catch (error: Exception) {
                WidgetLogger.error("Widget broadcast failed: ${error.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        OnScreenReceiver.updateRegistration(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        OnScreenReceiver.updateRegistration(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        OnScreenReceiver.updateRegistration(context)
        WidgetRefreshWorker.enqueue(context)
    }

}
