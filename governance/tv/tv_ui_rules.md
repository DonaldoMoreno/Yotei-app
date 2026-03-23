# TV UI Rules

**Android TV Module — Reglas de Diseño UI para Visualización en TV**  
**Versión:** 1.0 — Marzo 2026  
**Estado:** Vigente

---

## 1. Propósito

Define restricciones y recomendaciones de UI específicas para la visualización en pantallas de TV de 55"+ a distancia (≥3 metros).

TV no es una pantalla táctil de cercanía. Es un display de visualización pasiva donde el contenido debe ser legible desde distancia, con fuerte contraste, sin saturación, y con tipografía amplificada.

---

## 2. Typography (Tipografía)

### 2.1 Regla Mínima de Tamaños

Toda tipografía en la TV debe cumplir estos mínimos:

| Elemento | Mínimo | Recomendado | Máximo |
|----------|--------|-------------|--------|
| **Números grandes (ticket)** | 48sp | 72sp | - |
| **Nombres de cliente** | 28sp | 36sp | 48sp |
| **Servicios / Descripciones cortas** | 20sp | 24sp | 32sp |
| **Estadísticas secundarias** | 18sp | 20sp | 24sp |
| **Labels / Captions** | 16sp | 18sp | 20sp |
| **Texto diminuto (disclaimer)** | 12sp | 14sp | 16sp |

**Regla:** Si no puedes leer esto desde 3 metros, es demasiado pequeño.

### 2.2 Font Family

**Permitido:**
- `Roboto` (sans-serif, legible a distancia).
- `Courier New` / monospace para números (tickets, ETA).
- `Inter` o similares para números grandes.

**Prohibido:**
- Serif fonts (Georgia, Times New Roman) para pantallas de TV.
- Fuentes decorativas o script.
- Fuentes muy light (weight < 300) en tamaños pequeños.

### 2.3 Font Weight

- **Números grandes:** Bold (700) o ExtraBold (800).
- **Títulos / Secciones:** SemiBold (600).
- **Cuerpo / Descriptivo:** Regular (400) o Medium (500).
- **Captions / Labels:** Regular (400) o Medium (500).

**Regla:** Evita font weight < 300 en TV.

### 2.4 Line Height

Mínimo 1.4x el tamaño de fuente en contenido de lectura.

```kotlin
// ✅ CORRECTO
Text(
    text = "Cliente: Carlos Mendoza",
    fontSize = 24.sp,
    lineHeight = 36.sp  // 1.5x
)

// ❌ INCORRECTO (Muy comprimido)
Text(
    text = "Cliente: Carlos Mendoza",
    fontSize = 24.sp,
    lineHeight = 24.sp
)
```

---

## 3. Contrast (Contraste)

### 3.1 Contraste Mínimo Requerido

**Regla WCAG AA:** Ratio de contraste mínimo **4.5:1** entre texto y fondo.

**Métodos de verificación:**
- Usar WebAIM Contrast Checker.
- Android Studio Color Picker verifica contraste automáticamente.
- En preview: activar "Show Color Blindness Simulation".

### 3.2 Recomendaciones de Color

**Fondos de TV (basados en proyecto Yottei):**
- Fondo principal: #10101A (casi negro, muy oscuro).
- Fondo secundario: #1C5A5E (teal oscuro, en barberías).
- Fondo terciario: #0D3D40 (teal aún más oscuro).

