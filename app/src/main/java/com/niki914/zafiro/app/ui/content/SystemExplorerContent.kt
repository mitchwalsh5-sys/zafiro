package com.niki914.zafiro.app.ui.content

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.view.Display
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.niki914.uikit.infra.liquidScreenTopPadding
import kotlinx.coroutines.delay
import java.util.Locale

private data class SystemSnapshot(
    val device: String,
    val android: String,
    val build: String,
    val refreshRate: String,
    val resolution: String,
    val density: String,
    val ram: String,
    val storage: String,
    val battery: String,
    val batteryTemp: String,
    val uptime: String,
)

private data class MetricRow(
    val label: String,
    val value: String,
)

@Composable
fun SystemExplorerContent() {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(readSystemSnapshot(context)) }

    LaunchedEffect(context) {
        while (true) {
            snapshot = readSystemSnapshot(context)
            delay(2_000L)
        }
    }

    val sections = listOf(
        "DEVICE" to listOf(
            MetricRow("Model", snapshot.device),
            MetricRow("Android", snapshot.android),
            MetricRow("Build", snapshot.build),
            MetricRow("Uptime", snapshot.uptime),
        ),
        "DISPLAY" to listOf(
            MetricRow("Refresh rate", snapshot.refreshRate),
            MetricRow("Resolution", snapshot.resolution),
            MetricRow("Density", snapshot.density),
        ),
        "MEMORY" to listOf(
            MetricRow("RAM", snapshot.ram),
            MetricRow("Storage", snapshot.storage),
        ),
        "POWER" to listOf(
            MetricRow("Battery", snapshot.battery),
            MetricRow("Temperature", snapshot.batteryTemp),
        ),
    )

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = liquidScreenTopPadding() + 12.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "SYSTEM EXPLORER",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Live read-only diagnostics. Values refresh every two seconds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        sections.forEach { (title, rows) ->
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }
            items(rows) { row ->
                MetricCard(row)
            }
        }
    }
}

@Composable
private fun MetricCard(row: MetricRow) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = row.value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun readSystemSnapshot(context: Context): SystemSnapshot {
    val activityManager = context.getSystemService(ActivityManager::class.java)
    val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)

    val statFs = StatFs(Environment.getDataDirectory().absolutePath)
    val totalStorage = statFs.totalBytes
    val freeStorage = statFs.availableBytes
    val usedStorage = (totalStorage - freeStorage).coerceAtLeast(0L)

    val displayManager = context.getSystemService(DisplayManager::class.java)
    val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
    val mode = display?.mode
    val refreshRate = display?.refreshRate ?: mode?.refreshRate ?: 0f

    val metrics = context.resources.displayMetrics
    val width = mode?.physicalWidth ?: metrics.widthPixels
    val height = mode?.physicalHeight ?: metrics.heightPixels

    val batteryIntent = context.registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
    )
    val batteryLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val batteryScale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val batteryPercent = if (batteryLevel >= 0 && batteryScale > 0) {
        batteryLevel * 100 / batteryScale
    } else {
        -1
    }
    val batteryStatus = batteryIntent?.getIntExtra(
        BatteryManager.EXTRA_STATUS,
        BatteryManager.BATTERY_STATUS_UNKNOWN,
    ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
    val chargingLabel = when (batteryStatus) {
        BatteryManager.BATTERY_STATUS_CHARGING -> " · charging"
        BatteryManager.BATTERY_STATUS_FULL -> " · full"
        else -> ""
    }
    val temperatureTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        ?: Int.MIN_VALUE
    val batteryTemperature = if (temperatureTenths != Int.MIN_VALUE) {
        String.format(Locale.US, "%.1f °C", temperatureTenths / 10f)
    } else {
        "Unavailable"
    }

    val uptimeMs = SystemClock.elapsedRealtime()

    return SystemSnapshot(
        device = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" "),
        android = "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
        build = Build.DISPLAY,
        refreshRate = if (refreshRate > 0f) {
            String.format(Locale.US, "%.1f Hz", refreshRate)
        } else {
            "Unavailable"
        },
        resolution = "${width} × ${height}",
        density = "${metrics.densityDpi} dpi",
        ram = "${formatBytes(memoryInfo.totalMem - memoryInfo.availMem)} / ${formatBytes(memoryInfo.totalMem)}",
        storage = "${formatBytes(usedStorage)} / ${formatBytes(totalStorage)}",
        battery = if (batteryPercent >= 0) "${batteryPercent}%${chargingLabel}" else "Unavailable",
        batteryTemp = batteryTemperature,
        uptime = formatDuration(uptimeMs),
    )
}

private fun formatBytes(bytes: Long): String {
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f GB", gib)
}

private fun formatDuration(milliseconds: Long): String {
    val totalMinutes = milliseconds / 60_000L
    val days = totalMinutes / (24L * 60L)
    val hours = (totalMinutes / 60L) % 24L
    val minutes = totalMinutes % 60L
    return if (days > 0) {
        "${days}d ${hours}h ${minutes}m"
    } else {
        "${hours}h ${minutes}m"
    }
}
