<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Fase 4 · Subsistema de escaneo — Plan de migración

> Desde el [análisis 01](01-scanner-analysis.md) hasta la
> [arquitectura objetivo 02](02-scanner-target-architecture.md).
>
> **Regla del plan**: nada de *big bang*. Cada paso compila y es verificable por separado, y está
> pensado para caber en un commit revertible.

---

## Estado del plan

| Paso | Descripción | Riesgo | Estado |
|---|---|---|---|
| 0 | Red de seguridad (tests) | Nulo | ✅ Hecho |
| 1 | Borrar código muerto | Muy bajo | ✅ Hecho |
| 2 | Arreglar B1, B2, B3, B4 + extraer mapper puro | Bajo | ✅ Hecho |
| 3 | Introducir el contrato de dominio | Nulo | ✅ Hecho |
| 4 | Implementar el motor ML Kit contra el contrato | Nulo | ⏳ Pendiente |
| 5 | Cablear el shell (`MainActivity`) | **Medio-alto** | ⏳ Pendiente |
| 6 | Mover el ViewModel al puerto | Medio | ⏳ Pendiente |
| 7 | Limpiar la persistencia | Medio | ⏳ Pendiente |
| 8 | *(Opcional)* Módulos `:scanner-api` / `:scanner-mlkit` | Bajo | ⏳ Pendiente |
| 9 | *(Opcional)* Resiliencia a muerte de proceso | Bajo | ⏳ Pendiente |

---

## Paso 0 · Red de seguridad ✅

**Qué se toca**: solo `app/src/test`.

- [x] Añadir a `HomeViewModelTest` el caso «cancelación»: no guarda documento y **no** muestra
      error (hoy solo existe el caso de fallo genérico, `:245-259`).
- [x] Añadir el caso «fallo real»: no guarda documento **y sí** muestra error (bug **B4**).
- [x] Nuevo `ScanResultMapperTest` que documenta **B1** y **B3**.

**Verificación**: `:app:testDebugUnitTest`.
**Riesgo**: nulo.

> **Desviación respecto al plan original.** El plan preveía un `MlKitScannerRepositoryTest` con
> `mockkStatic(GmsDocumentScanningResult::class)`. Se descartó: obliga a mockear una estática de
> GMS y deja el test atado al motor, que es justo lo que queremos evitar. En su lugar se adelantó
> **una pieza pequeña del paso 4**: extraer la decisión a una función pura (`ScanResultMapper`).
> Eso hace el paso 2 verificable sin Robolectric ni mocks estáticos, y es exactamente lo que pide
> la «prueba del algodón» del análisis.

## Paso 1 · Borrar código muerto ✅

Borrar antes de refactorizar: a menudo el código muerto se lleva el acoplamiento por delante.

- [x] `SaveScannedDocumentUseCase.kt:53-70` — las dos sobrecargas muertas, **una de ellas es la
      fuga V2** (`import GmsDocumentScanningResult`).
- [x] `HomeContract.kt:27` — `mostRecentScan`, nunca escrito.
- [x] `HomeIntent.kt:18` — `ScanResult`, nunca emitido.
- [x] `HomeViewModel.kt:84` — la rama que lo manejaba.

**Verificación**: compila + tests verdes.
**Riesgo**: muy bajo. **Ganancia inmediata**: desaparece el acoplamiento P1 más visible.

## Paso 2 · Arreglar B1, B2, B3 y B4 ✅

- [x] Extraer `ScanResultMapper` (puro, sin tipos de framework) desde `MlKitScannerMapper`.
- [x] **B1**: sacar la decisión de cancelación fuera del `try`; el tipo `ScanCancelled` se conserva.
- [x] **B3**: un resultado sin PDF ya no es un éxito con `Uri.EMPTY`, es un `ScanFailed` explícito.
- [x] **B2**: `HomeViewModel.observeScanner` distingue `ScanCancelled` (→ `SCAN_CANCELLED`) de
      cualquier otro fallo (→ `SCAN_FAILED`, tipo nuevo).
