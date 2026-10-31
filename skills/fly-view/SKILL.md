---
name: fly-view
description: Show Jetpack Compose UI on top of other apps with the Fly-view library (com.github.aabadaa:Fly-view, v2). Use when the user wants a floating or draggable window, a bubble, a bar docked above the keyboard, an overlay that draws over other apps, or popups, menus, tooltips, snackbars, bottom sheets or a text-selection toolbar inside such a window. Covers FlyOverlay, FlyWindow, FlyItem, FlyService and the FlyPopup family. Latest version only; v1 APIs (FlyViews, FlyViewInfo, DraggableFlyView, FlyViewService, FlyController) do not exist.
---

# Fly-view (latest, v2)

Shows any composable in a real window over other apps. Draggable, snaps to edges, dropped at the bottom to close, optional foreground service. Source of truth: `README.md` in the repo; read it for the full parameter tables. If the project uses a v1 API, migrate with `docs/migration-v1-to-v2.md` instead of mixing.

## Setup

```groovy
// root build.gradle (or settings.gradle dependencyResolutionManagement)
allprojects { repositories { maven { url 'https://jitpack.io' } } }
// module: use the newest tag from https://jitpack.io/#aabadaa/Fly-view
implementation 'com.github.aabadaa:Fly-view:<latest-tag>'
```

- Built with Compose 1.12 (BOM 2026.09.00): the app needs a recent Compose BOM and `compileSdk` 37. Do not pin an older Compose.
- The library has no Material dependency. The default look is drawn with foundation; pass your own content for a themed one.
- The user must grant "Draw over other apps". The library never opens settings by itself.
- All calls on the main thread.

## The flow (always these three steps)

1. **State** in any flow, state holder or ViewModel. The window content is disposed when the window hides, so never keep important state inside it.
2. **Describe the window once** (e.g. `Application.onCreate`). The block composes in the process, not in an activity:

```kotlin
FlyOverlay.contribute("counter") {
    val open by State.isOpen.collectAsState()
    if (open) FlyWindow("counter", onDismissRequest = { State.isOpen.value = false }) {
        Button(onClick = { dismiss() }) { Text("close") }   // dismiss() is available in the content
    }
}
```

3. **Start** with the permission checked first (`start` throws `OverlayPermissionException` otherwise):

```kotlin
if (FlyOverlay.hasPermission(context) || FlyOverlay.requestPermission(activity)) {  // requestPermission is suspend (or takes a callback)
    State.isOpen.value = true
    FlyOverlay.start(context)
}
```

The overlay stops itself when its last window is gone; the next `start` composes the blocks again. `FlyOverlay.stop()` stops it explicitly. A `FlyWindow` composed from a screen (not via `contribute`) needs the overlay already started, else `FlyOverlayNotStartedException`.

## FlyWindow essentials

`FlyWindow(key, ...) { content }`: a duplicate `key` replaces the old content.

| Need | Parameter |
|---|---|
| Text field or back button | `focusable = true` (takes focus from the app below; without it no keyboard, and back goes to the app) |
| Where it shows | `visibleWhen = FlyVisibility.Default / Always / Foreground / Background / Owner(owner)`. Default: always for a `contribute` block, while the screen is started for a window a screen composes |
| Dragging | `drag = None / Whole / HandleOnly` (+ `Modifier.flyDragHandle()`) |
| Snapping | `snap = FlySnap.None` or `Edges(padding, durationMillis)` |
| Drop-to-close area | `dismissZone = DismissZone()` or `null` |
| Docked bar | `placement = FlyPlacement.Docked(FlyEdge.Bottom, FlySize.Fill)`; also `Floating` (default), `Fullscreen`. `FlySize`: `Wrap`, `Fill`, `Fixed(dp)`, `Fraction(f)` |
| Keyboard | `ime = FlyIme.Avoid` (default, stays above it) or `Ignore` |
| Hide/show | `visible`, `hideWhenEmpty`, `onRemoved` |
| Raw escape hatch | `configure = { params -> }` for `WindowManager.LayoutParams` |

One saved position per mode:

```kotlin
val icon = rememberFlyWindowState("note/icon", FlyPosition.Edge(FlySide.Right, y = 0.3f))
val editor = rememberFlyWindowState("note/editor", FlyPosition.InheritFrom(icon))
FlyWindow("note", state = if (extended) editor else icon) { }
```

## Dismissal

Every removal (drop on the zone, back, `dismiss()`, `FlyOverlay.requestDismiss(window)` / `requestDismissAll()`) is a request:
1. `FlyDismissEffect { ...work...; dismiss() }` runs first if present; it ends with `dismiss()`, `cancel()` (window snaps back) or returns (= dismiss).
2. `onDismissRequest` commits: update your state there. Without it the window just hides until it leaves and re-enters the composition.

