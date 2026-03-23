# TV Device Behavior

**Android TV Module — Comportamiento en Tiempo de Ejecución**  
**Versión:** 1.0 — Marzo 2026  
**Estado:** Vigente

---

## 1. Propósito

Define el comportamiento esperado en tiempo de ejecución: qué hace la app al iniciar, cómo se recupera de errores, cómo maneja pérdida de conectividad, y qué expectativas existen sobre el dispositivo TV.

---

## 2. Startup Expectations (Expectativas de Inicio)

### 2.0 Pairing Startup Flow (Prerequisito)

**Antes de mostrar cualquier contenido de queue display, la TV debe estar emparejada.**

Startup sequence:

1. **Second 0:** App está iniciando, mostrar splash screen.
2. **Second 1:** Generar/recuperar `device_id` (si no existe).
3. **Second 2:** Verificar si existe `display_binding` válido en cache local.
   - ✅ Si existe y es válido (< 24h desde último refresh): ir a **2.1**.
   - ❌ Si no existe o expiró: ir a **Pairing Mode**.

**Pairing Mode (New):**

```
┌─────────────────────────────────────┐
│                                     │
│      Introduce este código           │
│            en tu dashboard:          │
│                                     │
│              123456                 │
│                                     │
│        Esperando confirmación...    │
│      (Válido por 15 minutos)        │
│                                     │
└─────────────────────────────────────┘
```

IV debe:
- Generar código de 6 dígitos.
- Mostrar código en pantalla (grande, centrado, fácil de leer).
- Poll backend cada 2 segundos: "¿Ha este código sido canjeado?"
- Si sí: fetch binding details, ir a **2.1**.
- Si código expira (15 min): generar nuevo código, mantener mostrando.
- Si hay error de red: mostrar disclaimer, continuar pooling.

```kotlin
// En ViewModel
fun initializePairingFlow() {
    val deviceId = generateOrLoadDeviceId()
    val pairingCode = generateSixDigitCode()
    
    _pairingState.value = PairingState.WaitingForPairing(
        deviceId = deviceId,
        pairingCode = pairingCode,
        codeExpiresAt = System.currentTimeMillis() + (15 * 60 * 1000)
    )
    
    // Polling loop
    viewModelScope.launch {
        while (pairingState.value is PairingState.WaitingForPairing) {
            try {
                val binding = pairingRepository.checkCodeRedeemed(deviceId, pairingCode)
                if (binding != null && isBindingValid(binding)) {
                    cachDisplayBinding(binding)
                    _pairingState.value = PairingState.Paired(...)
                    // Now proceed to queue display (section 2.1)
                    initializeQueueDisplay()
                    return@launch
                }
            } catch (e: Exception) {
                // Log pero no crash, continuar polling
            }
            
            // Check code expiration
            val currentState = pairingState.value as? PairingState.WaitingForPairing
            if (currentState != null && System.currentTimeMillis() > currentState.codeExpiresAt) {
                // Generar nuevo código
                val newCode = generateSixDigitCode()
                _pairingState.value = currentState.copy(
                    pairingCode = newCode,
                    codeExpiresAt = System.currentTimeMillis() + (15 * 60 * 1000)
                )
            }
            
            delay(2000)  // Poll every 2 seconds
        }
    }
}
```

**Important:** El código se mantiene en memoria, **nunca guarda en disco**.

---

### 2.1 Cold Start Behavior (After Pairing)

Cuando la app inicia por primera vez **después de estar emparejada**:

1. **Loading State:** Mostrar "Cargando..." durante 1-3 segundos.
2. **Solicitar state del backend:** Realizar llamada HTTP GET al endpoint de estado de cola.
3. **Si éxito:** Renderizar UI con datos.
4. **Si fallo (sin internet):**
   - Mostrar "Sin conexión".
   - Ofrecerse a reintentar automáticamente cada 5 segundos.
   - No crashear.

