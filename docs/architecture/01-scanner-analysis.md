<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Fase 1 · Subsistema de escaneo — Análisis y diagnóstico

> **Fecha del análisis**: 2026-09-19
> **Commit base**: `84f27b6`
> **Alcance**: desde que el usuario pulsa «escanear» hasta que el documento queda persistido y
> visible en Home.
>
> Este documento es una **fotografía del código en esa fecha**. No se reescribe según avanza la
> migración: el progreso se registra en el [plan de migración](03-scanner-migration-plan.md).

---

## 1. Mapeo

### 1.1 Patrón ya consolidado

El proyecto **ya tiene** un patrón arquitectónico maduro y coherente. No hace falta proponer uno
nuevo; la propuesta de la fase 3 lo respeta.

- **MVI** vía `BaseViewModel<Intent, State, Effect>`
  (`app/src/main/java/com/bobbyesp/docucraft/core/util/viewModel/BaseViewModel.kt`): reducer
  (`setState`), canal de `Effect` y canal separado de `UiEvent`.
- **Clean Architecture por feature**: cada feature tiene `data/ · domain/ · presentation/ · di/`.
- **DI: Koin** (no Hilt), arrancado en `App.kt:39`.
- **Navegación**: Navigation 3 tipada, con secciones contribuidas por feature (`homeSection`).

> **Conclusión clave**: el problema no es la ausencia de arquitectura. Es que la abstracción del
> escáner está **partida en dos mitades** —lanzar y procesar— y ninguna de las dos vive en el
> dominio.

### 1.2 Inventario de elementos

Rutas relativas a `app/src/main/java/com/bobbyesp/docucraft/`.

| Archivo | Responsabilidad hoy | Depende de | ¿Mezcla responsabilidades? |
|---|---|---|---|
| `feature/docscanner/presentation/screens/home/HomeContent.kt:185-206` | FAB «Escanear» + spinner | `HomeIntent` | No |
| `feature/docscanner/presentation/screens/home/HomeScreen.kt` | Colecta efectos y eventos | ViewModel | No |
| `feature/docscanner/presentation/screens/home/viewmodel/HomeViewModel.kt:73-82, 188-259` | Lanza escaneo, colecta resultado, persiste, analítica | `ScannerManager`, `SaveScannedDocumentUseCase`, `AnalyticsHelper` | Sí: orquestación + política de analítica + mapeo de errores |
| `feature/docscanner/domain/ScannerManager.kt` | Bus de eventos singleton VM↔Activity | Coroutines | Sí: es infraestructura viviendo en `domain/` |
| `MainActivity.kt:54, 57-67, 119, 172-181` | **Inyecta el cliente ML Kit, pide el IntentSender, lanza y procesa** | `GmsDocumentScanner`, `ScannerRepository`, `ScannerManager` | **Sí, grave**: el shell de la app conoce ML Kit |
| `feature/docscanner/domain/repository/ScannerRepository.kt` | Contrato «procesar resultado» | `androidx.activity.result.ActivityResult` | Sí: contrato de dominio moldeado por el mecanismo de ML Kit |
| `feature/docscanner/data/repository/MlKitScannerRepository.kt` | Mapea `ActivityResult` → `RawScanResult` | ML Kit | Capa correcta, pero contiene el bug **B1** |
| `feature/docscanner/data/mapper/MlKitScannerMapper.kt` | `GmsDocumentScanningResult` → `RawScanResult` | ML Kit | Capa correcta, pero contiene **B3** |
| `feature/docscanner/di/GmsScannerModule.kt` | Opciones + cliente GMS + `ScannerManager` | ML Kit | Sí: mezcla el motor concreto con un componente agnóstico |
| `feature/docscanner/domain/model/RawScanResult.kt` | Modelo «resultado crudo» | Compose `@Immutable` | Sí: modelo de dominio anotado para Compose; `timestamp` no determinista |
| `feature/docscanner/domain/usecase/SaveScannedDocumentUseCase.kt` | Nombra, copia, miniatura, FileProvider, inserta en Room | **ML Kit**, `Context`, `FileProvider`, `App`, entidad Room, FileKit | **Sí, grave**: 5 responsabilidades + fuga de ML Kit en dominio |
| `feature/docscanner/domain/usecase/CopyDocumentToFileUseCase.kt`, `GenerateDocumentThumbnailUseCase.kt` | I/O de archivos | `Context`, FileKit | Ubicados en `domain/` siendo puro Android |
| `feature/docscanner/domain/usecase/ShareDocumentUseCase.kt`, `OpenDocumentInViewerUseCase.kt`, `ExportDocumentUseCase.kt` | Intents / diálogo del sistema | `Context`, `Intent`, `Environment`, FileKit dialogs | Sí: `ExportDocumentUseCase:29` abre **UI** desde un «use case» |
| `feature/docscanner/domain/repository/LocalDocumentsRepository.kt` + `data/repository/LocalDocumentsRepositoryImpl.kt` | Persistencia | Room, `Uri` | Sí: `saveDocument` recibe la **entidad Room** (`:84`) |
| `feature/docscanner/presentation/widgets/ScanDocumentWidget.kt:239-251` | Entrada alternativa vía `ACTION_SCAN_DOCUMENT` | `MainActivity` | No |
| `feature/docscanner/domain/service/DocumentOperationsService.kt` | Render de página a imagen | `Bitmap`, `File` | Sí: tipos Android en contrato de dominio |

