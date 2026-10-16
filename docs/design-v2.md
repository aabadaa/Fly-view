# Fly-view v2 design (draft)

Status: implemented in the `core` and `overlay` packages (see Implementation notes). The 1.x API was removed, see [migration-v1-to-v2.md](migration-v1-to-v2.md).

## Goal

Declarative Compose API with no loss of control: several windows, several contributions per window, per-activity and foreground/background scopes, and removal one by one.

```kotlin
// a window that aggregates contributions
FlyWindow("tools", visibleWhen = FlyVisibility.Foreground) { items ->
    Column { items.forEach { it.Content() } }
}

// any screen contributes an item
@Composable fun ScreenA() {
    FlyItem(window = "tools", id = "timer", onDismiss = { vm.stopTimer() }) { TimerChip() }
}

// process-level (no activity), declarative, several blocks can coexist
FlyOverlay.contribute("reminders") {
    if (state.hasReminder) FlyWindow("reminder", visibleWhen = FlyVisibility.Background) { ReminderCard() }
}
```

## The hybrid idea

A full-screen window can't let touches through its empty areas (the input dispatcher picks the window before our code runs), so every `FlyWindow` stays a **small real window** that wraps its content. What changes is how they are *composed*: like `Dialog` and `Popup` do, as sub-compositions of one host composition.

```
Host composition (one Recomposer, one lifecycle, one saved-state registry)
 ├─ FlyWindow "player"  -> small window, own ComposeView, parent = host composition
 ├─ FlyWindow "chat"    -> small window, own ComposeView, parent = host composition
 └─ FlyEffects          -> shared window for the dismiss zone (only while dragging)
```

Why it is better than v1:
- One Recomposer instead of one per view, `CompositionLocal`s flow into every window.
- A window exists exactly as long as its `FlyWindow` is in the composition. No registry, no `removeView`, no `onRemove` delay hack: leaving the composition (or `AnimatedVisibility`) is the exit.
- Dismiss zone, magnet and any cross-view effect read the same `FlyWindowState`.

## Building blocks

| Piece | Role | Notes |
|---|---|---|
| `FlyOverlay` | Process-level host. Takes the application context, never an activity | `contribute(id) { }` (declarative block, returns a handle, several allowed), `configure(service)`, `stop()` |
| `FlyWindow(key, visible, visibleWhen, state, hideWhenEmpty, onDismissRequest, content: (items) -> Unit)` | One window while the call is in the composition. Its content lays out the items contributed to it | `DisposableEffect` registers/unregisters, content composed with the caller's `rememberCompositionContext()` (same trick as `Popup`) |
| `FlyItem(window, id, order, onDismissRequest) { }` | A contribution to a window, from any screen or module | Same key `(window, id)` replaces the old content. The dismiss button is drawn by the content and calls `item.dismiss()` |
| `FlyDialog(onDismissRequest, properties, anchor) { }` | Modal on our own overlay window, no activity token needed | Dims the screen, takes focus, back and outside tap call `onDismissRequest`. Composed in the caller's composition. Added after the windows so it is on top |
| `FlyVisibility` | `Always`, `Foreground`, `Background`, `Owner(lifecycleOwner, minState)` | Default is the caller's `LocalLifecycleOwner` |
| `FlyWindowState` | Position, size, `isDragging`, `isOverDismissZone`, `animateTo`, `snapToEdge` | `rememberFlyWindowState(key)` can persist the position |
| `SnapPolicy` | Pure functions: snap target, clamp, fling projection, dismiss hit-test | No Android classes, unit tested |
| `FlyEffects` | The dismiss zone content | Replaces the `closeViewContent` global |

## Multiple contents (aggregation, scopes, visibility)

Two levels, both declarative:
1. **Windows** keyed by `key`: each one is a real small window.
2. **Items** keyed by `(window, id)`: contributions that a window lays out. Any composition can add one, so every screen or module contributes without knowing about the others.

The host keeps `SnapshotStateMap<key, FlyEntry>` and, per window, a list of items. A window with `hideWhenEmpty = true` disappears when its last item is gone.

