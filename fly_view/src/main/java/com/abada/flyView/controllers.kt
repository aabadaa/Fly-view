package com.abada.flyView

import android.os.Bundle
/**
 * Implement this interface and pass it to the [FlyViewInfo] to update FlyView using updateFlyView
 */
interface FlyController {
    fun update(data: Bundle)
}

/**
 * If you don't want to provide a controller pass this object to the [FlyViewInfo]
 */
object NoController:FlyController{
    override fun update(data: Bundle) {}
}