```kotlin
init {
    viewModelScope.launch {
        try {
            val state = queueRepository.getQueueStateInitial()
            _uiState.value = QueueDisplayUiState.Success(state)
        } catch (e: Exception) {
            _uiState.value = QueueDisplayUiState.Error(e, lastKnownState = null)
            startAutoRetry()  // Reintentar cada 5s
        }
    }
}

private fun startAutoRetry() {
    viewModelScope.launch {
        while (!isStateSuccessful()) {
            delay(5000)  // 5 segundos entre reintentos
            try {
                val state = queueRepository.getQueueState()
                _uiState.value = QueueDisplayUiState.Success(state)
            } catch (e: Exception) {
                // Continuar reintentando sin afectar UI
            }
        }
    }
}
```

### 2.2 Fullscreen Expectation

La app asume que se ejecuta en **fullscreen sin ActionBar, sin softkeyboard, sin notificaciones visuales**.

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Fullscreen
    window.decorView.systemUiVisibility = (
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_FULLSCREEN
    )
    
    setContent { /* ... */ }
}
```

### 2.3 Display Configuration After Pairing

Después de emparejamiento exitoso, la TV recupera configuración de display desde el binding:

```kotlin
// display_binding.json (cacheado localmente)
{
  "device_id": "uuid",
  "display_id": "uuid",
  "barbershop_id": "uuid",
  "display_config": {
    "display_name": "Waiting Room",
    "refresh_rate_seconds": 5,
    "theme": "dark",
    "language": "es"
  }
}

// En ViewModel
fun loadDisplayConfiguration() {
    val binding = loadDisplayBinding() ?: return
    // Usar display_config para UI theming, language, etc.
    applyDisplayConfig(binding.displayConfig)
}
```

**Regla:** La configuración de display (`display_config`) es un **snapshot en tiempo de binding**. Cambios posteriores en backend requieren **refresh de binding** (cada 24h automáticamente, o manual si se solicita).

---

## 3. Reconnection Behavior (Comportamiento de Reconexión)

### 3.0 Binding Refresh & Validation

**Regla:** La TV debe refrescar su binding cada **24 horas** para validar que sigue siendo válido.

```kotlin
// En ViewModel
private fun scheduleBindingRefresh() {
    val lastRefreshTime = loadLastBindingRefreshTime()
    val ageHours = (System.currentTimeMillis() - lastRefreshTime) / (60 * 60 * 1000)
    
    if (ageHours > 24) {
        viewModelScope.launch {
            try {
                val binding = pairingRepository.fetchBinding(deviceId)
                if (binding != null && isBindingValid(binding)) {
                    cachDisplayBinding(binding)
                    saveLastBindingRefreshTime(System.currentTimeMillis())
                } else {
                    // Binding revoked or invalid
                    _pairingState.value = PairingState.Unpaired
                    clearDisplayBinding()
                }
            } catch (e: Exception) {
                // Network error, continuar sin refrescar (mantener binding viejo)
                // Será reintentado en próximo poll
            }
        }
    }
}
```

Si binding es revocado (por staff en dashboard):
- TV detecta en próximo refresh: binding status = "revoked".
- Limpia binding local, transiciona a `PairingState.Unpaired`.
- Muestra pairing screen nuevamente.

### 3.1 Network Loss Detection

Implementar listener de conectividad para detectar pérdida de red:

```kotlin
val connectivityManager = getSystemService(ConnectivityManager::class.java)

val request = NetworkRequest.Builder()
    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    .build()

val callback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
        viewModel.onNetworkAvailable()  // Reconectar
    }
    
    override fun onLost(network: Network) {
        viewModel.onNetworkLost()  // Marcar offline
    }
}