| Feature | How |
|---|---|
| Several independent views | several `FlyWindow` keys |
| Several contents in one floating view | `FlyItem`s from any screen into one window |
| Each screen adds something | each screen composes its own `FlyItem` |
| User removes them one by one | every item gets a dismiss action that calls its `onDismiss`. The contributor changes its state and the item leaves. If it doesn't, the item stays: state is the truth |
| Dedicated per activity | default owner of a window/item is the caller's `LocalLifecycleOwner` (activity or fragment) |
| Foreground / background | `visibleWhen = Foreground / Background / Always` per window or item |
| Process-level, no activity (tile, receiver, service) | the entry point sets app-owned state, a `FlyOverlay.contribute(id) { }` block reads it. Several blocks coexist, so each feature module adds its own |
| Imperative `show/hide` by key | removed: your state decides, or `FlyOverlay.requestDismiss` |

Rules:
- Different keys coexist. Same key (window, or item in a window) is updated in place, the window and its position are kept.
- Items keep insertion order unless `order` is given.
- A key that returns later gets its last position back until the host is cleared.
- If two sources use the same key, the last one wins (warning in debug builds).
- Screen-scoped windows/items compose in the caller's composition, so they see that screen's `CompositionLocal`s, `ViewModel` and theme. `contribute` blocks compose in the host.

## Dismissal (who can remove a view)

Every way of removing a view is a **request**. It first goes to the view's own `FlyDismissEffect` (if any), which can take over and finish with `dismiss()` or `cancel()`. A committed dismissal calls `onDismissRequest` (like `Dialog`). The contributor decides, usually by updating its state, and the window or item leaves the composition. State stays the source of truth.

| Who | How |
|---|---|
| The content itself | its own button calls `LocalFlyWindow.current.dismiss()` / `item.dismiss()` (the library draws no dismiss button) |
| The user dragging to the dismiss zone | same request |
| Back button (focusable window) | same request |
| Other Compose code | changes the state that decides whether it is composed |
| Non-Compose code (tile, notification action, receiver, service) | `FlyOverlay.requestDismiss(window)`, `requestDismiss(window, itemId)`, `requestDismissAll()`. They return `true` if the entry exists |

A request to an entry without `onDismissRequest` hides it (the entry stays registered) until the contributor leaves and re-enters the composition.

Exit animations: `FlyWindow(visible = state)`. The window stays until its exit animation finishes, then it is removed. This replaces the `onRemove` + delay hack of v1.

### `FlyDismissEffect`: the flyview controls its own dismissal

When the user drops the view on the dismiss zone, a real app shows a short message ("Kept" / "Nothing to save") and removes the view after it. In v2 the view takes control itself with an effect, registered like `DisposableEffect`:

```kotlin
FlyWindow("note", visible = !closed, onDismissRequest = { vm.close() }) {
    FlyDismissEffect {                 // keys optional, re-registered when they change
        vm.showCloseMessage()          // anything: message, animation, a confirmation dialog...
        delay(400)
        dismiss()                      // the real dismissal
    }
    FlyIcon(message = vm.closeMessage, showMessage = vm.isClosing)
}
```

```kotlin
@Composable fun FlyDismissEffect(vararg keys: Any?, block: suspend FlyDismissScope.() -> Unit)
interface FlyDismissScope { val reason: DismissReason; fun dismiss(); fun cancel() }
// DismissReason: DragDrop, Back, External, Content
```

Two steps, one request:

| Step | Who | What |
|---|---|---|
| 1. Intercept | `FlyDismissEffect` (optional, window or item) | runs when anyone requests the dismissal: drop on the zone, Back, `FlyOverlay.requestDismiss`, the content's own button. The window stays put (no snapping) while it runs |
| 2. Commit | `onDismissRequest` (optional) | called by `dismiss()`. The contributor updates its state (`closed = true`), then the exit animation plays (`visible = false`) and the window leaves |

Rules:
- The effect ends with `dismiss()` (commit), `cancel()` (veto, the window snaps back to the edge), or by returning without either, which counts as `dismiss()`.
- Cleanup like `onDispose`: if the effect is cancelled because the window leaves the composition, `try/finally` inside the block runs. The window is leaving anyway, so no commit is needed.
- Further requests while the effect runs are ignored, the dismissal is already in progress.
- No effect registered: the request goes straight to step 2.
- No `onDismissRequest` either: the library hides the entry until the contributor leaves and re-enters the composition (see above).
- An item has its own effect, so `requestDismiss(window, itemId)` runs the item's effect, and `requestDismissAll()` runs every entry's.
- v1 equivalent: `onRemove` (runs while the view is still shown) plus the 100 ms delay.

