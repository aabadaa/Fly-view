package com.abada.flyview.v2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.delay

/** The logic behind the bubble window. A real ViewModel: it is cleared when the window session ends. */
class BubbleViewModel : ViewModel() {
    var text by mutableStateOf("")
    var count by mutableIntStateOf(0)
    var expanded by mutableStateOf(false)
    var closed by mutableStateOf(false)
    var message by mutableStateOf("")
    var closing by mutableStateOf(false)

    fun onRequest(request: String) {
        if (request.isNotEmpty()) text = request
        expanded = true
    }

    /** What happens when the user drops the bubble on the dismiss zone: a message, a save, a short wait */
    suspend fun showGoodbyeAndSave() {
        message = "Bye, count was $count"
        closing = true
        expanded = false
        delay(400) // pretend to save
    }
}
