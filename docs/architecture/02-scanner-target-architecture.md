<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Fase 3 · Subsistema de escaneo — Arquitectura objetivo

> Basado en el [análisis 01](01-scanner-analysis.md). El plan para llegar hasta aquí está en
> [03-scanner-migration-plan.md](03-scanner-migration-plan.md).
>
> **Objetivo**: poder sustituir el motor de escaneo sin tocar el resto de la app.

---

## 1. La decisión de diseño clave

El escaneo está partido en dos mitades —lanzar y procesar— porque la API de ML Kit lo obliga
(`IntentSender` + `ActivityResult`).

> **La abstracción debe borrar esa partición.** El dominio debe ver **una sola llamada suspendida**
> que devuelve un resultado; el mecanismo de Activity Result queda enterrado en la capa de datos.

Todo lo demás en este documento se deriva de ahí.

## 2. Capas

```
┌─ presentation ──────────────────────────────────────────────┐
│ HomeViewModel  →  val outcome = documentScanner.scan(req)   │  ← una línea
└─────────────────────────────┬───────────────────────────────┘
                              │ DocumentScanner (puerto)
┌─ domain ────────────────────┴───────────────────────────────┐
│ DocumentScanner · ScanRequest · ScanOutcome · ScanDraft     │
│ ScanArtifact · ScanError · ScannerCapabilities              │  ← 0 imports de Android
│ SaveScanDraftUseCase(DocumentStorage, DocumentCatalog)      │
└─────────────────────────────┬───────────────────────────────┘
┌─ data ──────────────────────┴───────────────────────────────┐
│ MlKitDocumentScanner : DocumentScanner                      │  ← único sitio con ML Kit
│   ├── GmsDocumentScannerOptions / GmsDocumentScanning       │
│   ├── ActivityResultHost (puerto de shell)                  │
│   └── ScanResultMapper (función PURA, testeable)            │
│ DocumentStorageImpl (FileProvider, FileKit)                 │
│ RoomDocumentCatalog (entidad Room; no sale de aquí)         │
└─────────────────────────────┬───────────────────────────────┘
┌─ app shell ─────────────────┴───────────────────────────────┐
│ MainActivity: registra el launcher y lo cede al host.       │
│ NO conoce ML Kit. NO conoce el escáner.                     │
└─────────────────────────────────────────────────────────────┘
```

## 3. El contrato de dominio

```kotlin
// feature/docscanner/domain/scanner/DocumentScanner.kt
interface DocumentScanner {
    /** Qué sabe hacer este motor. Permite a la UI adaptarse sin saber cuál es. */
    val capabilities: ScannerCapabilities

    /** Escanea y suspende hasta tener un desenlace. Nunca lanza por cancelación. */
    suspend fun scan(request: ScanRequest): ScanOutcome
}
```

`ScanOutcome` es **sellado**, no `Result<T>`: la cancelación es un desenlace normal, no un fallo —
que es exactamente lo que el bug **B1** demuestra que el modelo actual no sabe expresar.

```kotlin
sealed interface ScanOutcome {
    data class Completed(val draft: ScanDraft) : ScanOutcome
    data object Cancelled : ScanOutcome
    data class Failed(val error: ScanError) : ScanOutcome
}

sealed interface ScanError {
    /** El módulo del motor no está disponible ni es descargable (GMS ausente, etc.). */
    data object EngineUnavailable : ScanError
    /** Reservado para motores in-app: permiso de cámara denegado. */
    data object PermissionDenied : ScanError
    /** El motor terminó «bien» pero no produjo salida — hoy: bug B3. */
    data object NoOutputProduced : ScanError
    data class Engine(val cause: Throwable) : ScanError
}
```

## 4. Modelos de dominio propios

```kotlin
/**
 * Localizador opaco. Mantiene android.net.Uri fuera del dominio;
 * solo la capa de datos sabe resolverlo.
 */
@JvmInline value class ContentRef(val value: String)

data class ScanDraft(
    val artifacts: List<ScanArtifact>,
    val capturedAtEpochMillis: Long,   // inyectado, NO System.currentTimeMillis() por defecto
) {
    val pdf: ScanArtifact.Pdf?
        get() = artifacts.filterIsInstance<ScanArtifact.Pdf>().firstOrNull()
}

sealed interface ScanArtifact {
    data class Pdf(val content: ContentRef, val pageCount: Int) : ScanArtifact
    data class Pages(val contents: List<ContentRef>) : ScanArtifact   // motores que dan imágenes
}

data class ScanRequest(
    val outputs: Set<ScanOutputFormat> = setOf(ScanOutputFormat.PDF),
    val maxPages: Int? = null,
    val allowGalleryImport: Boolean = true,
)

enum class ScanOutputFormat { PDF, JPEG }

data class ScannerCapabilities(
    val supportedOutputs: Set<ScanOutputFormat>,
    val supportsGalleryImport: Boolean,
    val maxPages: Int?,
    val requiresCameraPermission: Boolean,   // hoy false (GMS); un motor CameraX diría true
)
```

