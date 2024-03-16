package com.abada.flyview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.abada.flyview.ui.theme.FLyViewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FLyViewTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background
    ) {
        val context = LocalContext.current
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = {
                context.createFlyViewUsingFlyService()
            }) {
                Text("start using fly service")
            }
            Button(onClick = {
                context.createFlyView()
            }) {
                Text("start using window manager")
            }
            Button(onClick = {
                updateFlyView("fly service",5)
            }) {
                Text("set x to 5 in fly service")
            }

            Button(onClick = {
                updateFlyView("fly",5)
            }) {
                Text("set x to 5 in normal fly")
            }
        }
    }
}