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
| 4 | Implementar el motor ML Kit contra el contrato | Nulo | ✅ Hecho |
| 5+6 | Cablear el shell y mover el ViewModel al puerto | **Medio-alto** | ✅ Hecho y **verificado en dispositivo** |
| 7 | Limpiar la persistencia | Medio | ✅ Hecho y verificado en dispositivo |
| 8 | Módulos `:scanner-api` / `:scanner-mlkit` | Bajo | ✅ Hecho |
| 9 | Resiliencia a muerte de proceso | Bajo | ⚠️ Hecho, pendiente de verificar en dispositivo |

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

## Paso 4 · Implementar el motor ML Kit contra el contrato ✅

El camino viejo sigue vivo y en producción durante todo este paso.

- [x] `ActivityResultHost` (`core/presentation/activityresult/`) + `ActivityResultHostImpl`.
- [x] `MlKitDocumentScanner : DocumentScanner` en `data/scanner/`.
- [x] `ScanResultMapper.toOutcome(...)`; el `map(...)` antiguo **delega** en él, así que solo hay
      una tabla de decisión que razonar.
- [x] `kotlinx-coroutines-play-services` declarada explícitamente: ya llegaba transitivamente, pero
      depender de eso para una API de compilación es frágil.
- [x] Clasificación de errores: `ApiException` con código de `ConnectionResult` → `EngineUnavailable`
      (no hay reintento que lo arregle); el resto → `ScanError.Engine`. **Sin verificar en
      dispositivo**: hay que confirmarlo en el paso 5.

**Riesgo**: nulo (código aún no alcanzable).

> **Desviación**: no se escribió `MlKitDocumentScannerTest` con `FakeActivityResultHost`. Lo poco
> que aportaría exige mockear las estáticas de GMS (`GmsDocumentScanning.getClient`,
> `GmsDocumentScanningResult.fromActivityResultIntent`), justo lo que se descartó en el paso 0. La
> parte que sí tiene decisiones —la tabla— está cubierta por `ScanResultMapperTest`, y el resto es
> fontanería que solo el E2E del paso 5 prueba de verdad.

## Pasos 5 y 6 · Cablear el shell y mover el ViewModel al puerto ⚠️

> **Los dos pasos se fusionaron durante la implementación.** El paso 5 tal y como estaba escrito no
> funciona: si `MainActivity` llama al puerto suspendido desde `repeatOnLifecycle(STARTED)`, lanzar
> el escáner de GMS para la Activity, se cancela el bloque, y con él el escaneo en vuelo. El código
> anterior no sufría esto porque `launchScanner()` era *fire-and-forget*. La llamada suspendida
> tiene que vivir en `viewModelScope`, que era el paso 6 — y en medio no quedaría nadie lanzando el
> escáner. **Es el tipo de fallo que solo aparece al implementar.**

- [x] `MainActivity` ya no conoce el escaneo: registra el launcher, se lo presta al
      `ActivityResultHost` y lo suelta en `onDestroy`.
- [x] `HomeViewModel` llama a `documentScanner.scan()` en `viewModelScope`, que sobrevive a que el
      escáner tape la app y a que la Activity se recree debajo.
- [x] `ScannerManager` (bus bidireccional petición+resultado) se reduce a `ScanRequestBus`: solo la
      señal de «alguien de fuera de la UI pide un escaneo», que es lo único que el widget necesita.
- [x] Borrados `ScannerRepository`, `MlKitScannerRepository`, `ScannerException`, `GmsScannerModule`
      y el camino legacy de `ScanResultMapper`.
- [x] `gmsScannerModule` + `mlKitModule` → un solo `documentScannerModule`.
- [x] `HomeViewModelTest` sobre un `FakeDocumentScanner`: los tres desenlaces y la vía del widget,
      sin GMS ni dispositivo.

**Resultado**: ML Kit vive ahora en **un único archivo**, `data/scanner/MlKitDocumentScanner.kt`.

### ✅ Verificado en dispositivo (2026-09-19)

Escaneo desde el FAB, desde el widget, cancelación y rotación durante el escaneo: todo correcto.