#### Responsabilidad ausente: permisos

**No existe ninguna capa de permisos.** Hoy no hace falta: ML Kit corre en el proceso de Google
Play Services y gestiona la cámara él mismo; `AndroidManifest.xml` **no declara `CAMERA`**.

Si mañana el motor es CameraX propio, hará falta el permiso y un flujo de UI que **hoy no tiene
dónde vivir**. La abstracción objetivo debe contemplarlo desde el diseño, aunque no se use aún.

### 1.3 Flujo de control y datos

```
 1. HomeContent.kt:204        FAB → onAction(HomeIntent.LaunchScanner)
 2. HomeViewModel.kt:74       guard `if (isScanning) return` → setState(isScanning = true)
 3. HomeViewModel.kt:80       analytics SCAN_STARTED → scannerManager.requestScan()
 4. ScannerManager.kt:46      Channel<Unit>.send()           ══ frontera: dominio → shell ══
 5. MainActivity.kt:119       scanRequest.collect { launchScanner() }
 6. MainActivity.kt:173       scannerClient.getStartScanIntent(this)      ← GMS Task API
 7.                             .addOnSuccessListener → scannerLauncher.launch(IntentSenderRequest)
                                .addOnFailureListener → scannerManager.onScanResult(failure)
 8.  [ Activity de GMS fuera de proceso: cámara, recorte, filtros, exportación a PDF ]
 9. MainActivity.kt:58        StartIntentSenderForResult callback → ActivityResult
10. MainActivity.kt:60        scannerRepository.processResult(result)     ══ shell → datos ══
11. MlKitScannerRepository:17 GmsDocumentScanningResult.fromActivityResultIntent(...)
12. MlKitScannerMapper.kt:12  .pdf?.uri / .pdf?.pageCount → RawScanResult
13. MainActivity.kt:63        scannerManager.onScanResult(Result)         ══ shell → dominio ══
14. HomeViewModel.kt:189      scanResult.collect → processScanResult
15. SaveScannedDocumentUseCase.kt:72
                              copy → validar tamaño → FileProvider URI → miniatura → insert Room
16. Room emite → ObserveDocumentsUseCase → combine() → HomeUiState.visibleDocuments
```

**Entrada alternativa (widget)**:
`ScanDocumentActionCallback:245` → `Intent(ACTION_SCAN_DOCUMENT)` → `MainActivity.handleIntent:184`
→ `requestScan()` → entra en el paso 4.

> **Lo importante del diagrama**: el flujo cruza **cuatro veces** la frontera dominio/framework
> (pasos 4-5, 9-10, 13-14) y en dos de esos cruces viaja un tipo que no es de dominio.

### 1.4 Puntos de acoplamiento directo con ML Kit