## Lifecycle rules (the part v1 left unclear)

The window needs a context only to be created. It lives in the system window manager and depends on the **process**, not on an activity or a service. So the host takes the application context, and *when windows are visible* is a separate, explicit decision: the **owner**.

An owner is just a standard `LifecycleOwner`, no new type. It is set **per window or item** with `visibleWhen`, and defaults to the caller's `LocalLifecycleOwner`:

| Owner | How | Windows are visible while | State is cleared when |
|---|---|---|---|
| Process (`Foreground`) | `ProcessLifecycleOwner.get()` | app is in the foreground (`STARTED`) | `stop()` is called |
| `Background` | `ProcessLifecycleOwner.get()` | app is not in the foreground | `stop()` is called |
| Any `LifecycleOwner` | `Owner(activity)` / fragment / `LifecycleService` / your own `LifecycleRegistry` | owner is at least `STARTED` | owner is `DESTROYED` |
| `Always` | none | until removed or `stop()` | removed or `stop()` |
| Composition | call `FlyWindow` inside any composition | the call is in the composition | it leaves the composition |

- Activity owner and rotation: a destroyed activity with `isChangingConfigurations` only hides the windows, the state is kept for the next instance. A real finish clears it.
- Hidden means removed from the window manager. The host and every `FlyWindowState` (position, size) are kept, so the windows come back where they were.
- The foreground service is **not an owner**. It only raises the process priority and shows a notification. `FlyOverlay.configure(service = MyService::class.java)` keeps the process alive; windows with `Foreground` still hide, use `Always` or `Background` for the ones that must stay. It stops itself when the host has no windows.

| Who | Owns | Lifetime |
|---|---|---|
| `FlyOverlay` | the host (one Recomposer, saved state, view model store) | until `stop()` or the owner is destroyed |
| owner | visibility | see table above |
| `FlyWindow` call site | one window | exactly while it is in the composition |
| Service (optional) | process priority | while the host has windows |

## Process-level blocks: `FlyOverlay.contribute`

A tile, a widget or a receiver has no composition to put a `FlyWindow` in. `contribute` registers a composable block, usually in `Application.onCreate`, that the overlay host composes while it is running. The host lives in the process, not in an activity.

```kotlin
val handle = FlyOverlay.contribute("bubble") {      // returns a handle, handle.dispose() removes the block
    val open by BubbleEntry.isOpen.collectAsState()    // app-owned state, set by the entry points
    if (open) BubbleWindow()
}
```

- The block is composed only while the host is running, so a `FlyWindow` inside it never hits the not-started error.
- Entry points flip app state and call `FlyOverlay.start(...)`. The block reacts.
- Several blocks can coexist, so each feature module adds its own.
- When no window remains, the service stops itself. The block stays registered, so the next `start()` composes it again.

## Window state, one per mode (decided: shape A)

```kotlin
val iconState = rememberFlyWindowState("note/icon", initial = FlyPosition.Edge(Side.Right, y = 0.3f))
val editorState = rememberFlyWindowState("note/editor", initial = FlyPosition.InheritFrom(iconState))
FlyWindow("note", state = if (extended) editorState else iconState) { ... }
```

