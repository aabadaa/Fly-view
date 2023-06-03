package com.abada.flyView

import android.view.WindowManager
/**
 * This class provides view property to the Composable content
 * @param params pass the initial [WindowManager.LayoutParams] object to enable updating layout params inside the composable view
 * @property removeView call this in your composable function to remove it from the [WindowManager]
 * @property updateLayoutParams this function will be called when you assign a new object to [params]
 */
class FlyViewScope(
    params: WindowManager.LayoutParams,

    private val updateLayoutParams: (WindowManager.LayoutParams) -> Unit,
) {
    var removeView: () -> Unit = { }
        internal set
    var params: WindowManager.LayoutParams = params
        set(value) {
            updateLayoutParams(value)
            field = value
        }
}