connectivityManager.registerNetworkCallback(request, callback)
```

### 3.2 Reconnection Strategy

Cuando se detecta que la red está disponible nuevamente:

1. Marcar `isOnline = false` inmediatamente.
2. Solicitar nuevo state del backend.
3. Si éxito: actualizar UI con nuevo state, marcar `isOnline = true`.
4. Si fallo: volver a `isOnline = false`, reintentar cada 5 segundos.
5. Máximo 5 reintentos antes de mostrar error persistente.

```kotlin
suspend fun onNetworkAvailable() {
    repeat(5) { attempt ->
        try {
            val freshState = queueRepository.getQueueState()
            _uiState.value = QueueDisplayUiState.Success(freshState)
            return  // Éxito
        } catch (e: Exception) {
            if (attempt == 4) {  // Último intento
                _uiState.value = QueueDisplayUiState.Error(
                    e,
                    lastKnownState = currentSuccessState
                )
            }
            delay(2000)  // Esperar 2s antes del siguiente intento
        }
    }
}
```

### 3.3 Graceful Offline State

Mientras está offline:
- Mostrar último state conocido.
- Agregar banner: "Modo offline — Último actualizado: HH:mm".
- No mostrar datos animados o "en tiempo real".
- Desactivar timer de auto-refresh.

```kotlin
@Composable
fun QueueDisplayScreen(state: QueueDisplayState) {
    Box {
        QueueContent(state)
        
        if (!state.isOnline) {
            OfflineBanner(lastUpdateTime = state.lastUpdateTime)
        }
    }
}
```

---

## 4. Fallback UI on Failures (UI de Fallback)

### 4.1 Network Timeout (> 10 segundos)

```
┌─────────────────────────────────────┐
│        ⚠️ SIN CONEXIÓN              │
│                                     │
│   Verifying connection...           │
│   Intentando en 5 segundos          │
│                                     │
│   Último estado: 15:42:30           │
└─────────────────────────────────────┘
```

### 4.2 Invalid State from Backend

```
┌─────────────────────────────────────┐
│        ⚠️ ERROR DE DATOS             │
│                                     │
│   El servidor envió datos           │
│   inválidos. Reintentar...          │
│                                     │
│   Error: [exception message]        │
└─────────────────────────────────────┘
```

### 4.3 Barbershop Not Found (404)

```
┌─────────────────────────────────────┐
│    ⚠️ BARBERÍA NO ENCONTRADA        │
│                                     │
│   ID: [barbershop_id]               │
│   Verificar configuración del       │
│   dispositivo.                      │
└─────────────────────────────────────┘
```

### 4.4 Authentication Failed (401)

```
┌─────────────────────────────────────┐
│    ⚠️ AUTENTICACIÓN FALLIDA         │
│                                     │
│   El dispositivo no está            │
│   autorizado. Contactar admin.      │
└─────────────────────────────────────┘
```

---

## 5. Recovery After Restart or Power Loss

### 5.1 Quick Resume (< 30 segundos)

Después de un crash o power loss:

1. **Second 0-2:** App abre, muestra Loading.
2. **Second 2-5:** Cargar último state de caché local (si existe).
3. **Second 5-10:** Solicitar state fresco del backend.
4. **Second 10+:** Si no hay respuesta, mostrar último cached state con banner offline.
5. **Target:** UI visible en < 10 segundos.

### 5.2 Cache Strategy (Local Persistence)

Solo cachear lo **mínimo necesario**:

```kotlin
// Persistencia permitida:
// 1. Last successful QueueDisplayState (archivo)
// 2. Device configuration (SharedPreferences)

// Persistencia PROHIBIDA:
// ❌ Historial de tickets
// ❌ Datos de usuario
// ❌ Credenciales
// ❌ Información de barbería completa
```

Implementación:

```kotlin
// Guardar state exitoso
suspend fun cacheQueueState(state: QueueDisplayState) {
    val json = Json.encodeToString(state)
    File(context.cacheDir, "queue_state.json").writeText(json)
}

// Restaurar en startup
private suspend fun getLastKnownState(): QueueDisplayState? {
    return try {
        val json = File(context.cacheDir, "queue_state.json").readText()
        Json.decodeFromString(json)
    } catch (e: Exception) {
        null  // No hay caché válido
    }
}
```

### 5.3 No Sticky Data

**Regla:** Después de que el estado se actualice desde el backend, **sobrescribir** cualquier caché local. No mantener datos "pegados" de actualizaciones anteriores.

```kotlin
// ✅ CORRECTO: Sobrescribir caché
suspend fun onStateReceived(newState: QueueDisplayState) {
    cacheQueueState(newState)  // Sobrescribir archivo
    _uiState.value = QueueDisplayUiState.Success(newState)
}
```

---

## 6. Fullscreen Requirements

### 6.1 No Status Bar, No Navigation Bar

La app debe ejecutarse en **modo fullscreen verdadero**:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE  // TV landscape
    
    window.decorView.systemUiVisibility = (
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    )
    
    setContent { /* ... */ }
}
```

### 6.2 Screen Timeout

Por defecto, **no apagar pantalla de TV**:

```kotlin
window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
```

