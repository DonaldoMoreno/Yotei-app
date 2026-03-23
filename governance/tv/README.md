# Android TV App — Governance Documentation

**Versión:** 1.0 — Marzo 2026  
**Estado:** Vigente  
**Aplicación:** `app-tv` Module (Display Client)

---

## Propósito

Este directorio contiene la gobernanza arquitectónica y operativa completamente específica para el **módulo Android TV** disponible en el proyecto.

El módulo Android TV (`app-tv`) es un cliente de **visualización pasiva** dedicado a mostrar estado de cola en pantallas de TV de 55"+ en barberías. No contiene lógica de negocio; es un cliente delgado impulsado completamente por el backend.

---

## Estructura

Todos los documentos en esta carpeta son de **precedencia igualmente alta** dentro del contexto del módulo Android TV. Conflictos se resuelven consultando la gobernanza del backend de TurnoExpress.

```
governance/tv/
├── README.md                              ← este archivo
│
├── tv_app_principles.md                   ← principios fundacionales
├── tv_architecture.md                     ← capas permitidas y límites de módulos
├── tv_ui_rules.md                         ← reglas de UI para TV
├── tv_state_contract.md                   ← contrato de renderizado y estado
├── tv_device_behavior.md                  ← comportamiento en tiempo de ejecución
└── tv_non_functional_requirements.md      ← requisitos de rendimiento y estabilidad
```

---

## Resumen de Contenido

### `tv_app_principles.md`
Define los principios no negociables del módulo:
- Single responsibility (solo visualización, cero lógica de negocio)
- Responsabilidad del backend
- Renderizado determinístico
- Legibilidad primero (distancia + contraste)
- Resiliencia y recuperación
- No inventar estado faltante

### `tv_architecture.md`
Define la estructura permitida:
- Capas (datos, lógica de presentación, UI)
- Límites de módulos (separación `app-tv` vs módulos compartidos)
- Flujo de datos (unidireccional)
- Patrones prohibidos (estado compartido, lógica de negocio)
- Regla: TV es un cliente delgado

### `tv_ui_rules.md`
Define restricciones de UI para visualización en TV:
- Tipografía grande (≥24sp para texto, ≥48sp para números)
- Contraste alto (≥4.5:1)
- Legibilidad a distancia (arquitectura visual simple)
- Sin animaciones densas
- Sin layouts complejos
- Comportamiento amigable con control remoto (si existe interacción)

### `tv_state_contract.md`
Define el contrato de renderizado:
- `QueueDisplayState` estructura obligatoria
- `QueueTicket` campos requeridos
- Manejo de null (nunca inventar estado)
- Regla: UI debe derivar únicamente de estado explícito
- Fallback para estado vacío/faltante

### `tv_device_behavior.md`
Define el comportamiento en tiempo de ejecución:
- Expectativas de inicio
- Reconexión tras pérdida de red
- UI de fallback en errores
- Expectativa de fullscreen
- Recuperación tras reinicio o corte de energía
- Persistencia local mínima (solo configuración de dispositivo)

### `tv_non_functional_requirements.md`
Define requisitos no funcionales:
- Rendimiento: actualizaciones <500ms, memoria <150MB
- Estabilidad: uptime >99%, sin crashes
- Bajo uso de memoria en dispositivos Roku/Fire TV
- Reactividad: intervalo máximo 5 segundos sin actualización
- Comportamiento offline (mostrar último estado conocido)
- Seguridad: sin almacenamiento de datos sensibles

---

## Convención: Terminología

Términos usados en toda la gobernanza del módulo `app-tv`:

- **"Cliente delgado"**: aplicación que no contiene lógica de negocio; solo recibe y renderiza.
- **"Backend-driven"**: todo estado y transiciones de negocio ocurren en el servidor; el cliente es receptor.
- **"State"**: snapshot actual de datos de cola desde el backend (QueueDisplayState).
- **"Renderizado determinístico"**: para el mismo state, UI siempre produce visualmente el mismo resultado.
- **"Resiliencia"**: capacidad de recuperación ante falta de red, cambios de estado corruptos, o reinicio.
- **"Legibilidad a distancia"**: capacidad de ser leída desde ≥3 metros en pantalla de TV ≥55".

---

## Cómo Usar Esta Gobernanza

1. **Al diseñar feature nuevo**: consulta `tv_app_principles.md` y `tv_architecture.md` primero.
2. **Al implementar UI**: sigue `tv_ui_rules.md` y `tv_state_contract.md` de forma estricta.
3. **Al integrar con backend**: asegura que dispositivo respeta `tv_device_behavior.md` y `tv_non_functional_requirements.md`.
4. **En conflictos**: los archivos en este directorio prevalecen sobre comentarios o documentación externa (excepto la gobernanza backend).

---

## Relación con Gobernanza Backend

La gobernanza del módulo `app-tv` es **complementaria** a la gobernanza de TurnoExpress (`../../../turnoexpress/governance/`).

- **Gobernanza Backend** (TurnoExpress): define la verdad canónica del dominio, e invariantes de negocio.
- **Gobernanza TV** (este directorio): define cómo el cliente TV **implementa y respeta** esas verdades dentro de restricciones de TV.

En caso de conflicto, la gobernanza backend tiene precedencia. El módulo TV debe adaptarse, no cuestionar.

---

## Actualización

Versión: 1.0 — Marzo 2026  
Estado: Vigente  
Próxima revisión: Septiembre 2026
