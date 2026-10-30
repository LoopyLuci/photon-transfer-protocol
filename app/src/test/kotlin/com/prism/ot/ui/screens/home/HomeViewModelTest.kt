package com.prism.ot.ui.screens.home

import app.cash.turbine.test
import com.prism.ot.core.PrismLogger
import com.prism.ot.core.PrismResult
import com.prism.ot.data.model.TransferMode
import com.prism.ot.data.preferences.PrismPreferences
import com.prism.ot.ml.MlBridge
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Home ViewModel tests.
 *
 * Pattern: `runTest` for virtual time, Turbine for sequential state
 * assertions, MockK for the preference and ML collaborators. Turbine is
 * used because the ViewModel emits Init → Ready → content; without it,
 * only the final `StateFlow.value` can be asserted and the ordering is
 * lost.
 *
 * Dispatchers.setMain is mandatory: ViewModels that launch in
 * viewModelScope fail with "Module with the Main dispatcher had failed to
 * initialize" otherwise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest : FreeSpec({

    val testDispatcher = StandardTestDispatcher()

    beforeEach { Dispatchers.setMain(testDispatcher) }
    afterEach { Dispatchers.resetMain() }

    "startup reaches Ready when ML initialisation succeeds" {
        runTest(testDispatcher) {
            val preferences = mockk<PrismPreferences>(relaxed = true) {
                every { totalSessionsCompleted } returns flowOf(0)
                every { totalBytesTransferred } returns flowOf(0L)
                every { preferredTransferMode } returns flowOf(TransferMode.KINETIC)
            }
            val mlBridge = mockk<MlBridge>(relaxed = true) {
                coEvery { initialize() } returns PrismResult.Success(Unit)
                every { listMissingModelAssets() } returns emptyList()
            }
            val vm = HomeViewModel(preferences, mlBridge, mockk<PrismLogger>(relaxed = true))

            vm.startupState.test {
                awaitItem() shouldBe StartupState.Initialising
                awaitItem() shouldBe StartupState.Ready
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "startup reaches Failed when ML initialisation fails" {
        runTest(testDispatcher) {
            val preferences = mockk<PrismPreferences>(relaxed = true) {
                every { totalSessionsCompleted } returns flowOf(0)
                every { totalBytesTransferred } returns flowOf(0L)
                every { preferredTransferMode } returns flowOf(TransferMode.STATIC)
            }
            val mlBridge = mockk<MlBridge>(relaxed = true) {
                coEvery { initialize() } returns PrismResult.Failure(
                    com.prism.ot.core.PrismError.ModelLoadFailed("M05", "missing"),
                )
            }
            val vm = HomeViewModel(preferences, mlBridge, mockk(relaxed = true))

            vm.startupState.test {
                awaitItem() shouldBe StartupState.Initialising
                awaitItem().shouldBeInstanceOf<StartupState.Failed>()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "content state reflects preferences" {
        runTest(testDispatcher) {
            val preferences = mockk<PrismPreferences>(relaxed = true) {
                every { totalSessionsCompleted } returns flowOf(7)
                every { totalBytesTransferred } returns flowOf(1_048_576L)
                every { preferredTransferMode } returns flowOf(TransferMode.KINETIC)
            }
            val mlBridge = mockk<MlBridge>(relaxed = true) {
                coEvery { initialize() } returns PrismResult.Success(Unit)
                every { listMissingModelAssets() } returns emptyList()
            }
            val vm = HomeViewModel(preferences, mlBridge, mockk(relaxed = true))

            vm.uiState.test {
                // First emission is the combine() result once flows
                // produce their initial values.
                val state = awaitItem()
                state.contentState.totalSessionsCompleted shouldBe 7
                state.contentState.totalBytesTransferred shouldBe 1_048_576L
                state.contentState.preferredMode shouldBe TransferMode.KINETIC
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
})
