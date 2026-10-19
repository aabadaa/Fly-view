package com.abada.flyview

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The state of the sample: nothing special, plain flows that any screen and the floating window read.
 * Change it from anywhere (a button, a notification, a service) and every place that shows it updates.
 */
object CounterState {
    val count = MutableStateFlow(0)
    val isOpen = MutableStateFlow(false)
    val isExpandableOpen = MutableStateFlow(false)
    val expanded = MutableStateFlow(false)
    val isPlaygroundOpen = MutableStateFlow(false)
}
