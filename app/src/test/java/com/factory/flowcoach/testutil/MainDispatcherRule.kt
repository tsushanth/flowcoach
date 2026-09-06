package com.factory.flowcoach.testutil

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps [Dispatchers.Main] for a [TestDispatcher] for the duration of each test.
 *
 * Defaults to [UnconfinedTestDispatcher] so that `stateIn`/`combine` chains started by
 * `viewModelScope`/`WhileSubscribed` collect eagerly as soon as a test subscribes, instead of
 * requiring an explicit `advanceUntilIdle()` before every assertion.
 */
@ExperimentalCoroutinesApi
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
