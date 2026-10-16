package com.abada.flyview.v2

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * App state that the entry points (a button, a tile, a widget, a receiver) set and the overlay reads.
 * Requests go through a [Channel]: `send` suspends until the request is queued, and a request sent
 * before the window exists is kept until the window starts collecting (a SharedFlow would drop it).
 */
object BubbleEntry {
    val isOpen = MutableStateFlow(false)
    private val queue = Channel<String>(Channel.BUFFERED)
    val requests = queue.receiveAsFlow()

    suspend fun request(text: String) {
        queue.send(text)
        isOpen.value = true
    }
}
