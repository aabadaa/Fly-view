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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.abada.flyview.ui.theme.FLyViewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FLyViewTheme() {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background
                ) {

                    Column {
                        Button(onClick = {
                            createFlyView()
                        }) {
                            Text("start", color = Color.Black)
                        }
                        Button(onClick = {
                            updateFlyView(5)
                        }) {
                            Text("update", color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}
