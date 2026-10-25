package de.yukigasai.obsidiantodowidget

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.RECEIVER_EXPORTED
import androidx.core.content.ContextCompat.registerReceiver
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.appwidget.state.updateAppWidgetState
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
import de.yukigasai.obsidiantodowidget.reminder.ReminderRepo
import de.yukigasai.obsidiantodowidget.reminder.ReminderStartReceiver
import de.yukigasai.obsidiantodowidget.todo.TodoItem
import de.yukigasai.obsidiantodowidget.todo.TodoRepo
import de.yukigasai.obsidiantodowidget.util.ActionsConstants
import de.yukigasai.obsidiantodowidget.util.Counter
import de.yukigasai.obsidiantodowidget.util.KeysConstants
import de.yukigasai.obsidiantodowidget.util.OnScreenReceiver
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val widgetConfigKey = stringPreferencesKey("widgetConfig")
private val widgetTodosKey = stringPreferencesKey("widgetTodos")
private val widgetRefreshLocks = ConcurrentHashMap<Int, Mutex>()

class TodoWidget : GlanceAppWidget() {

    private var onScreenReceiver: OnScreenReceiver? = null

    fun removeOnScreenReceiver(context: Context) {
        println("removeOnScreenReceiver")
        if (onScreenReceiver != null) {
            context.unregisterReceiver(onScreenReceiver)
            onScreenReceiver = null
        }
    }

    fun registerOnScreenReceiver(context: Context) {
        Counter(context, false).increment()
        removeOnScreenReceiver(context)

        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_USER_PRESENT)

        onScreenReceiver = OnScreenReceiver()
        println("registerOnScreenReceiver")
        registerReceiver(context, onScreenReceiver, filter, RECEIVER_EXPORTED)
    }


    override suspend fun provideGlance(context: Context, id: GlanceId) {
        loadWidgetState(context, id)
        provideContent { Content() }
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
    fun Content() {
        val preferences = currentState<Preferences>()
        val gson = Gson()
        val config = gson.fromJson(preferences[widgetConfigKey], WidgetConfig::class.java) ?: WidgetConfig()
        val todos = gson.fromJson(preferences[widgetTodosKey], Array<TodoItem>::class.java)?.toList().orEmpty()

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
                        provider = ImageProvider(R.drawable.refresh),
                        contentDescription = "Refresh",
                        modifier = modifier
                            .clickable(actionRunCallback<RefreshButtonHandler>())
                            .padding(0.dp)
                            .width(24.dp)
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
                text = item.name,
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

class CheckboxClickAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {

        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val config = WidgetConfig.loadFromPrefs(context, appWidgetId)
        val taskJson = parameters[toggledItemKey]
        val checked = parameters[ToggleableStateKey]
        val state = TodoWidget().getAppWidgetState<Preferences>(context, glanceId)
        val toggledItem = taskJson?.takeIf { it.startsWith("{") }?.let {
            Gson().fromJson(it, TodoItem::class.java)
        }
        val displayedConfig = Gson().fromJson(state[widgetConfigKey], WidgetConfig::class.java)
        if (toggledItem == null || displayedConfig != config) {
            refreshTodos(context, glanceId)
            return
        }

        val updated = withContext(Dispatchers.IO) { toggledItem.updateInFile(config, checked) }
        if (updated == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.task_edit_failed, Toast.LENGTH_LONG).show()
            }
        }

        refreshTodos(context, glanceId)
    }
}

class RefreshButtonHandler : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        refreshTodos(context, glanceId)
    }
}

/**
 * Force update the todo info after user click
 */
suspend fun refreshTodos(context: Context, glanceId: GlanceId) {
    loadWidgetState(context, glanceId)

    // Redraw widget
    TodoWidget().update(context, glanceId)
}

private suspend fun loadWidgetState(context: Context, glanceId: GlanceId) {
    val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
    widgetRefreshLocks.computeIfAbsent(appWidgetId) { Mutex() }.withLock {
        if (appWidgetId !in WidgetConfig.getWidgetIds(context)) return@withLock
        withContext(Dispatchers.IO) {
            val config = WidgetConfig.loadFromPrefs(context, appWidgetId)
            val todos = TodoRepo.getTodos(config)
            ReminderRepo.getInstance().update(context)
            updateAppWidgetState(context, glanceId) { state ->
                val gson = Gson()
                state[widgetConfigKey] = gson.toJson(config)
                state[widgetTodosKey] = gson.toJson(todos)
                todos.forEach { todo ->
                    state[booleanPreferencesKey(todo.id.toString())] = todo.isChecked
                }
            }
        }
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget()

    private suspend fun updateAllWidgets (context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val widget = TodoWidget()
        val glanceIds = manager.getGlanceIds(widget.javaClass)
        glanceIds.forEach { glanceId ->
            refreshTodos(context, glanceId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        runBlocking {
            appWidgetIds.forEach { id ->
                widgetRefreshLocks.computeIfAbsent(id) { Mutex() }.withLock {
                    WidgetConfig.delete(context, intArrayOf(id))
                }
            }
            withContext(Dispatchers.IO) { ReminderRepo.getInstance().update(context) }
        }
        super.onDeleted(context, appWidgetIds)
        TodoWidget().removeOnScreenReceiver(context)
        Counter(context, true).reset()
        Counter(context, false).reset()
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        WidgetConfig.restore(context, oldWidgetIds, newWidgetIds)
        runBlocking(Dispatchers.IO) {
            ReminderRepo.getInstance().update(context)
        }
        val manager = AppWidgetManager.getInstance(context)
        newWidgetIds.forEach { id ->
            manager.updateAppWidgetOptions(id, Bundle().apply {
                putBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true)
            })
        }
        super.onRestored(context, oldWidgetIds, newWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ActionsConstants.START_NOTIFICATION -> runBlocking(Dispatchers.IO) {
                ReminderStartReceiver.onReceive(context, intent)
            }
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
                ) {
                    runBlocking {
                        updateAllWidgets(context)
                    }
                }
            }
            ActionsConstants.END_NOTIFICATION_CHECK -> {
                runBlocking {
                    withContext(Dispatchers.IO) { ReminderEndReceiver.onReceive(context, intent) }
                    updateAllWidgets(context)
                }
            }
            ActionsConstants.UPDATE_WIDGET -> {
                runBlocking {
                    val id = intent.getIntExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
                    )
                    if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
                        updateAllWidgets(context)
                    } else {
                        refreshTodos(context, GlanceAppWidgetManager(context).getGlanceIdBy(id))
                    }
                }
            }
        }
    }


    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        TodoWidget().registerOnScreenReceiver(context)

        val manager = GlanceAppWidgetManager(context)
        runBlocking {
            val glanceIds = manager.getGlanceIds(TodoWidget().javaClass)
            glanceIds.forEach { glanceId ->
                refreshTodos(context, glanceId)
            }
        }
    }

}