| Piece | Spec |
|---|---|
| `FlyWindowState` | `position`, `isDragging`, `isOverDismissZone`, `snappedSide`, `suspend animateTo(position)` |
| What is stored | an absolute top-left position plus the snapped side, never raw `params` (independent of gravity and size). A window snapped to the right edge returns flush to the right even when its size changed |
| `FlyPosition` | `Center`, `Absolute(x, y)`, `Edge(side, y)` (y as a fraction of the screen height), `InheritFrom(state)` (first use takes the other state's position) |
| Switching the state object | the window animates to the other state's saved position. The left state keeps where the window was when it left |
| `rememberFlyWindowState(key, persist = false)` | `persist = true` keeps the position on disk for a process restart |
| Atomic apply | placement, focus and the position change of one switch are applied in the same frame: layout and focus first, then the position animation, so content effects (like showing the keyboard) see the final state |

## Dragging, snapping and the removal callback

| API | Spec |
|---|---|
| `FlyWindow(drag = FlyDrag.Whole / HandleOnly / None)` | `Whole`: a drag anywhere moves the window (children that consume the gesture win, like buttons and scrolling). `HandleOnly`: only `Modifier.flyDragHandle()` areas drag. Replaces `DraggableFlyView` |
| `Modifier.flyDragHandle()` | makes an area drag the whole window |
| `FlyWindow(snap = FlySnap.Edges(padding, duration, fling = true) / FlySnap.None)` | replaces `autoGoToBorder`, `edgePadding` and `duration` of v1 |
| `FlyWindow(dismissZone = DismissZone() / null)` | the drag-to-dismiss area, same as in v1 |
| `FlyWindow(onRemoved = { })` | called once, after the window has fully left (the exit finished). Use it to reset your own state, for example `BubbleEntry.isOpen = false` |

## State holders: `rememberFlyViewModel`

```kotlin
@Composable inline fun <reified VM : ViewModel> rememberFlyViewModel(key: Any? = null, noinline factory: () -> VM): VM
```

- Window-session scope: it holds its own `ViewModelStore` through `remember` and a `RememberObserver`. The store is cleared (`onCleared()`) when the branch that called it leaves the composition.
- It is usable outside `FlyWindow`, so the VM can feed the window's own parameters (`slot`/`state`, `focusable`, `drag`, `snap`).
- `viewModel()` inside the window's content uses the window's own store instead, cleared when the window leaves.
- `SavedStateHandle` comes from the host's saved-state registry (in memory).

## Example: a bubble (sample)

The sample app (`app/.../v2`) implements this: a bubble that snaps to the edge and expands into a panel, each mode with its own position.

```kotlin
// entry points (a button, a tile, a widget trampoline, a receiver)
suspend fun openBubble(context: Context, text: String) {
    BubbleEntry.request(text)        // suspends until queued, a Channel keeps it until the window collects it
    FlyOverlay.start(context)        // throws if the permission is missing or the service is refused
}

// Application.onCreate
FlyOverlay.configure(service = ExampleFlyService::class.java)
FlyOverlay.contribute("bubble") {
    val open by BubbleEntry.isOpen.collectAsState()
    if (open) BubbleWindow()
}

@Composable
fun BubbleWindow() {
    val vm = rememberFlyViewModel { BubbleViewModel() }
    val bubbleState = rememberFlyWindowState("bubble/collapsed", FlyPosition.Edge(FlySide.Right, y = 0.3f))
    val panelState = rememberFlyWindowState("bubble/expanded", FlyPosition.InheritFrom(bubbleState))
    LaunchedEffect(vm) { BubbleEntry.requests.collect(vm::onRequest) }

    FlyWindow(
        key = "bubble",
        state = if (vm.expanded) panelState else bubbleState,
        visibleWhen = FlyVisibility.Always,
        visible = !vm.closed,
        focusable = vm.expanded,
        drag = if (vm.expanded) FlyDrag.HandleOnly else FlyDrag.Whole,
        snap = if (vm.expanded) FlySnap.None else FlySnap.Edges(),
        ime = if (vm.expanded) FlyIme.Avoid else FlyIme.Ignore,
        onDismissRequest = { vm.closed = true },
        onRemoved = { BubbleEntry.isOpen.value = false },
    ) {
        FlyDismissEffect { vm.showGoodbyeAndSave(); dismiss() }
        BackHandler(vm.expanded) { vm.expanded = false }
        /* the content: the bubble, or the panel with Modifier.flyDragHandle() on its title */
    }
}
```

## Placement, size and keyboard

A real app's keyboard-docked bar used raw params (`x = 0`, `y = 0`, `width = MATCH_PARENT`, `gravity = BOTTOM`). In v2 it is a declared mode.

```kotlin
FlyWindow(
    key = "note",
    placement = if (docked) FlyPlacement.Docked(Edge.Bottom, size = FlySize.Fill) else FlyPlacement.Floating,
    ime = if (docked) FlyIme.Avoid else FlyIme.Ignore,
    configure = { params -> /* raw escape hatch */ },
)
```

| Piece | Options |
|---|---|
| `placement` | `Floating` (default: position from the `FlyWindowState`, drag and snap apply). `Docked(edge, size, margin, insets)` with `edge` Top, Bottom, Start or End. `Fullscreen` |
| `FlySize` | `Wrap`, `Fill`, `Fixed(dp)`, `Fraction(0.5f)` along the docked edge. Also available for `Floating` |
| `ime` (decided: a separate parameter) | `FlyIme.Ignore` or `FlyIme.Avoid` |
| `insets` | `SystemBars` (default, respects status bar, navigation bar and cutout) or `None` (edge to edge) |

What `ime = Avoid` does:

| Placement | Mechanism |
|---|---|
| Docked bottom, Fullscreen | `softInputMode = ADJUST_RESIZE`: the window frame shrinks and stays above the keyboard |
| Floating | the library watches the keyboard height and nudges the window up while it is visible, then restores. The stored position is not changed |
| Docked top / start / end | no effect, documented |

- API 26 to 29: the keyboard height comes from the visible display frame (less precise than the insets of API 30+).
- The keyboard needs a focusable window. `ime = Avoid` with `focusable = false` throws in debug builds.

Behavior rules:
- `Docked` and `Fullscreen` ignore `drag` and `snap`. The `FlyWindowState` keeps the last floating position, so going back to `Floating` restores it.
- Switching placement is instant, as that app does today. The content can animate itself. A built-in slide transition can come later (animating the window size each frame is costly).
- Raw escape hatch: `FlyWindow(configure = { params -> ... })` is called every time the library builds the params. For imperative changes: `LocalFlyWindow.current.updateParams { }`. `windowAnimations` goes through it. Code that edits raw params ports as it is, so no control is lost.

Out of the first version (decided):

| Later | Why |
|---|---|
| `Anchored(to, alignment, offset)`: a window that follows another window | Not needed by the app that drove this design. It comes with `FlyDialog`'s anchor, after the spike validates multiple windows. Small menus already work with `Popup` / `DropdownMenu` |
| Bottom sheet gesture (drag a docked window to resize it) | A continuous drag has to resize the real window every frame (an oversized transparent window would swallow touches meant for the app below). It also needs snap points and scrolling cooperation. Meanwhile `FlySize` can be switched from state |

## Errors and permissions (fail fast)

The developer handles the permission and the service rules up front, so the happy path never meets them. The library throws instead of opening settings or silently falling back.

| Situation | Behavior |
|---|---|
| Overlay permission missing | `FlyOverlay.start(context)` throws `OverlayPermissionException` |
| Android 12+ refuses the service start (background) | throws `FlyServiceStartException` (wraps `ForegroundServiceStartNotAllowedException`) |
| Android 14 service type / permission problem | the same exception, wrapping the `SecurityException` |
| `FlyWindow` composed while the overlay is not started | throws `FlyOverlayNotStartedException` (decided: fail fast, the developer starts the overlay first) |

```kotlin
if (!FlyOverlay.hasPermission(context)) {
    if (!FlyOverlay.requestPermission(activity)) return   // suspend, true if granted
}
FlyOverlay.start(context)   // throws if the OS refuses (the service comes from FlyOverlay.configure)
```

- `FlyOverlay.hasPermission(context)`, `requestPermission(activity)` (suspend, plus a callback version), `FlyOverlay.isRunning` (state).
- No silent fallback to running without the service: the developer decides what to do, for example launch the trampoline activity.
- `FlyLaunchActivity`: a ready-made invisible trampoline that starts the overlay from a visible activity (Android 12+), so a widget, a tile or a notification action can use it. Replaces the hand-written trampoline activities of apps.
- v1 returned `false` and opened the settings screen. v2 throws.

## State holders and ViewModels

Learned from a real app: a ViewModel that is a plain class with its own never-cancelled scope leaks its flow collections after the window is gone, and a save started on close can be cut if the scope is cancelled too early.

| Kind of state | Lives |
|---|---|
| Draft (text, current item, undo history, mode) | through recompositions and mode switches of the same window |
| Persisted data (repository) | always, it is the source of truth |
| Work in flight (save on close, scheduling) | until it finishes, after the window is gone: do it inside `FlyDismissEffect` and `join()` before `dismiss()` |

Decided: **window session scope** (option A). The state holder is created when the window opens, and cleared after the dismissal is committed and its work is done. The next open starts fresh and loads the persisted data.

- A real `ViewModel` is the recommended holder. `viewModel()` inside a `FlyWindow` uses a per-window `ViewModelStore`, cleared when the window leaves the composition, so `onCleared()` cancels its scope.
- `SavedStateHandle` comes from the window's saved-state registry (in memory, like today). Surviving a process death is the job of persistence plus `onClosingSoon`.
- Optional `FlyWindow(viewModelScope = FlyViewModelScope.Window)` (default) or `.Process` for a holder that survives dismissals until `FlyOverlay.stop()`. Not needed by the sample.
- Pattern: create the holder inside the branch that is composed while the window is open, so leaving the branch ends the session.

## Window lifecycle (shown and hidden)

Instead of `onShown` / `onHidden` callbacks, a window uses what already existed:
- The window's `FlyView` is a real `LifecycleOwner`: `CREATED` when it is created, `RESUMED` when it is added to the screen, `DESTROYED` when it is removed. Its content is composed only in between, so `DisposableEffect` or `LifecycleEventEffect` inside the content are the "shown" and "hidden" hooks.
- For observers outside the window, `FlyWindowState` has `isShown` and `hiddenReason` (`FlyHiddenReason`: `Visibility`, `Invisible`, `Empty`, `Dismissed`, `OverlayStopped`, `LeftComposition`), both Compose state (`snapshotFlow` turns them into a flow).
- `onClosingSoon` is the signal before, `hiddenReason` the explanation after, `onRemoved` only for `visible = false`.

## Process lifetime and the closing-soon signal

Without a foreground service the process can be killed by Android while the app is in the background, and every window disappears with it. Document this clearly, and let the content warn the user.

Android has no reliable "about to be killed" callback, so the signal is best-effort and has three sources:

| Source | When | Reliability |
|---|---|---|
| `onTrimMemory` at the background levels (`TRIM_MEMORY_BACKGROUND` and above) while no service keeps the process alive | memory pressure, process is a candidate to be killed | a hint, a kill can come with no warning |
| Our own policies: host stopping, an owner leaving `STARTED`, `hideOnBackground` | known in advance | exact, but short (state saving, exit animation) |
| `ActivityManager.getHistoricalProcessExitReasons` (API 30+) | next start | after the fact: "the system closed the overlay", offer to show it again |

API:

```kotlin
FlyWindow("tools", onClosingSoon = { reason -> save() }) {
    val soon = LocalFlyWindow.current.closingSoon        // State: null or a FlyClosingReason
    if (soon != null) Text("This may close soon, enable keep-alive")
}
// FlyClosingReason: ProcessAtRisk(trimLevel), OwnerStopping, HostStopping
FlyOverlay.lastExitReason()                               // null, or why the previous process ended (API 30+)
```

- `ProcessAtRisk` is only raised when no service is configured. With the service it is not raised.
- It is a hint to the user, never a guarantee. The docs must say so.
- Positions and window state are persisted (`rememberFlyWindowState(key)`), so the windows can come back after the process is recreated.

## Dialogs

Android's `Dialog` needs an activity window token and fails with an application context, so v2 provides `FlyDialog` on a window of our own. `Popup` and dropdown menus work as they are, they attach to the overlay window they belong to.

| | `FlyDialog` |
|---|---|
| Window | top-level `TYPE_APPLICATION_OVERLAY`, full screen, can be larger than the window that opened it |
| Touch / focus | focusable and modal, outside tap and Back call `onDismissRequest` |
| Dim | compose scrim (configurable), no activity needed |
| Position | centered, or next to a window with `anchor = "windowKey"` |
| Z-order | added after the windows, so on top. If a window is re-added later, the dialog is moved above it |
| Composition | in the caller's composition (screen theme, `ViewModel`) |
| Visibility | same `visibleWhen` as windows, defaults to the caller's lifecycle owner |

## What the hybrid does not solve

- The shared effects window is still a second window (a few ms to add at drag start). Pre-creating it hidden may be needed.
- `rememberSaveable` has nothing to restore after process death, only positions can be persisted.
- Windows still can't be clipped to non-rectangular touch regions.

## Spike checklist (do before building)

1. Sub-compositions in a `Service` context: owners (lifecycle, saved state, view model store) on every window.
2. Disposal order when the host is stopped while windows are animating.
3. IME and focus: two focusable windows, text field in one of them.
4. Dismiss window latency at drag start on a low-end device.
5. Rotation, folding, multi-display.
6. Android 12+ obscured-touch behavior with the `FlyEffects` window.

## Milestones

| | Work | Status |
|---|---|---|
| M0 | Extract `SnapPolicy` as pure functions + unit tests | yes |
| M1 | `FlyWindowState` (position, animation, dismiss state) | yes, internal |
| M2 | Host + `FlyWindow` (spike checklist above) | no |
| M4 | Owners (`LifecycleOwner`), optional service, position persistence | no |
| M5 | Docs, migration guide, 2.0.0 | no |

## Lessons from a real app

Read from the app's on-fly module. What it needs that v2 must keep:

| Real usage | Consequence for v2 |
|---|---|
| The "controller" is a full `ViewModel` (use cases, `SavedStateHandle`, undo/redo, `StateFlow`s) | Keep the idea: a state holder owned by the app. v2 only stops requiring `FlyController`/`Bundle` |
| Opened from a Quick Settings tile, a widget trampoline activity, a process-text activity, a reminder receiver (no activity alive) | A process-wide entry point that works from any context is required, so the host can't depend on an activity. Process-owned host plus the optional service is the main case, not the exception |
| `if (!updateFlyView(key, args)) { register; show }` | First-class `showOrUpdate(key, args)` (v1 `FlyViews.show` with a bundle did it) |
| Custom `LayoutParams` (no gravity, own flags, own animations) and runtime changes of `x`, `y`, `width`, `gravity`, `FLAG_NOT_FOCUSABLE` | Never assume a gravity or flags. Positions must be converted through the gravity (done by `AnchoredPosition`) |
| Two modes in one window: a small draggable icon and a big editor (also a full-width bar docked at the keyboard) | One window changes size and mode. `FlyWindow` needs to allow that, not only fixed-size popups |
| `DraggableFlyView` wraps only a small handle, but the whole window moves | Drag target and window are different things in the API |
| Starting the foreground service from a visible trampoline activity (Android 12+) | Document it, maybe provide the trampoline |

## Decisions

| Topic | Decision |
|---|---|
| Minimum API | 26 (overlay window type needs it, newer APIs are version-guarded) |
| `FlyController` + `Bundle` | Not part of the v2 API (state hoisting instead) |
| Host owner | The process, through the application context. Windows don't need an activity or a service to live |
| Custom owner | Any standard `LifecycleOwner` (activity, fragment, `LifecycleService`, own registry), chosen per window/item with `visibleWhen`. No new owner type |
| Declarative first | Windows and items are composables. There is no imperative show/hide |
| Duplicate keys | The new content replaces the old one (window, and position, kept). No exception |
| Dismiss UI | Drawn by the content, the library draws no dismiss button |
| Self-controlled dismissal | `FlyDismissEffect { ...; dismiss() }`: the flyview takes control when a dismissal is requested (message, animation, confirmation) and calls `dismiss()` or `cancel()`. Then `onDismissRequest` commits it in the contributor's state |
| Dismissal | Always a request to `onDismissRequest`, from the content, the drag zone, back, or outside via `FlyOverlay.requestDismiss*` |
| Request without a handler | If the entry has no `onDismissRequest` the library hides it until the contributor leaves and re-enters the composition |
| Process lifetime | Documented. Content gets a best-effort `closingSoon` state (trim-memory hint, own policies, exit reason after the fact), never promised as a guarantee |
| Dialogs | We provide `FlyDialog` on our own window, not Android's `Dialog` |
| Errors | `FlyOverlay.start` throws (`OverlayPermissionException`, `FlyServiceStartException`), no silent fallback. The developer requests the permission first |
| Trampoline | The library ships `FlyLaunchActivity` |
| State holder lifetime | Window session scope (A) with a real `ViewModel`. Work in flight is awaited inside `FlyDismissEffect`. `FlyViewModelScope.Process` is an option, not the default |
| Position per mode | One `FlyWindowState` per mode (shape A). Switching the state animates to its saved position |
| Drag / snap | `drag = FlyDrag.Whole/HandleOnly/None` + `Modifier.flyDragHandle()`, `snap = FlySnap.Edges(..)/None`, replacing `DraggableFlyView` and its parameters |
| Removal callback | `FlyWindow(onRemoved)` runs once after the window has left, to reset app state |
| Placement | `Floating`, `Docked(edge, size, margin, insets)`, `Fullscreen`, with `FlySize` and the `configure` escape hatch. `Anchored` and the bottom sheet come later |
| Keyboard | A separate `ime = FlyIme.Ignore / Avoid` parameter, not part of `Docked` |
| Shown / hidden | No callbacks: the window lifecycle and the composition give the hooks inside the content, `FlyWindowState.isShown` and `hiddenReason` give them outside |
| Aggregation | Items contributed from any screen into a window, removable one by one, no feature lost vs v1 |
| Hide on background | Opt-in with `visibleWhen = Foreground`. The default is `Always` for `contribute` windows (they float over other apps) and "while the screen is started" for windows a screen composes. Hidden windows are removed, their position is kept and restored on return |
| Activity recreation | Nothing to reattach with the process owner. With an activity owner, a config change only hides the windows |
| Service | Optional keep-alive for the process (priority + notification), not an owner. Needs a device check of how long an overlay-only process survives |

## Implementation notes (what the first implementation does differently)

The first implementation is in `fly_view/.../overlay` (Android + Compose, not compiled or run yet) on top of `fly_view/.../core` (pure Kotlin, 43 unit tests that run on the JVM).

| Topic | Design | Implementation |
|---|---|---|
| Items | composed in the caller's composition | composed in the window's composition (simpler). The window's `CompositionLocal`s apply, not the contributor's |
| `FlyWindow` default `focusable` | not specified | `false`: a display only widget does not take Back from the app below. Text fields need `true` |
| Visibility default | the caller's owner | the caller's activity/fragment being started for windows a screen composes, always for `contribute` blocks (decided: they float over other apps) |
| Hiding | removed from the window manager | removed and the content is disposed: content state is not kept, keep it in a ViewModel or state holder. Position and `FlyWindowState` are kept |
| Overlay stop | stops when no window is left | stops when no window is alive: meant to exist (visible, not empty, not dismissed) or still playing its exit. Windows hidden by their visibility rule count as alive |
| `FlyDialog` exit | exit animation | enter only, removed right away when it leaves the composition. `anchor` comes with `Anchored` |
| Keyboard for a floating window | nudge up while visible | uses window insets (Android 11+), best effort |
| Dismiss after commit | - | if the owner keeps the window after `onDismissRequest`, the next request works again after 600 ms |
| Foreground tracking | `ProcessLifecycleOwner` | an `ActivityLifecycleCallbacks` counter debounced 700 ms (no extra dependency) |

Verified: the core logic (unit tests). **Not verified**: everything that touches `WindowManager` and Compose. The spike checklist above is the verification plan.

## Open questions

- Device test: which `onTrimMemory` levels does an overlay-only process really receive, and how long does it survive in the background on AOSP, Samsung and Xiaomi? Decides the thresholds of `ProcessAtRisk` and how loudly the docs recommend the service.
- `FlyDialog` z-order when windows are re-added: moving the dialog above is enough, or does it need its own layer?

### Host behavior by event

| Event | Windows | Host state |
|---|---|---|
| App goes to background (process owner) | Removed (hidden) | Kept |
| App returns to foreground | Added back at the same positions | Kept |
| Activity recreated (activity owner) | Hidden, then shown again | Kept |
| Owner destroyed for good | Removed | Cleared |
| `stop()` | Removed | Cleared |
| Service mode | Stay visible | Kept |
