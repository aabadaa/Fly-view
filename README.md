# FLyView
this library enables you to add a composable view to the window manager easily using a foreground service
## Setup
### Step 1 Add this in your root build.gradle at the end of repositories:
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
### Step 2 Add the dependency, get the last version from [here](https://jitpack.io/#aabadaa/FLyView)
	dependencies {
	        implementation 'com.github.aabadaa:FLyView:<version>'
	}

## Usage
At first you need to define a ```FlyViewInfo``` like this:
```kotlin
    FlyViewService.infoProviders["example"] = {
    FlyViewInfo(NoController) { // you are in the FlyViewScope
        DraggableFlyView {
		Column {
		    Text(text = "example")
		    // removeView is a method provided by the FlyViewScope
		    Button(onClick = removeView) {
			Text("Close")
		    }
		    Button(onClick = {
		     // params is a variable that enable you to modify your layout params in the windowManager
		     // this is also provided by the FlyViewScope
			params = WindowManager.LayoutParams()
		    }) {
			Text("update params")
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
Be sure that there is a defined FlyViewInfo object  that is associated to the passed key.
## Docs
### FlyViewInfo
  A holder to all information needed for the ```FlyView```<br>
   @property ```controller``` used to send bundles from anywhere to the ```FlyView```<br>
   @property ```params``` a ```WindowManager.LayoutParams``` that passed when adding the ```FlyView```<br>
   @property ```keyDispatcher``` this will be passed to the [FlyView] to handle key events<br>
   @property ```content``` the content of the flyView<br>
   @property ```flyView``` the ```android.view.View``` object that will be added to the ```WindowManager```<br>
### FlyViewScope
This class provides view property to the Composable content<br>
   @param ```params``` pass the initial ```WindowManager.LayoutParams``` object to enable updating layout params inside the composable view<br>
   @property ```removeView``` call this in your composable function to remove it from the ```WindowManager```<br>
   @property ```updateLayoutParams``` this function will be called when you assign a new object to ```params```<br>
### FlyViewService
has a static ```show``` method to show a ```FlyViewInfo``` that you added to the ```infoProviders```<br>
   @param ```context``` a context to start the service<br>
   @param ```key``` the key you used to add your ```FlyViewInfo``` to the ```infoProviders```<br>
   @param ```bundle``` an optional bundle that will be passed to your controller ```FlyController.update``` method<br>

