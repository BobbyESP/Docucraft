<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Checklist de estabilización de un subsistema

Método extraído de la [fase 1 (escaneo de documentos)](../architecture/01-scanner-analysis.md) para
reaplicarlo a los demás subsistemas de Docucraft: visor PDF, preferencias, búsqueda, suscripciones.

El orden importa: **no se diseña antes de mapear, y no se migra antes de tener red de seguridad.**

---

## 1 · Mapear

- [ ] Listar todos los archivos del flujo, de gesto de usuario a efecto persistido.
- [ ] Por archivo: responsabilidad · dependencias · ¿más de una responsabilidad?
- [ ] Escribir el flujo como una traza numerada `archivo:línea → archivo:línea`.
- [ ] Marcar cada cruce de frontera de capa y **qué tipo viaja en él**.

> El valor de la traza numerada es que hace visibles los cruces de frontera. Sin ella, el
> acoplamiento se discute en abstracto.

## 2 · Localizar el acoplamiento

- [ ] `grep` de los imports del SDK externo: ¿cuántos archivos, en qué capas?
- [ ] ¿Algún tipo del SDK aparece en una firma de `domain/`?
- [ ] ¿La **forma** del contrato está dictada por el mecanismo del SDK?
      *(La señal más sutil y la más importante: un contrato que solo sirve para un proveedor no es
      una abstracción.)*
- [ ] ¿El SDK está en el grafo de DI global, al alcance de cualquiera?
- [ ] ¿Qué responsabilidad hace el SDK **por nosotros** y desaparecería al cambiarlo?
      *(permisos, caché, UI, reintentos…)* Esa es la responsabilidad **ausente** que hay que
      diseñar aunque hoy no se use.
- [ ] ¿En qué módulo Gradle está la dependencia? Si está en `:app`, nada impide la regresión.

## 3 · Diagnosticar

- [ ] Una entrada por violación, con `archivo:línea` y **por qué duele**
      (mantenibilidad / testabilidad / reemplazabilidad).
- [ ] Priorizar P0…P3 por **qué bloquea hoy**, no por fealdad.
- [ ] Anotar los bugs reales que aparezcan durante el mapeo. **Siempre aparecen**: el mapeo lee el
      código con más atención que cualquier revisión.
- [ ] **Prueba del algodón**: *«¿puedo testear el paso crítico en JVM pura?»* Si la respuesta es
      no, ahí está el P0 real, independientemente de lo que diga la intuición.

## 4 · Diseñar

- [ ] Identificar **la** decisión de diseño clave, en una frase. Todo lo demás se deriva de ella.
      *(En la fase 1: «unificar el flujo partido en dos».)*
- [ ] Un puerto en el dominio; modelos propios sin tipos de framework.
- [ ] Errores **sellados**, no `Result<T>` genérico: la cancelación no es un fallo.
- [ ] Dejar hueco en el contrato para lo que **otro** proveedor sabría hacer.
- [ ] Localizar el «punto único de intercambio» en DI y verificar que sea **una línea**.
      Si no lo es, el diseño no está terminado.
- [ ] Comprobar que la propuesta respeta el patrón ya consolidado (MVI + Koin + Navigation 3) en
      vez de introducir uno nuevo.

## 5 · Migrar

- [ ] **Paso 0 siempre = tests**, incluido al menos uno rojo que documente un bug encontrado.
- [ ] **Borrar código muerto antes de refactorizar**: a menudo se lleva el acoplamiento por
      delante sin esfuerzo.
- [ ] Introducir lo nuevo **sin cablear** (riesgo nulo); cablear al final.
- [ ] Marcar explícitamente el paso de mayor riesgo y aislarlo en un commit revertible.
- [ ] **Separar «arreglar bug» de «mover código»**: nunca en el mismo commit.
- [ ] Listar los huecos **preexistentes** que el refactor no arregla, para no confundirlos después
      con regresiones.
- [ ] Anotar los cambios de comportamiento deliberados (analítica, mensajes al usuario) antes de
      desplegar.

## 6 · Cerrar

- [ ] Marcar el progreso en el plan conforme se completa cada paso.
- [ ] Actualizar `AGENTS.md` / `ARCHITECTURE_PLAN.md` si el refactor cambia una regla que ahí se
      afirma.
- [ ] Dejar el documento de análisis **sin reescribir**: es una fotografía fechada, y su valor está
      en poder compararla con el estado posterior.
