# TV App Principles

**Android TV Module — Principios Fundacionales**  
**Versión:** 1.0 — Marzo 2026  
**Estado:** Vigente

---

## 1. Propósito

Este documento define los **principios no negociables** del módulo Android TV (`app-tv`). Ninguna decisión arquitectónica, feature o cambio de código puede violarlos sin aprobación explícita del equipo de arquitectura.

### Alcance

Aplica únicamente al módulo `app-tv` en el proyecto Yottei_app. No aplica a la aplicación móvil (`app`).

---

## 2. Principios Fundamentales

### Principio 1: Single Responsibility — Cliente Delgado

**Regla:**
- `app-tv` es **únicamente un cliente de visualización pasiva**.
- El módulo nunca contiene lógica de negocio (decisiones sobre orden de fila, elegibilidad, transiciones de estado, cálculos de ETA, penalización).
- Toda lógica de negocio reside en el backend de TurnoExpress.

**Implicación:**
- No hay `if` condicionales que evalúen reglas de negocio.
- No hay cálculos que deriven valores de negocio.
- No hay decisiones sobre quién debe ser atendido, en qué orden, o cuándo.

**Ejemplo de violación:**
```kotlin
// ❌ VIOLACIÓN: Calcular ETA es lógica de negocio
val eta = currentTicket.serviceMinutes + (nextTickets.size * avgServiceTime)
```

**Ejemplo correcto:**
```kotlin
// ✅ CORRECTO: Usar ETA recibida del backend
val eta = queueState.currentTicket.estimatedWaitMinutes
```

---

### Principio 2: Backend-Driven State

**Regla:**
- Toda fuente de verdad es el backend de TurnoExpress.
- El estado de la TV (`QueueDisplayState`) es un snapshot en el momento de recepción del backend.
- No modificar, inferir, o completar estado localStorage si no lo envía el backend.
- No realizar bifurcaciones de renderizado basadas en estado local fabricado.

**Implicación:**
- Si el backend omite un campo, no asumir un valor por defecto.
- Si el cliente se desconecta, mostrar último estado conocido O un estado de error, pero nunca inventar.
- El estado en la TV debe ser siempre tratable como inmutable (o efectivamente inmutable en la UI).

**Ejemplo de violación:**
```kotlin
// ❌ VIOLACIÓN: Fabricar estado faltante
val status = response.status ?: "waiting"  // No, espera que backend lo envíe
```

**Ejemplo correcto:**
```kotlin
// ✅ CORRECTO: Fallar o mostrar estado nulo explícitamente
val status = response.status ?: QueueTicketStatus.UNKNOWN
```

---

### Principio 3: Renderizado Determinístico

**Regla:**
- Para un estado (`state: QueueDisplayState`) dado, la UI siempre produce el mismo resultado visual.
- No hay randomización, timestamps, o cálculos en tiempo de layout que dependan de `System.currentTimeMillis()`.
- Las animaciones son determinísticas (siempre comienzan y terminan en el mismo tiempo relativo).

**Implicación:**
- No usar `Random()` en código de Compose.
- No usar `System.currentTimeMillis()` para condicionales de renderizado.
- Si necesitas timestamp, recibirlo del backend o del sistema de forma controlada.

**Ejemplo de violación:**
```kotlin
// ❌ VIOLACIÓN: Renderizado no determinístico
val showAnimation = System.currentTimeMillis() % 2000 < 1000
```

**Ejemplo correcto:**
```kotlin
// ✅ CORRECTO: Renderizado basado únicamente en state
val showAnimation = queueState.serverTime % 2000 < 1000  // El backend proporciona time
```

---

### Principio 4: Legibilidad Primero (Readability-First)

**Regla:**
- La TV es un dispositivo de **viewing pasivo desde distancia**.
- Toda decisión de UI, tipografía, contraste, y layout debe optimizar legibilidad desde ≥3 metros.
- Nunca sacrificar legibilidad por "diseño elegante" o densidad de información.

**Implicación:**
- Tipografía mínima: 24sp para texto, 48sp+ para números grandes.
- Contraste mínimo: 4.5:1 (WCAG AA).
- Máximo 2-3 líneas de texto por sección lógica.
- Sin iconografía ambigua; usar emojis o símbolos muy claros.
- Espacios amplios, layouts simples.

---

### Principio 5: Passive Display by Default

**Regla:**
- La TV es un **display pasivo por defecto**. No espera input del usuario.
- Si existe interacción (navegación con control remoto), debe ser secundaria, nunca crítica para la operación.
- La TV actualiza automáticamente sin necesidad de refresh manual.
- Nunca requerir toque o entrada para inicializar la aplicación.

**Implicación:**
- Toda información crítica se actualiza automáticamente desde el backend.
- Los botones y controles remotos son opcionales.
- La app debe ser 100% funcional sin entrada de usuario.

---

### Principio 6: Resilience and Recovery

