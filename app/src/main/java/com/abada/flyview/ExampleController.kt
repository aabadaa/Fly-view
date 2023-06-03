package com.abada.flyview

import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.abada.flyView.DraggableFlyView
import com.abada.flyView.FlyController
import com.abada.flyView.FlyViewInfo
import com.abada.flyView.FlyViewService
import kotlinx.coroutines.delay

class ExampleController : FlyController {
    var x by mutableStateOf(0)
    override fun update(data: Bundle) {
        x = data.getInt("exampleInt")
    }
}

fun Context.createFlyView() {
    FlyViewService.infoProviders["test"] = {
        val controller = ExampleController()
        FlyViewInfo(controller = controller, onRemove = {
            controller.x = 10
            delay(1000)
        }) {
            var auto by remember { mutableStateOf(false) }
            DraggableFlyView (autoGoToBorder = auto){
                BackHandler(true) {
                    Log.i(ContentValues.TAG, "createFlyView: backHandler")
                    removeView()
                }
                Column(modifier = Modifier.background(Color.White)) {
                    Text(text = "test ${controller.x}")
                    Button(onClick = removeView) {
                        Text("Close")
                    }
                    Button(onClick = {
                        controller.x++
                    }) {
                        Text("x++")
                    }
                    Button(onClick = { auto = auto.not() }) {
                        Text(text = auto.toString())
                    }
                }
            }
        }
    }
    FlyViewService.show(this, "test")
}

fun updateFlyView(value: Int) {
    com.abada.flyView.windowManagerUtils.updateFlyView(
        "test",
        Bundle().also { it.putInt("exampleInt", value) })
}