package com.abada.flyView.overlay

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class FlySnackbarStateTest {
    @Test
    fun theActionEndsItWithActionPerformed() = runTest(UnconfinedTestDispatcher()) {
        val state = FlySnackbarState()
        val result = async(start = CoroutineStart.UNDISPATCHED) { state.showSnackbar("Deleted", "Undo") }
        assertEquals("Deleted", state.current?.message)
        state.current!!.performAction()
        assertEquals(FlySnackbarResult.ActionPerformed, result.await())
        assertNull(state.current)
    }

    @Test
    fun dismissEndsItWithDismissed() = runTest(UnconfinedTestDispatcher()) {
        val state = FlySnackbarState()
        val result = async(start = CoroutineStart.UNDISPATCHED) { state.showSnackbar("Saved") }
        state.current!!.dismiss()
        assertEquals(FlySnackbarResult.Dismissed, result.await())
    }

    @Test
    fun snackbarsShowOneAtATimeInOrder() = runTest(UnconfinedTestDispatcher()) {
        val state = FlySnackbarState()
        val first = async(start = CoroutineStart.UNDISPATCHED) { state.showSnackbar("one") }
        val second = async(start = CoroutineStart.UNDISPATCHED) { state.showSnackbar("two") }
        assertEquals("one", state.current?.message)
        state.current!!.dismiss()
        first.await()
        assertEquals("two", state.current?.message)
        state.current!!.dismiss()
        second.await()
        assertNull(state.current)
    }

    @Test
    fun cancellingTheCallerRemovesTheSnackbar() = runTest(UnconfinedTestDispatcher()) {
        val state = FlySnackbarState()
        val job = async(start = CoroutineStart.UNDISPATCHED) { state.showSnackbar("gone") }
        job.cancel()
        assertNull(state.current)
    }

    @Test
    fun durationDefaultsToLongerWithAnAction() = runTest(UnconfinedTestDispatcher()) {
        val state = FlySnackbarState()
        async(start = CoroutineStart.UNDISPATCHED) { state.showSnackbar("a", "Undo") }
        assertEquals(FlySnackbarDuration.Long, state.current!!.duration)
        state.current!!.dismiss()
        async(start = CoroutineStart.UNDISPATCHED) { state.showSnackbar("b") }
        assertEquals(FlySnackbarDuration.Short, state.current!!.duration)
        state.current!!.dismiss()
    }
}
