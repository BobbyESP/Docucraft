package com.bobbyesp.docucraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val navHostController = rememberNavController()

            DocucraftTheme {
                Navigator(
                    navHostController = navHostController
                )
            }
        }
    }
}
