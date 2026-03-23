# TV Architecture

**Android TV Module — Arquitectura y Límites Permitidos**  
**Versión:** 1.0 — Marzo 2026  
**Estado:** Vigente

---

## 1. Propósito

Define la estructura arquitectónica permitida, límites de módulos, flujo de datos, y patrones prohibidos para la aplicación Android TV.

---

## 2. Allowed Layers (Capas Permitidas)

El módulo `app-tv` está organizado en **4 capas lógicas**:

```
┌─────────────────────────────────────────────────────────────┐
│                      UI Layer (Composables)                  │
│           (Components, Screens, Navigation)                  │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│               Presentation Layer (ViewModel)                 │
│        (State Management, Recomposition, ErrorHandling)      │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                  Data Layer (Repository)                     │
│         (Aggregation, Caching, Transformation)              │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│              Remote Data (Backend API + Realtime)            │
│                 (Supabase, Network Calls)                    │
└─────────────────────────────────────────────────────────────┘
```

### 2.1 UI Layer

**Responsabilidad:** Renderizado visual y navegación.

**Permitido:**
- Jetpack Compose Composables (stateless y stateful).
- Navegación entre pantallas.
- Binding de estado a UI.
- Manejo de affordances (botones, controles remoto).

**Prohibido:**
- Llamadas directas a APIs.
- Lógica de negocio.
- Instantiación de ViewModel o Repository.
- Estado mutable global.

**Ubicación:** `app-tv/src/main/java/com/yotei/tv/ui/`

```
ui/
├── screens/
│   ├── PairingScreen.kt           ← pantalla de emparejamiento (NUEVA)
│   └── QueueDisplayScreen.kt      ← pantalla principal
├── components/
│   ├── pairing/                   ← (NUEVA)
│   │   ├── PairingCodeDisplay.kt
│   │   ├── PairingErrorDisplay.kt
│   │   └── PairingSuccessOverlay.kt
│   ├── QueueDisplayHeader.kt
│   ├── CurrentTicketCard.kt
│   ├── NextTicketsSection.kt
│   └── QueueStatsPanel.kt
└── navigation/
    └── TvNavGraph.kt              ← rutas y navegación
```

---

### 2.2 Presentation Layer (ViewModel)

**Responsabilidad:** Gestión de estado (pairing + queue display), transformaciones, y orquestación.

**Permitido:**
- ViewModels con StateFlow / LiveData.
- Reacción a cambios de estado desde Repository.
- Transformación de datos (join, filter, map).
- Manejo de errores y loading states.
- Lógica de presentación (qué mostrar, cuándo mostrar).

**Prohibido:**
- Llamadas directas a APIs o Supabase.
- Lógica de negocio (decisiones sobre orden, elegibilidad).
- MutableState global.
- Acceso a Context o Activity (excepto recurso de strings).

**Ubicación:** `app-tv/src/main/java/com/yotei/tv/presentation/`

```
presentation/
└── viewmodel/
    ├── PairingViewModel.kt        ← (NUEVA)
    └── QueueDisplayViewModel.kt
```

**PairingViewModel Sample:**
```kotlin
class PairingView Model(
    private val pairingRepository: PairingRepository,
    private val deviceStore: LocalDeviceStore
) : ViewModel() {
    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Unpaired)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()
    
    fun initializePairingFlow() {
        // Generar device_id si no existe
        // Generar pairing code
        // Mostrar PairingScreen
        // Iniciar polling de backend
    }
}
```

---

### 2.3 Data Layer (Repository) — Pairing

**Responsabilidad:** Agregación de pairing data sources, caching binding, validación.

**Ubicación:** `app-tv/src/main/java/com/yotei/tv/data/`

```
data/
├── repository/
│   ├── PairingRepository.kt       ← (NUEVA)
│   └── QueueRepository.kt
├── datasource/
│   ├── pairing/                   ← (NUEVA)
│   │   ├── RemotePairingDataSource.kt
│   │   ├── LocalDeviceStore.kt
│   │   └── LocalDisplayBindingStore.kt
│   ├── RemoteQueueDataSource.kt
│   └── LocalQueueDataSource.kt
└── mapper/
    ├── DisplayBindingMapper.kt     ← (NUEVA)
    └── QueueMapper.kt
```

