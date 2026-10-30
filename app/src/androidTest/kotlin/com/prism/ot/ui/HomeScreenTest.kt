package com.prism.ot.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * Compose UI test for the home screen.
 *
 * Uses `createAndroidComposeRule<HiltTestActivity>` rather than a plain
 * activity rule so Hilt can inject into the composition. The rule order
 * matters: `HiltAndroidRule` must run before the compose rule.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HomeScreenTest : FreeSpec({

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    beforeSpec { hiltRule.inject() }

    "shows both scan actions" {
        composeRule.setContent {
            com.prism.ot.ui.theme.PrismTheme {
                com.prism.ot.ui.screens.home.HomeScreen(
                    onScanStatic = {},
                    onScanKinetic = {},
                    onOpenSettings = {},
                    onOpenResult = {},
                )
            }
        }
        composeRule.onNodeWithText("Static scan").assertIsDisplayed()
        composeRule.onNodeWithText("Kinetic scan").assertIsDisplayed()
    }

    "static action invokes the callback" {
        var clicked = false
        composeRule.setContent {
            com.prism.ot.ui.theme.PrismTheme {
                com.prism.ot.ui.screens.home.HomeScreen(
                    onScanStatic = { clicked = true },
                    onScanKinetic = {},
                    onOpenSettings = {},
                    onOpenResult = {},
                )
            }
        }
        composeRule.onNodeWithText("Static scan").performClick()
        composeRule.waitForIdle()
        clicked shouldBe true
    }
})
