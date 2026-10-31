package com.abada.flyView.overlay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abada.flyView.core.PopupAlignment
import com.abada.flyView.core.PopupSide
import kotlinx.coroutines.awaitCancellation

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
@OptIn(ExperimentalFoundationApi::class)
class FlyTextToolbar internal constructor(private val windowOrigin: () -> Offset) : TextToolbar, TextContextMenuProvider {
    private class Action(val label: String, val run: () -> Unit)

    private class Request(val anchor: Rect, val actions: List<Action>)

    /** Any coordinates of the compose tree: the context menu asks for positions relative to them */
    internal var coordinates: LayoutCoordinates? = null

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
        val labels = labels
        request = Request(
            anchor = rect.translate(windowOrigin()),
            actions = listOfNotNull(
                onCutRequested?.let { Action(labels.cut, it) },
                onCopyRequested?.let { Action(labels.copy, it) },
                onPasteRequested?.let { Action(labels.paste, it) },
                onSelectAllRequested?.let { Action(labels.selectAll, it) },
            ),
        )
    }

    /**
     * Compose 1.10+ opens the selection toolbar and the right-click menu through this, not through
     * [TextToolbar]. Its default starts an ActionMode, which has no activity window to live in here.
     * Suspends until the menu is closed, as the contract wants: cancelling it removes the popup.
     */
    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider): Nothing {
        val root = coordinates?.let { if (it.isAttached) it.findRootCoordinates() else null }
        val session = object : TextContextMenuSession {
            override fun close() = hide()
        }
        try {
            val items = dataProvider.data().components.filterIsInstance<TextContextMenuItem>()
            request = Request(
                anchor = (root?.let { dataProvider.contentBounds(it) } ?: Rect.Zero).translate(windowOrigin()),
                actions = items.map { item ->
                    Action(item.label(resources)) { with(item) { session.onClick() } }
                },
            )
            awaitCancellation()
        } finally {
            request = null
        }
    }

    private var labels = FlyTextToolbarLabels("Cut", "Copy", "Paste", "Select all")
    private var resources: android.content.res.Resources = android.content.res.Resources.getSystem()

    override fun hide() {
        request = null
    }

    @Composable
    internal fun Popup(labels: FlyTextToolbarLabels) {
        this.labels = labels
        resources = LocalContext.current.resources
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
                    current.actions.forEach { ToolbarButton(it) }
                }
            }
        }
    }

    @Composable
    private fun ToolbarButton(action: Action) {
        BasicText(
            action.label,
            modifier = Modifier
                .clickable {
                    hide()
                    action.run()
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
@OptIn(ExperimentalFoundationApi::class)
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
        LocalTextContextMenuDropdownProvider provides toolbar,
    ) { content() }
    Spacer(Modifier.size(0.dp).onGloballyPositioned { toolbar.coordinates = it })
    toolbar.Popup(labels)
}