**PairingRepository Sample:**
```kotlin
class PairingRepository(
    private val remotePairingDataSource: RemotePairingDataSource,
    private val localBindingStore: LocalDisplayBindingStore,
    private val deviceStore: LocalDeviceStore
) {
    suspend fun checkCodeRedeemed(deviceId: String, code: String): DisplayBinding? {
        return try {
            remotePairingDataSource.queryCodeStatus(deviceId, code)
        } catch (e: Exception) {
            null  // Non-blocking
        }
    }
    
    suspend fun cacheDisplayBinding(binding: DisplayBinding) {
        localBindingStore.saveBinding(binding)
    }
    
    fun getDisplayBinding(): DisplayBinding? {
        return localBindingStore.getBinding()?.takeIf { !isBindingExpired(it) }
    }
}
```

---

### 2.4 Remote Data Layer — Pairing Endpoints

**Ubicación:** `core/network/` (módulo compartido)

**Pairing API Endpoints Required:**

```kotlin
interface PairingApi {
    // Query si pairing code fue canjeado
    @GET("/api/v1/pairing/codes/{device_id}/{code}")
    suspend fun checkCodeStatus(
        @Path("device_id") deviceId: String,
        @Path("code") code: String,
        @Header("X-Device-Secret") deviceSecret: String
    ): DisplayBindingDto?
    
    // Fetch current binding para device
    @GET("/api/v1/devices/{device_id}/binding")
    suspend fun getDeviceBinding(
        @Path("device_id") deviceId: String,
        @Header("X-Device-Secret") deviceSecret: String
    ): DisplayBindingDto?
}
```

**Data Models (sharing):**
```kotlin
// core-model or core-network DTOs
data class DisplayBindingDto(
    val binding_id: String,
    val device_id: String,
    val display_id: String,
    val barbershop_id: String,
    val binding_status: String,  // "active", "revoked"
    val display_config: JsonObject?
)
```

---

### 2.5 Queue Display Data Layer (Repository)

**Responsabilidad:** Agregación de queue data source
├── api/
│   └── QueueApi.kt               ← cliente HTTP
├── supabase/
│   └── SupabaseClient.kt         ← cliente realtime
└── dto/
    └── QueueStateDto.kt          ← modelos serialización
```

---

## 3. Module Boundaries (Límites de Módulos)

### 3.1 `app-tv` Module (Aplicación TV)

**Es propietario de:**
- UI (Composables, Screens).
- ViewModels específicos de TV.
- Configuración de TV (AndroidManifest TV-specific).
- Validación de parámetros de entrada (barbershop_id).
- Recursos de strings, colores, dimensiones de TV.
- Temas (Material3 personalizado para TV si es necesario).

**Depende de:**
- `core-model` (Domain Models: `QueueState`, `QueueTicket`, `Barbershop`).
- `core-common` (Composables generales, utilidades).
- `data` (Repository implementations).
- `network` (API clients).

**Nunca debe:**
- Acceder a la aplicación móvil (`app` module).
- Tener dependencias circulares.
- Contener lógica de negocio canónica.
- Definir contratos de dominio (eso es tarea de `core-model`).

**Ejemplo de dependencia permitida:**
```gradle
dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-common"))
    implementation(project(":data"))
    implementation(project(":network"))
}
```

---

### 3.2 `core-model` Module (Domain Models Compartidos)

**Es propietario de:**
- Enums canónicos (`QueueStatus`, `BarbershopStatus`, etc.).
- Data classes de dominio (`QueueTicket`, `Barbershop`, `QueueState`).
- Invariantes de negocio documentadas.

**Puede ser usado por:**
- `app` (app móvil).
- `app-tv` (app TV).
- `data` (repository implementations).
- `network` (DTOs y mappers).

**Nunca debe:**
- Contener lógica de presentación.
- Depender de Compose o Android UI.
- Depender de capas de datos o red.

---

### 3.3 `core-common` Module (Utilidades Compartidas)

**Es propietario de:**
- Extensiones Kotlin reutilizables.
- Composables base/base components.
- Temas y estilos compartidos (si aplica).
- Dependencias comunes (Compose, Material3).

**Puede ser usado por:**
- `app`.
- `app-tv`.
- Cualquier módulo que necesite utilidades.

---

### 3.4 `data` Module (Repository Implementations)

**Es propietario de:**
- Implementaciones de Repository (QueueRepository, etc.).
- Data sources (Remote, Local).
- Mappers (DTO → Domain Model).
- Caching y persistencia local.

**Depende de:**
- `core-model` (Domain Models).
- `network` (API clients).

**Puede ser usado por:**
- `app`.
- `app-tv`.

---

### 3.5 `network` Module (API Clients)

**Es propietario de:**
- Clientes HTTP (Retrofit).
- Clientes Supabase.
- DTOs (modelos de serialización).
- Configuración de red.

**Depende de:**
- `core-model` (mínimamente, solo si necesita Domain Models).

---

## 4. Data Flow (Flujo de Datos)

### Unidireccional (One-way binding)

**Regla:** Los datos fluyen desde Remote → Data → ViewModel → UI. **Nunca al revés** (excepto user input).

```
Remote Data (Supabase)
    ↓
