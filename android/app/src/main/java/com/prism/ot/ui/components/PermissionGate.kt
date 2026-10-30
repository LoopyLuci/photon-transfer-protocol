package com.prism.ot.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

/**
 * Wraps a camera-dependent screen with a permission gate.
 *
 * The gate is a hard block, not a dismissible prompt. Without camera
 * permission there is no useful fallback: the screen has nothing to show.
 * If the user has permanently denied the permission, the gate routes to
 * the system settings page, which is the only path that can recover it.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPermissionGate(
    content: @Composable () -> Unit,
) {
    val permission = rememberPermissionState(Manifest.permission.CAMERA)

    if (permission.status.isGranted) {
        content()
        return
    }

    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Camera access is required",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "PRISM uses the camera to read optical codes. " +
                "No images leave your device.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = { permission.launchPermissionRequest() }) {
            Text("Grant camera access")
        }
        Button(
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
        ) {
            Text("Open app settings")
        }
    }
}
