package com.abada.flyview

import android.content.Context
import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.abada.flyView.FlyController
import com.abada.flyView.FlyView
import com.abada.flyView.FlyViewInfo
import com.abada.flyView.FlyViewService

class ExampleController : FlyController {
    var x by mutableStateOf(0)
    override fun update(data: Bundle) {
        x = data.getInt("exampleInt")
    }
}

fun Context.createFlyView() {
    FlyView.infoProviders["test"] = {
        FlyViewInfo(controller = ExampleController()) {
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
    FlyViewService.show(this, "test")
}

fun Context.updateFlyView(value: Int) {
    FlyViewService.update(this, "test", Bundle().also { it.putInt("exampleInt", value) })
}