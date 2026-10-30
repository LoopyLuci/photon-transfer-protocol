package com.prism.ot

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Empty activity required by `createAndroidComposeRule` when Hilt is
 * present. Hilt must be able to inject into the host activity, and the
 * production `MainActivity` is too heavyweight to use as a test host.
 */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()
