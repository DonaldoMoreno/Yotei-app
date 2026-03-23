# TV Non-Functional Requirements

**Android TV Module — Requisitos No Funcionales**  
**Versión:** 1.0 — Marzo 2026  
**Estado:** Vigente

---

## 1. Propósito

Define los requisitos no funcionales **obligatorios** para el módulo Android TV: rendimiento, estabilidad, consumo de memoria, seguridad, y confiabilidad. Estos son límites duros, no recomendaciones.

---

## 2. Performance Requirements

### 2.1 Update Latency

| Operación | Máximo Permitido | Meta de Diseño |
|-----------|------------------|-------------------|
| **Recibir estado backend → Renderizar en pantalla** | 2 segundos | 1 segundo |
| **Navegación con Dpad (si existe) → Focus change visual** | 200ms | 100ms |
| **Scroll en lista (si existe) → Scroll smooth** | No drop < 30fps | 60fps target |
| **Reconectar a red → Solicitar state** | 3 segundos | 1 segundo |

### 2.2 Memory Constraints

| Límite | Máximo Permitido | Ideal |
|--------|------------------|-------|
| **Consumo base en memoria RAM** | 200MB | 100MB |
| **Consumo en pico (durante update) | 250MB | 150MB |
| **Heap allocation en update** | < 50MB | < 10MB |

**Target devices:** Roku, Amazon Fire TV, Android TV emulator (1-2GB RAM disponible para apps).

### 2.3 Battery/Power (Si es dispositivo móvil de respaldo)

- CPU: < 5% en reposo (idle).
- GPU: < 10% durante renderizado normal.
- Screen: Mantener encendida (FLAG_KEEP_SCREEN_ON).

### 2.4 Network Bandwidth

- **Llamada getQueueState():** < 50KB payload.
- **Política:** Si payload > 100KB, falso recurso, loguear y reportar.
- **Polling cada 5s:** ~600KB/hora = negligible.
- **Realtime (WebSocket):** < 10KB/segundo medio.

---

## 3. Stability Requirements

### 3.1 Uptime Target

**Regla:** App debe estar disponible y funcional > **99%** del tiempo operativo.

- Máximo 0.01% downtime = ~86 segundos al mes.
- Máximo 1 crash por 100 horas de ejecución.

### 3.2 Crash Rate

| Métrica | Máximo Permitido |
|---------|------------------|
| Crashes sin manejadora: | 1 por 100 horas (0.01%) |
| Crashes manejadas (recuperables): | 1 por 10 horas (0.1%) |
| ANRs (Application Not Responding): | < 1 per semana |

### 3.3 No Silent Failures

**Regla:** Si algo falla, la UI **debe indicarlo visualmente** al usuario (aunque sea con "⚠️ Error").

```kotlin
// ❌ PROHIBIDO: Silent failure (app aparece normal pero estancada)
viewModelScope.launch {
    try {
        queueRepository.getQueueState()
    } catch (e: Exception) {
        // ← Sin hacer nada, UI muestra state viejo sin aviso
    }
}

// ✅ CORRECTO: Indicar error
viewModelScope.launch {
    try {
        queueRepository.getQueueState()
    } catch (e: Exception) {
        _uiState.value = QueueDisplayUiState.Error(e, lastKnownState)
        // ← UI muestra estado de error claramente
    }
}
```

---

## 4. Startup & Initialization

### 4.1 Time to Interactive (TTI)

**Regla:** La pantalla debe estar completamente visible y interactiva en < **10 segundos** desde el tap de launch.

| Fase | Máximo | Target |
|------|--------|--------|
| App launch → First frame | 2s | 1s |
| First frame → Network request sent | 1s | 0.5s |
| Network request → Response received | 5s | 2s |
| Response → UI rendered | 2s | 0.5s |
| **Total TTI** | **10s** | **4s** |

### 4.2 Cold Start Fallback

Si el network request está tardando > 5 segundos:
- Mostrar "Cargando..." con última data en caché (si existe).
- No bloquear UI esperando network.

```kotlin
private suspend fun getShowableState(): QueueDisplayState? {
    return withTimeoutOrNull(5000) {
        queueRepository.getQueueState()
    } ?: getLastCachedState()
}
```

---

## 4.3 Pairing Latency (New)

**Pairing es un requerimiento previo obligatorio.**

| Operación | Máximo Permitido | Meta de Diseño |
|-----------|------------------|-------------------|
| **Generar device_id + pairing code** | 1s | <500ms |
| **Mostrar pairing screen** | 1s | <500ms |
| **Poll backend: "¿código canjeado?" (primera vez)** | 2s | <1s |
| **Redimir código en dashboard** | 5s | <2s |
| **Fetch binding details después de redux** | 5s | <2s |
| **Transicionar a paired + mostrar queue display** | 2s | <1s |
| **Total pairing cycle (generation → paired ready)** | **15 minutos** | **<2 minutos** |

