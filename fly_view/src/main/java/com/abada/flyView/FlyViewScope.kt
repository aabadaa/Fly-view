package com.abada.flyView

import android.view.WindowManager

class FlyViewScope(
    params: WindowManager.LayoutParams,
    val removeView: () -> Unit,
    private val updateLayoutParams: (WindowManager.LayoutParams) -> Unit,
) {
    var params: WindowManager.LayoutParams = params
        set(value) {
            updateLayoutParams(value)
            field = value
        }
}

