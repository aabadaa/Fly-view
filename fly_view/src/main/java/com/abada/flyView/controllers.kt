package com.abada.flyView

import android.os.Bundle

/**
 * Implement this interface and pass it to the [FlyViewInfo] to talk to a showing FlyView.
 *
 * There are two ways to talk to it:
 * - From anywhere, with a bundle: [FlyViews.update] calls [update].
 * - Typed, with [FlyViews.withController]: no bundle needed, you get your own controller type.
 */
interface FlyController {
    /** Called by [FlyViews.update] (and when a bundle is passed to [FlyViews.show]) */
    fun update(data: Bundle) {}
}

/**
 * If you don't want to provide a controller pass this object to the [FlyViewInfo]
 */
object NoController : FlyController