**Pairing Success Rate:**
- Meta: > 99.5% de códigos canjeados exitosamente.
- Si < 95% tasa de éxito 7 dias, investigar (network, backend issues, UX friction).

**Code Expiration:**
- Pairing code expira en **15 minutos**.
- Si cliente no canjea en 15 min: generar nuevo código automáticamente, mostrar en pantalla.
- No aumentar tiempo de expiración (seguridad).

**Polling Interval:**
- TV poll backend cada **2 segundos** mientras está esperando pairing.
- No más frecuente (innecesario, consume batería).
- Not menos frecuente (experiencia lenta).

---

## 5. Responsiveness & Reactivity

### 5.1 No Framedrops on Update

Cuando llega un estado nuevo del backend:
- UI debe recomposhar y renderizar sin drops de frame.
- Target: 60fps durante recomposition.

```kotlin
// ✅ CORRECTO: Smooth update
_uiState.value = QueueDisplayUiState.Success(newState)
// Compose recompose en < 16ms (60fps)

// ❌ INCORRECTO: Bloquear durante actualización
val heavyCalculation = processQueueState(newState)  // < en el hilo principal
_uiState.value = QueueDisplayUiState.Success(heavyCalculation)
```

### 5.2 UI is Always Responsive

Incluso si el backend está lento:
- Boton "Reintentar" responde < 200ms.
- Dpad navigation responde < 200ms.
- Página nunca se "congela" (ANR).

**Usar coroutinas para trabajo pesado:**

```kotlin
// ✅ CORRECTO: Trabajo en background
viewModelScope.launch(Dispatchers.Default) {
    val processed = expensiveCalculation()
    withContext(Dispatchers.Main) {
        _uiState.value = QueueDisplayUiState.Success(processed)
    }
}
```

### 5.3 Maximum Stale Data Age

**Regla:** Si no recibe actualización en **> 5 minutos**, mostrar warning.

```kotlin
// En ViewModel
private fun checkDataFreshness() {
    val age = System.currentTimeMillis() - state.lastUpdateTime
    if (age > 300_000) {  // 5 minutos
        _showStaleDataWarning.value = true
    }
}
```

---

## 6. Low Memory Handling

### 6.1 Memory Leak Protection

**Regla:** No permitir memory leaks detectables en testing.

```kotlin
// ❌ PROHIBIDO: Memory leak (Activity reference hold)
class BadViewModel {
    private val activityRef: Activity = activity  // ← Leaks activity
}

// ✅ CORRECTO: WeakReference o no guardar Activity
class GoodViewModel {
    // Solo guardar datos, no referencias largas del Activity
}
```

### 6.2 Collection Limits

- `nextTickets`: máximo 20 tickets en memoria.
- Si backend envía > 20, mostrar primeros 5-10 y descartar el resto.

```kotlin
// ✅ CORRECTO: Limitar tamaño
val displayTickets = state.nextTickets.take(5)
```

### 6.3 Bitmap/Image Constraints

- No cargar imágenes de barbería (si las hay) en caché > 1MB.
- Use thumbnail size (máximo 200x200px).
- Comprimir a webp si es posible.

---

## 7. Error Resilience

### 7.1 Graceful Degradation

Si algo falla (red, estado corrupto), aplicación debe **seguir siendo usable**:

- ✅ Sin red: mostrar último estado conocido.
- ✅ Estado corrupto: mostrar error y último estado válido.
- ✅ Auth fallida: mostrar error con instrucciones.
- ✅ Barbershop no encontrada: mostrar error persistente (sin retry infinito).

### 7.2 Retry Strategy

| Situación | Reintentos | Tipo |
|-----------|------------|------|
| **Network timeout** | 5x (con backoff) | Automático cada 5s |
| **500 error del servidor** | 3x (con backoff) | Automático cada 10s |
| **401 Auth failed** | 1x | Manual (usar instrucciones) |
| **404 Not found** | 0x | Error permanente |

```kotlin
// ✅ CORRECTO: Exponential backoff
private suspend fun retryWithBackoff(maxRetries: Int): QueueDisplayState {
    repeat(maxRetries) { attempt ->
        try {
            return queueRepository.getQueueState()
        } catch (e: Exception) {
            if (attempt == maxRetries - 1) throw e
            delay(1000L * (2 pow attempt))  // 1s, 2s, 4s, 8s...
        }
    }
}
```

### 7.3 No Retry Cascades

**Regla:** Si todos los reintentos fallan, **parar** (no retry infinito).

```kotlin
// ❌ PROHIBIDO: Retry infinito
while (true) {  // ← Infinito
    try { getQueueState() }
    catch (e: Exception) { delay(1000) }
}

// ✅ CORRECTO: Límite de reintentos + fallback
repeat(5) {  // ← Máximo 5
    try { return getQueueState() }
    catch (e: Exception) { delay(1000) }
}
throw Exception("Max retries exceeded")
```

---

## 8. Offline & Last-Known-State Behavior

### 8.1 Offline Fallback

