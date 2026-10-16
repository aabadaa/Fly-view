# Fly-view

Show any composable **on top of other apps**. Draggable, snaps to the screen edges, drop it at the bottom to close it, aggregates what your screens contribute, and optionally stays alive in a foreground service.

## Demo
[flyView-demo.webm](https://github.com/aabadaa/FLyView/assets/54916786/79d6c4f2-6a83-4ac4-a44c-8e3f74d58dfa)


**Jump to:** [Install](#install) · [Quick start](#quick-start) · [Cheat sheet](#cheat-sheet) · [Windows](#windows) · [Items](#items-several-screens-one-window) · [Dismissal](#dismissal) · [Placement](#placement-size-keyboard) · [Service](#service-and-lifetime) · [Testing](#testing) · [Troubleshooting](#troubleshooting) · [Classic API](#classic-api-1x)

## Install

```groovy
// root build.gradle
allprojects { repositories { maven { url 'https://jitpack.io' } } }

// module build.gradle  (latest tag: see the badge)
implementation 'com.github.aabadaa:Fly-view:Tag'
```
[![](https://jitpack.io/v/aabadaa/Fly-view.svg)](https://jitpack.io/#aabadaa/Fly-view)

Needs the user to allow **"Draw over other apps"**: you ask for it, the library never opens settings behind your back.

## Quick start

**1. Describe your windows, once** (for example in `Application.onCreate`). The block is composed while the overlay runs, in the process and not in an activity.

```kotlin
FlyOverlay.contribute("player") {
    val open by PlayerState.isOpen.collectAsState()      // your own app state
    if (open) {
        FlyWindow(
            "player",
            visibleWhen = FlyVisibility.Always,       // over other apps too. The default hides it while your app is in the background
            onDismissRequest = { PlayerState.isOpen.value = false },
        ) {
            Column(Modifier.background(Color.White)) {
                Text("Now playing")
                Button(onClick = ::dismiss) { Text("Close") }
            }
        }
    }
}
```

**2. Start the overlay** (permission first, `start` throws if it is missing)

```kotlin
if (FlyOverlay.hasPermission(context) || FlyOverlay.requestPermission(activity)) {   // requestPermission is suspend
    PlayerState.isOpen.value = true
    FlyOverlay.start(context)
}
```

That is all. The overlay stops itself when its last window is gone, the next `start` composes your blocks again.

Full sample: [`app/.../v2`](app/src/main/java/com/abada/flyview/v2) (a bubble that expands into a panel, and a window that aggregates items from a screen).

## Cheat sheet

| I want to... | Use |
|---|---|
| Declare windows without a screen | `FlyOverlay.contribute(id) { FlyWindow(...) { } }` |
| Declare a window inside a screen | `FlyWindow(key) { }` (needs the overlay started) |
| Start / stop | `FlyOverlay.start(context)` · `FlyOverlay.stop()` |
| Permission | `FlyOverlay.hasPermission(context)` · `FlyOverlay.requestPermission(activity)` (suspend or callback) · `FlyOverlay.permissionIntent(context)` |
| Several screens, one window | `FlyItem(window, id) { }` + `items` in the window |
| Dismiss from inside | `dismiss()` in the window content |
| Dismiss from outside | `FlyOverlay.requestDismiss(window)` · `requestDismissAll()` |
| Take control of the dismissal | `FlyDismissEffect { ...; dismiss() }` |
| One position per mode | `rememberFlyWindowState(key)` once per mode, pass the one you want |
| Dock to an edge | `placement = FlyPlacement.Docked(FlyEdge.Bottom, FlySize.Fill)` |
| Stay above the keyboard | `ime = FlyIme.Avoid` |
| A dialog | `FlyDialog(onDismissRequest) { }` |
| A ViewModel for the window session | `rememberFlyViewModel { MyViewModel() }` |
| Start from a widget / tile (Android 12+) | subclass `FlyLaunchActivity` |
| Keep the process alive | `FlyOverlay.configure(service = MyFlyService::class.java)` |

All calls: main thread.

## Windows

`FlyWindow` is a small real window that wraps its content, so touches outside of it reach the app below.

| Param | Default | |
|---|---|---|
| `key` | - | identifies the window. A duplicate key replaces the old content |
| `state` | `rememberFlyWindowState(key)` | position, one per mode if it remembers one for each |
| `visible` | `true` | `false` plays `exit`, then removes it and calls `onRemoved` |
| `visibleWhen` | `Default` | `Default` hides it while the app is in the background (the caller's activity or fragment must be started, the app in the foreground for `contribute`). Use `Always` to float over other apps. Also `Foreground`, `Background`, `Owner(lifecycleOwner)` |
| `focusable` | `false` | `true` for text fields and the back button, it takes them from the app below |
| `drag` | `Whole` | `None`, `Whole`, `HandleOnly` (use `Modifier.flyDragHandle()`) |
| `snap` | `Edges()` | `FlySnap.None`, or `Edges(padding, durationMillis)` |
| `dismissZone` | `DismissZone()` | the area at the bottom where it is dropped, `null` = off |
| `placement` | `Floating` | see below |
| `ime` | `Ignore` | `Avoid` keeps it above the keyboard |
| `hideWhenEmpty` | `false` | hides when no `FlyItem` is contributed |
| `onDismissRequest` | `null` | commit a dismissal: update your state here |
| `onRemoved` | `null` | the window has gone after `visible = false` |
| `onClosingSoon` | `null` | a hint that it may close soon |
| `configure` | `null` | last resort: change the raw `WindowManager.LayoutParams` |

Behavior: it stays inside the usable screen (system bars and cutout), survives rotation and size changes, flies to the closest edge with the fling velocity, and shows a drop area while dragging that highlights with a haptic tick (`closeViewContent` changes its look).

### One position per mode

```kotlin
val icon = rememberFlyWindowState("note/icon", FlyPosition.Edge(FlySide.Right, y = 0.3f))
val editor = rememberFlyWindowState("note/editor", FlyPosition.InheritFrom(icon))
FlyWindow("note", state = if (extended) editor else icon) { ... }   // switching animates to the saved position
```

## Items: several screens, one window

```kotlin
FlyWindow("tools", hideWhenEmpty = true) {
    items.forEach { item -> Row { item.Content(); Button(onClick = item::dismiss) { Text("x") } } }  // you draw the dismiss button
}

@Composable fun ScreenA() { FlyItem("tools", "timer", onDismissRequest = { vm.stopTimer() }) { TimerChip() } }
@Composable fun ScreenB() { FlyItem("tools", "note", order = 1) { NoteChip() } }
```

Items with the same `(window, id)` replace each other. They are composed in the window, so the window's `CompositionLocal`s apply.

## Dismissal

Every way of removing a window is a **request**: the drop on the dismiss zone, the back button, `dismiss()` in the content, `FlyOverlay.requestDismiss`.

```kotlin
FlyWindow("note", visible = !closed, onDismissRequest = { vm.close() }) {
    FlyDismissEffect {          // optional: the window takes control of its own dismissal
        vm.showMessage()        // anything: a message, a save, a confirmation...
        delay(400)
        dismiss()               // the real dismissal, or cancel() to keep the window
    }
}
```

1. `FlyDismissEffect` (if any) runs first. It ends with `dismiss()`, `cancel()` (the window snaps back), or by returning, which counts as `dismiss()`.
2. `onDismissRequest` commits it: update your state. Without it the window hides until it leaves the composition and comes back.

Requests while one is running are ignored.

## Placement, size, keyboard

| `placement` | |
|---|---|
| `Floating` | free window, drag and snap apply (default) |
| `Docked(edge, size, margin, insets)` | stuck to `Top`, `Bottom`, `Start` or `End`. `size`: `Wrap`, `Fill`, `Fixed(dp)`, `Fraction(f)`. Drag and snap are ignored, the floating position is kept |
| `Fullscreen` | covers the screen |

```kotlin
FlyWindow("note", focusable = true,
    placement = FlyPlacement.Docked(FlyEdge.Bottom, FlySize.Fill),
    ime = FlyIme.Avoid)               // a bar above the keyboard
```

`ime = Avoid`: a docked-bottom or fullscreen window resizes, a floating one moves up while the keyboard shows (Android 11+). It needs `focusable = true`.

## Service and lifetime

The window needs no activity and no service, it lives as long as the process. Without a service Android may kill the process in the background and the windows disappear. A foreground service keeps it alive:

```kotlin
class MyFlyService : FlyService()                       // body can be empty
FlyOverlay.configure(service = MyFlyService::class.java)
```
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<service android:name=".MyFlyService" android:exported="false" android:foregroundServiceType="specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="shows a floating window" />
</service>
```

- The service is not an owner: windows still hide with their `visibleWhen`. Use `Always` or `Background` for the ones that must stay.
- `FlyOverlay.start` throws `OverlayPermissionException` if the permission is missing and `FlyServiceStartException` if the system refuses the service (from the background on Android 12+). Start from a visible activity, `FlyLaunchActivity` is an invisible one for widgets, tiles and notification actions.
- **Closing soon:** the window content gets `closingSoon` (a state) and `onClosingSoon`: `ProcessAtRisk` when the system is short on memory and no service runs, `OwnerStopping`, `HostStopping`. It is a hint, a kill can come without warning. `FlyOverlay.lastExitReason(context)` tells why the previous process ended (Android 11+).
- State holders: `rememberFlyViewModel { }` lives for the window session. Do work that must finish after the window is gone inside `FlyDismissEffect`, before `dismiss()`.
- Requests from outside (a tile, a receiver) go through a `Channel` and `send` (not a `SharedFlow`, which drops values when nobody listens yet): see `BubbleEntry` in the sample.

## Testing

The math and the state machines have no Android dependency and run as plain JVM tests (`fly_view/src/test`): snapping and dismiss zone, gravity independent positions, placement and size, visibility rules, duplicate keys, the dismiss effect state machine, closing hints.

| Layer | Package | Tested with |
|---|---|---|
| Core (pure Kotlin) | `com.abada.flyView.core` | JUnit + `kotlinx-coroutines-test`, no emulator |
| Windows (Compose + WindowManager) | `com.abada.flyView.overlay` | on a device or emulator (`compose-ui-test`), the sample app is the manual test |

## Building

- The wrapper uses **Gradle 9.8.0**, and Gradle runs on **JDK 21**: [`gradle/gradle-daemon-jvm.properties`](gradle/gradle-daemon-jvm.properties) asks for it (Gradle Daemon toolchain) and the Foojay plugin in `settings.gradle` downloads it if it is not installed. Update it with `./gradlew updateDaemonJvm --jvm-version=<n>`.
- The library keeps Java 17 bytecode, so apps that still build with JDK 17 can use it.
- Unit tests of the core: `./gradlew :fly_view:testDebugUnitTest`.

## Troubleshooting

| Problem | Fix |
|---|---|
| `OverlayPermissionException` | ask first: `FlyOverlay.requestPermission(activity)` |
| `FlyServiceStartException` | start from a visible activity (`FlyLaunchActivity`) |
| `FlyOverlayNotStartedException` | call `FlyOverlay.start` before a screen composes a `FlyWindow` |
| Nothing shows after `start` | the `contribute` block must show a window (check your state), and its `visibleWhen` must allow it |
| The window vanishes when the user leaves the app | `visibleWhen = FlyVisibility.Always` (the default hides it in the background). If the process itself is killed, add a `FlyService` |
| Back button goes to the overlay, not the app | `focusable = false` |
| The text field has no keyboard | `focusable = true` |
| A request is lost | use a `Channel` with `send`, not a `SharedFlow` |
| State resets when the window hides | content state is not kept across hide/show, keep it in a ViewModel or state holder |

## Classic API (1.x)

The first API (`FlyViews`, `FlyViewInfo`, `DraggableFlyView`) keeps working: [docs/classic-api.md](docs/classic-api.md).

The design notes behind the new API: [docs/design-v2.md](docs/design-v2.md).

# License
```xml
Designed and developed by 2023 AbdulAleem Alsayed

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
