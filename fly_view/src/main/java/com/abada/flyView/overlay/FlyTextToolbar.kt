package com.abada.flyView.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abada.flyView.core.PopupAlignment
import com.abada.flyView.core.PopupSide

/** What the selection toolbar says. The defaults are the system's own, so they are translated. */
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
 * Compose's [TextToolbar] for a [FlyWindow]: the system's selection popup needs an activity window
 * and is missing there, so selecting text gives no cut/copy/paste/select all. This draws them in a
 * [FlyPopup] next to the selection, from the callbacks that `showMenu` receives. Use
 * [FlyTextToolbarProvider] to install it.
 */
@Stable
class FlyTextToolbar internal constructor(private val windowOrigin: () -> androidx.compose.ui.geometry.Offset) : TextToolbar {
    private class Request(
        val anchor: Rect,
        val onCopy: (() -> Unit)?,
        val onPaste: (() -> Unit)?,
        val onCut: (() -> Unit)?,
        val onSelectAll: (() -> Unit)?,
    )

    private var request by mutableStateOf<Request?>(null)

    override val status: TextToolbarStatus
        get() = if (request != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        // the selection is in the coordinates of the window of the text field, the popup is in the screen's
        request = Request(
            anchor = rect.translate(windowOrigin()),
            onCopy = onCopyRequested,
            onPaste = onPasteRequested,
            onCut = onCutRequested,
            onSelectAll = onSelectAllRequested,
        )
    }

    override fun hide() {
        request = null
    }

    @Composable
    internal fun Popup(labels: FlyTextToolbarLabels) {
        val current = request ?: return
        // a touch outside only closes the toolbar: the text is still selected
        FlyPopup(
            anchor = current.anchor,
            onDismissRequest = { hide() },
            side = PopupSide.Top,
            alignment = PopupAlignment.Center,
        ) {
            FlyPopupSurface {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToolbarButton(labels.cut, current.onCut)
                    ToolbarButton(labels.copy, current.onCopy)
                    ToolbarButton(labels.paste, current.onPaste)
                    ToolbarButton(labels.selectAll, current.onSelectAll)
                }
            }
        }
    }

    @Composable
    private fun ToolbarButton(label: String, action: (() -> Unit)?) {
        if (action == null) return
        BasicText(
            label,
            modifier = Modifier
                .clickable {
                    hide()
                    action()
                }
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
 * The toolbar is a [FlyPopup]: a touch outside closes it and does not reach the text, so the tap that
 * would move the cursor needs a second one.
 */
@Composable
fun FlyTextToolbarProvider(
    labels: FlyTextToolbarLabels = FlyTextToolbarLabels.system(),
    content: @Composable () -> Unit,
) {
    val origin = rememberWindowOrigin()
    val toolbar = remember(origin) { FlyTextToolbar(origin) }
    CompositionLocalProvider(LocalTextToolbar provides toolbar) { content() }
    toolbar.Popup(labels)
}
