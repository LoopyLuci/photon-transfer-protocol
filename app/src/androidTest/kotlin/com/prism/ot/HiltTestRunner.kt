package com.prism.ot

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom instrumented test runner.
 *
 * Hilt generates `HiltTestApplication` for instrumented tests. Without
 * this runner, the production `PrismApplication` is used and the test
 * graph is not installed, which produces "Hilt was not initialized"
 * failures. The runner is declared in `android.defaultConfig` as
 * `testInstrumentationRunner`.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader,
        className: String,
        context: Context,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
