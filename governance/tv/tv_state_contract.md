# TV State Contract

**Android TV Module — Contrato de Renderizado y Estado**  
**Versión:** 1.0 — Marzo 2026  
**Estado:** Vigente

---

## 1. Propósito

Define el contrato **invariable** entre la capa ViewModel (state source) y la capa UI (renderer). Especifica qué estructura de estado debe existir, qué campos son obligatorios, cómo manejar valores nulos, y cuáles son las garantías que la UI puede asumir.

---

## 2. QueueDisplayState (Core State Structure)

Esta es la **estructura de estado única** que la TV renderiza. Proviene directamente del backend (Supabase) vía Repository.

```kotlin
sealed class QueueDisplayUiState {
    object Loading : QueueDisplayUiState()
    
    data class Success(
        val state: QueueDisplayState
    ) : QueueDisplayUiState()
    
    data class Error(
        val exception: Throwable,
        val lastKnownState: QueueDisplayState?  // fallback
    ) : QueueDisplayUiState()
}

data class QueueDisplayState(
    val barbershop: Barbershop,           // ← REQUERIDO
    val currentTicket: QueueTicket?,      // ← NULLABLE (si no hay cliente siendo atendido)
    val nextTickets: List<QueueTicket>,   // ← REQUERIDO (puede estar vacío)
    val queueStats: QueueStats,           // ← REQUERIDO
    val serverTime: Long,                 // ← REQUERIDO (timestamp en ms del backend)
    val isOnline: Boolean,                // ← REQUERIDO (conexión activa)
    val lastUpdateTime: Long              // ← REQUERIDO (cuándo llegó este state)
)

data class QueueStats(
    val totalInQueue: Int,                // ← REQUERIDO (≥0)
    val estimatedWaitMinutes: Int,        // ← REQUERIDO (≥0)
    val barbershopStatus: BarbershopStatus // ← REQUERIDO
)
```

---

## 3. QueueTicket (Required Fields)

Todos los campos de `QueueTicket` que se renderizen **deben estar presentes y válidos**. El backend NUNCA envía un QueueTicket incompleto.

```kotlin
data class QueueTicket(
    val id: String,                       // ← REQUERIDO: UUID único
    val ticketNumber: String,             // ← REQUERIDO: "042", "T-123", etc.
    val status: QueueTicketStatus,        // ← REQUERIDO: enum (WAITING, READY, etc.)
    val customerName: String,             // ← REQUERIDO: al menos 1 caracter
    val serviceName: String,              // ← REQUERIDO: "Corte Clásico", etc.
    val serviceMinutes: Int,              // ← REQUERIDO: duración estimada (≥5)
    val queuePosition: Int,               // ← NULLABLE: posición en fila (null si no en fila)
    val estimatedWaitMinutes: Int?,       // ← NULLABLE: ETA calculado por backend (null si no aplica)
    val checkedInAt: Long?,               // ← NULLABLE: timestamp de check-in (null si no registrado)
    val barbershopId: String              // ← REQUERIDO: relación a barbershop
)
```

---

## 4. Barbershop (Required Fields)

```kotlin
data class Barbershop(
    val id: String,                       // ← REQUERIDO: UUID
    val name: String,                     // ← REQUERIDO: "Yottei Barbershop", etc.
    val address: String,                  // ← REQUERIDO: "Calle Principal 123, CDMX"
    val status: BarbershopStatus,         // ← REQUERIDO: OPEN, CLOSED, BREAK
    val phoneNumber: String?,             // ← NULLABLE
    val openingHours: String?             // ← NULLABLE
)

enum class BarbershopStatus {
    OPEN,            // Barbería operativa
    CLOSED,          // Barbería cerrada (fuera de horario)
    BREAK,           // Barbería en descanso (dentro de horario)
    MAINTENANCE      // Mantenimiento (raro)
}
```

---

## 5. PairingState (Pairing Contract)

El estado de emparejamiento es **previo y prerrequisito** para renderizar contenido de display (QueueDisplayState).

