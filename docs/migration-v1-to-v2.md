# Migrating from v1 to v2

v2 replaces the whole v1 API (`FlyViews`, `FlyViewInfo`, `DraggableFlyView`, `FlyViewService`, `FlyController`, `addFlyInfo`...).
The idea is the same (a composable over other apps, optionally in a foreground service) but it is **declarative**: you describe
windows from your own state, you do not register, show and hide them by key.

**Jump to:** [The big differences](#the-big-differences) · [Every v1 API](#every-v1-api-and-its-v2-replacement) · [Before and after](#before-and-after) · [Behavior changes](#behavior-changes-to-expect) · [Checklist](#checklist)

## The big differences

| v1 | v2 |
|---|---|
| Register a provider under a key, then `show(key)` / `hide(key)` | Describe the windows in a composable from your state: `if (open) FlyWindow(...)` |
| A `FlyController` + `Bundle` to talk to the view | Your own state holder (a `ViewModel`, a `StateFlow`...). The window just reads it |
| `FlyViewService` holds the views and decides when to stop | `FlyService` only keeps the process alive. The overlay stops itself when no window is left |
| Missing permission: `show` opens the settings and returns `false` | You ask first (`FlyOverlay.requestPermission`), `FlyOverlay.start` **throws** if it is missing |
| `params.x`/`params.y` meant different things for each gravity | Positions are absolute and kept in a `FlyWindowState`, any gravity works |
| `onRemove` suspend lambda + a fixed delay | `FlyDismissEffect` (you take control of the dismissal) and `visible = false` for the exit |
| One window per `FlyViewInfo` | Windows, plus `FlyItem`s that screens contribute to a window |

## Every v1 API and its v2 replacement

### Showing and hiding

| v1 | v2 |
|---|---|
| `FlyViews.register(key) { FlyViewInfo(...) { } }` | `FlyOverlay.contribute(id) { if (open) FlyWindow(key) { } }`, `open` is your own state |
| `FlyViewService.infoProviders[key] = { ... }` | same as above |
| `FlyViews.show(context, key)` | set your state to open, then `FlyOverlay.start(context)` |
| `FlyViews.show(context, key, MyService::class.java)` | `FlyOverlay.configure(service = MyFlyService::class.java)` once, then `FlyOverlay.start(context)` |
| `FlyViewService.show(context, key, serviceClass, bundle)` | same as above, the bundle becomes state |
| `FlyViews.show(...)` when the view is already showing (delivers the bundle) | set your state, the window is already there |
| `FlyViews.hide(key)` | set your state to closed, or `visible = false`, or `FlyOverlay.requestDismiss(key)` |
| `FlyViews.isShown(key)` | read your own state, `FlyOverlay.isRunning` for the overlay |
| `WindowManager.addFlyInfo(context, key, info, onRemoved)` | `FlyWindow(key, onRemoved = { })` inside a `contribute` block or a screen |
| `WindowManager.removeFlyView(key)` | leave the composition (`if (open)`), or `visible = false` |
| `FlyViewInfo.removeView` | `dismiss()` in the window content (runs your `FlyDismissEffect`) |

### Talking to the view

| v1 | v2 |
|---|---|
| `interface FlyController { fun update(Bundle) }` | not needed. Keep a state holder, `FlyWindow` reads it |
| `NoController` | nothing to pass |
| `FlyViews.update(key, bundle)` / `updateFlyView(key, bundle)` | change your state (a `StateFlow`, a `Channel` for one-shot requests) |
| `FlyViews.withController<C>(key) { }` | call your state holder directly |
| The controller as a ViewModel | `rememberFlyViewModel { MyViewModel() }` (window session), or `viewModel()` inside the window |

One-shot requests (an index to select, a note to open) from a tile, a widget or a receiver: send them through a `Channel` with `send`
and collect them in the window. Do not use a `SharedFlow`, it drops the value when the window does not exist yet.

### The window

| v1 `FlyViewInfo(...)` | v2 `FlyWindow(...)` |
|---|---|
| `controller` | gone (see above) |
| `onRemove = { ...; delay(400) }` | `FlyDismissEffect { ...; delay(400); dismiss() }` |
| `params = LayoutParams(...)` | `placement`, `focusable`, `ime`, and `configure = { params -> }` for anything else |
| `params.windowAnimations` | `enter = fadeIn()`, `exit = fadeOut()` (any Compose transition) |
| `params.x` / `params.y` | `rememberFlyWindowState(key, initial = FlyPosition...)` |
| `keyDispatcher` | no window level hook. Use Compose key modifiers in the content (`Modifier.onPreviewKeyEvent`), and `focusable = true` |
| `dismissZone = DismissZone(...)` / `null` | `dismissZone = DismissZone(...)` / `null` (same type, now in `com.abada.flyView.overlay`) |
| `focusable = true/false/null` | `focusable = true/false` (default is now `false`) |
| `content = { ... }` with the `FlyViewInfo` as receiver | `content = { ... }` with `FlyWindowScope` as receiver (`items`, `dismiss()`, `closingSoon`) |

### Members of `FlyViewInfo` used inside the content

| v1 | v2 |
|---|---|
| `animateTo(x, y, duration)` | `state.animateTo(x, y, durationMillis)` on the `FlyWindowState` |
| `goToScreenBorder(duration)` | `state.snapToEdge(durationMillis)` |
| `updateLayoutParams()` after editing `params` | declare it: `placement`, `focusable`, `ime`; or `configure` |
| `screenBounds` | no equivalent, the library keeps the window inside the usable screen |
| `isOverDismissZone` | `state.isOverDismissZone` |
| `focusable` (var) | `focusable = ...` parameter, driven by your state |
| `removeDelayMs` | `FlyDismissEffect` / `visible` and `exit` |

### Dragging

| v1 `DraggableFlyView(autoGoToBorder, duration, edgePadding) { }` | v2 |
|---|---|
| wrap the content | `FlyWindow(drag = FlyDrag.Whole)` (default) |
| wrap only a handle, the whole window moves | `drag = FlyDrag.HandleOnly` + `Modifier.flyDragHandle()` on the handle |
| `autoGoToBorder = true/false` | `snap = FlySnap.Edges()` / `FlySnap.None` |
| `duration` | `FlySnap.Edges(durationMillis = ...)` |
| `edgePadding` | `FlySnap.Edges(padding = ...)` |

### The service and the permission

| v1 | v2 |
|---|---|
| `class MyService : FlyViewService()` | `class MyService : FlyService()`, pass it to `FlyOverlay.configure(service = ...)` |
| `createNotification()` / `notificationId` | the same two overridable members on `FlyService` |
| manifest: the same `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `<service ... specialUse>` | unchanged |
| `FlyViews.hasOverlayPermission(context)` | `FlyOverlay.hasPermission(context)` |
| `FlyViews.requestOverlayPermission(context)` | `startActivity(FlyOverlay.permissionIntent(context))` |
| `FlyViews.requestOverlayPermission(activity) { granted -> }` | `FlyOverlay.requestPermission(activity) { granted -> }`, or `suspend` `requestPermission(activity)` |
| `show` returns `false` when the service is refused (Android 12+) | `FlyOverlay.start` throws `FlyServiceStartException`: start from a visible activity, `FlyLaunchActivity` is an invisible one |
| `closeViewContent = { ... }` | `FlyOverlay.dismissZoneContent = { isHovered -> ... }` |

## Before and after

**v1**

```kotlin
class MyController : FlyController {
    var title by mutableStateOf("")
    override fun update(data: Bundle) { title = data.getString("title").orEmpty() }
}

FlyViews.register("player") {
    val controller = MyController()
    FlyViewInfo(controller = controller, onRemove = { delay(300) }) {
        DraggableFlyView(autoGoToBorder = true) {
            Column(Modifier.background(Color.White)) {
                Text(controller.title)
                Button(onClick = removeView) { Text("Close") }
            }
        }
    }
}

// somewhere
if (FlyViews.show(context, "player", MyService::class.java, bundleOf("title" to "Hello"))) { /* started */ }
FlyViews.update("player", bundleOf("title" to "Bye"))
FlyViews.hide("player")
```

**v2**

```kotlin
object Player {                                           // your state, no controller interface
    val isOpen = MutableStateFlow(false)
    val title = MutableStateFlow("")
}

// Application.onCreate
FlyOverlay.configure(service = MyFlyService::class.java)  // class MyFlyService : FlyService()
FlyOverlay.contribute("player") {
    val open by Player.isOpen.collectAsState()
    val title by Player.title.collectAsState()
    if (open) {
        FlyWindow("player", onDismissRequest = { Player.isOpen.value = false }) {
            Column(Modifier.background(Color.White)) {
                Text(title)
                Button(onClick = ::dismiss) { Text("Close") }
            }
        }
    }
}

// somewhere: permission first, start throws if it is missing
if (FlyOverlay.hasPermission(context) || FlyOverlay.requestPermission(activity)) {
    Player.title.value = "Hello"
    Player.isOpen.value = true
    FlyOverlay.start(context)
}
Player.title.value = "Bye"
Player.isOpen.value = false          // or FlyOverlay.requestDismiss("player")
```

## Behavior changes to expect

| Topic | v1 | v2 |
|---|---|---|
| Visible in the background | always | `visibleWhen`: always for `contribute` windows, while the screen is started for windows a screen composes |
| `focusable` default | `true` (took Back and the keyboard from the app below) | `false`. Set it to `true` for text fields and the back button |
| Position coordinates | window manager `x`/`y`, depends on gravity | absolute top-left in a `FlyWindowState`, one state per mode |
| Remove | `onRemove` + 100 ms | a dismissal is a request: `FlyDismissEffect`, then `onDismissRequest`; `visible = false` plays `exit` |
| Duplicate key | the second `show` was ignored | the new content replaces the old one |
| Back button | always consumed | consumed only when a `BackHandler` or the dismissal wants it |
| Stopping | the service stopped when its last view was removed | the overlay and the service stop when no window is alive |
| Content state when hidden | not applicable | the content is disposed when a window is hidden (background rule, `visible = false`), keep state in a ViewModel or state holder |
| Library dependencies | material3 | none of material3 |

## Checklist

1. Replace every `FlyController` with a state holder you own.
2. Move each `FlyViewInfo` into a `FlyWindow` inside a `FlyOverlay.contribute` block (or a screen) driven by that state.
3. Swap `DraggableFlyView` for the `drag` and `snap` parameters.
4. Swap `onRemove` for `FlyDismissEffect`, and `removeView` for `dismiss()`.
5. Replace `show` / `hide` / `update` by state changes plus `FlyOverlay.start`.
6. Ask for the permission before `start`, and handle `FlyServiceStartException` (or use `FlyLaunchActivity`).
7. Subclass `FlyService` instead of `FlyViewService` and pass it to `FlyOverlay.configure`.
8. Set `focusable = true` where you need text input or the back button.
9. If you relied on the window manager `params`, use `placement`, `ime` and `configure`.
