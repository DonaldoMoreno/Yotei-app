package com.yotei.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Boot/Splash screen shown during app initialization.
 *
 * Displayed during BOOTSTRAPPING state:
 * - Loading device state from storage
 * - Checking if device is already registered
 *
 * Simple spinner + "Iniciando..." text.
 * No user interaction required.
 */
@Composable
fun BootScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                color = Color(0xFF0F172A) // Dark blue background (consistent with other screens)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Loading spinner
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = Color(0xFF10B981), // Teal accent (matches app theme)
                strokeWidth = 4.dp
            )

            // Space between spinner and text
            Spacer(modifier = Modifier.height(24.dp))

            // "Loading..." text
            Text(
                text = "Iniciando...",
                style = TextStyle(
                    fontSize = 24.sp,
                    color = Color.White
                )
            )

            // Optional subtitle for tech transparency
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Cargando configuración",
                style = TextStyle(
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            )
        }
    }
}
