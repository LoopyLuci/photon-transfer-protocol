package com.prism.ot.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Instrumented session lifecycle test.
 *
 * Verifies that a StaticSession can be created, started, and cancelled
 * without a real camera. The FakeCameraModule supplies a deterministic
 * camera controller; the fake does not produce tiles, so the test asserts
 * lifecycle transitions only, not decoding.
 *
 * `@HiltAndroidTest` is mandatory on any test that uses Hilt injection;
 * `HiltAndroidRule` must be ordered before any rule that triggers
 * injection.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StaticSessionTest : FreeSpec({

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var sessionFactory: SessionFactory

    beforeSpec { hiltRule.inject() }

    "session reaches Running and then Finished on cancel" {
        runBlocking {
            val session = sessionFactory.create(com.prism.ot.data.model.TransferMode.STATIC)

            withTimeout(10_000) {
                session.state.first { it is SessionState.Running }
            }

            session.cancel("test")

            withTimeout(10_000) {
                session.state.first { it == SessionState.Finished }
            }
            session.result.value.shouldBeInstanceOf<SessionResult.Cancelled>()
        }
    }

    "session can be stopped without a cancel" {
        runBlocking {
            val session = sessionFactory.create(com.prism.ot.data.model.TransferMode.STATIC)
            withTimeout(10_000) { session.state.first { it is SessionState.Running } }
            session.stop()
            withTimeout(10_000) { session.state.first { it == SessionState.Finished } }
        }
    }
})