```kotlin
sealed class PairingState {
    object Unpaired : PairingState()
    
    data class WaitingForPairing(
        val deviceId: String,                 // ← REQUERIDO: UUID del device
        val pairingCode: String,              // ← REQUERIDO: 6 dígitos actuales
        val codeExpiresAt: Long               // ← REQUERIDO: timestamp de expiración
    ) : PairingState()
    
    data class Paired(
        val deviceId: String,                 // ← REQUERIDO: UUID del device
        val displayId: String,                // ← REQUERIDO: UUID del display vinculado
        val barbershopId: String,             // ← REQUERIDO: UUID de barbería
        val displayConfig: JsonObject?,       // ← NULLABLE: configuración del display (snapshot)
        val bindingValidUntil: Long           // ← REQUERIDO: cuándo refrescar binding (24h)
    ) : PairingState()
    
    data class PairingError(
        val reason: PairingErrorReason,      // ← REQUERIDO: tipo de error
        val message: String,                 // ← REQUERIDO: descripción legible
        val retryCount: Int,                 // ← REQUERIDO: intentos realizados
        val lastAttemptTime: Long            // ← REQUERIDO: timestamp del último intento
    ) : PairingState()
}

enum class PairingErrorReason {
    CODE_EXPIRED,               // Código expiró antes de ser canjeado
    CODE_INVALID,               // Código no válido (no existe o ya fue canjeado)
    DEVICE_NOT_FOUND,           // Device no encontrado en backend después de code redeem
    BINDING_CREATION_FAILED,    // Fallo al crear binding en backend
    BINDING_FETCH_FAILED,       // Fallo al obtener binding después de pairing
    NETWORK_ERROR,              // Error de conexión sin especificar
    UNKNOWN                     // Error desconocido
}
```

**Reglas de contrato de pairing:**
- ✅ Solo **una** de las ramas (`Unpaired`, `WaitingForPairing`, `Paired`, `PairingError`) es activa a la vez.
- ✅ El código de pairing (6 dígitos) se genera **en memoria**, nunca persiste en disco.
- ✅ El `displayId` y `displayConfig` se almacenan en disco **solo cuando** en estado `Paired`.
- ✅ `retryCount` en `PairingError` tiene máximo 3 — después saltar a error permanente.
- ✅ `displayConfig` es un snapshot — cambios posteriores en backend requieren refresh.

---

## 6. Null Handling (Manejo de Nulls)

### 6.1 Regla de Oro

**"Nunca inventar estado faltante. Nunca asumir valores por defecto."**

Si el backend **no envía** un valor, la UI **debe fallar explícitamente o usar un placeholder visual** — nunca asumir un valor.

### 6.2 Null Handling Policy

| Campo | Nullable | Si es null | Acción UI |
|-------|----------|-----------|-----------|
| `barbershop` | ❌ NO | N/A | Fallar con error (invariante rota) |
| `currentTicket` | ✅ SÍ | No hay cliente siendo atendido | Mostrar sección vacía: "Sin cliente en atención" |
| `nextTickets` | ❌ NO | Fila vacía | Mostrar lista vacía (UI valida) |
| `queueStats` | ❌ NO | N/A | Fallar con error |
| `estimatedWaitMinutes` (de ticket) | ✅ SÍ | ETA no calculado aún | Mostrar "—" o "Calculando..." |
| `queuePosition` | ✅ SÍ | Cliente no en fila | Mostrar "Fuera de fila" |
| `checkedInAt` | ✅ SÍ | Cliente no ha hecho check-in | Mostrar estado sin timestamp |

### 6.3 Code Examples

