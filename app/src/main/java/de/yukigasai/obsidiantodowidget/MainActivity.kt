package de.yukigasai.obsidiantodowidget

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.yukigasai.obsidiantodowidget.ui.theme.ObsidianTodoWidgetTheme
import de.yukigasai.obsidiantodowidget.util.ActionsConstants


class MainActivity : ComponentActivity() {

    private var storageGranted by mutableStateOf(false)
    private var notificationsGranted by mutableStateOf(false)
    private var exactAlarmsGranted by mutableStateOf(false)
    private var obsidianReturnEnabled by mutableStateOf(false)

    private val storagePermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { updatePermissionState() }

    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updatePermissionState() }

    private fun requestForStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            storagePermissionRequest.launch(
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun requestNotificationPermission() {
        val preferences = getPreferences(MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED &&
            !preferences.getBoolean("notification_permission_requested", false)
        ) {
            preferences.edit().putBoolean("notification_permission_requested", true).apply()
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }
    }

    fun checkStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //Android is 11 (R) or above
            Environment.isExternalStorageManager()
        } else {
            //Below android 11
            val write =
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val read =
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
            read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ObsidianTodoWidgetTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionSettings()
//                    ColorBox()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionState()
        if (storageGranted) {
            sendBroadcast(
                Intent(this, TodoWidgetReceiver::class.java)
                    .setAction(ActionsConstants.UPDATE_WIDGET)
            )
        }
    }

    private fun updatePermissionState() {
        storageGranted = checkStoragePermissions()
        notificationsGranted = NotificationManagerCompat.from(this).areNotificationsEnabled()
        exactAlarmsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (getSystemService(ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        val service = ComponentName(this, ObsidianReturnService::class.java)
        obsidianReturnEnabled = (getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id) == service }
    }

    @Composable
    private fun PermissionSettings() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.permissions_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.storage_permission_description))
            Button(onClick = { requestForStoragePermissions() }, enabled = !storageGranted) {
                Text(stringResource(
                    if (storageGranted) R.string.storage_permission_granted
                    else R.string.storage_permission_allow
                ))
            }
            Text(stringResource(R.string.notification_permission_description))
            Button(onClick = { requestNotificationPermission() }, enabled = !notificationsGranted) {
                Text(stringResource(
                    if (notificationsGranted) R.string.notifications_enabled
                    else R.string.notifications_allow
                ))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Text(stringResource(R.string.exact_alarm_permission_description))
                Button(
                    onClick = {
                        startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:$packageName")
                            )
                        )
                    },
                    enabled = !exactAlarmsGranted
                ) {
                    Text(stringResource(
                        if (exactAlarmsGranted) R.string.exact_alarms_enabled
                        else R.string.exact_alarms_allow
                    ))
                }
            }
            Text(
                stringResource(R.string.obsidian_return_service_label),
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(R.string.obsidian_return_setup_description))
            Text(stringResource(
                if (obsidianReturnEnabled) R.string.obsidian_return_enabled
                else R.string.obsidian_return_disabled
            ))
            Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                Text(stringResource(R.string.accessibility_settings_open))
            }
        }
    }
}
@Composable
fun ColorBox() {
    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1F)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Text(text = "Primary", color = MaterialTheme.colorScheme.onPrimary)
        }
        Box(
            modifier = Modifier
                .weight(1F)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Text(text = "PrimaryContainer", color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Box(
            modifier = Modifier
                .weight(1F)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondary)
        ) {
            Text(text = "Secondary", color = MaterialTheme.colorScheme.onSecondary)
        }
        Box(
            modifier = Modifier
                .weight(1F)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Text(text = "SecondaryContainer", color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Box(
            modifier = Modifier
                .weight(1F)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.tertiary)
        ) {
            Text(text = "Tenary", color = MaterialTheme.colorScheme.onTertiary)
        }
        Box(
            modifier = Modifier
                .weight(1F)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Text(text = "TenaryContainer", color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
        Box(
            modifier = Modifier
                .weight(1F)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Text(text = "Background", color = MaterialTheme.colorScheme.onBackground)
        }
        Box(
            modifier = Modifier
                .weight(1F)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Text(text = "Surface", color = MaterialTheme.colorScheme.onSurface)
        }
        Box(
            modifier = Modifier
                .weight(1F)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(text = "SurfaceVariant", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(name = "DayView")
@Composable
fun ColorBoxLight() {
    ObsidianTodoWidgetTheme(darkTheme = false) {
        ColorBox()
    }
}

@Preview(name = "NightView", uiMode = UI_MODE_NIGHT_YES)
@Composable
fun ColorBoxDark() {
    ObsidianTodoWidgetTheme(darkTheme = true) {
        ColorBox()
    }
}