- [x] **B4**: un fallo real muestra un mensaje al usuario; una cancelación sigue siendo silenciosa.
- [x] Borrar `MlKitScannerMapper.kt` (su lógica queda repartida entre el mapper puro y dos accesos
      `?.` en el repositorio).

**Verificación**: los tests del paso 0 pasan a verde.

> ⚠️ **Cambio de comportamiento deliberado.** Los paneles que miren `scan_cancelled` cambiarán de
> valor: hasta ahora ese evento incluía los fallos reales. El evento nuevo es `scan_failed`.
> Conviene avisar antes de desplegar.

## Paso 3 · Introducir el contrato (solo archivos nuevos) ✅

- [x] Crear `domain/scanner/`: `DocumentScanner`, `ScanRequest` (+ `ScanOutputFormat`),
      `ScanOutcome` (+ `ScanError`), `ScanDraft`, `ScanArtifact`, `ScannerCapabilities`,
      `ContentRef`.
- [x] Nada cableado todavía: el camino viejo sigue intacto y en producción.

**Verificación**: compila. **Riesgo**: nulo.

> El paquete `domain/scanner/` se mantiene autocontenido a propósito: en el paso 8 se convierte en
> `:scanner-api` con un simple movimiento de directorio.

## Paso 4 · Implementar el motor ML Kit contra el contrato ⏳

El camino viejo sigue vivo y en producción durante todo este paso.

- [ ] `ActivityResultHost` + `ActivityResultHostImpl`.
- [ ] `MlKitDocumentScanner : DocumentScanner`.
- [ ] Ampliar `ScanResultMapper` para devolver `ScanOutcome` en vez de `Result<RawScanResult>`.
- [ ] Tests: `MlKitDocumentScannerTest` con un `FakeActivityResultHost`.

**Riesgo**: nulo (código aún no alcanzable).

## Paso 5 · Cablear el shell ⏳ ⚠️ **paso de mayor riesgo**

- [ ] `MainActivity`: quitar `GmsDocumentScanner` y `ScannerRepository`; registrar el launcher y
      cedérselo al host.
- [ ] `ScannerManager` sigue existiendo, pero ahora lo alimenta `MlKitDocumentScanner`.

**Verificación — E2E manual obligatoria**:
- [ ] Escanear desde el FAB
- [ ] **Escanear desde el widget**
- [ ] Cancelar con el botón atrás
- [ ] Rotar durante el escaneo
- [ ] Escanear con la app enviada a segundo plano

**Riesgo**: medio-alto. Aquí se mueve de verdad la fontanería de lanzar/recibir. Hacerlo en un
commit aislado y fácil de revertir.

## Paso 6 · Mover el ViewModel al puerto ⏳

- [ ] `HomeViewModel` pasa de `scannerManager.requestScan()` + `scanResult.collect` a
      `when (documentScanner.scan(request))`.
- [ ] Borrar `ScannerManager`, `ScannerRepository`, `MlKitScannerRepository`, `RawScanResult`,
      `gmsScannerModule`, `mlKitModule`.
- [ ] `HomeViewModelTest` pasa a usar un **`FakeDocumentScanner`** con desenlaces programables.

> **Cuidado**: preservar el guard de reentrada `isScanning` (`HomeViewModel.kt:74`), que ya tiene
> test.

**Riesgo**: medio. Los tests existentes cubren los cuatro caminos (lanzar, reentrada, éxito, fallo).

## Paso 7 · Limpiar la persistencia ⏳

- [ ] `SaveScanDraftUseCase(storage: DocumentStorage, catalog: DocumentCatalog)`: sin `Context`,
      sin `FileProvider`, sin `App`, sin entidad Room.
- [ ] `FileProvider` y FileKit pasan a `DocumentStorageImpl` en `data`.
- [ ] `LocalDocumentsRepository.saveDocument` recibe un modelo de dominio (`NewScannedDocument`) en
      vez de `ScannedDocumentEntity` (**V5**).
- [ ] `SaveScanDraftUseCaseTest` con fakes — primer test JVM del paso de persistencia.

