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
[optional] params: WindowManager.LayoutPramas which is set to a default value.<br>
[optional] keyDispatcher: pass a lambda that handles a ```KeyEvent?``` to handle navigation key from your fly view.<br>
[mandatory] content: a composable function where you define your view content.<br>
### FlyViewScope
this is the scope of your view content which provides you:
```removeView()``` which remove the view from the WindowManager.<br>
```params``` to access and modify the view layout params.<br>
### FlyViewService
has a static ```show``` method to show a specific view as the previous example.
a map<String,()->FlyViewInfo> to create your ```FlyViewInfo``` provider then the service will use it to add the view to the ```WindowManager``` 