> `ScanArtifact.Pages` y `requiresCameraPermission` **no se usan hoy**. Existen precisamente para
> que el día del cambio de motor no haya que tocar el contrato.

### Por qué `ContentRef` y no `Uri`

`android.net.Uri` no puede instanciarse en un test de JVM puro (devuelve `null` sin Robolectric) —
razón por la que `HomeViewModelTest:89` tiene que hacer `mockk<Uri>()`. Un `value class` sobre
`String` mantiene el dominio testeable y deja la resolución a la capa de datos, que es la única que
sabe qué significa el localizador.

## 5. Aislamiento de ML Kit en la capa de datos

```kotlin
// core/presentation/ActivityResultHost.kt — único puente con el shell
interface ActivityResultHost {
    suspend fun launch(request: IntentSenderRequest): ActivityResult
}

// data/scanner/MlKitDocumentScanner.kt — ÚNICO archivo con imports de ML Kit
class MlKitDocumentScanner(
    private val clientFactory: (ScanRequest) -> GmsDocumentScanner,
    private val host: ActivityResultHost,
    private val clock: () -> Long,
) : DocumentScanner {

    override val capabilities = ScannerCapabilities(
        supportedOutputs = setOf(ScanOutputFormat.PDF, ScanOutputFormat.JPEG),
        supportsGalleryImport = true,
        maxPages = null,
        requiresCameraPermission = false,
    )

    override suspend fun scan(request: ScanRequest): ScanOutcome =
        runCatching {
            val sender = clientFactory(request).getStartScanIntent(activity).await() // Task→suspend
            host.launch(IntentSenderRequest.Builder(sender).build())
        }.fold(
            onSuccess = { ScanResultMapper.map(it, clock()) },   // función PURA
            onFailure = { ScanOutcome.Failed(ScanError.Engine(it)) },
        )
}
```

`ScanResultMapper.map(...)` como **función pura** es lo que hace testeable el mapeo (incluidos B1 y
B3), cosa que hoy no lo es.

`MainActivity` queda sin una sola referencia al escaneo:

```kotlin
private val resultHost: ActivityResultHostImpl by inject()
private val launcher = registerForActivityResult(StartIntentSenderForResult()) {
    resultHost.deliver(it)
}
// onCreate:  resultHost.attach(launcher)
// onDestroy: resultHost.detach()
```

### Qué desaparece

`ScannerManager` · `ScannerRepository` · `MlKitScannerRepository` · `RawScanResult` ·
`MlKitScannerMapper` · `MainActivity.kt:54, 57-67, 119, 172-181`.

## 6. Inyección de dependencias (Koin)

Los dos módulos actuales (`gmsScannerModule` + `mlKitModule`, registrados en `App.kt:48,50`) se
funden en uno:

```kotlin
val documentScannerModule = module {
    single<ActivityResultHostImpl> { ActivityResultHostImpl() }
    single<ActivityResultHost> { get<ActivityResultHostImpl>() }

    // ── El único punto de intercambio de motor de toda la app ──
    single<DocumentScanner> {
        MlKitDocumentScanner(host = get(), clock = System::currentTimeMillis)
    }
}
```

**Cambiar de motor = cambiar esa línea.** Y permite un `CompositeDocumentScanner(primary, fallback)`
para degradar cuando GMS no esté disponible, sin que el dominio se entere.

## 7. Refuerzo opcional: frontera física en Gradle

Con todo dentro de `:app`, la regla «no filtrar ML Kit» es una convención que hay que vigilar en
cada PR. Extrayendo dos módulos:

- **`:scanner-api`** → contrato + modelos. **Cero** dependencias de Android y de ML Kit.
- **`:scanner-mlkit`** → implementación. Única con `libs.gms.mlkit.docscanner`.
- **`:app`** → depende de `:scanner-api`; solo su módulo Koin conoce `:scanner-mlkit`.

Así **el compilador impide** la regresión. Es la fase final opcional del plan, no un requisito.

## 8. Cómo encaja el patrón existente

| Elemento del proyecto | Cómo se respeta |
|---|---|
| MVI (`BaseViewModel`) | Sin cambios. `HomeIntent.LaunchScanner` sigue igual; solo cambia lo que hace el handler |
| Koin | Sin cambios de estilo; un módulo menos |
| Navigation 3 | No se toca |
| Room / esquema | **No se toca la tabla.** Solo cambia el tipo Kotlin en la frontera del repositorio |
| FileProvider / authority | **No se toca** |
| Widget `ACTION_SCAN_DOCUMENT` | Sigue entrando por `MainActivity`; solo cambia a quién llama |
