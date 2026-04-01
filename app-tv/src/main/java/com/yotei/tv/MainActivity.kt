package com.yotei.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import com.yotei.tv.data.PairingRepository
import com.yotei.tv.data.PairingCodeManager
import com.yotei.tv.data.repository.QueueRepository
import com.yotei.tv.data.api.TvQueueApiClient
import com.yotei.tv.domain.state.QueueStateManager
import com.yotei.tv.ui.screens.FirstLaunchScreen
import com.yotei.tv.ui.screens.PairingScreen
import com.yotei.tv.ui.screens.BootScreen
import com.yotei.tv.ui.QueueDisplayScreenLive
import com.yotei.tv.ui.viewmodel.TvQueueViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Main Activity for TV App.
 *
 * Orchestrates the device registration and pairing flow:
 * 1. First Launch → Device Registration (barbershop selection)
 * 2. After Registration → Pairing Screen (code display + polling)
 * 3. After Pairing → Queue Display (real-time updates via TvQueueViewModel)
 *
 * Phase 2 Implementation:
 * - Queue fetching moved to QueueRepository (data layer)
 * - Queue display state managed by QueueStateManager (domain layer)
 * - Queue display UI connected to TvQueueViewModel (presentation layer)
 * - MainActivity no longer contains queue fetching logic
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Supabase config for device registration
            val supabaseUrl = "https://qghrryxqfxxqhmedegis.supabase.co"
            val anonKey = "YOUR_SUPABASE_ANON_KEY"
            
            // TurnoExpress backend for pairing codes and queue data
            val turnoExpressUrl = "http://192.168.1.27:3000"
            
            // Setup HTTP client for all API calls
            val httpClient = remember {
                HttpClient {
                    install(ContentNegotiation) {
                        json(Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        })
                    }
                }
            }
            
            // Initialize repositories and managers
            val pairingRepository = remember {
                PairingRepository(
                    context = applicationContext,
                    baseUrl = turnoExpressUrl,
                    anonKey = anonKey
                )
            }

            val pairingCodeManager = remember {
                PairingCodeManager(
                    httpClient = httpClient,
                    apiBaseUrl = turnoExpressUrl
                )
            }

            // Phase 2: Initialize Queue components
            val queueApiClient = remember {
                TvQueueApiClient(
                    baseUrl = turnoExpressUrl,
                    httpClient = httpClient
                )
            }

            val queueRepository = remember {
                QueueRepository(queueApiClient)
            }

            val queueStateManager = remember {
                QueueStateManager()
            }

            val queueViewModel = remember {
                TvQueueViewModel(queueRepository, queueStateManager)
            }

            // Provide repository to entire composable tree
            CompositionLocalProvider(LocalPairingRepository provides pairingRepository) {
                // Use a factory to create ViewModel with constructor argument
                val viewModel = remember {
                    AppStateViewModel(pairingRepository)
                }

                val appState = viewModel.appState.value
                val deviceId = viewModel.deviceId.value
                val deviceSecret = viewModel.deviceSecret.value
                val deviceName = viewModel.deviceName.value
                val deviceModel = viewModel.deviceModel.value
                val errorMessage = viewModel.errorMessage.value
                val barbershopId = viewModel.barbershopId.value

                when (appState) {
                    AppStateViewModel.AppState.INITIALIZING -> {
                        BootScreen()
                    }

                    AppStateViewModel.AppState.FIRST_LAUNCH -> {
                        FirstLaunchScreen(
                            deviceName = deviceName ?: "TV",
                            deviceModel = deviceModel ?: "Unknown",
                            onRegisterDevice = { barbershopId ->
                                viewModel.registerDevice(barbershopId)
                            }
                        )
                    }

                    AppStateViewModel.AppState.REGISTERING -> {
                        FirstLaunchScreen(
                            deviceName = deviceName ?: "TV",
                            deviceModel = deviceModel ?: "Unknown",
                            onRegisterDevice = { },
                            isLoading = true
                        )
                    }

                    AppStateViewModel.AppState.WAITING_PAIRING_CODE -> {
                        PairingScreen(
                            deviceId = deviceId ?: "",
                            deviceSecret = deviceSecret ?: "",
                            pairingCodeManager = pairingCodeManager,
                            onPairingComplete = { binding ->
                                android.util.Log.d(
                                    "MainActivity",
                                    "onPairingComplete callback triggered"
                                )
                                android.util.Log.d(
                                    "MainActivity",
                                    "  binding.barbershop_id: ${binding.barbershop_id}"
                                )
                                viewModel.waitForPairingCodeRedemption(binding)
                            },
                            onError = { error ->
                                // Already handled in ViewModel
                            }
                        )
                    }

                    AppStateViewModel.AppState.POLLING_BINDING -> {
                        PairingScreen(
                            deviceId = deviceId ?: "",
                            deviceSecret = deviceSecret ?: "",
                            pairingCodeManager = pairingCodeManager,
                            onPairingComplete = { binding ->
                                viewModel.waitForPairingCodeRedemption(binding)
                            },
                            onError = { }
                        )
                    }

                    AppStateViewModel.AppState.PAIRED -> {
                        // Legacy path (should not happen in normal flow)
                        android.util.Log.d("MainActivity", "✗ PAIRED state (legacy) - should use LOADING_DISPLAY_CONFIG")
                        QueueDisplayScreenLive(viewModel = queueViewModel)
                    }

                    AppStateViewModel.AppState.LOADING_DISPLAY_CONFIG -> {
                        android.util.Log.d("MainActivity", "╔═══════════════════════════════════════════════════════════╗")
                        android.util.Log.d("MainActivity", "║   LOADING_DISPLAY_CONFIG STATE ENTERED                      ║")
                        android.util.Log.d("MainActivity", "╚═══════════════════════════════════════════════════════════╝")
                        android.util.Log.d("MainActivity", "    [→] Validating binding and starting queue fetch")
                        
                        // Get barbershop ID from ViewModel
                        val currentBarbershopId = viewModel.barbershopId.value
                        android.util.Log.d("MainActivity", "    [✓] barbershopId=$currentBarbershopId")
                        
                        // Observe queue state - when data arrives, transition to PAIRED state
                        val queueState by queueViewModel.queueState.collectAsState()
                        
                        // When queue data arrives, transition to PAIRED state
                        LaunchedEffect(queueState) {
                            if (queueState != null) {
                                android.util.Log.d("MainActivity", "    [✓✓✓] Queue data available! Transitioning to PAIRED state")
                                viewModel.transitionAppState(
                                    AppStateViewModel.AppState.PAIRED,
                                    reason = "Queue data loaded successfully"
                                )
                            }
                        }
                        
                        // Set timeout callback on first entry
                        LaunchedEffect(appState) {
                            android.util.Log.d("MainActivity", "    [→] LaunchedEffect firing for LOADING_DISPLAY_CONFIG")
                            
                            // Set timeout callback so polling failures can trigger error state
                            queueViewModel.onPollingTimeout = {
                                android.util.Log.d("MainActivity", "    [✗] Polling timeout/failure callback triggered!")
                                android.util.Log.d("MainActivity", "    [→] Transitioning AppStateViewModel to ERROR")
                                viewModel.transitionAppState(
                                    AppStateViewModel.AppState.ERROR,
                                    reason = "Queue data failed to load (timeout or polling error)",
                                    errorMessage = "Display data could not be loaded. Please retry pairing."
                                )
                            }
                            
                            if (currentBarbershopId != null && currentBarbershopId.isNotEmpty()) {
                                android.util.Log.d("MainActivity", "    [→] Starting polling via queueViewModel")
                                val currentDisplayId = viewModel.displayId.value
                                try {
                                    queueViewModel.startLiveQueueUpdates(
                                        displayId = currentDisplayId ?: currentBarbershopId,  // Use displayId, fallback to barbershopId
                                        barbershopId = currentBarbershopId
                                    )
                                    android.util.Log.d("MainActivity", "    [✓] Polling started successfully")
                                } catch (e: Exception) {
                                    android.util.Log.e("MainActivity", "    [✗] Polling failed: ${e.message}", e)
                                }
                            }
                        }

                        // Show loading spinner with progress message
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val progressMessage = viewModel.pollingProgress.value 
                                ?: "Loading display config..."
                            
                            android.util.Log.d("MainActivity", "    [UI] Rendering loading screen: $progressMessage")
                            
                            CircularProgressIndicator()
                            Text(
                                text = progressMessage,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }

                    AppStateViewModel.AppState.ERROR -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Text(
                                text = "Error: $errorMessage",
                                color = androidx.compose.ui.graphics.Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