DataSource (transformación DTO → Domain)
    ↓
Repository (agregación, caching)
    ↓
ViewModel (estado, transformaciones de presentación)
    ↓
UI (renderizado)
    ↓ (user input: botón remoto)
ViewModel (manejo de evento)
    ↓
Repository (llamada a acción)
    ↓
Remote Data (enviar cambio)
```

**Prohibido:**
- UI escribe directamente a Repository.
- ViewModel modifica campos de Domain Models.
- DataSource accede a ViewModels.

---

## 5. Forbidden Architecture Patterns (Patrones Prohibidos)

### ❌ Patrón 1: Business Logic in UI

```kotlin
// PROHIBIDO
@Composable
fun QueueScreen() {
    val isEligible = ticket.status == "waiting" && hasCheckedIn
    if (isEligible) { /* render */ }
}
```

### ❌ Patrón 2: Direct API Calls from UI

```kotlin
// PROHIBIDO
@Composable
fun TicketCard() {
    LaunchedEffect(Unit) {
        val response = supabase.from("queue").select().execute()
    }
}
```

### ❌ Patrón 3: Global Mutable State

```kotlin
// PROHIBIDO
object GlobalState {
    var queueState: QueueState? = null  // ← State global mutable
}
```

### ❌ Patrón 4: ViewModel Instantiation in Composables

```kotlin
// PROHIBIDO
@Composable
fun QueueScreen() {
    val viewModel = QueueDisplayViewModel()  // ← Crear aquí
}
```

**Correcto:** Usar `hiltViewModel()` o inyección de dependencias.

### ❌ Patrón 5: Circular Dependencies

```gradle
// PROHIBIDO
// app-tv depende de app
// app depende de app-tv
```

### ❌ Patrón 6: Repository Logic in ViewModel

```kotlin
// PROHIBIDO
class QueueViewModel {
    fun refreshQueue() {
        val remoteState = api.getQueue()
        val localState = db.getQueue()
        // ← Agregar datos aquí (es tarea de Repository)
        val merged = remoteState + localState
    }
}
```

---

## 6. TV is a Thin Client (TV es un Cliente Delgado)

**Declaración:** El módulo `app-tv` es **un cliente delgado que no replica lógica de backend**.

**Implicaciones:**
- No hay cálculos de ETA, orden de fila, o elegibilidad.
- No hay almacenamiento persistente de lógica de negocio.
- No hay "sincronización bidireccional" of state.
- No hay caché offline completo que implique offline-first.

**Única caché permitida:**
- Último estado conocido (para recuperación tras crash).
- Parámetros de configuración del dispositivo (brightness, language).
- Configuración de conexión (URL del backend).

**No es permitido:**
- Caché completo de cola (tickets históricos, etc.).
- Sincronización local-remoto de conflictos.
- Decisiones de negocio local basadas en caché stale.

---

## 7. Enforcement

- **Code Review:** Verificar que PRs respeten límites de módulos y capas.
- **Linting:** Usar Lint rules para detectar dependencias circulares.
- **TestingStrategy:** Escribir tests que verifiquen separación de capas.
- **Documentation:** Mantener estos limits actualizados y linkearlos en PRs.

---

**Versión:** 1.0 — Marzo 2026  
**Próxima revisión:** Septiembre 2026
