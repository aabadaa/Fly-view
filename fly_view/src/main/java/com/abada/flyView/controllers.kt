package com.abada.flyView

import android.os.Bundle

interface FlyController {
    fun update(data: Bundle)
}
object NoController:FlyController{
    override fun update(data: Bundle) {}
}