| # | Ubicación | Tipo de fuga |
|---|---|---|
| **A** | `MainActivity.kt:38, 54, 173-180` | Import + inyección de `GmsDocumentScanner` y uso de la **Task API** de GMS (`addOnSuccessListener`) en el shell de la app |
| **B** | `SaveScannedDocumentUseCase.kt:18, 61-70` | `import GmsDocumentScanningResult` y sobrecarga `invoke(scanPdfResult: GmsDocumentScanningResult.Pdf, …)` **dentro de `domain/usecase`** |
| **C** | `ScannerRepository.kt:6, 10` | El contrato de dominio recibe `androidx.activity.result.ActivityResult`: la **forma** del contrato la dicta el mecanismo IntentSender de ML Kit |
| **D** | `GmsScannerModule.kt:9-13, 19-28` | `GmsDocumentScanner` y `GmsDocumentScannerOptions` publicados como singletons globales del grafo Koin → cualquiera puede inyectarlos (y **A** lo hace) |
| **E** | `MlKitScannerRepository.kt:12, 17` | *Legítimo* (capa de datos), pero contiene **B1** |
| **F** | `MlKitScannerMapper.kt:8, 12` | *Legítimo* (capa de datos), pero contiene **B3** |
| **G** | `app/build.gradle.kts:113` | La dependencia ML Kit está en `:app`: **nada impide por compilación** que vuelva a filtrarse |
| **H** | `RawScanResult.kt` solo modela *un PDF + nº de páginas* | Es exactamente lo que devuelve `RESULT_FORMAT_PDF`. No hay hueco para páginas/imágenes que otros motores sí producen |

---

## 2. Diagnóstico

### 2.1 Violaciones de separación de responsabilidades

#### V1 · `MainActivity` conoce el motor de escaneo
`MainActivity.kt:54, 172-181`

La Activity inyecta el cliente concreto y habla su Task API. Cambiar de motor obliga a tocar el
shell de la app, que no tiene nada que ver con escanear. Además el `addOnSuccessListener` no está
ligado al ciclo de vida: si la Activity se destruye entre la petición y el callback, se lanza un
`IntentSender` contra un launcher muerto.

#### V2 · Fuga de ML Kit en la capa de dominio
`SaveScannedDocumentUseCase.kt:18, 61-70`

Un caso de uso de dominio importa un tipo de ML Kit. Contradice literalmente la regla del propio
`AGENTS.md` («keep ViewModel scanner-API-agnostic»). **Además es código muerto**: el único llamante
es `HomeViewModel.kt:238`, con `RawScanResult`. La sobrecarga de `:53` también está muerta.

#### V3 · El contrato de dominio habla Activity Result
`ScannerRepository.kt:10`

`processResult(result: ActivityResult)` solo tiene sentido para un motor que devuelve por
`startActivityForResult`. Un motor in-app (CameraX) no tiene `ActivityResult` que pasar: **el
contrato no le sirve**. Es una abstracción que no abstrae.

#### V4 · `SaveScannedDocumentUseCase` hace cinco cosas
`SaveScannedDocumentUseCase.kt:72-124`

Nombrar el archivo, copiar bytes, validar tamaño, construir URI de FileProvider, generar miniatura
e insertar en Room. Depende de `Context`, `FileProvider`, la clase `App` (singleton estático) y de
la **entidad Room** de la capa de datos.

**Hoy es imposible testearlo en JVM pura** sin Robolectric — y eso es precisamente el síntoma.

#### V5 · La entidad Room atraviesa el dominio
`LocalDocumentsRepository.kt:7, 84` y `ScannedDocument.kt:10, 59`

El contrato de dominio recibe `ScannedDocumentEntity` para guardar pero devuelve `ScannedDocument`
para leer: asimetría que obliga al dominio a importar `data/db/entity`. Y el modelo de dominio
contiene el mapper desde la entidad, invirtiendo la dependencia.

#### V6 · `ScannerManager` es infraestructura disfrazada de dominio
`domain/ScannerManager.kt`

Es un event bus singleton con `Channel.BUFFERED` + `receiveAsFlow()`, que es **single-consumer**:
si en el futuro dos state holders observaran `scanResult`, los eventos se repartirían entre ellos
en vez de duplicarse. Declarado además dentro de `gmsScannerModule` (`GmsScannerModule.kt:30`),
mezclando lo agnóstico con lo específico.

