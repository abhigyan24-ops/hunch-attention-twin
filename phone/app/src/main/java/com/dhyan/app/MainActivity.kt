package com.dhyan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dhyan.app.ui.DhyanMainScreen
import com.dhyan.app.ui.theme.DhyanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DhyanTheme {
                DhyanMainScreen()
            }
        }
    }
}