### B5 · Un fallo al guardar felicitaba al usuario

Encontrado al reescribir `processScanResult`. `SaveScannedDocumentUseCase` devuelve `Result<Uri>`
en vez de lanzar, y el ViewModel **descartaba el valor**: si la copia o el insert fallaban, el
usuario veía igualmente «documento guardado correctamente». Arreglado y cubierto por test.

## Paso 7 · Limpiar la persistencia ⚠️

- [x] `SaveScanDraftUseCase(storage, repository)` sustituye a `SaveScannedDocumentUseCase`: recibe
      un `ScanDraft` y no menciona `Context`, `FileProvider`, `App` ni la entidad Room. Solo
      contiene el **orden de las operaciones**.
- [x] Nuevo puerto `DocumentStorage` (+ `StoredDocument`). `DocumentStorageImpl` absorbe
      `CopyDocumentToFileUseCase`, `GenerateDocumentThumbnailUseCase` y la lógica de `FileProvider`.
- [x] Nuevo modelo `NewScannedDocument`: el lado de escritura de `ScannedDocument`, sin id porque
      nadie se lo ha asignado todavía.
- [x] `LocalDocumentsRepository.saveDocument` recibe dominio, no `ScannedDocumentEntity` (**V5**).
- [x] `toModel()` sale de `ScannedDocument` a `data/mapper/ScannedDocumentMapper.kt`: el dominio ya
      no importa `data/db/entity` (**V5**, segunda mitad).
- [x] Borrados `RawScanResult` y el puente temporal del paso 5+6.
- [x] `SaveScanDraftUseCaseTest`: **6 tests, los primeros que ha tenido nunca el paso de guardado.**

> **La tabla Room no ha cambiado**: solo el tipo Kotlin en la frontera. `app/schemas/` sin diff.

### Bug encontrado al implementar: el spinner no se apagaba al guardar

Al eliminar `processScanResult` se fue con él el `isScanning = false` del camino de éxito. Lo
detectó el test `a completed scan clears isScanning and saves the document`. Arreglado, y de paso
mejor estructurado: el spinner refleja «hay una sesión en vuelo», así que ahora se apaga **en un
solo sitio**, justo donde la sesión termina, en vez de repetirse en cada rama.

### Nota sobre mockk y `Result`

Re-stubear un mock cuyo retorno es `Result<T>` (value class) **no surte efecto**: mockk conserva la
primera respuesta. Hay que configurar el resultado al construir, no después. Si aparece un test que
«ignora» su `coEvery`, es esto.

### ✅ Verificado en dispositivo (2026-09-19)

Escaneo, miniatura, visor, compartir, exportar, borrar y documentos antiguos: todo correcto.

### Lo que este paso deliberadamente no toca

`ShareDocumentUseCase`, `OpenDocumentInViewerUseCase`, `ExportDocumentUseCase` y
`DeleteDocumentUseCase` siguen siendo Android puro dentro de `domain/usecase/`, y los modelos
siguen llevando `Uri` y anotaciones de Compose. Son **V7 y V8** del análisis, priorizados como P3 y
fuera del alcance del subsistema de escaneo. Merecen su propia fase.

## Paso 8 · Módulos Gradle ✅

Hasta aquí, «no filtrar ML Kit» era una convención que había que vigilar en cada PR. Ahora es un
error de compilación.

- [x] **`:scanner-api`** — Kotlin plano, **sin plugin de Android y sin una sola dependencia**.
      Contiene `DocumentScanner` y su vocabulario, en `com.bobbyesp.scanner`.
- [x] **`:scanner-mlkit`** — librería Android con `MlKitDocumentScanner`, `ActivityResultHost` y el
      mapeo de resultados, en `com.bobbyesp.scanner.mlkit`. La dependencia de GMS es
      `implementation`, no `api`: por eso no alcanza a `:app`.
- [x] `:app` pierde `libs.gms.mlkit.docscanner` y depende de los dos módulos.
- [x] `ScanRequestBus` se queda en `:app`: es una señal de la aplicación, no parte del contrato del
      motor, y no tenía por qué viajar a la API.

