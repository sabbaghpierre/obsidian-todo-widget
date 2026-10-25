package de.yukigasai.obsidiantodowidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import de.yukigasai.obsidiantodowidget.util.PreferencesConstants
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


data class WidgetConfig(
    var folder: String = "",
    var fileName: String = "",
    var vaultName: String = "",
    var hideDoneTasks: Boolean = false,
    var header: String = "",
    var includeSubHeader: Boolean = false
) {

    companion object {
        private fun key(appWidgetId: Int) = "${PreferencesConstants.PREFIX}_$appWidgetId"

        fun getWidgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, TodoWidgetReceiver::class.java)
            )

        @Synchronized
        private fun preferences(context: Context): SharedPreferences {
            val preferences = context.getSharedPreferences(PreferencesConstants.WIDGET_CONFIG, Context.MODE_PRIVATE)
            val legacyConfig = preferences.getString(PreferencesConstants.PREFIX, null)
            if (legacyConfig != null) {
                val widgetIds = getWidgetIds(context)
                if (widgetIds.isNotEmpty()) {
                    preferences.edit {
                        widgetIds.forEach { id ->
                            if (!preferences.contains(key(id))) putString(key(id), legacyConfig)
                        }
                        remove(PreferencesConstants.PREFIX)
                    }
                }
            }
            return preferences
        }

        fun loadFromPrefs(context: Context, appWidgetId: Int): WidgetConfig {
            val configAsJson = preferences(context).getString(key(appWidgetId), null)
            return Gson().fromJson(configAsJson, WidgetConfig::class.java) ?: WidgetConfig()
        }

        fun delete(context: Context, appWidgetIds: IntArray) {
            preferences(context).edit {
                appWidgetIds.forEach { remove(key(it)) }
            }
        }

        fun restore(context: Context, oldIds: IntArray, newIds: IntArray) {
            val preferences = preferences(context)
            val configurations = oldIds.map { preferences.getString(key(it), null) }
            preferences.edit {
                oldIds.forEach { remove(key(it)) }
                newIds.forEachIndexed { index, id ->
                    configurations[index]?.let { putString(key(id), it) }
                }
            }
        }
    }

    private fun replacePattern(today: LocalDateTime, pattern: String): String{
        val trueDatePattern = pattern.drop(2).dropLast(2)
        val formatter = DateTimeFormatter.ofPattern(trueDatePattern)
        return today.format(formatter)
    }

    private fun String.checkForPatterns(): String {
        // Filename has special date placeholder
        val today:LocalDateTime = LocalDateTime.now()
        // Replace patterns with values for current day
        return Regex("\\{\\{[^}]*\\}\\}").replace(this) {
                result -> replacePattern(today, result.value)
        }
    }

    fun getArticleFileName(): String {
        // Remove filename if it is entered.
        return "${fileName.removeSuffix(".md").checkForPatterns()}.md"
    }

    private fun getFolderPath(): String {
        //Make sure path is formatted right
        var folderPath = folder
        if(!folderPath.endsWith("/")) {
            folderPath = "${folderPath}/"
        }

        if(!folderPath.startsWith("/")) {
            folderPath = "/${folderPath}"
        }

        return folderPath.checkForPatterns()
    }

    fun getFullPath(): String {

        return "${getFolderPath()}${getArticleFileName()}"
    }

    fun getFullObsidianURL(): String {
        val getInternalPathToFile = getFullPath().split(vaultName).last()
        return "obsidian://open?vault=${vaultName}&file=${getInternalPathToFile}"
    }

    fun isConfigured(): Boolean {
        return folder.isNotBlank() && fileName.isNotBlank() && vaultName.isNotBlank()
    }

    fun saveToPrefs(context: Context, appWidgetId: Int) {
        val gson = Gson()
        val configAsJson = gson.toJson(this)
        preferences(context).edit {
            putString(key(appWidgetId), configAsJson)
        }
    }

}
