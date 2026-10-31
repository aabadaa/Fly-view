package com.abada.flyView.overlay

import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abada.flyView.core.PopupAlignment
import com.abada.flyView.core.PopupSide
import kotlinx.coroutines.CompletableDeferred

/**
 * What the selection toolbar says on the old text toolbar path. The defaults are the system's own, so
 * they are translated. The new context menu brings its own labels, already translated.
 */
class FlyTextToolbarLabels(
    val cut: String,
    val copy: String,
    val paste: String,
    val selectAll: String,
) {
    companion object {
        @Composable
        fun system() = FlyTextToolbarLabels(
            cut = stringResource(android.R.string.cut),
            copy = stringResource(android.R.string.copy),
            paste = stringResource(android.R.string.paste),
            selectAll = stringResource(android.R.string.selectAll),
        )
    }
}

/**
 * The selection toolbar of a [FlyWindow]: the system's selection popup needs an activity window and is
 * missing there, so selecting text gives no cut/copy/paste/select all. This draws them in a [FlyPopup]
 * next to the selection. Use [FlyTextToolbarProvider] to install it.
 *
 * Compose asks for the toolbar in one of two ways, and this answers both:
 * - the new text context menu (`ComposeFoundationFlags.isNewContextMenuEnabled`, on by default since
 *   Compose 1.10): a [TextContextMenuProvider], whose default starts an `ActionMode` that a floating
 *   window can't show;
 * - the old one, when that flag is off: a [TextToolbar], from the callbacks that `showMenu` receives.
 */
@Stable
class FlyTextToolbar internal constructor(private val windowOrigin: () -> Offset) :
    TextToolbar, TextContextMenuProvider {

    private sealed interface Shown

    /** From the old path: the anchor and the actions are known when it opens */
    private class Callbacks(
        val anchor: Rect,
        val onCopy: (() -> Unit)?,
        val onPaste: (() -> Unit)?,
        val onCut: (() -> Unit)?,
        val onSelectAll: (() -> Unit)?,
    ) : Shown

    /** From the new path: the anchor and the items are read while it is shown, they follow the selection */
    private class Menu(val data: TextContextMenuDataProvider, val session: TextContextMenuSession) : Shown

    private var shown by mutableStateOf<Shown?>(null)

    /** Where the content of the provider is, the coordinates the new path measures the selection in */
    internal var coordinates by mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy())

    private val menuMutex = MutatorMutex()

    // old path

    override val status: TextToolbarStatus
        get() = if (shown is Callbacks) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        // the selection is in the coordinates of the window of the text field, the popup is in the screen's
        shown = Callbacks(
            anchor = rect.translate(windowOrigin()),
            onCopy = onCopyRequested,
            onPaste = onPasteRequested,
            onCut = onCutRequested,
            onSelectAll = onSelectAllRequested,
        )
    }

    override fun hide() {
        if (shown is Callbacks) shown = null
    }

    // new path

    /** Shown until the session is closed; a newer request cancels this one, like the platform's */
    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        menuMutex.mutate {
            val closed = CompletableDeferred<Unit>()
            val menu = Menu(dataProvider, object : TextContextMenuSession {
                override fun close() {
                    closed.complete(Unit)
                }
            })
            shown = menu
            try {
                closed.await()
            } finally {
                if (shown === menu) shown = null
            }
        }
    }

    @Composable
    internal fun Popup(labels: FlyTextToolbarLabels) {
        when (val current = shown) {
            null -> Unit
            is Callbacks -> ToolbarPopup(current.anchor, onDismissRequest = { hide() }) {
                CallbackButton(labels.cut, current.onCut)
                CallbackButton(labels.copy, current.onCopy)
                CallbackButton(labels.paste, current.onPaste)
                CallbackButton(labels.selectAll, current.onSelectAll)
            }

            is Menu -> {
                val destination = coordinates?.takeIf { it.isAttached } ?: return
                // both are snapshot-aware: reading them here follows the selection while it is shown
                val anchor = current.data.contentBounds(destination)
                    .translate(destination.positionInWindow() + windowOrigin())
                val items = current.data.data().components.filterIsInstance<TextContextMenuItem>()
                if (items.isEmpty()) return
                ToolbarPopup(anchor, onDismissRequest = { current.session.close() }) {
                    // an item closes the menu itself when it is done (cut, copy...), as the docs ask
                    items.forEach { item -> ToolbarButton(item.label) { with(item) { current.session.onClick() } } }
                }
            }
        }
    }

    @Composable
    private fun ToolbarPopup(anchor: Rect, onDismissRequest: () -> Unit, buttons: @Composable () -> Unit) {
        // a touch outside only closes the toolbar: the text is still selected
        FlyPopup(
            anchor = anchor,
            onDismissRequest = onDismissRequest,
            side = PopupSide.Top,
            alignment = PopupAlignment.Center,
        ) {
            FlyPopupSurface {
                Row(verticalAlignment = Alignment.CenterVertically) { buttons() }
            }
        }
    }

    /** A button of the old path: it closes the toolbar, then acts */
    @Composable
    private fun CallbackButton(label: String, action: (() -> Unit)?) {
        if (action == null) return
        ToolbarButton(label) {
            hide()
            action()
        }
    }

    @Composable
    private fun ToolbarButton(label: String, onClick: () -> Unit) {
        BasicText(
            label,
            modifier = Modifier
                .clickable(onClick = onClick)
                .heightIn(min = 40.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            style = TextStyle(color = FlyPopupDefaults.contentColor(), fontSize = 14.sp, lineHeight = 20.sp),
        )
    }
}

/**
 * Gives the text fields in [content] a cut/copy/paste/select all toolbar that works inside a
 * [FlyWindow]. Call it inside the window:
 *
 * ```
 * FlyWindow("note", focusable = true) { FlyTextToolbarProvider { TextField(...) } }
 * ```
 *
 * It works with the new text context menu of Compose and with the old text toolbar. The right click
 * menu of a mouse is the same toolbar.
 *
 * The toolbar is a [FlyPopup]: a touch outside closes it and does not reach the text, so the tap that
 * would move the cursor needs a second one.
 *
 * [labels] are used on the old text toolbar path only.
 */
@Composable
fun FlyTextToolbarProvider(
    labels: FlyTextToolbarLabels = FlyTextToolbarLabels.system(),
    content: @Composable () -> Unit,
) {
    val origin = rememberWindowOrigin()
    val toolbar = remember(origin) { FlyTextToolbar(origin) }
    CompositionLocalProvider(
        LocalTextToolbar provides toolbar,
        LocalTextContextMenuToolbarProvider provides toolbar,
        // the default dropdown (a right click) is a Popup too, which needs an activity window as well
        LocalTextContextMenuDropdownProvider provides toolbar,
    ) {
        // the selection of the new path is measured in these coordinates, as the platform toolbar does
        Box(
            propagateMinConstraints = true,
            modifier = Modifier.onGloballyPositioned { toolbar.coordinates = it },
        ) { content() }
    }
    toolbar.Popup(labels)
}
