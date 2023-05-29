package com.abada.flyView.windowManagerUtils

import android.content.res.Resources
import android.view.WindowManager
import com.abada.flyView.FlyController
import com.abada.flyView.FlyViewInfo

internal val showedViews: MutableMap<String, FlyViewInfo<out FlyController>> = mutableMapOf()

internal fun WindowManager.hideCloseView() {
    showedViews["close"]?.let { closeView ->
        closeView.params.width = 0
        closeView.params.height = 0
        updateViewLayout(closeView.flyView, closeView.params)
    }
}

internal fun WindowManager.showCloseView() {
    showedViews["close"]?.let { closeView ->
        closeView.params.width = WindowManager.LayoutParams.MATCH_PARENT
        closeView.params.height = Resources.getSystem().displayMetrics.heightPixels / 6
        updateViewLayout(closeView.flyView, closeView.params)
    }
}