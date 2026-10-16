# Fly-view
this library enables you to add a composable view to the window manager easily using a foreground service
## Demo
[flyView-demo.webm](https://github.com/aabadaa/FLyView/assets/54916786/79d6c4f2-6a83-4ac4-a44c-8e3f74d58dfa)

## Setup
### Step 1 Add this in your root build.gradle at the end of repositories:
```groovy
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```
### Step 2 Add the dependency, get the last version from [![](https://jitpack.io/v/aabadaa/Fly-view.svg)](https://jitpack.io/#aabadaa/Fly-view)
```groovy
	dependencies {
	        implementation 'com.github.aabadaa:Fly-view:Tag'
	}
```
## Usage
There are two ways to show a fly view: through a foreground service (works while your activity is not running) or directly with a `WindowManager` (while your app is alive). Both need the overlay permission (`SYSTEM_ALERT_WINDOW`), which the library declares; if it is missing the permission screen is opened and nothing is shown.

### 1. Define a `FlyViewInfo`
```kotlin
class ExampleController : FlyController {
    var x by mutableIntStateOf(0)
    var auto by mutableStateOf(false)

    override fun update(data: Bundle) {
        x = data.getInt("exampleInt")
    }
}

fun createInfo(): FlyViewInfo<ExampleController> {
    val controller = ExampleController()
    return FlyViewInfo(controller = controller, onRemove = {
        controller.x = 10
        controller.auto = false
        delay(1000)
    }) {
        DraggableFlyView(autoGoToBorder = controller.auto) {
            BackHandler { removeView() }
            Column(modifier = Modifier.background(Color.White)) {
                Text(text = "test ${controller.x}")
                Button(onClick = removeView) { Text("Close") }
                Button(onClick = { controller.x++ }) { Text("x++") }
                Button(onClick = { controller.auto = controller.auto.not() }) {
                    Text(text = controller.auto.toString())
                }
            }
        }
    }
}
```
Use `NoController` if you don't need a controller.

### 2a. Show it using the service
Extend the service:
```kotlin
class FlyServiceImpl : FlyViewService()
```
Declare it in your manifest:
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<service
    android:name=".FlyServiceImpl"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Shows an overlay view using the WindowManager" />
</service>
```
Register a provider for your key, then call `show`:
```kotlin
FlyViewService.infoProviders["example"] = { createInfo() }
FlyViewService.show(context, "example", FlyServiceImpl::class.java)
```
`show` can be called again with the same key and a `bundle` to update a view that is already shown. The service stops itself when its last view is removed. Override `createNotification` in your service to customize the foreground notification.

### 2b. Show it using the `WindowManager`
```kotlin
val windowManager = context.getSystemService<WindowManager>()!!
windowManager.addFlyInfo(context, "example", createInfo())
// ...
windowManager.removeFlyView("example")
```
`addFlyInfo` returns `false` if the permission is missing or the key is already shown.

### Updating a shown view
```kotlin
updateFlyView("example", bundleOf("exampleInt" to 5)) // calls FlyController.update
```
### Customizing the close view
The bin shown while dragging a view to the bottom of the screen can be replaced by assigning `closeViewContent`.

Check the full example [here](https://github.com/aabadaa/FLyView/blob/master/app/src/main/java/com/abada/flyview/ExampleController.kt)
## Docs
### FlyViewInfo
A holder to all information needed for the ```FlyView```<br>
   @property controller used to send bundles from anywhere to the ```FlyView```<br>
   @property onRemove a suspend lambda which will be called before removing the ```FlyView```<br>
   @property params a ```WindowManager.LayoutParams``` that passed when adding the ```FlyView```<br>
   @property keyDispatcher this will be passed to the ```FlyView``` to handle key events, return `null` to let the view handle the event<br>
   @property content the content of the flyView<br>
   @property removeView call it in your view to remove it (it is safe to call more than once)<br>
   `updateLayoutParams()` applies your modifications of `params` to the shown view<br>
   `animateTo(x, y, duration)` and `goToScreenBorder(duration)` move the view smoothly<br>
### FlyController
Implement it to receive bundles through `update`, or use `NoController`.
### FlyViewService
An abstract service, extend it and declare it in your manifest.<br>
   `infoProviders` a map from a key to a lambda that creates the ```FlyViewInfo``` for it<br>
   `show(context, key, serviceClass, bundle)` shows the view registered for ```key```<br>
   @param ```context``` a context to start the service<br>
   @param ```key``` the key you used to add your ```FlyViewInfo``` to the ```infoProviders```<br>
   @param ```serviceClass``` your ```FlyViewService``` subclass<br>
   @param ```bundle``` an optional bundle that will be passed to your controller ```FlyController.update``` method<br>
### DraggableFlyView
Makes the content draggable, `autoGoToBorder` snaps it to the screen border after a drag and `duration` is the snapping animation time in milliseconds. Dropping it in the bottom zone removes it.

## Migrating to v2
- `FlyViewService` is abstract: extend it, declare it in the manifest and pass the class to `show`.
- `infoProviders` holds lambdas that create the `FlyViewInfo`, not the info itself.
- `addFlyInfo` now returns a `Boolean`.

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