Si no hay internet:
1. Mostrar último estado válido en caché.
2. Agregar banner: "Modo offline — Última actualización: HH:mm".
3. Deshabilitar boton "Reintentar" si no hay mejora en 2 minutos.

### 8.2 Cache Validity

El último estado cacheado es válido por:
- **Hasta 24 horas:** si es estado conocido válido.
- **Nunca:** si hay razones para creer que es stale (ej. timestamp del ticket es demasiado antiguo).

```kotlin
// ✅ CORRECTO: Validar edad de caché
fun isLastStateFresh(): Boolean {
    val ageInMinutes = (System.currentTimeMillis() - cachedState.lastUpdateTime) / 60_000
    return ageInMinutes < (24 * 60)  // < 24 horas
}
```

### 8.3 Automatic Sync on Online

Cuando se recupera conexión, **no reutilizar caché antiguo**:

```kotlin
// ✅ CORRECTO: Sincronizar cuando online
fun onNetworkRecovered() {
    viewModelScope.launch {
        try {
            val freshState = queueRepository.getQueueState()  // Freshly fetched
            _uiState.value = QueueDisplayUiState.Success(freshState)
        } catch (e: Exception) {
            // Fallback de todos formas
        }
    }
}
```

---

## 9. Security Requirements

### 9.1 No Sensitive Data Local Storage

Prohibido almacenar localmente:
- ❌ Nombres de clientes completos (si no son minimales).
- ❌ Números de teléfono.
- ❌ Addresses completas de clientes.
- ❌ API keys.
- ❌ Session tokens (excepto en SharedPreferences encriptado, temporal).

### 9.2 HTTPS Only

**Regla:** Todas las comunicaciones backend MUST ser HTTPS.

```kotlin
// ✅ CORRECTO
retrofit = Retrofit.Builder()
    .baseUrl("https://api.yottei.com/")  // ← HTTPS
    .build()

// ❌ PROHIBIDO
.baseUrl("http://api.yottei.com/")  // ← HTTP (inseguro)
```

### 9.3 Authentication

- Usar Android AccountManager o OAuth para credenciales.
- No guardar plaintext tokens.
- Renovar token si está próximo expiración.

```kotlin
// ✅ CORRECTO: Token manejado por AuthManager
val token = authManager.getValidToken()  // Refrescado automáticamente
```

### 9.4 Logging Security

No loguear variables que contengan datos sensibles:

```kotlin
// ❌ PROHIBIDO
Log.d("API", "Fetching queue for client: ${client.phone}")

// ✅ CORRECTO
Log.d("API", "Fetching queue for barbershop: ${barbershopId}")
```

---

## 10. Update Responsiveness

### 10.1 Máxima latencia estado → pantalla: 2 segundos

Cuando el backend envía nuevo estado:

1. Data arrives.
2. Validar estado.
3. Actualizar ViewModel state.
4. Recompose Composables.
5. Frame rendered en pantalla.

**Total:** < 2 segundos (target 1 segundo).

### 10.2 Polling Frequency

Si usan HTTP polling:
- **Mínimo:** 5 segundos.
- **Máximo:** 10 segundos.

No más frecuente (consume batería, red), no menos frecuente (experiencia stale).

### 10.3 Realtime Subscriptions (Realtime alternativo)

Si usan WebSocket/Supabase realtime:
- Actualizar pantalla < 500ms de recibir mensaje.
- Debounce updates si llegan > 1 por segundo.

---

## 11. Monitoring & Observability

### 11.1 Key Metrics to Track

- **Uptime:** % tiempo app funcional (meta: > 99%).
- **Crash rate:** crashes/1M executions (meta: < 1).
- **TTI (Time to Interactive):** median time from launch (meta: < 4s).
- **Update latency:** backend state → rendered (median, meta: < 1s).
- **Memory usage:** pico RAM consumption (meta: < 150MB).

### 11.2 Logging

Estructura mínima:
```
[timestamp] [level] [tag] message
```

Máximo log file size: **10MB** (rotate after).

**No incluir:** nombres, números, servicios detallados.

---

## 12. Testing Requirements

### 12.1 Unit Tests

Mínima cobertura: **80%** de ViewModels y data layers.

### 12.2 Integration Tests

- Simulate network failures (offline, timeout, errors).
- Validate state transitions.
- Verify UI rendering for different states.

### 12.3 E2E Tests (Manual)

- Startup test (TTI measurement).
- Offline test (last known state + reconnect).
- Crash recovery test (kill process, restart).
- Memory test (monitor RAM during 1 hour uptime).

---

## 13. Enforcement

- **Performance baseline:** Medir en cold start e incluir en CI.
- **Memory profiling:** Jerarquía de memoria cada sprint.
- **Crash reporting:** Integrar Firebase Crashlytics o similar.
- **Monitoring:** Dashboard público de uptime y métricas clave.

---

**Versión:** 1.0 — Marzo 2026  
**Próxima revisión:** Septiembre 2026
