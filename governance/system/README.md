# System Governance — Display Pairing

**Yottei — Sistema de Emparejamiento de Pantallas**  
**Versión:** 1.0 — Marzo 2026  
**Estado:** Vigente

---

## Propósito

Este directorio contiene la gobernanza **global del sistema** que afecta múltiples módulos:
- Emparejamiento de dispositivos TV con pantallas de display
- Modelo de identidad de dispositivo
- Códigos de emparejamiento temporal
- Autorización de staff dashboard
- Configuración de display compartida

La gobernanza aquí es **vinculante para todos los módulos** (web, TV, backend).

---

## Estructura

```
governance/system/
├── README.md                              ← este archivo
└── display_pairing_model.md               ← modelo de emparejamiento global
```

---

## Relación con Otras Gobernanzas

**Jerarquía de precedencia:**

1. **Máxima:** `turnoexpress/governance/domain/domain_model.md` (verdades canónicas del dominio)
2. **Alta:** `governance/system/display_pairing_model.md` (modelo global de emparejamiento)
3. **Media:** `governance/tv/` (implementación específica TV)

Si hay conflicto:
- Gobernanza system resuelve a favor del modelo de dominio TurnoExpress.
- Gobernanza TV respeta gobernanza system.

---

## Contenido

### `display_pairing_model.md`

Define:
- Modelo conceptual de emparejamiento (device ↔ display)
- Ciclo de vida de código de emparejamiento
- Flujos de pairing (actual: numeric code, futuro: QR)
- Responsabilidades por componente (TV app, web dashboard, backend)
- Identidad de dispositivo y binding persistente
- Estados y transiciones

---

## Actualización

Versión: 1.0 — Marzo 2026  
Estado: Vigente  
Próxima revisión: Septiembre 2026