```kotlin
// ✅ CORRECTO: Manejar null explícitamente
@Composable
fun CurrentTicketDisplay(ticket: QueueTicket?) {
    if (ticket == null) {
        // Mostrar placeholder visual
        Text("No hay cliente en atención", fontSize = 24.sp)
    } else {
        // Renderizar ticket
        Text(ticket.ticketNumber, fontSize = 72.sp)
    }
}

// ❌ INCORRECTO: Asumir valor por defecto
@Composable
fun CurrentTicketDisplay(ticket: QueueTicket?) {
    val name = ticket?.customerName ?: "Cliente"  // ← No inventar
    Text(name)
}

// ✅ CORRECTO: Mostrar "—" para ETA faltante
Text(
    text = ticket.estimatedWaitMinutes?.let { "$it min" } ?: "—",
    fontSize = 28.sp
)
```

---

## 7. Rendering Guarantee (Garantías de Renderizado)

### 7.1 "Same State, Same UI"

**Invariante:** Para el mismo `QueueDisplayState`, la UI siempre produce el mismo resultado visual (determinístico).

```kotlin
// ✅ GARANTIZADO: Mismo state → Mismo UI
val state1 = QueueDisplayState(...)
val ui1 = renderUI(state1)

val state2 = state1.copy()  // Clona el state
val ui2 = renderUI(state2)

// ui1 == ui2 (mismo tree, misma layout)
```

### 7.2 Immutable State in UI

El `QueueDisplayState` pasado a Composables es **immutable**. La UI nunca lo modifica.

```kotlin
// ❌ PROHIBIDO: Modificar state en UI
@Composable
fun QueueScreen(state: QueueDisplayState) {
    state.currentTicket?.customerName = "New Name"  // ← NO
}

// ✅ PERMITIDO: Leerlo, nunca modificarlo
@Composable
fun QueueScreen(state: QueueDisplayState) {
    val name = state.currentTicket?.customerName
}
```

### 7.3 No Derived State in Composables

La UI **nunca calcula o deriva estado de negocio**.

```kotlin
// ❌ PROHIBIDO: Derivar lógica en Composable
@Composable
fun TicketCard(ticket: QueueTicket) {
    val isEligible = ticket.status == "READY" && ticket.checkedInAt != null
    if (isEligible) { /* render */ }  // ← Es decisión del backend
}

// ✅ CORRECTO: Derivación en ViewModel
// ViewModel define: val isEligible = state.currentTicket?.status == READY
// UI simplemente renderiza: if (isHighlighted) { /* */ }
```

---

## 8. State Contract Rules (Reglas de Contrato)

### Regla 1: Backend Owns Truth

El backend es la **única fuente de verdad** de:
- Orden de fila.
- Elegibilidad de cliente.
- ETA calculada.
- Estado de ticket (WAITING, READY, IN_SERVICE, etc.).

La TV **nunca** calcula, modifica, o anticipa cambios de estos valores.

### Regla 2: UI Must Never Invent State

Si un valor está faltando:
- ❌ NO: inventar un valor por defecto (`?? "default"`).
- ❌ NO: calcular un valor derivado (ETA local).
- ✅ SÍ: mostrar placeholder visual ("—", "Cargando...", sección vacía).

### Regla 3: Null Tolerance

Ciertos campos **pueden ser null** (como `currentTicket`). Esto es válido y la UI debe manejar elegantemente.

```kotlin
// ✅ CORRECTO: Null es un estado válido
val currentTicket: QueueTicket? = null  // Válido, significa "no hay cliente en atención"

// ❌ INCORRECTO: Asumir que siempre existe
val currentTicket: QueueTicket = state.currentTicket!!  // Crash si null
```

### Regla 4: Fallback to Last Known State on Error

Si la conexión falla, **usar el último estado exitoso** (cacheado en ViewModel).

```kotlin
// ✅ CORRECTO: Error + fallback
data class Error(
    val exception: Throwable,
    val lastKnownState: QueueDisplayState?  // Usar si está disponible
)

// En UI
when (uiState) {
    is QueueDisplayUiState.Error -> {
        if (uiState.lastKnownState != null) {
            QueueDisplayScreen(uiState.lastKnownState)  // Mostrar último estado
        } else {
            ErrorScreen(uiState.exception)               // O mostrar error
        }
    }
}
```

### Regla 5: isOnline Flag Matters

El campo `isOnline` indica si la conexión es activa.

