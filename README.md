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
### Step 2 Add the dependency, get the last version from [![](https://jitpack.io/v/aabadaa/FLyView.svg)](https://jitpack.io/#aabadaa/FLyView)
```groovy
	dependencies {
	        implementation 'com.github.aabadaa:Fly-view:Tag'
	}
```
## Usage
At first you need to define a ```FlyViewInfo``` like this:
```kotlin
        val controller = ExampleController()
        FlyViewInfo(controller = controller, onRemove = {
            controller.x = 10
            controller.auto = false
            delay(1000)
        }) {
            DraggableFlyView(autoGoToBorder = controller.auto) {
                BackHandler(true) {
                    Log.i(ContentValues.TAG, "createFlyView: backHandler")
                    removeView()
                }
                Column(modifier = Modifier.background(Color.White)) {
                    Text(text = "test ${controller.x}")
                    Button(onClick = removeView) {
                        Text("Close")
                    }
                    Button(onClick = {
                        controller.x++
                    }) {
                        Text("x++")
                    }
                    Button(onClick = { controller.auto = controller.auto.not() }) {
                        Text(text = controller.auto.toString())
                    }
                }
            }
        }
    }
```
Then you can call show method to launch the service, for example:
  ```kotlin
  FlyViewService.show(context, "example")
  ````
Be sure that there is a defined FlyViewInfo object  that is associated to the passed key.<br>
Check the full example [here](https://github.com/aabadaa/FLyView/blob/master/app/src/main/java/com/abada/flyview/ExampleController.kt)
## Docs
### FlyViewInfo
A holder to all information needed for the ```FlyView```<br>
   @property controller used to send bundles from anywhere to the ```FlyView```<br>
   @property onRemove pass a lambda which will be called before removing the ```FlyView```<br>
   @property params a ```WindowManager.LayoutParams``` that passed when adding the ```FlyView```<br>
   @property keyDispatcher this will be passed to the ```FlyView``` to handle key events<br>
   @property content the content of the flyView<br>
   @property flyView the ```android.view.View``` object that will be added to the ```WindowManager```<br>
   @property removeView call it in your view to remove it<br>
   @property params pass your lambda that updates the view's layout params to update it<br>
### FlyViewService
has a static ```show``` method to show a ```FlyViewInfo``` that you added to the ```infoProviders```<br>
   @param ```context``` a context to start the service<br>
   @param ```key``` the key you used to add your ```FlyViewInfo``` to the ```infoProviders```<br>
   @param ```bundle``` an optional bundle that will be passed to your controller ```FlyController.update``` method<br>

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