**Textos permitidos:**
- Blanco puro (#FFFFFF).
- Gris claro (#E0E0E0, #F0F0F0).
- Verde claro (#10B981) — para estados positivos.
- Amarillo (#EAB308) — para advertencias.
- Rojo (#EF4444) — para errores o estados críticos.

**Prohibido:**
- Gris sobre gris (bajo contraste).
- Colores pastel como texto (no legible a distancia).
- Rojo y verde juntos sin otro diferenciador (daltonismo).

### 3.3 Color Blindness Considerations

**Regla:** No uses color como único diferenciador.

**Ejemplo incorrecto:**
```kotlin
// ❌ Solo color para diferencia
val color = if (ticket.isReady) Color.Green else Color.Red
```

**Ejemplo correcto:**
```kotlin
// ✅ Color + símbolo + texto
val (color, label) = if (ticket.isReady) {
    Pair(Color.Green, "LISTO ✓")
} else {
    Pair(Color.Red, "ESPERANDO ...")
}
```

---

## 4. Distance Readability (Legibilidad a Distancia)

### 4.1 Jerarquía Visual Simplificada

Máximo **3 niveles de importancia visual**:

1. **Nivel 1 (Primario):** Número de ticket actual, estado crítico.
2. **Nivel 2 (Secundario):** Nombre del cliente, próximos tickets.
3. **Nivel 3 (Terciario):** Metadata (timestamp, barbero, servicio).

**No confundir con 5+ niveles** (abruma visualmente).

### 4.2 Spatial Separation

Usa espacios amplios entre secciones lógicas.

**Mínimo:**
- Padding dentro de un card/section: 16dp.
- Espaciado entre cards: 24dp.
- Margen de pantalla (top/sides): 32dp.

```kotlin
// ✅ CORRECTO: Espacios amplios
Column(
    modifier = Modifier.padding(32.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp)
) {
    // Secciones bien separadas
}
```

### 4.3 No Dense Layouts

**Prohibido:**
- Mostrar más de 5 tickets a la vez (overwhelming).
- Mezclar múltiples secciones en una sola línea.
- Empaquetar información horizontalmente sin separación.

**Permitido:**
- 1 ticket grande actual + hasta 5 siguientes en grid horizontal (con espaciado).
- 1-2 paneles de estadísticas (máximo 3 números cada uno).

### 4.4 Focus and Highlight

Cuando existe navegación (control remoto), highlighting debe ser **muy obvio**:

```kotlin
// ✅ CORRECTO: Borde + fondo elevado
Modifier
    .then(
        if (isFocused) {
            Modifier
                .border(4.dp, Color.Yellow)
                .background(Color.White.copy(alpha = 0.1f))
        } else {
            Modifier
        }
    )
```

---

## 5. Animations (Animaciones)

### 5.1 Minimal Animations

**Permitido:**
- Fade in/out (cuando aparece nueva información).
- Slide in (entrada de nueva sección).
- Subtle scale (0.95 → 1.0) en focus.

**Prohibido:**
- Animaciones rápidas (<200ms) que distraigan.
- Bounces, rotations, complejas transformaciones.
- Auto-looping animations (que repitan indefinidamente).
- Parallax o motion en backgrounds.

### 5.2 Animation Duration

- **Fade in:** 300-500ms.
- **Slide in:** 300-500ms.
- **Focus highlight:** 200-300ms.
- **State change:** 500-800ms máximo.

```kotlin
// ✅ CORRECTO
AnimatedVisibility(
    visible = showNewTickets,
    enter = fadeIn(animationSpec = tween(durationMillis = 300)),
    exit = fadeOut(animationSpec = tween(durationMillis = 300))
) { /* content */ }
```

### 5.3 No Animations Durante Datos Stale

Si la conexión se perdió o el estado es incierto, **deshabilitar animaciones**.

```kotlin
// ✅ CORRECTO: Animar solo en estado válido
val animationsEnabled = uiState is QueueDisplayUiState.Success

AnimatedVisibility(
    visible = condition,
    enter = if (animationsEnabled) fadeIn() else null,
    // ...
) { /* content */ }
```

---

## 6. Simple Hierarchy (Jerarquía Simple)

### 6.1 Maximum 3 Nesting Levels (Nested Layouts)

No profundizar más de 3 niveles en la jerarquía de Compose.

```kotlin
// ✅ CORRECTO (3 niveles)
Column {                          // Nivel 1: Container principal
    TicketHeader()                // Nivel 2: Sección lógica
    CurrentTicketCard()           // Nivel 2: Card
}

// ❌ INCORRECTO (5+ niveles, confuso)
Column { Row { Card { Column { Row { /* ... */ } } } } }
```

### 6.2 Clear Section Boundaries

Cada sección debe tener límites visuales claros:

```kotlin
// ✅ CORRECTO: Límites claros
Surface(
    color = Color(0xFF1C5A5E),
    modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
) {
    Column(modifier = Modifier.padding(20.dp)) { /* content */ }
}
```

### 6.3 Grid Layouts for Multiple Items

Si mostras múltiples tickets similares, usa Grid:

```kotlin
// ✅ CORRECTO: Grid para tickets
LazyVerticalGrid(columns = GridCells.Fixed(3)) {
    items(nextTickets) { ticket ->
        TicketItem(ticket)
    }
}
```

---

## 7. Remote-Friendly Interaction (Interacción Remoto-Amigable)

**Nota:** La TV es display pasivo por defecto, pero si existe navegación con control remoto:

### 7.1 Large Touch Targets (si hay botones)

Mínimo **56dp × 56dp** para cualquier elemento interactivo.

```kotlin
// ✅ CORRECTO
Button(
    modifier = Modifier.size(56.dp, 56.dp),
    onClick = { /* */ }
)
```

### 7.2 Clear Visual Focus Indicator

Cuando el foco se mueve (Dpad), el elemento debe cambiar drásticamente:

```kotlin
// ✅ CORRECTO: Focus muy claro
Modifier
    .focusable()
    .then(
        if (isFocused) {
            Modifier
                .background(Color.Yellow)
                .scale(1.05f)
        } else {
            Modifier
        }
    )
```

### 7.3 No Hover States (TV no tiene mouse)

TV no tiene hover; solo focus.

```kotlin
// ❌ PROHIBIDO
Modifier.pointerInput(Unit) { detectTapGestures { } }

// ✅ PERMITIDO
Modifier.focusable()
```

### 7.4 Keyboard Navigation (Mandatory if Interactive)

Si hay buttons/focusable elements, **deben ser navegables** con Dpad:

```kotlin
// ✅ CORRECTO
FocusRequester().requestFocus()
Modifier
    .focusRequester(focusRequester)
    .onKeyEvent { event ->
        when (event.key) {
            Key.DirectionUp -> { /* navegar arriba */ }
            Key.DirectionDown -> { /* navegar abajo */ }
            // ...
        }
        true
    }
```

---

## 8. Specific Component Rules

### 8.1 QueueDisplayHeader

- Shop name: 32sp Bold.
- Address: 20sp Regular.
- Clock: 48sp Bold (monospace).
- Spacing: mínimo 16dp entre elementos.

### 8.2 CurrentTicketCard

- Ticket number: **72sp Bold monospace** (máximo enfoque).
- Customer name: 36sp Medium.
- Service description: 20sp Regular.
- Status badge: 24sp Bold + ice color (teal/green).
- Padding: 24dp interior.

### 8.3 NextTicketsSection

- Grid de 3 columnas (máximo visible en pantalla 55").
- Card mínimo 120sp altura ( readable).
- Ticket number: 32sp.
- Name: 18sp.
- Spacing: 16dp entre cards.

### 8.4 QueueStatsPanel

- Máximo 4 stats (total queue, ETA, shop status, uptime).
- Números grandes (32sp+).
- Labels cortos (14sp).
- Sin gráficas complejas.

---

## 9. Enforcement

- **Preview:** Testear layouts en emulator con screen size ≥ 1280×720 (@2x density).
- **Design Review:** Verificar tipografía, contraste, y espacios en PRs de UI.
- **A11y Testing:** Usar Android Accessibility Scanner en emulator.
- **Real Device Test:** Probar en TV real si es posible (o emulator with display config).

---

**Versión:** 1.0 — Marzo 2026  
**Próxima revisión:** Septiembre 2026
