package com.softandtech.tuitionmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.softandtech.tuitionmanager.ui.navigation.AppNavHost
import com.softandtech.tuitionmanager.ui.theme.TuitionTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TuitionTheme {
                AppNavHost()
            }
        }
    }
}
