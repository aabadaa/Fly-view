package com.abada.flyview

import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.abada.flyView.DraggableFlyView
import com.abada.flyView.FlyController
import com.abada.flyView.FlyViewInfo
import com.abada.flyView.FlyViewService
import com.abada.flyView.windowManagerUtils.addFlyInfo
import kotlinx.coroutines.delay

class ExampleController : FlyController {
    var x by mutableIntStateOf(0)
    var auto by mutableStateOf(false)

    override fun update(data: Bundle) {
        x = data.getInt("exampleInt")
    }
}

fun Context.createFlyViewUsingFlyService() {
    FlyViewService.infoProviders["fly service"] = {
        val controller = ExampleController()
        FlyViewInfo(controller = controller, onRemove = {
            controller.x = 10
            controller.auto = false
            delay(1000)
            goToScreenBorder()
        }) {
            FlyViewContent()
        }
    }
    FlyViewService.show(this, "fly service")
}

fun Context.createFlyView() {
    val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val controller = ExampleController()

    windowManager.addFlyInfo(this, "fly", FlyViewInfo(controller = controller, onRemove = {
        controller.x = 10
        controller.auto = false
        delay(1000)
        goToScreenBorder()
    }) {
        FlyViewContent()
    })
}

@Composable
private fun FlyViewInfo<ExampleController>.FlyViewContent() {
    DraggableFlyView(autoGoToBorder = controller.auto) {
        BackHandler(true) {
            Log.i(ContentValues.TAG, "createFlyView: backHandler")
            removeView()
        }
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(Color.LightGray),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "x = ${controller.x}")
            Button(onClick = removeView) {
                Text("Close")
            }
            Button(onClick = {
                controller.x++
            }) {
                Text("x++")
            }
            Button(onClick = { controller.auto = controller.auto.not() }) {
                Text(text = controller.auto.toString())
            }
            Button(onClick = ::goToScreenBorder) {
                Text(text = "Go to screen border")
            }
            Button(onClick = {
                animateTo(0, 0)
            }) {
                Text("Go to center")
            }
        }
    }

}

fun updateFlyView(key: String, value: Int) {
    com.abada.flyView.windowManagerUtils.updateFlyView(
        key,
        Bundle().also { it.putInt("exampleInt", value) })
}