> **La tabla Room no cambia**: solo cambia el tipo Kotlin en la frontera. Sin migración de esquema.
> Verificar que `app/schemas/` no genere diff.

**Riesgo**: medio. Mitigación: el test nuevo se escribe *antes* de mover el código.

## Paso 8 *(opcional)* · Módulos Gradle ⏳

- [ ] Extraer `:scanner-api` y `:scanner-mlkit`.
- [ ] Sacar `libs.gms.mlkit.docscanner` de `app/build.gradle.kts:113`.
- [ ] *Contract test* compartido que todo motor futuro debe pasar.

**Riesgo**: bajo pero ruidoso (muchos imports cambian de paquete).

## Paso 9 *(opcional)* · Resiliencia a muerte de proceso ⏳

Hoy, si el sistema mata el proceso durante el escaneo, el `Channel` del `ScannerManager` se pierde.
Con el puerto suspendido pasa lo mismo (la corrutina vive en `viewModelScope`).

> **No es una regresión: es un hueco preexistente.** Se documenta aquí para no confundirlo con un
> efecto de la migración.

- [ ] Marcar «escaneo en vuelo» en `SavedStateHandle`.
- [ ] Que el host re-entregue el `ActivityResult` que `registerForActivityResult` **sí** recupera
      tras la recreación.

---

## Tests del plan

| Test | Paso | Qué protege |
|---|---|---|
| `HomeViewModelTest` · cancelación y fallo | 0 | B1, B2, B4 y el contrato de UX de cancelar |
| `ScanResultMapperTest` | 0 | Los 4 desenlaces del mapeo, como función pura |
| **`FakeDocumentScanner`** *(no es un test: es infraestructura)* | 4 | **La pieza clave**: simular cualquier motor sin GMS ni dispositivo |
| `MlKitDocumentScannerTest` + `FakeActivityResultHost` | 4 | La fontanería IntentSender, aislada |
| `HomeViewModelTest` reescrito sobre el fake | 6 | Sustituye mocks por un doble de verdad |
| `SaveScanDraftUseCaseTest` | 7 | Primer test del guardado; hoy: cero |
| *Contract test* de `DocumentScanner` | 8 | Suite compartida que todo motor futuro debe pasar |

## Riesgos de regresión y mitigación

| Riesgo | Dónde | Mitigación |
|---|---|---|
| **Widget dispara escaneo duplicado** | `MainActivity.handleIntent:184-187` con `launchMode="singleTask"`; el `intent.action = null` es el único guard | Test explícito del consumo una-sola-vez; probar rotación tras entrar por widget |
| **Se pierde el guard de reentrada** | `HomeViewModel.kt:74` | Ya cubierto por test; no borrarlo en el paso 6 |
| **La URI de ML Kit caduca** | El PDF vive en la caché de GMS con un grant temporal | **No diferir el copiado**: el guardado debe seguir ocurriendo en el camino del resultado, nunca en background diferido |
| **Cambio de semántica de analítica** | Paso 2 | Avisado arriba; el evento nuevo es `scan_failed` |
| **FileProvider / authority** | `filepathsprovider.xml`, `App.getAuthority` | **No tocar** durante esta fase |
| **Esquema Room** | Paso 7 | Solo cambia el tipo en la frontera; verificar que `app/schemas/` no genere diff |
| **Rotación durante el escaneo** | Paso 5 | E2E manual + `adb shell settings put global always_finish_activities 1` |

## Nota sobre la documentación existente

`AGENTS.md` y `ARCHITECTURE_PLAN.md` afirman *«keep ViewModel scanner-API-agnostic via
ScannerManager»* como si la regla estuviera cumplida, pero `MainActivity.kt:54` y
`SaveScannedDocumentUseCase.kt:18` la incumplían al escribir el análisis.

- [x] El paso 1 elimina el incumplimiento de `SaveScannedDocumentUseCase`.
- [ ] El paso 5 elimina el de `MainActivity`. **Actualizar ambos documentos al completarlo.**
