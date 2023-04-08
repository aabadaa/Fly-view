package com.abada.flyview

import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.abada.flyView.DraggableFlyView
import com.abada.flyView.FlyController
import com.abada.flyView.FlyViewInfo
import com.abada.flyView.FlyViewService

class ExampleController : FlyController {
    var x by mutableStateOf(0)
    override fun update(data: Bundle) {
        x = data.getInt("exampleInt")
    }
}

fun Context.createFlyView() {
    FlyViewService.infoProviders["test"] = {
        val controller = ExampleController()
        FlyViewInfo(controller = controller) {
            DraggableFlyView {
                BackHandler(true) {
                    Log.i(ContentValues.TAG, "createFlyView: backHandler")
                    removeView()
                }
                Column {
                    Text(text = "test ${controller.x}")
                    Button(onClick = removeView) {
                        Text("Close")
                    }
                    Button(onClick = {
                        controller.x++
                    }) {
                        Text("x++")
                    }
                }
            }
        }
    }
    FlyViewService.show(this, "test")
}

fun updateFlyView(value: Int) {
    com.abada.flyView.updateFlyView("test", Bundle().also { it.putInt("exampleInt", value) })
}