<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Documentación de Docucraft

Documentación viva del proyecto. A diferencia de [`AGENTS.md`](../AGENTS.md) y
[`ARCHITECTURE_PLAN.md`](../ARCHITECTURE_PLAN.md) —que describen el estado actual de forma
compacta para orientarse rápido—, esta carpeta contiene **análisis en profundidad y planes de
trabajo** por subsistema.

## Fases de estabilización

El proyecto está en fase de estabilización: el código funciona, pero hay subsistemas con
responsabilidades fragmentadas o mezcladas entre capas. Cada fase toma un subsistema, lo analiza,
diseña su arquitectura objetivo y lo migra en pasos pequeños y verificables.

| # | Subsistema | Estado | Documentos |
|---|---|---|---|
| 1 | **Escaneo de documentos** | ✅ Completado | [Análisis](architecture/01-scanner-analysis.md) · [Arquitectura objetivo](architecture/02-scanner-target-architecture.md) · [Plan de migración](architecture/03-scanner-migration-plan.md) |
| 2 | **Frontera del dominio** (V7/V8) | ✅ Completado | [Plan y resultado](architecture/04-domain-purity-plan.md) |
| 3 | Visor PDF (`:composepdf` + `feature/pdfviewer`) | ⏳ Pendiente | — |
| 4 | Preferencias y tema | ⏳ Pendiente | — |
| 5 | Búsqueda y filtrado | ⏳ Pendiente | — |
| 6 | Suscripciones y analítica | ⏳ Pendiente | — |
| 7 | **Navegación** (Navigation 3) | 🔎 Auditado, cierre en curso | [Auditoría y plan de cierre](architecture/05-navigation-audit.md) |

## Método

- [**Checklist de estabilización**](method/stabilization-checklist.md) — el método seguido en la
  fase 1, extraído para reaplicarlo a los demás subsistemas.

## Cómo leer esta carpeta

- `architecture/` — un análisis por subsistema, numerado por fase. Los documentos de análisis son
  **fotografías fechadas**: describen el código tal y como estaba al analizarlo. No se reescriben
  cuando el código cambia; se marca su estado y se enlaza al plan que los supera.
- `method/` — conocimiento transversal reutilizable, independiente de cualquier subsistema.

## Convenciones

- Toda afirmación sobre el código lleva referencia `archivo:línea`.
- Los planes de migración llevan casillas de progreso; se marcan al completar cada paso.
- Los bugs descubiertos durante el análisis se numeran (`B1`, `B2`, …) y se rastrean hasta su
  arreglo.
- Las violaciones de arquitectura se numeran (`V1`, `V2`, …) y se priorizan por *qué bloquean*, no
  por gravedad estética.
