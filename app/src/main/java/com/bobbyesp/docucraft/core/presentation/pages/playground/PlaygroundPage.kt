package com.bobbyesp.docucraft.core.presentation.pages.playground

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PlaygroundPage(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { paddingValues ->
        Text("Playground Page", modifier = Modifier.padding(paddingValues).fillMaxSize())
    }
}
