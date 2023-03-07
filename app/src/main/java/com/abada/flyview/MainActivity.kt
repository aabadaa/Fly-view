package com.abada.flyview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.abada.flyView.FlyView
import com.abada.flyView.FlyViewInfo
import com.abada.flyView.FlyViewService
import com.abada.flyview.ui.theme.FLyViewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FlyView.infos["test"] = FlyViewInfo(controller = this) {
            var x by remember { mutableStateOf(0) }
            Column {
                Text(text = "test $x")
                Button(onClick = removeView) {
                    Text("Close")
                }
                Button(onClick = {
                    x++
                }) {
                    Text("x++")
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
                        FlyViewService.show(this, "test")
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