**Regla:**
- El módulo TV debe recuperarse automáticamente de:
  - Pérdida de conexión de red (reconectar y solicitar estado).
  - Recepción de estado corrupto o parcial (mostrar UI de fallback).
  - Crash de la app (restart y restaurar último estado conocido).
  - Corte de energía (reinicio limpio, sin datos stale).
- Sin intervención del usuario, la TV debe estar 100% operativa de nuevo en <30 segundos.

**Implicación:**
- Implementar reconexión automática con backoff exponencial.
- Validar todo estado recibido antes de renderizar.
- Persistir estado en disco (temporalmente) para recuperación tras crash.
- No confiar en estado de archivo; usar último estado del backend tan pronto como se reconecte.

---

### Principio 7: No Business Logic in UI/Client

**Regla:**
- La capa de Composables UI (`src/ui/components/`) es **únicamente* para renderizado y navegación visual.
- Ningún `if` condicional que evalúe reglas de negocio.
- Ningún cálculo o derivación de datos de negocio.
- Ningún acceso directo a APIs; todo va a través de una capa de datos (`repository`, `usecase`, etc.).

**Implicación:**
- La lógica de presentación vive en ViewModels o Composables stateful, pero sin lógica de negocio.
- Los Composables stateless nunca contienen reglas de negocio.
- Las APIs se consultan solo desde capas de datos o ViewModel.

**Ejemplo de violación:**
```kotlin
// ❌ VIOLACIÓN: Lógica de negocio en Composable
@Composable
fun TicketCard(ticket: QueueTicket) {
    val isEligible = ticket.status == "waiting" && ticket.checkInTime != null
    if (isEligible) { /* render */ }
}
```

**Ejemplo correcto:**
```kotlin
// ✅ CORRECTO: Decidido por el backend, UI solo renderiza
@Composable
fun TicketCard(ticket: QueueTicket, isHighlighted: Boolean) {
    // isHighlighted viene del estado, no de lógica local
    if (isHighlighted) { /* render */ }
}
```

---

### Principio 8: Required Pairing Before Display Content

**Regla:**
- La TV **nunca debe mostrar contenido de display de producción** sin estar vinculada a un display a través del modelo de pairing.
- El pairing es un **prerequisito**, no un feature opcional.
- Si no existe binding válido, la TV entra en `waiting_for_pairing` state.
- El código de pairing es **temporal y de un solo uso**, nunca una credencial de larga duración.

**Implicación:**
- Startup: verificar `display_binding` válido antes de renderizar `QueueDisplayScreen`.
- Si binding no existe: mostrar `PairingScreen` con código de 6 dígitos.
- Si binding es revocado: transicionar de vuelta a `PairingScreen`.
- El pairing model es **global** (gobernanza en `governance/system/display_pairing_model.md`).

**Ejemplo de violación:**
```kotlin
// ❌ VIOLACIÓN: Mostrar display content sin verificar binding
@Composable
fun MainActivity() {
    setContent {
        QueueDisplayScreen()  // ← Sin validar pairing
    }
}
```

**Ejemplo correcto:**
```kotlin
// ✅ CORRECTO: Verificar binding antes de renderizar display
@Composable
fun MainActivity() {
    val pairingState = viewModel.pairingState.collectAsState()
    setContent {
        when (pairingState.value) {
            PairingState.PAIRED -> QueueDisplayScreen()
            PairingState.WAITING_FOR_PAIRING -> PairingScreen()
            PairingState.ERROR -> PairingErrorScreen()
        }
    }
}
```

**Security Note:**
- Nunca loguear el pairing code.
- Nunca almacenar el pairing code en disco (solo en memoria durante pairing flow).
- Almacenar `device_secret` (token de autenticación) en `EncryptedSharedPreferences`.

---

## 3. Consecuencias de Violar Estos Principios

- **Violación 1-2 (Lógica, Estado)**: Aplicación no escalable; incompatible con cambios de backend.
- **Violación 3 (Determinismo)**: Bugs intermitentes, comportamiento impredecible en testing.
- **Violación 4 (Legibilidad)**: Pérdida de propósito (TV inlegible a distancia).
- **Violación 5 (Pasividad)**: Fricción del usuario, requiere intervención, no se puede dejar sola.
- **Violación 6 (Resiliencia)**: Necesita restart manual, estado stale, experiencia pobre.
- **Violación 7 (Separación)**: Código no testeable, acoplamiento fuerte backend-frontend, vulnerabilidades.
- **Violación 8 (Pairing)**: Seguridad comprometida, contenido expuesto a device no autorizado, violación de confianza de staff.

---

## 4. Enforcement

- Code review: verificar que propuestas no violen estos principios.
- Tests: escribir tests que aseguren renderizado determinístico y separación de capas.
- Documentation: linkear estos principios en PRs que afecten arquitectura.

---

**Versión:** 1.0 — Marzo 2026  
**Próxima revisión:** Septiembre 2026