## Several screens, one window

```kotlin
FlyWindow("tools", hideWhenEmpty = true) { items.forEach { it.Content() } }       // inside the window
@Composable fun ScreenA() { FlyItem("tools", "timer", onDismissRequest = { }) { TimerChip() } }
```

Same `(window, id)` replace each other; `order` sorts them.

## Lifecycle

The window is a real `LifecycleOwner`; content lives exactly while the window is on screen. There are no `onShown`/`onHidden`: use `DisposableEffect` / `LifecycleEventEffect` inside, or read `rememberFlyWindowState(key).isShown` and `.hiddenReason` (`Visibility`, `Invisible`, `Empty`, `Dismissed`, `OverlayStopped`, `LeftComposition`) from outside. `rememberFlyViewModel { MyViewModel() }` lives for the window session.

## Popups, menus, tooltips, sheets (Compose's own do not work in a FlyWindow)

`Popup`, `DropdownMenu`, `TooltipBox`, the system selection toolbar and `ModalBottomSheet` need an activity window. Use the Fly versions:

| Want | Use |
|---|---|
| Popup | `FlyPopup(anchor, onDismissRequest) { }` |
| Menu | `FlyDropdownMenu(anchor, onDismissRequest) { FlyDropdownMenuItem("Share", onClick = { }) }` |
| Anchor | `val anchor = rememberFlyAnchor()` + `Modifier.flyAnchor(anchor)` (prefer over `onScreenBounds`, which can be stale after a drag) |
| Tooltip | `FlyTooltipHost { windows }` once, then `Modifier.flyTooltip("Save")`; from code `LocalFlyTooltips.current.show(text, rect)` |
| Text toolbar (cut/copy/paste/select all, right-click) | `FlyTextToolbarProvider { TextField(...) }` inside the window; works with Compose's new context menu and the old toolbar |
| Snackbar | `FlySnackbarHost(state)` + `state.showSnackbar("Deleted", "Undo")` (suspends, returns result); give `snackbar = { data -> }` to theme it |
| Sheet | `FlyBottomSheet(onDismissRequest) { }` (always focusable) |
| Dialog | `FlyDialog(onDismissRequest) { }` |
| Custom overlay | `FlyOverlayLayer(touchable, focusable) { Modifier.placeOnScreen { size -> pos } }` |

Popups, menus, the text toolbar and sheets take every touch: a touch outside calls `onDismissRequest` and does not reach what is below (so the tap that closes the text toolbar does not move the cursor). Popups flip and clamp to the usable screen (`rememberFlyUsableScreen()`), keyboard-aware when known (`rememberFlyImeOverlap()`).

## Staying alive and launching

Without a service Android may kill the process in the background. Optional foreground service:

```kotlin
class MyFlyService : FlyService()
FlyOverlay.configure(service = MyFlyService::class.java)
```
Manifest: `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` permissions, plus `<service android:name=".MyFlyService" android:exported="false" android:foregroundServiceType="specialUse">` with `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="shows a floating window"/>`.

- `start` also throws `FlyServiceStartException` if the system refuses the service (background start on Android 12+). Start from a visible activity; subclass `FlyLaunchActivity` (invisible) for widgets, tiles, notification actions.
- A service is not an owner: windows still follow `visibleWhen`. Use `Always` or `Background` for ones that must stay.
- Requests from outside (tile, receiver) should go through a `Channel` + `send`, not a `SharedFlow` (drops when nobody listens).
- `onClosingSoon` / `closingSoon` is only a hint; `FlyOverlay.lastExitReason(context)` (Android 11+) says why the previous process ended.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Nothing shows after `start` | the `contribute` block must actually emit a `FlyWindow` (check state) and `visibleWhen` must allow it |
| Vanishes when leaving the app | window from a screen follows it: `visibleWhen = Always`; add a `FlyService` against process death |
| No keyboard in a text field | `focusable = true` |
| Back closes the app below, not the overlay | `focusable = false` is the cause of the opposite; set what you need |
| State resets on hide/show | keep it in a ViewModel/state holder |
| Crash using `Popup` / `DropdownMenu` | use the Fly equivalents above |
| Debug layout | `FlyOverlay.debugOutlines = true` |

## Rules for the agent

- Use only the v2 APIs above. Never emit `FlyViews`, `FlyViewInfo`, `DraggableFlyView`, `FlyViewService`, `FlyController`.
- Always check/request the overlay permission before `start`.
- Do not use Compose popups or Material `ModalBottomSheet` in a window; do not add a Material dependency for the library's sake.
- When unsure about a parameter, read the source in `fly_view/src/main/java/com/abada/flyView/` rather than guessing.
