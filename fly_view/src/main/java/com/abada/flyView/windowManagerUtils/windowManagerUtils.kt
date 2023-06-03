package com.abada.flyView.windowManagerUtils

import android.content.res.Resources
import android.view.WindowManager
import com.abada.flyView.FlyController
import com.abada.flyView.FlyViewInfo

internal val showedViews: MutableMap<String, FlyViewInfo<out FlyController>> = mutableMapOf()

internal fun hideCloseView() {
    showedViews["close"]?.let { closeView ->
        closeView.params {
           it.width = 0
           it.height = 0
           it
        }
    }
}

internal fun showCloseView() {
    showedViews["close"]?.let { closeView ->
        closeView.params{
            it.width = WindowManager.LayoutParams.MATCH_PARENT
            it.height = Resources.getSystem().displayMetrics.heightPixels / 6
            it
        }
    }
}