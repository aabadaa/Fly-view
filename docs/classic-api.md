# Classic API (1.x)

The original API of Fly-view keeps working. For new code prefer the [new overlay API](../README.md).

## Install

```groovy
// root build.gradle
allprojects { repositories { maven { url 'https://jitpack.io' } } }

// module build.gradle  (latest tag: see the badge)
implementation 'com.github.aabadaa:Fly-view:Tag'
```
[![](https://jitpack.io/v/aabadaa/Fly-view.svg)](https://jitpack.io/#aabadaa/Fly-view)

Needs the user to allow **"Draw over other apps"** (handled for you, see step 2).

## Quick start

**1. Register** a view under a key

```kotlin
FlyViews.register("player") {
    val controller = PlayerController()
    FlyViewInfo(controller = controller) {
        DraggableFlyView {
            Column(Modifier.background(Color.White)) {
                Text(controller.title)
                Button(onClick = removeView) { Text("Close") }
            }
        }
    }
}

class PlayerController : FlyController {
    var title by mutableStateOf("")
    override fun update(data: Bundle) { title = data.getString("title").orEmpty() }
}
```

**2. Show** it

```kotlin
FlyViews.requestOverlayPermission(activity) { granted ->
    if (granted) FlyViews.show(context, "player")
}
```

**3. Talk to** it, **hide** it

```kotlin
FlyViews.withController<PlayerController>("player") { it.title = "Hello" }
FlyViews.hide("player")
```

Full sample: [`ExampleController.kt`](app/src/main/java/com/abada/flyview/ExampleController.kt)

## Cheat sheet

| I want to... | Use |
|---|---|
| Define a view | `FlyViews.register(key) { FlyViewInfo(...) { ... } }` |
| Show it (no service) | `FlyViews.show(context, key)` |
| Show it (survives app close) | `FlyViews.show(context, key, MyService::class.java)` |
| Ask for the permission | `FlyViews.requestOverlayPermission(activity) { granted -> }` |
| Hide it (with exit animation) | `FlyViews.hide(key)` or `removeView()` inside the view |
| Check if showing | `FlyViews.isShown(key)` |
| Send data (typed) | `FlyViews.withController<MyController>(key) { ... }` |
| Send data (bundle) | `FlyViews.update(key, bundle)` → `FlyController.update` |
| Make it draggable | wrap content in `DraggableFlyView { }` |
| Move it from code | `animateTo(x, y)` · `goToScreenBorder()` · `updateLayoutParams()` |

All calls: main thread.

## Features

| Feature | Details |
|---|---|
| Stays on screen | Respects status bar, navigation bar and cutout. Survives rotation and size changes |
| Snap to edge | Picks the closest edge, fling velocity counts |
| Any gravity | Drag and snap work with the gravity you set on `params`; the whole window moves even if only a handle is draggable |
| Drag to dismiss | Drop area appears at the bottom while dragging, highlights + haptic tick on hover |
| Safe removal | `removeView()` can be called many times, `onRemove` can animate |
| Service mode | Foreground service starts/stops itself, cleans up on destroy |
| No UI toolkit lock-in | No material3 dependency, use anything in your view |

## Options

### `DraggableFlyView(...)`

| Param | Default | |
|---|---|---|
| `autoGoToBorder` | `true` | `false` = stay where the user left it |
| `duration` | `300` | snap animation (ms) |
| `edgePadding` | `0.dp` | gap between the window and the edge when snapped |

### `FlyViewInfo(...)`

| Param | Default | |
|---|---|---|
| `controller` | - | your `FlyController`, or `NoController` |
| `onRemove` | `{}` | suspend lambda, runs before removal (exit animation) |
| `params` | `defaultFlyViewParams()` | your `WindowManager.LayoutParams`; `x`,`y` keep their window manager meaning (any gravity works) |
| `dismissZone` | `DismissZone()` | `DismissZone(heightFraction = 0.18f, hapticFeedback = true)`, `null` = off |
| `focusable` | `null` | `false` = app below keeps Back and keyboard (display-only widgets), `null` = leave `params.flags` alone |
| `keyDispatcher` | `null` | handle key events |

Handy members inside your content: `removeView`, `isOverDismissZone` (state), `screenBounds`, `focusable` (settable).

### Custom drop area

```kotlin
closeViewContent = { isHovered -> MyTrashIcon(big = isHovered) }   // set once, e.g. Application.onCreate
```

## Service mode

Only needed if the view must stay when your app is in the background.

```kotlin
class MyFlyService : FlyViewService()    // body can be empty
```
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<service android:name=".MyFlyService" android:exported="false"
    android:foregroundServiceType="specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="shows a floating window" />
</service>
```
Then `FlyViews.show(context, "player", MyFlyService::class.java)`.

> Without the service, Android can kill your process when the app is in the background and the view disappears with it. The service keeps the process alive.
Override `createNotification()` to style the notification. The service stops when its last view is removed.

## Troubleshooting

| Problem | Fix |
|---|---|
| `show` returns `false`, settings screen opens | Overlay permission missing. Use `requestOverlayPermission(activity) { }` to continue after |
| `show` returns `false`, nothing happens | Nothing registered for that key, call `FlyViews.register` first (see Logcat) |
| Back button goes to the overlay, not the app | Use `FlyViewInfo(focusable = false)` |
| Text field / back button not working | Don't set `FLAG_NOT_FOCUSABLE` (`focusable = true`) |
| Same key shown twice | Not added twice, only the bundle is delivered |

## Changes that need attention when upgrading from the first 1.x releases

Unreleased changes (a higher level, compose based API is planned for the next major version):

| Before | Now |
|---|---|
| `FlyViewService.show(ctx, key)` | `FlyViews.show(ctx, key, MyService::class.java)` (old name still works) |
| `infoProviders[key] = {...}` | `FlyViews.register(key) { ... }` (old name still works) |
| `closeViewContent = { ... }` | `closeViewContent = { isHovered -> ... }` |
| `FlyViewInfo` data class (`copy`) | plain class |
| material3 pulled in by the library | not anymore |

## Changes from the first release

The classic API keeps its concepts, but a few signatures changed, so code written against 1.x may need a small edit:

- `closeViewContent` is now `@Composable (isHovered: Boolean) -> Unit`, so the drop area can react while the view is over it. Add the parameter to your lambda (`{ _ -> ... }` if you ignore it).
- `FlyViewInfo` is a plain class, not a data class: there is no `copy(...)`. Create a new `FlyViewInfo` in your provider instead.
- `FlyViewInfo` has two new constructor parameters, `dismissZone` and `focusable`, before `content`. Passing `content` as the trailing lambda is not affected.
- `addFlyInfo` returns a `Boolean`: `false` when the permission is missing (the settings screen is opened) or the key is already shown.
