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
    
    // State management
    var pairingCode by remember { mutableStateOf<String?>(null) }
    var isPolling by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var timeoutSeconds by remember { mutableStateOf(900) } // 15 minutes
    var isGenerating by remember { mutableStateOf(true) }
    var codeRedeemed by remember { mutableStateOf<PairingApiClient.GetBindingResponse?>(null) }
    var codeThatWasUsed by remember { mutableStateOf<String?>(null) }
    
    // Store the callback in remember so it doesn't change reference
    val onPairingCompleteCallback = remember { onPairingComplete }

    // ✅ OPTIMAL FIX: Delay navigation to allow polling LaunchedEffect to exit cleanly
    // When codeRedeemed is set, wait ONE composition cycle before navigating
    // This gives the polling LaunchedEffect time to exit its while(isPolling) loop
    LaunchedEffect(codeRedeemed) {
        if (codeRedeemed != null) {
            android.util.Log.d(TAG, "=== [Navigation Effect] TRIGGERED ===")
            android.util.Log.d(TAG, "codeRedeemed != null: binding_id=${codeRedeemed!!.binding_id}")
            
            // Wait for current frame to finish (gives polling loop chance to exit)
            delay(50)  // Small delay to let polling LaunchedEffect clean up from while loop
            android.util.Log.d(TAG, "✓ [Navigation] Polling has exited, safe to navigate")
            android.util.Log.d(TAG, "✓ [Navigation] Calling onPairingCompleteCallback...")
            
            try {
                onPairingCompleteCallback(codeRedeemed!!)
                android.util.Log.d(TAG, "✓ [Navigation] onPairingCompleteCallback COMPLETED")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "✗ [Navigation] onPairingCompleteCallback THREW EXCEPTION: ${e.message}")
                e.printStackTrace()
                errorMessage = "Error en navegación: ${e.message}"
            }
        }
    }
    
    // Separate effect: When code is used, fetch binding
    // This runs independently from polling to avoid nesting too deep
    LaunchedEffect(codeThatWasUsed) {
        if (codeThatWasUsed != null) {
            android.util.Log.d(TAG, "=== [BindingFetch Effect] TRIGGERED ===")
            android.util.Log.d(TAG, "[BindingFetch] Code was redeemed: $codeThatWasUsed, fetching binding...")
            val bindingResult = pairingCodeManager.getBindingAfterCodeRedeem(
                code = codeThatWasUsed!!,
                deviceId = deviceId
            )
            
            bindingResult.onSuccess { binding ->
                android.util.Log.d(TAG, "✓ [BindingFetch] Binding received: ${binding.binding_id}")
                android.util.Log.d(TAG, "✓ [BindingFetch] Setting codeRedeemed state...")
                codeRedeemed = binding
                android.util.Log.d(TAG, "✓ [BindingFetch] codeRedeemed state UPDATED (this will trigger Navigation Effect)")
            }
            
            bindingResult.onFailure { error ->
                android.util.Log.e(TAG, "✗ [BindingFetch] Failed: ${error.message}")
                errorMessage = "Error obteniendo vinculación: ${error.message}"
            }
        }
    }

    // Step 1: Register device as provisional, then generate pairing code
    LaunchedEffect(Unit) {
        // ✅ NO NEED for rememberCoroutineScope - LaunchedEffect provides its own scope
        try {
            android.util.Log.d(TAG, "=== PAIRING SCREEN INIT ===")
            android.util.Log.d(TAG, "deviceId: $deviceId")
            android.util.Log.d(TAG, "deviceSecret: ${deviceSecret?.take(4)}...")
            
            // Step 1a: Register device as provisional (NEW Option 3)
            android.util.Log.d(TAG, "Step 1a: Registering device as PROVISIONAL...")
            val deviceName = "TV"
            val deviceModel = "Unknown"
            
            val regResult = pairingCodeManager.registerDeviceProvisional(
                deviceId = deviceId,
                deviceName = deviceName,
                deviceModel = deviceModel
            )
            
            regResult.onFailure { exception ->
                android.util.Log.e(TAG, "✗ Device registration FAILED: ${exception.message}")
                errorMessage = "Error registrando dispositivo: ${exception.message}"
                isGenerating = false
                isPolling = false
                return@LaunchedEffect
            }
            
            android.util.Log.d(TAG, "✓ Device registered as PROVISIONAL")
            
            // Step 1b: Now generate pairing code
            android.util.Log.d(TAG, "Step 1b: Generating pairing code...")
            
            // For provisional flow, generate a temporary secret locally
            // Server will handle actual device_secret_hash after pairing
            val secretToUse = deviceSecret ?: run {
                // Use first 16 chars of device_id as temporary secret
                deviceId.substring(0, minOf(16, deviceId.length))
            }
            android.util.Log.d(TAG, "Using secret: ${secretToUse.take(4)}...")
            
            val codeResult = pairingCodeManager.generatePairingCode(
                deviceId = deviceId,
                deviceSecret = secretToUse
            )
            
            codeResult.onSuccess { response ->
                android.util.Log.d(TAG, "✓ generatePairingCode SUCCESS: ${response.code}")
                pairingCode = response.code
                timeoutSeconds = response.ttl_seconds
                isGenerating = false
            }
            
            codeResult.onFailure { exception ->
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
                val statusResult = pairingCodeManager.getPairingCodeStatus(
                    deviceId = deviceId,
                    code = pairingCode!!
                )
                
                statusResult.onSuccess { statusResponse ->
                    when (statusResponse.code_status) {
                        "pending" -> {
                            // Still waiting for staff to enter code
                            android.util.Log.d(TAG, "Code still pending, TTL: ${statusResponse.ttl_seconds}s")
                            // Update countdown from response
                            if (statusResponse.ttl_seconds > 0) {
                                timeoutSeconds = statusResponse.ttl_seconds
                            }
                        }
                        
                        "used" -> {
                            // ✅ CODE REDEEMED! Binding data may already be in response
                            android.util.Log.d(TAG, "=== CODE REDEEMED (status=used) ===")
                            
                            // Optimization: If binding is already in the response, use it directly
                            if (statusResponse.binding != null) {
                                android.util.Log.d(TAG, "✅ Binding already in status response, using directly: ${statusResponse.binding!!.binding_id}")
                                codeRedeemed = statusResponse.binding
                                android.util.Log.d(TAG, "✓ codeRedeemed SET directly, skipping separate binding fetch")
                                isPolling = false
                            } else {
                                // Fallback: Binding not in response, trigger separate fetch
                                android.util.Log.d(TAG, "Binding NOT in status response, fetching separately...")
                                android.util.Log.d(TAG, "Setting codeThatWasUsed = $pairingCode (this will trigger BindingFetch Effect)")
                                codeThatWasUsed = pairingCode  // ← Trigger separate binding-fetch effect
                                android.util.Log.d(TAG, "✓ codeThatWasUsed SET, exiting polling loop...")
                                isPolling = false              // ← Exit this polling loop cleanly
                            }
                        }
                        
                        "expired" -> {
                            // Code expired
                            android.util.Log.d(TAG, "Code expired (status = expired)")
                            errorMessage = "Código expirado. Por favor, solicite uno nuevo."
                            isPolling = false
                        }
                    }
                }
                
                statusResult.onFailure { exception ->
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
