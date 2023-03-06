package com.abada.flyview

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.abada.flyView.FlyView
import com.abada.flyView.FlyViewInfo
import com.abada.flyview.ui.theme.FLyViewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FlyView.infos["test"] = FlyViewInfo {
            Column {
                Text(text = "test")
                Button(onClick = removeView) {
                    Text("Close")
                }
                Button(onClick = {
                    params = WindowManager.LayoutParams()
                }) {
                    Text("update params")
                }
            }
        }
        setContent {
            FLyViewTheme() {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background
                ) {
                    Button(onClick = {
                        throw Exception("test")
                    }) {
                        Text("start", color = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    FLyViewTheme {
        Greeting("Android")
    }
}