### Verificación: las fronteras tienen dientes

No basta con que compile; hay que comprobar que **impide** lo que debe impedir. Se añadió un archivo
sonda a cada módulo y se confirmó que el compilador lo rechaza:

| Sonda | Resultado |
|---|---|
| `import com.google.mlkit...` en `:app` | `e: Unresolved reference 'mlkit'` ✅ |
| `import android.net.Uri` en `:scanner-api` | `e: Unresolved reference 'android'` ✅ |

Ambas sondas se eliminaron después. **49 tests** en todos los módulos, 0 fallos.

### Nota de Gradle

El plugin de Kotlin ya llega por `buildSrc`, así que `alias(libs.plugins.kotlin.jvm)` falla con
«already on the classpath with an unknown version». Hay que usar
`id(libs.plugins.kotlin.jvm.get().pluginId)`, que es el idioma que el proyecto ya usa para los
plugins de AGP.

## Paso 9 · Resiliencia a muerte de proceso ⚠️

El escáner corre en el proceso de Play Services y **sigue vivo** aunque el sistema mate el nuestro
por memoria. Hasta ahora, el usuario terminaba el escaneo y la app tiraba el documento a la basura
al volver.

### Qué sobrevive y qué no

`registerForActivityResult` ya hacía su parte: el *registry* guarda las peticiones pendientes en el
`Bundle` de la Activity y **re-entrega** el resultado cuando la Activity se recrea. Lo que no puede
hacer es encontrar la corrutina que esperaba, porque murió con el proceso. Faltaban dos piezas:

- [x] **Que el resultado no se tire.** `ActivityResultHostImpl` guarda el resultado que llega sin
      nadie esperándolo, en vez de descartarlo.
- [x] **Que alguien vuelva a por él.** `DocumentScanner.resumePendingScan()` (con implementación por
      defecto `null`, para motores que no pueden sobrevivir a su llamante) y el flag
      `scan_in_flight` en el `SavedStateHandle` del `HomeViewModel`.

### El detalle que evita que se cuelgue

Si el flag estuviera obsoleto —el proceso murió *antes* de lanzar nada— esperar un resultado que no
existe dejaría el spinner girando para siempre. Por eso `hasPendingLaunch` viaja en el **`Bundle` de
la propia Activity**: es el mismo mecanismo que preserva la petición pendiente del registry, así que
los dos sobreviven, o dejan de hacerlo, juntos. Si no hay nada pendiente, `awaitPendingResult()`
devuelve `null` inmediatamente.

También hubo que contemplar la carrera al revés: el registry re-entrega durante el `onCreate`, que
puede **adelantarse** a que la Activity restaure su flag. Por eso `restorePendingLaunch` no pisa un
resultado ya recibido. Hay un test por cada orden.

### ⚠️ Pendiente de verificación en dispositivo

Los tests cubren la lógica, pero no la muerte de proceso de verdad:

```
1. Iniciar un escaneo y dejar el escáner abierto
2. adb shell am kill com.bobbyesp.docucraft.debug
3. Terminar el escaneo
4. La app se reconstruye → el documento debe aparecer guardado
```

- [ ] Lo anterior
- [ ] Con «No conservar actividades» activado en Opciones de desarrollador
- [ ] Matar el proceso **antes** de que aparezca el escáner (el flag obsoleto): el spinner debe
      apagarse solo, no quedarse girando

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

- [x] El paso 1 eliminó el incumplimiento de `SaveScannedDocumentUseCase`.
- [x] El paso 5+6 eliminó el de `MainActivity`.
- [x] **Ambos documentos actualizados** en el paso 8, con la estructura de cuatro módulos, el flujo
      real de escaneo y el punto único de intercambio de motor.

---

## Pendientes tras la migración

Todo cerrado. Lo que quedaba abierto al terminar esta fase se resolvió en la
[fase 2](04-domain-purity-plan.md):

- **V7 / V8** — los use cases que eran Android puro y los modelos contaminados. Hecho.
- **Miniaturas `.png` que en realidad eran WEBP** — hecho, en el apéndice de la fase 2.
