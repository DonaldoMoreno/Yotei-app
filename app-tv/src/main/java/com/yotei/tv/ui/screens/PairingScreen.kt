package com.yotei.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yotei.tv.data.PairingApiClient
import com.yotei.tv.data.PairingCodeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pairing Screen — Displayed while waiting for staff to enter pairing code on dashboard.
 *
 * Real-time Flow:
 * 1. TV generates 6-digit code from backend /api/pairing-codes/generate
 * 2. Displays code centered on screen (96sp, monospaced)
 * 3. Polls /api/pairing-codes/[code]/status every 2 seconds
 * 4. When staff enters code in dashboard, backend responds with binding
 * 5. TV transitions to QueueDisplayScreen automatically
 *
 * Accessibility:
 * - 96sp bold code for high visibility from 12+ feet
 * - High contrast (green Emerald on dark Slate)
 * - Clear instructions in Spanish
 */
@Composable
fun PairingScreen(
    deviceId: String,
    deviceSecret: String,  // Added: needed for generating code
    pairingCodeManager: PairingCodeManager,  // Added: for API calls
    onPairingComplete: (binding: PairingApiClient.GetBindingResponse) -> Unit,
    onError: (String) -> Unit
) {
    val TAG = "PairingScreen"
    val coroutineScope = rememberCoroutineScope()
    
    // State management
    var pairingCode by remember { mutableStateOf<String?>(null) }
    var isPolling by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var timeoutSeconds by remember { mutableStateOf(900) } // 15 minutes
    var isGenerating by remember { mutableStateOf(true) }

    // Step 1: Generate pairing code from backend on first load
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                android.util.Log.d(TAG, "=== PAIRING SCREEN INIT ===")
                android.util.Log.d(TAG, "deviceId: $deviceId")
                android.util.Log.d(TAG, "deviceSecret: ${deviceSecret?.take(4)}...")
                android.util.Log.d(TAG, "Starting code generation...")
                
                // Use a default device secret if one hasn't been provided yet
                // (On first pairing, the device doesn't have a secret yet from backend)
                val secretToUse = if (deviceSecret.isNullOrBlank()) {
                    // Use device-id as temporary secret for initial pairing code request
                    deviceId.take(16)
                } else {
                    deviceSecret
                }
                android.util.Log.d(TAG, "Using secret: ${secretToUse.take(4)}...")
                
                val result = pairingCodeManager.generatePairingCode(
                    deviceId = deviceId,
                    deviceSecret = secretToUse
                )
                
                result.onSuccess { response ->
                    android.util.Log.d(TAG, "✓ generatePairingCode SUCCESS: ${response.code}")
                    pairingCode = response.code
                    timeoutSeconds = response.ttl_seconds
                    isGenerating = false
                }
                
                result.onFailure { exception ->
                    android.util.Log.e(TAG, "✗ generatePairingCode FAILED: ${exception.message}")
                    exception.printStackTrace()
                    errorMessage = "Error generando código: ${exception.message}"
                    isGenerating = false
                    isPolling = false
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "✗ Unexpected exception: ${e.message}", e)
                errorMessage = "Error: ${e.message}"
                isGenerating = false
                isPolling = false
            }
        }
    }

    // Step 2: Count down timeout (decrements every second)
    LaunchedEffect(isPolling) {
        if (!isPolling) return@LaunchedEffect

        while (timeoutSeconds > 0 && isPolling) {
            delay(1000)
            timeoutSeconds--
        }
        
        if (timeoutSeconds == 0 && isPolling) {
            errorMessage = "Código expirado. Por favor, solicite uno nuevo."
            isPolling = false
        }
    }

    // Step 3: Poll for pairing code status (every 2 seconds)
    LaunchedEffect(isPolling, pairingCode) {
        android.util.Log.d(TAG, "=== POLLING LAUNCHED EFFECT TRIGGERED ===")
        android.util.Log.d(TAG, "isPolling: $isPolling, pairingCode: $pairingCode")
        
        if (!isPolling || pairingCode == null) {
            android.util.Log.d(TAG, "Polling condition failed, returning early")
            return@LaunchedEffect
        }
        
        android.util.Log.d(TAG, "✓ Entering polling loop for code: $pairingCode")

        while (isPolling) {
            try {
                delay(2000) // Poll every 2 seconds
                
                // Call backend to check if staff has redeemed the code
                val result = pairingCodeManager.getPairingCodeStatus(
                    deviceId = deviceId,
                    code = pairingCode!!
                )
                
                result.onSuccess { statusResponse ->
                    when (statusResponse.code_status) {
                        "used" -> {
                            // ✅ CODE REDEEMED! Staff has entered the code
                            isPolling = false
                            
                            android.util.Log.d(TAG, "=== CODE REDEEMED ===")
                            android.util.Log.d(TAG, "statusResponse.binding: ${statusResponse.binding}")
                            android.util.Log.d(TAG, "statusResponse.redeemed_by_display_id: ${statusResponse.redeemed_by_display_id}")
                            
                            // Use the complete binding from backend response
                            val binding = if (statusResponse.binding != null) {
                                android.util.Log.d(TAG, "✓ Using binding from backend")
                                android.util.Log.d(TAG, "  binding_id: ${statusResponse.binding.binding_id}")
                                android.util.Log.d(TAG, "  barbershop_id: ${statusResponse.binding.barbershop_id}")
                                android.util.Log.d(TAG, "  device_id: ${statusResponse.binding.device_id}")
                                statusResponse.binding
                            } else {
                                android.util.Log.e(TAG, "✗ Binding is NULL from backend! Creating fallback (THIS IS THE BUG)")
                                android.util.Log.e(TAG, "  redeemed_by_display_id: ${statusResponse.redeemed_by_display_id}")
                                // Fallback - but will have empty barbershop_id
                                PairingApiClient.GetBindingResponse(
                                    binding_id = statusResponse.redeemed_by_display_id ?: "",
                                    device_id = deviceId,
                                    display_id = statusResponse.redeemed_by_display_id ?: "",
                                    barbershop_id = "", // ⚠️ EMPTY - TV app won't be able to fetch queue data!
                                    binding_status = "active",
                                    display_config = null,  // ✅ Changed from emptyMap()
                                    device_secret = deviceSecret,
                                    created_at = statusResponse.redeemed_at ?: ""
                                )
                            }
                            
                            onPairingComplete(binding)
                        }
                        
                        "pending" -> {
                            // Still waiting for staff to enter code
                            // Update countdown from response
                            if (statusResponse.ttl_seconds > 0) {
                                timeoutSeconds = statusResponse.ttl_seconds
                            }
                        }
                        
                        "expired" -> {
                            // Code expired
                            errorMessage = "Código expirado. Por favor, solicite uno nuevo."
                            isPolling = false
                        }
                    }
                }
                
                result.onFailure { exception ->
                    // Check if error is 410 (Gone) = Expired
                    if (exception.message?.contains("410") == true) {
                        errorMessage = "Código expirado. Por favor, solicite uno nuevo."
                        isPolling = false
                    } else if (exception.message?.contains("404") == true) {
                        errorMessage = "Código no encontrado. Por favor, intente de nuevo."
                        isPolling = false
                    } else {
                        // For other errors, continue polling (network might be temporary)
                        android.util.Log.w("PairingScreen", "Poll error: ${exception.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("PairingScreen", "Polling exception: ${e.message}")
                // Continue polling on error, might be temporary
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A) // Slate 900
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Error state
            if (errorMessage != null) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = Color(0xFFF87171), // Red 400
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = errorMessage!!,
                    fontSize = 32.sp,
                    color = Color(0xFFFFFFFF),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(40.dp))
                Button(
                    onClick = {
                        // Reset state and try again
                        errorMessage = null
                        isPolling = true
                        pairingCode = null
                        isGenerating = true
                        timeoutSeconds = 900
                    },
                    modifier = Modifier
                        .width(300.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981) // Emerald
                    )
                ) {
                    Text("Reintentar", fontSize = 24.sp)
                }
            } else {
                // Waiting for code state
                Text(
                    text = "Vinculando Pantalla",
                    fontSize = 48.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color(0xFFFFFFFF),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Pairing code display (large, visible from far away)
                if (pairingCode != null && !isGenerating) {
                    Text(
                        text = pairingCode!!,
                        fontSize = 96.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = Color(0xFF10B981), // Emerald
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B), shape = RoundedCornerShape(16.dp))
                            .padding(40.dp)
                    )
                } else {
                    // Loading state (generating code from backend)
                    CircularProgressIndicator(
                        modifier = Modifier.size(80.dp),
                        color = Color(0xFF10B981),
                        strokeWidth = 4.dp
                    )
                }

                Spacer(modifier = Modifier.height(60.dp))

                // Instructions
                Text(
                    text = "Ingrese este código en la plataforma de staff\npara vincular esta pantalla con su localización",
                    fontSize = 28.sp,
                    color = Color(0xFFCED5DC), // Slate 400
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Timeout countdown
                Text(
                    text = "Expira en: ${timeoutSeconds / 60}:${String.format("%02d", timeoutSeconds % 60)}",
                    fontSize = 24.sp,
                    color = if (timeoutSeconds < 60) Color(0xFFF87171) else Color(0xFFFCA5A5), // Red if < 1 min
                )
            }
        }
    }
}
