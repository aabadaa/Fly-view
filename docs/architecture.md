# Architecture and decisions

How Fly-view works inside and why. For usage see the [README](../README.md), for the old API see the [migration guide](migration-v1-to-v2.md).

## Two layers

| Layer | Package | What it is | Tested with |
|---|---|---|---|
| Core | `com.abada.flyView.core` | Pure Kotlin, no Android: snap, clamp and dismiss-zone math, gravity independent positions, placement and size, visibility rules, keyed slots, the dismiss state machine, closing hints, the exceptions | JVM unit tests (`fly_view/src/test`) |
| Overlay | `com.abada.flyView.overlay` | Compose and `WindowManager`: `FlyOverlay`, `FlyWindow`, `FlyItem`, `FlyDialog`, `FlyService`... Every Android detail lives here and calls the core for the decisions | a device or emulator, the sample app |

## Windows: small real windows, one composition

A full-screen overlay window can not let touches through its empty areas (the system picks the window before our code runs). So every `FlyWindow` is a **small real window** that wraps its content, and the empty screen stays touchable for the app below.

The windows are composed like `Popup` and `Dialog` are, as sub-compositions of the composition that calls `FlyWindow`:

```
Overlay host (a 1x1 window, composes the FlyOverlay.contribute blocks)
 ├─ contribute block "counter"  -> FlyWindow "counter" -> small window, own ComposeView
 └─ contribute block "tools"    -> FlyWindow "tools"   -> small window, own ComposeView
Screen (an activity)            -> FlyWindow / FlyItem -> composed in the screen, the window is added to the host
```

- A window exists exactly while its `FlyWindow` call is in the composition (and `visible` and its `visibleWhen` rule say so).
- The host belongs to the process and takes the application context. A window needs no activity and no service.
- `FlyItem`s are contributions to a window, by `(window, id)`: any screen or module adds one and the window lays them out. They compose inside the window.
- A duplicate key replaces the old content, the position is kept.

## Decisions

| Topic | Decision | Why |
|---|---|---|
| API style | Declarative: windows and items are composables driven by your state | no registry, no controller, no `show`/`hide` by key |
| State of a window's logic | A state holder you own, `rememberFlyViewModel` for the window session | the library does not need a controller type |
| Position | Absolute top-left pixels in a `FlyWindowState`, one state per mode | independent of gravity and window size, switching the state animates to the saved position |
| Visibility | `visibleWhen` per window or item: `Default`, `Always`, `Foreground`, `Background`, `Owner`. Default: always for `contribute` windows, while the screen is started for windows a screen composes | floating over other apps is the main use, a screen's window follows the screen |
| Dismissal | Every way of removing a window is a request: `FlyDismissEffect` (optional) takes control, `dismiss()` commits it, `onDismissRequest` updates your state | state stays the source of truth, a window can show a message or ask for a confirmation first |
| Dismiss without a handler | The window hides until it leaves the composition and comes back | outside callers (`FlyOverlay.requestDismiss`) still work |
| Dismiss UI | Drawn by your content, the library draws none. The drop area is `FlyOverlay.dismissZoneContent` | full control of the look |
| Errors | `FlyOverlay.start` throws (`OverlayPermissionException`, `FlyServiceStartException`), a window composed before `start` throws `FlyOverlayNotStartedException` | the developer asks for the permission first, no silent fallback |
| Service | An optional keep-alive for the process (priority and notification), not an owner of the windows. The overlay and the service stop when no window is alive | an overlay-only process can be killed in the background |
| Lifecycle | A window has a `LifecycleOwner`: `RESUMED` when added, `DESTROYED` when removed. `FlyWindowState.isShown` and `hiddenReason` for observers outside | no `onShown`/`onHidden` callbacks needed |
| Keyboard | `ime = FlyIme.Avoid` (default): docked bottom and fullscreen resize, a floating window moves up and can't be dragged behind the keyboard (Android 11+). The keyboard is watched by `ImeProbe`, an invisible untouchable display-tall 1 px window, because a window's own insets only cover the part the keyboard overlaps | needs `focusable = true` |
| Keys | `onKeyEvent` sees every key first, then the content's Compose key modifiers, then the back handling | fixes device quirks (a key reported as Back) |
| Requests from outside | `Channel` + `send`, not `SharedFlow` | a `SharedFlow` drops a value when nobody collects yet |
| Minimum API | 26 | the overlay window type needs it |
| Build | JDK 21 (Gradle daemon toolchain), library bytecode Java 17 | apps still on JDK 17 can use the library |

## How the pieces work

**Dismissal** (`DismissCoordinator`, core): a request is ignored while another runs. With a `FlyDismissEffect` the effect runs and ends with `dismiss()`, `cancel()` (the window snaps back) or by returning (counts as `dismiss()`). Without one the request commits right away. If the owner keeps the window after `onDismissRequest`, a later request works again after 600 ms.

**Dragging** (`FlyWindowRuntime`, `SnapPolicy`): the pointer is measured in screen coordinates (the window moves under the finger), the window stays inside the display, the dismiss zone is a separate window shown only while dragging, and on release the window flies to the closest edge with the fling velocity taken in account.

**Placement**: `Floating` (position from the state, drag and snap apply), `Docked(edge, size, margin, insets)` (drag and snap are ignored, the floating position is kept), `Fullscreen`. Switching is instant, the content can animate itself.

**Closing hints**: `closingSoon` and `onClosingSoon` report `ProcessAtRisk` (the system is short on memory and no service runs), `OwnerStopping` and `HostStopping`. A hint, never a guarantee. `FlyOverlay.lastExitReason` tells why the previous process ended (Android 11+).

**Foreground tracking**: an `ActivityLifecycleCallbacks` counter debounced 700 ms (a rotation is not the background), double-checked with the system.

## Known limits

- A hidden window is removed and its content is disposed: keep state in a ViewModel or state holder. Items compose in the window, so the contributor's `CompositionLocal`s do not apply.
- `FlyDialog` has no exit animation and no anchor yet.
- The keyboard nudge of a floating window uses window insets (Android 11+) and is best effort.
- Nothing runs when the process is killed.

## Not verified yet

The core is covered by unit tests. The parts that touch `WindowManager` and Compose were written without a device and need a run on real hardware:

1. Windows composed in a service context: lifecycle, saved state and view model store on every window.
2. Stopping the overlay while windows animate.
3. Two focusable windows and a text field: keyboard and focus.
4. The dismiss window latency at drag start on a low-end device.
5. Rotation, folding, multi-display.
6. Android 12+ obscured-touch behavior with the drop area window.
7. Which `onTrimMemory` levels an overlay-only process really receives, and how long it survives in the background on AOSP, Samsung and Xiaomi.
8. The keyboard nudge of a floating window.
9. `FlyDialog` z-order when windows are added later.

## Later

- `Anchored(to, alignment, offset)`: a window that follows another window (menus, a chat head with its bubble), with `FlyDialog`'s anchor. Small menus already work with `Popup` and `DropdownMenu`.
- A bottom sheet gesture (drag a docked window to resize it): it needs a window resize on every frame, snap points and scroll cooperation.
