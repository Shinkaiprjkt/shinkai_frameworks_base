/*
 * Copyright (C) 2026 Shinkai Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.volume.ui.navigation

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay

/**
 * Self-contained per-app volume panel.
 *
 * Reads active app volumes straight from [AudioManager] (no dedicated
 * ViewModel/Interactor, by design — kept intentionally minimal) and lets the
 * user adjust each one with a real Material 3 [Slider]. Polls every second
 * while visible so apps that start/stop playing audio show up without the
 * user having to reopen the panel.
 */
@Composable
fun AppVolumePanelRoot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var activeApps by remember { mutableStateOf(audioManager.activeAppVolumes()) }

    LaunchedEffect(audioManager) {
        while (true) {
            activeApps = audioManager.activeAppVolumes()
            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "App volume",
            style = MaterialTheme.typography.titleMedium,
        )

        if (activeApps.isEmpty()) {
            Text(
                text = "No apps are currently playing audio",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            activeApps.forEach { app ->
                key(app.packageName) {
                    AppVolumeRow(
                        context = context,
                        audioManager = audioManager,
                        packageName = app.packageName,
                        initialVolume = app.volume,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppVolumeRow(
    context: Context,
    audioManager: AudioManager,
    packageName: String,
    initialVolume: Float,
) {
    var volume by remember(packageName) { mutableStateOf(initialVolume) }
    val pm = context.packageManager
    val label = remember(packageName) { appLabel(pm, packageName) }
    val icon = remember(packageName) { appIcon(pm, packageName) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = BitmapPainter(icon.toBitmap().asImageBitmap()),
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = Color.Unspecified,
            )
        }
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Slider(
                value = volume,
                onValueChange = {
                    volume = it
                    audioManager.setAppVolume(packageName, it)
                },
            )
        }
    }
}

private data class ActiveAppVolume(val packageName: String, val volume: Float)

private fun AudioManager.activeAppVolumes(): List<ActiveAppVolume> =
    listAppVolumes()
        .filter { it.isActive }
        .map { ActiveAppVolume(it.packageName, it.volume) }

private fun appLabel(pm: PackageManager, packageName: String): String =
    try {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

private fun appIcon(pm: PackageManager, packageName: String): Drawable? =
    try {
        pm.getApplicationIcon(packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