- Si `isOnline = true`: mostrar datos con confianza.
- Si `isOnline = false`: mostrar datos con disclaimer "Último estado conocido: HH:mm".

```kotlin
@Composable
fun QueueDisplayScreen(state: QueueDisplayState) {
    Column {
        QueueDisplayContent(state)
        
        if (!state.isOnline) {
            DisclaimerBanner(
                text = "Sin conexión. Último actualizado: ${formatTime(state.lastUpdateTime)}"
            )
        }
    }
}
```

### Regla 6: Empty vs Null

- **Empty list** (`nextTickets = []`): fila sin otros clientes. Válido.
- **Null list**: nunca, `nextTickets` siempre es una lista (posiblemente vacía).
- **Null ticket** (`currentTicket = null`): no hay cliente siendo atendido. Válido.

---

## 9. State Validation (Validación de Estado)

Antes de renderizar, el ViewModel debe validar que el state es consistente:

```kotlin
private fun isStateValid(state: QueueDisplayState): Boolean {
    return state.barbershop.id.isNotBlank() &&
           state.nextTickets.all { it.id.isNotBlank() } &&
           state.queueStats.totalInQueue >= 0 &&
           state.lastUpdateTime > 0 &&
           state.serverTime > 0
}

// En ViewModel
private fun onStateReceived(state: QueueDisplayState) {
    if (isStateValid(state)) {
        _uiState.value = QueueDisplayUiState.Success(state)
    } else {
        _uiState.value = QueueDisplayUiState.Error(
            exception = IllegalStateException("Invalid state received"),
            lastKnownState = lastValidState
        )
    }
}
```

---

## 10. Backward Compatibility

**Regla:** Si el backend agrega nuevos campos a `QueueDisplayState`:
- Si son opcionales (nullable): UI funciona sin cambios.
- Si son obligatorios: cambio de versión de API, UI debe soportar ambas versiones transitoriamente.

**Regla:** Si el backend cambia enums (ej. nuevo `QueueTicketStatus`):
- UI debe tener fallback para valores desconocidos.

```kotlin
// ✅ CORRECTO: Fallback para status desconocido
val statusLabel = when (ticket.status) {
    QueueTicketStatus.WAITING -> "Esperando"
    QueueTicketStatus.READY -> "Listo"
    // Si backend agrega status nuevo:
    else -> "Estado desconocido"
}
```

---

## 11. Pairing State Validation

El ViewModel **debe validar** que existe un binding válido antes de pasar a estado `Paired`:

```kotlin
// ✅ CORRECTO: Validar binding
private fun isBindingValid(binding: DisplayBinding): Boolean {
    return binding.deviceId.isNotBlank() &&
           binding.displayId.isNotBlank() &&
           binding.barbershopId.isNotBlank() &&
           binding.bindingStatus == "active"
}

// En ViewModel
fun onPairingCodeRedeemed(binding: DisplayBinding) {
    if (isBindingValid(binding)) {
        cachDisplayBinding(binding)
        _pairingState.value = PairingState.Paired(
            deviceId = binding.deviceId,
            displayId = binding.displayId,
            barbershopId = binding.barbershopId,
            displayConfig = binding.displayConfig,
            bindingValidUntil = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
        )
    } else {
        _pairingState.value = PairingState.PairingError(
            reason = PairingErrorReason.BINDING_CREATION_FAILED,
            message = "Binding inválido recibido",
            retryCount = 0,
            lastAttemptTime = System.currentTimeMillis()
        )
    }
}
```

---

## 12. Enforcement

- **Code Review:** Verificar que Composables no fabrican estado.
- **Tests:** Escribir tests parametrizados (null vs non-null fields).
- **Lint:** Custom lint rule para detectar default values no permitidos.
- **Integration Tests:** Probar con states incompletos o corruptos.
- **Pairing Tests:** Validar transiciones de pairing y validación de binding.

---

**Versión:** 1.0 — Marzo 2026  
**Próxima revisión:** Septiembre 2026
