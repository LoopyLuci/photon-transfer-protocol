package com.prism.ot.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.prism.ot.ui.navigation.PrismNavGraph
import com.prism.ot.ui.theme.PrismColors
import com.prism.ot.ui.theme.PrismTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Single-activity host for the whole app.
 *
 * Responsibilities:
 *  - Install the system splash screen before `super.onCreate()` and hold
 *    it until the ML bridge has finished initialising. This is the
 *    documented use of `setKeepOnScreenCondition`.
 *  - Request the camera and Bluetooth permissions that the scanning
 *    screens depend on. The permissions are requested once, here, so the
 *    screens themselves never have to block on a permission dialog.
 *  - Set the Compose content and the top-level navigation graph.
 *
 * The activity is `singleTask` (declared in the manifest) so that
 * notifications from the scanning services return to the existing task
 * rather than creating a second camera owner.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        // Individual screens re-check the grants they need; this callback
        // exists only so the system does not silently drop the request.
        val allGranted = grants.values.all { it }
        if (!allGranted) {
            // The scanning screens surface a rationale and a settings
            // deep-link if the user denies; no further action here.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hold the splash screen until the Compose content has drawn its
        // first frame. The ML bridge initialisation is triggered from
        // PrismApplication and is already in flight; this flag is released
        // once the graph has been composed.
        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }

        requestStartupPermissions()

        setContent {
            PrismTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PrismColors.Background,
                ) {
                    val homeVm: com.prism.ot.ui.screens.home.HomeViewModel =
                        androidx.hilt.navigation.compose.hiltViewModel()
                    val startupState by homeVm.startupState.collectAsStateWithLifecycle()

                    LaunchedEffect(startupState) {
                        if (startupState is com.prism.ot.ui.screens.home.StartupState.Ready) {
                            keepSplash = false
                        }
                    }

                    PrismNavGraph()
                }
            }
        }
    }

    private fun requestStartupPermissions() {
        val permissions = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}