#### V7 · Casos de uso que son Android puro
`ShareDocumentUseCase`, `OpenDocumentInViewerUseCase`, `CopyDocumentToFileUseCase`,
`ExportDocumentUseCase`

Todos en `domain/usecase/` pero con `Context`, `Intent`, `Environment`. `ExportDocumentUseCase:29`
abre un **diálogo del sistema**: es UI, no dominio.

#### V8 · Modelos de dominio contaminados
- `RawScanResult.kt:6` — `@Immutable` de Compose.
- `ScannedDocument.kt:6-7` — `android.net.Uri` + `@Immutable`.
- `DocumentOperationsService.kt:6, 9` — `Bitmap` y `File` en el contrato.

#### V9 · Analítica acoplada al ViewModel
`HomeViewModel.kt:79, 90-101, 109-120, 192-204, 240-251`

Nueve bloques de `logEvent` intercalados en la lógica de estado, inflando el ViewModel y sus tests.

### 2.2 Bugs descubiertos durante el mapeo

#### B1 · La cancelación nunca llega como cancelación
`MlKitScannerRepository.kt:20 → :28`

```kotlin
return try {
    val gmsResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
    if (gmsResult == null) {
        throw ScannerException.ScanCancelled()      // ← lanzado DENTRO del try…
    } else { … }
} catch (e: Exception) {
    Result.failure(ScannerException.ScanFailed(…))  // ← …y capturado aquí
}
```

Cuando el usuario cancela con el botón atrás (`data == null`), el `ScanCancelled` se convierte en
`ScanFailed("Scan was cancelled by the user")`. **El tipo de error se pierde por completo.**

#### B2 · La analítica no distingue cancelar de fallar
`HomeViewModel.kt:192-204`

`observeScanner` registra `SCAN_CANCELLED` en **cualquier** `onFailure`. Combinado con B1, hoy es
imposible saber desde los datos cuántos escaneos fallan de verdad.

#### B3 · Éxito vacío silencioso
`MlKitScannerMapper.kt:13-14`

Si `pdf` es `null`, mapea a `Uri.EMPTY` / `pageCount = 0` y lo reporta como **éxito**. El fallo
reaparece más abajo como error genérico de copia en `SaveScannedDocumentUseCase:86`.

#### B4 · Un fallo real de escaneo es invisible para el usuario
`HomeViewModel.kt:192-207`

La rama `onFailure` de `observeScanner` solo apaga `isScanning`; **nunca emite un `UiEvent`**. Si
el escaneo falla de verdad (no cancelación), el spinner del FAB se para y no ocurre nada más. El
usuario no recibe ninguna señal.

### 2.3 Priorización por impacto

| Prio | Problema | Qué bloquea **hoy** |
|---|---|---|
| **P0** | **V1 + V3 + D** — launch en la Activity, contrato con `ActivityResult`, cliente en el grafo global | **Bloquea el cambio de motor.** Un motor in-app no encaja en este contrato y el shell habría que reescribirlo |
| **P0** | **V4** — `SaveScannedDocumentUseCase` con `Context`/`FileProvider`/entidad Room | **Bloquea el testing**: el paso crítico de persistencia no tiene ni un test |
| **P1** | **V2** — ML Kit en dominio | Fuga documental y mental; trivial de eliminar (es código muerto) |
| **P1** | **B1 + B2 + B4** | Bug de UX visible + datos de producto inservibles |
| **P2** | **V5, V6** | Fricción de mantenimiento; no bloquean |
| **P2** | **G** — dependencia ML Kit en `:app` | Nada impide que la fuga vuelva; solo se resuelve con módulos Gradle |
| **P3** | **V7, V8, V9** | Deuda de higiene, aislable en otra fase |

> **Prueba del algodón**: *«¿puedo testear el paso crítico en JVM pura?»* Hoy, no. Ahí está el P0
> real.

---

## Siguientes documentos

- [Fase 3 · Arquitectura objetivo](02-scanner-target-architecture.md)
- [Fase 4 · Plan de migración](03-scanner-migration-plan.md)
