package com.abada.flyView.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DismissCoordinatorTest {
    private val commits = mutableListOf<DismissReason>()
    private var cancelled = 0
    private val errors = mutableListOf<Throwable>()

    private fun TestScope.coordinator() = DismissCoordinator(
        scope = this,
        commit = { commits += it },
        onCancelled = { cancelled++ },
        onError = { errors += it },
    )

    @Test
    fun withoutAnEffectTheRequestIsCommittedRightAway() = runTest {
        val c = coordinator()
        assertTrue(c.request(DismissReason.Back))
        assertEquals(listOf(DismissReason.Back), commits)
        assertEquals(DismissCoordinator.State.Committed, c.state)
    }

    @Test
    fun theEffectTakesControlAndCommitsWhenItCallsDismiss() = runTest {
        val c = coordinator()
        val events = mutableListOf<String>()
        c.effect = {
            events += "message"
            delay(400)
            events += "dismiss"
            dismiss()
        }
        c.request(DismissReason.DragDrop)
        runCurrent()
        assertEquals(listOf("message"), events)
        assertTrue(commits.isEmpty(), "nothing is committed while the effect runs")
        assertEquals(DismissCoordinator.State.Running, c.state)
        advanceTimeBy(401)
        runCurrent()
        assertEquals(listOf(DismissReason.DragDrop), commits)
    }

    @Test
    fun returningWithoutChoosingCountsAsDismiss() = runTest {
        val c = coordinator()
        c.effect = { delay(10) }
        c.request(DismissReason.External)
        advanceUntilIdle()
        assertEquals(listOf(DismissReason.External), commits)
    }

    @Test
    fun cancelKeepsTheWindowAndAllowsAnotherRequest() = runTest {
        val c = coordinator()
        c.effect = { cancel() }
        c.request(DismissReason.DragDrop)
        advanceUntilIdle()
        assertTrue(commits.isEmpty())
        assertEquals(1, cancelled)
        assertEquals(DismissCoordinator.State.Idle, c.state)
        assertTrue(c.request(DismissReason.Back))
    }

    @Test
    fun requestsWhileOneIsInProgressAreIgnored() = runTest {
        val c = coordinator()
        c.effect = { delay(100); dismiss() }
        assertTrue(c.request(DismissReason.DragDrop))
        assertFalse(c.request(DismissReason.Back))
        advanceUntilIdle()
        assertEquals(listOf(DismissReason.DragDrop), commits)
        assertFalse(c.request(DismissReason.Back), "already committed")
    }

    @Test
    fun dismissTwiceCommitsOnce() = runTest {
        val c = coordinator()
        c.effect = { dismiss(); dismiss(); cancel() }
        c.request(DismissReason.Content)
        advanceUntilIdle()
        assertEquals(1, commits.size)
        assertEquals(0, cancelled)
    }

    @Test
    fun theEffectSeesTheReason() = runTest {
        val c = coordinator()
        var seen: DismissReason? = null
        c.effect = { seen = reason; dismiss() }
        c.request(DismissReason.Back)
        advanceUntilIdle()
        assertEquals(DismissReason.Back, seen)
    }

    @Test
    fun aFailingEffectCancelsAndReportsTheError() = runTest {
        val c = coordinator()
        c.effect = { error("boom") }
        c.request(DismissReason.Content)
        advanceUntilIdle()
        assertTrue(commits.isEmpty())
        assertEquals(1, cancelled)
        assertEquals("boom", errors.single().message)
        assertEquals(DismissCoordinator.State.Idle, c.state)
    }

    @Test
    fun cleanupInFinallyRunsWhenTheScopeIsCancelled() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val c = DismissCoordinator(scope, commit = { commits += it })
        var cleaned = false
        c.effect = { try { delay(10_000) } finally { cleaned = true } }
        c.request(DismissReason.Content)
        runCurrent()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        runCurrent()
        assertTrue(cleaned)
        assertTrue(commits.isEmpty(), "a cancelled effect does not commit")
    }

    @Test
    fun resetAllowsADismissalAfterTheWindowIsShownAgain() = runTest {
        val c = coordinator()
        c.request(DismissReason.Back)
        c.reset()
        assertTrue(c.request(DismissReason.Back))
        assertEquals(2, commits.size)
    }
}

class ClosingPolicyTest {
    @Test
    fun aProcessWithoutAServiceIsAtRiskInTheBackgroundLevels() {
        assertEquals(ClosingReason.ProcessAtRisk(40), ClosingPolicy.reasonForTrim(40, serviceActive = false))
        assertEquals(ClosingReason.ProcessAtRisk(80), ClosingPolicy.reasonForTrim(80, serviceActive = false))
    }

    @Test
    fun lowerLevelsAreNotAWarning() {
        assertEquals(null, ClosingPolicy.reasonForTrim(20, serviceActive = false))
    }

    @Test
    fun withAServiceTheProcessIsNotAtRisk() {
        assertEquals(null, ClosingPolicy.reasonForTrim(80, serviceActive = true))
    }
}
