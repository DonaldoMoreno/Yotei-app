package com.yotei.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * First Launch Screen — Select barbershop and register device.
 *
 * UX Flow:
 * 1. Show device info (auto-detected model)
 * 2. Text field to enter barbershop UUID or search code
 * 3. Button to register device with that barbershop
 * 4. Transitions to PairingScreen after successful registration
 *
 * TODO: In production, integrate with Staff API to list available barbershops
 * For now, we accept UUID/code input and validate on backend.
 */
@Composable
fun FirstLaunchScreen(
    deviceName: String,
    deviceModel: String,
    onRegisterDevice: (barbershopId: String) -> Unit,
    isLoading: Boolean = false
) {
    var barbershopInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A) // Slate 900
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                text = "Bienvenido a YOTTEI",
                fontSize = 48.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = Color(0xFFFFFFFF),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Info card with device details
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 24.dp),
                color = Color(0xFF1E293B), // Slate 800
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Device Info",
                        tint = Color(0xFF10B981), // Emerald
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Este dispositivo",
                        fontSize = 20.sp,
                        color = Color(0xFFCED5DC),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$deviceName\n$deviceModel",
                        fontSize = 24.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = Color(0xFFFFFFFF),
                        textAlign = TextAlign.Center,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Instruction text
            Text(
                text = "Ingrese el ID de ubicación asignado por su gerencia",
                fontSize = 24.sp,
                color = Color(0xFFCED5DC),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Barbershop ID input
            OutlinedTextField(
                value = barbershopInput,
                onValueChange = {
                    barbershopInput = it
                    errorMessage = null // Clear error when user types
                },
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(56.dp),
                label = { Text("ID de Ubicación (UUID)", fontSize = 18.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFF475569),
                    focusedBorderColor = Color(0xFF10B981),
                    unfocusedTextColor = Color(0xFFFFFFFF),
                    focusedTextColor = Color(0xFFFFFFFF),
                    unfocusedLabelColor = Color(0xFF94A3B8),
                    focusedLabelColor = Color(0x10B981)
                ),
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 18.sp),
                singleLine = true,
                enabled = !isLoading,
                isError = errorMessage != null
            )

            // Error message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    fontSize = 16.sp,
                    color = Color(0xFFF87171), // Red 400
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Register button
            Button(
                onClick = {
                    if (barbershopInput.isBlank()) {
                        errorMessage = "Por favor ingrese un ID de ubicación"
                    } else {
                        onRegisterDevice(barbershopInput.trim())
                    }
                },
                modifier = Modifier
                    .width(400.dp)
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981) // Emerald
                ),
                enabled = !isLoading && barbershopInput.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Color(0xFF0F172A),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Registrando...", fontSize = 24.sp)
                } else {
                    Text("Registrar Dispositivo", fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Help text
            Text(
                text = "Si no conoce su ID de ubicación, por favor contacte a su administrador",
                fontSize = 16.sp,
                color = Color(0xFF94A3B8), // Slate 500
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }
    }
}