Si se desea timeout configurable:
- Agregar a SharedPreferences: `screen_timeout_minutes`.
- Usar `PowerManager.WakeLock` con timeout.

---

## 7. Update Responsiveness (Reactividad a Cambios)

### 7.1 Maximum Update Interval

**Regla:** La pantalla debe actualizarse desde el backend **cada ≤ 5 segundos**.

**Implementación:**
- Usar WebSocket o HTTP polling cada 5s.
- Alternativamente, usar Supabase realtime subscriptions.

```kotlin
// Opción 1: Polling HTTP cada 5 segundos
init {
    viewModelScope.launch {
        withTimeoutOrNull(Long.MAX_VALUE) {
            while (true) {
                try {
                    val state = queueRepository.getQueueState()
                    _uiState.value = QueueDisplayUiState.Success(state)
                } catch (e: Exception) {
                    // Log pero no crash
                }
                delay(5000)
            }
        }
    }
}

// Opción 2: Supabase realtime subscriptions
init {
    viewModelScope.launch {
        queueRepository.subscribeToQueueUpdates()
            .collect { state ->
                _uiState.value = QueueDisplayUiState.Success(state)
            }
    }
}
```

### 7.2 Debounce Updates (Opcional)

Si las actualizaciones llegan muy frecuentemente (< 500ms), debounce:

```kotlin
private val updateDebounce = MutableSharedFlow<QueueDisplayState>(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)

init {
    viewModelScope.launch {
        updateDebounce
            .debounce(500)  // Máximo cada 500ms
            .collect { state ->
                _uiState.value = QueueDisplayUiState.Success(state)
            }
    }
}
```

---

## 8. Minimal Local Persistence Only

### 8.1 Permitido

- **Last successful QueueDisplayState** (para recuperación en caso de crash).
- **Device configuration:**
  - `barbershop_id`
  - `display_brightness`
  - `auto_refresh_interval`
  - `timezone`

### 8.2 Prohibido

- ❌ Historial de tickets (nunca).
- ❌ Información de clientes (nombres, contactos).
- ❌ Credenciales o API keys (usar Google Account o Device ID).
- ❌ Logs detallados con datos sensibles.

### 8.3 Storage Location

- Use `context.cacheDir` para datos temporales (auto-limpiable por el sistema).
- Use `SharedPreferences (encrypted)` para configuración pequeña.
- Se recomienda `EncryptedSharedPreferences` si almacenas cualquier configuración sensible.

```kotlin
// ✅ CORRECTO: Cache temporal
File(context.cacheDir, "queue_state.json")

// ✅ CORRECTO: Configuración pequena
EncryptedSharedPreferences
    .create("tv_config", context, ...)
    .edit()
    .putString("barbershop_id", "uuid")
    .apply()

// ❌ PROHIBIDO: Almacenamiento de datos detallados
File(context.filesDir, "all_customer_history.json")  // NO
```

---

## 9. Crash Handling and Logging

### 9.1 Crash Handler

Implementar global exception handler:

```kotlin
Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
    // Loguear (no incluya datos sensibles)
    logError("Uncaught exception in ${thread.name}", exception)
    
    // Esperar 2s para flush de logs
    Thread.sleep(2000)
    
    // Restart app limpio
    val intent = Intent(context, MainActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    
    System.exit(1)
}
```

### 9.2 Logging Policy

- ✅ Loguear errores, timestamps, estado de conexión.
- ❌ Nunca loguear nombres de clientes, servicios, o datos sensibles.
- ❌ Nunca loguear credenciales.

```kotlin
// ✅ CORRECTO
Log.e("QueueVM", "Network error fetching queue: ${exception::class.simpleName}")

// ❌ INCORRECTO
Log.e("QueueVM", "Failed to fetch: client=${ticket.customerName}, service=${ticket.serviceName}")
```

---

## 10. Enforcement

- **Device Testing:** Validar en TV real (Roku, Fire TV, Android TV emulator) al menos trimestral.
- **Network Simulation:** Testear offline behavior usando Android Studio Network Profile.
- **Power Loss Tests:** Simular crash y verificar recuperación en < 30s.
- **Monitoring:** Loguear uptime y crash rates en analytics.

---

**Versión:** 1.0 — Marzo 2026  
**Próxima revisión:** Septiembre 2026
