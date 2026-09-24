<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Fase 3 · Visor PDF — Análisis y diagnóstico

> **Fecha del análisis**: 2026-09-23
> **Commit base**: `54ced5a`
> **Alcance**: desde que el usuario abre un documento (desde Home o desde otra app) hasta que ve sus
> páginas y actúa sobre ellas; más la viabilidad de dos funcionalidades nuevas —**copiar texto** y
> **abrir enlaces**— y la preparación del terreno para el **OCR** que llegará en una fase posterior.
>
> Este documento es una **fotografía del código en esa fecha**. No se reescribe según avanza la
> migración: el diseño está en la [arquitectura objetivo](07-pdfviewer-target-architecture.md) y el
> progreso en el [plan de migración](08-pdfviewer-migration-plan.md).
>
> La numeración de bugs (`B`) y violaciones (`V`) es propia de este documento.

---

## Resumen

1. **No hace falta cambiar el motor de renderizado.** `:composepdf` usa
   `android.graphics.pdf.PdfRenderer`, que hoy solo se usa para rasterizar. Pero esa misma clase
   expone texto, enlaces y enlaces internos desde API 35 (`getTextContents()`, `getLinkContents()`,
   `getGotoLinks()`) sobre el mismo `PdfRenderer.Page` que el motor ya abre (§4).
2. **La cobertura queda condicionada por `minSdk = 24`.** Con texto nativo solo hay API 35+ (y
   posiblemente 31–34 vía `PdfRendererPreV` con SDK Extension; se confirma en el paso *a* del plan).
   Por debajo, copiar texto degrada igual que en un documento escaneado.
3. **`androidx.pdf` no encaja hoy**: 1.0.0-beta01, `PdfViewer` de Compose todavía
   `@ExperimentalPdfApi`, y exige minSdk 28 (§4.3).
4. **La premisa «la lógica está bien» solo es cierta a medias.** El motor es sólido; el feature
   `pdfviewer` **no tiene ViewModel ni capa de lógica** (§3).
5. **Todos los documentos que produce Docucraft son PDF de solo imagen** (§6): son exactamente el
   caso que resolverá el OCR, y el caso que la versión de hoy tiene que degradar con elegancia.

---

## 1. Mapeo

### 1.1 Patrón ya consolidado

Se respeta lo que ya existe, no se propone nada nuevo:

- **MVI** vía `BaseViewModel<Intent, State, Effect>` (`core/util/viewModel/BaseViewModel.kt`), con
  dos tuberías de salida de semántica distinta (`effects` descartables, `defaultEvents` que esperan).
- **Koin** para DI, con `ViewModel` por entrada de navegación (`parametersOf` desde la key).
- **Navigation 3**: una pila, un `NavDisplay`, keys `@Serializable` que llevan solo identidad,
  destinos modales en la pila (`OverlaySceneStrategy`).
- **Puertos con implementación en módulo aparte** cuando hay un SDK externo (`:scanner-api` +
  `:scanner-mlkit`): el grafo de Gradle impide la regresión.

### 1.2 Inventario

| Capa | Archivo | Responsabilidad | ¿Más de una? |
|---|---|---|---|
| Key | `feature/pdfviewer/navigation/PdfViewerKey.kt:14` | `PdfViewer(documentUuid)` | No |
| Entrada | `feature/pdfviewer/presentation/PdfViewerSection.kt:35-58` | Observa el catálogo; `Loading/Gone/Open`; adapta a `BasicDocument` | No |
| Host externo | `feature/pdfviewer/presentation/PdfViewerActivity.kt:45-127` | `ACTION_VIEW/SEND` de otras apps; `BasicDocument` sintético | No |
| Pantalla | `feature/pdfviewer/presentation/screens/PdfViewerScreen.kt` | Estado, chrome, analítica, acciones, sheet | **Sí, todas** |
| Chrome | `components/toolbar/PdfViewerTopBar.kt`, `PdfViewerBottomToolbar.kt` | Barra superior tipo píldora; floating toolbar | Poco |
| Sheet | `components/PdfDetailsSheet.kt` | `ModalBottomSheet` local + consultas al `ContentResolver` | **Sí** |
| «Dominio» | `feature/pdfviewer/domain/PdfDocumentActions.kt`, `PdfPrintDocumentAdapter.kt` | Intents, `FileProvider`, `PrintManager` | Es framework, no dominio |
| API motor | `composepdf/.../PdfViewer.kt`, `PdfViewerState.kt`, `PdfViewerSpecs.kt`, `PdfViewerStyle.kt`, `PdfTapEvent.kt` | Composable, estado elevado, specs inmutables | No |
| Orquestación | `composepdf/.../internal/logic/PdfViewerController.kt` | Ciclo de vida del documento, transformaciones, animaciones (`MutatorMutex`) | Hub deliberado |
| Geometría | `internal/logic/PageLayoutSnapshot.kt`, `ViewerViewportCoordinator.kt` | Layout a zoom 1, hit-test, clamp. **Puro y con tests JVM** | No |
| Render | `internal/engine/RenderEngine.kt`, `PlanComputer.kt`, `TileStore.kt`, `PageBitmapStore.kt`, `BitmapPool.kt`, `WorkQueue.kt` | Planificador, 2 workers, tiles por nivel, caché, pool | No |
| Acceso al PDF | `internal/service/pdf/PdfDocumentManager.kt` | Pool de 2 `PdfRenderer` con FD duplicado y semáforo | No |
| Gestos | `internal/ui/gesture/PdfGestures.kt` | Una máquina de estados: pan, fling, pinch, doble tap, quick scale, tap, long press | No |
| Dibujo | `internal/ui/PdfDocumentCanvas.kt`, `PdfPageLoadingOverlay.kt` | Un nodo de dibujo; pan/zoom leídos en draw/layout | No |

### 1.3 Traza del flujo

Los cruces de frontera van marcados con **⟶ [tipo que viaja]**.

1. `HomeSection.kt:47` → `navigator.goTo(PdfViewer(uuid))` **⟶ [`NavKey` con `String`]**
2. `PdfViewerSection.kt:36-42` → `ObserveDocumentUseCase(uuid)` **⟶ [`ScannedDocument?`]**
3. `PdfViewerSection.kt:89-96` → `toBasicDocument()` **⟶ [`BasicDocument`]**. Aquí se **pierden**
   `pageCount`, `sizeBytes` y `thumbnail`.
4. `PdfViewerScreen.kt:106-107` → `PdfSource.Uri(documentInfo.uri.toUri())` **⟶ [`PdfSource`: de
   `:app` a `:composepdf`]**
5. `PdfViewer.kt:111-114` → `remember(context, state) { PdfViewerController(...) }`; `:126` →
   `LaunchedEffect(source, controller) { loadDocument(source) }`
6. `PdfViewerController.kt:97-109` → `state.reset()` + `documentSession.open(source)`
7. `PdfDocumentSession.kt:60-69` → `PdfDocumentManager.open` (`:78-107`) → `PdfSourceResolver` →
   `ParcelFileDescriptor` → `PdfRenderer(fd)` ×2 **⟶ [framework]**
8. `PdfDocumentManager.kt:150-157` → `getAllPageSizes()` **⟶ [`List<Size>`]**. Es **lo único que se
   lee del documento** aparte de los píxeles.
9. `RenderEngine.requestPlan()` → `PlanComputer` → `WorkQueue` → workers →
   `PageRenderer.kt:17-46` → `page.render(bitmap, …, RENDER_MODE_FOR_DISPLAY)` **⟶ [`Bitmap`]**
10. `PdfDocumentCanvas` dibuja leyendo `panX/panY/zoom` en `drawBehind`.
11. Gestos → `controller.panBy/zoomTo` → `PdfViewerState` → `requestPlan()`.
12. Tap → `controller.tapEventAt` (`PdfViewerController.kt:132-151`) **⟶ [`PdfTapEvent` con
    posición de página normalizada]** → `PdfViewerScreen.kt:113-130` alterna el chrome.

Ruta externa: `PdfViewerActivity.kt:103-127` → `BasicDocument(uuid = UUID.randomUUID(), …)` →
`PdfViewerScreen` (paso 4 en adelante).

---

## 2. Localizar el acoplamiento

| Pregunta del checklist | Respuesta |
|---|---|
| ¿Dónde se importa `PdfRenderer`? | `composepdf/.../PdfDocumentManager.kt:7`, `PageRenderer.kt` y, fuera del visor, `feature/docscanner/data/service/DocumentOperationsServiceImpl.kt` (miniaturas). Encapsulado. |
| ¿Tipos de `:composepdf` en `domain/`? | No. Solo en `presentation/` (`PdfViewerScreen`, `PdfViewerBottomToolbar`), que es donde deben estar. |
| ¿La **forma** del contrato la dicta el mecanismo? | Sí, y es la causa de fondo: el contrato de `:composepdf` es **«un documento son páginas-imagen con tamaño»**. No existe el concepto de contenido de página. |
| ¿Qué hace el SDK por nosotros y desaparecería? | Hoy nada relevante. Si se migrara a `androidx.pdf`, **la selección, el menú contextual y el OCR vivirían dentro del visor**: responsabilidades que habría que recuperar al salir de él. |
| ¿Dependencia en qué módulo? | `PdfRenderer` es de plataforma; `:composepdf` es un módulo propio. No hay SDK de terceros de renderizado. |

---

## 3. Diagnóstico

### 3.1 Violaciones

#### V1 · El feature no tiene capa de lógica · **P0**

No existe `PdfViewerViewModel`. `PdfViewerScreen.kt:63-76` guarda en `remember` seis variables
(`areControlsVisible`, `isTopBarVisible`, `hasScrolled`, `showDetails`, `fitMode`,
`isNightModeEnabled`). La analítica se construye en línea en los callbacks (`:192-229`) y las
acciones se instancian en composición (`:67`).

*Por qué duele*: nada se puede testear en JVM; los ajustes no sobreviven a una rotación (**B2**); y
la premisa de «reescribir la UI sin tocar la lógica» no se puede cumplir porque **no hay lógica que
conservar**. Bloquea la decisión D2 (ajustes por sesión y globales), que necesita un sitio donde
vivir.

#### V2 · `feature/pdfviewer/domain/` es framework · **P1**

`PdfDocumentActions.kt:6-15` importa `Activity`, `Intent`, `FileProvider`, `PrintManager` y `App`;
`PdfPrintDocumentAdapter.kt` es un `PrintDocumentAdapter`. Es exactamente lo que la
[fase 2](04-domain-purity-plan.md) sacó de `docscanner/domain/`.

#### V3 · Compartir está implementado dos veces · **P1**

`PdfDocumentActions.share` (`:34`) frente al puerto `DocumentSharer` +
`feature/docscanner/data/sharing/AndroidDocumentSharer.kt`, ya existente.

#### V4 · El sheet de detalles incumple la regla de navegación · **P1**

`PdfViewerScreen.kt:234-240` + `PdfDetailsSheet.kt:65`: un `ModalBottomSheet` con estado local, que
el usuario puede abrir, cerrar y volver a abrir. `AGENTS.md` exige que sea un destino en la pila. Además
`PdfDetailsSheet.kt:60-63,126` consulta el `ContentResolver` desde un composable para recalcular un
tamaño que el catálogo ya tiene (**V5**).

#### V5 · `BasicDocument` es un adaptador con pérdidas · **P2**

El paso 3 de la traza tira `pageCount`, `sizeBytes` y `thumbnail`. Por eso los detalles recalculan
el tamaño con I/O y el número de páginas depende de que el motor haya terminado de cargar.

#### V6 · La máquina de gestos está cerrada · **P0 para las funcionalidades nuevas**

- `PdfGestures.kt:138`: `awaitFirstDown(requireUnconsumed = false)` y nunca se comprueba el
  consumo. **Un hijo del `overlay` no puede quedarse un arrastre**; el documento hace pan debajo.
  Esto impide los tiradores de selección.
- `PdfGestures.kt:153-184`: tras disparar el long press el gesto sigue en `UNDECIDED` y un arrastre
  pasa a `PAN`. Una selección necesita lo contrario: que el long press **reclame** el gesto.
- `PdfGestures.kt:229-243`: con el doble tap activo, `onTap` espera a que venza
  `doubleTapTimeout`. Un enlace respondería con retraso.

#### V7 · El documento vive atado a la composición · **P2**

`PdfViewer.kt:111-114` crea el controller con `remember(context, state)`. Cada recreación de la
Activity crea un controller nuevo, que reabre el fichero, vuelve a leer los tamaños y vacía las cachés.
Junto con **B1**, eso devuelve al usuario a la página 1.

#### V8 · La vista del visor se registra desde Home · **P3**

`HomeScreen.kt:46` registra `logScreenView("PdfViewer")` en el tap de Home. Las aperturas desde
`PdfViewerActivity` no se registran nunca.

#### V9 · El viewport se redimensiona por frame · **P2**

`PdfViewerScreen.kt:90-103,131-135`: la altura de la barra superior se calcula a mano con insets y
se aplica como `padding(top = animado)` al visor. Cada frame de la animación dispara
`onViewportSizeChanged` → reconstrucción del `PageLayoutSnapshot` + clamp. `hasScrolled` nunca
vuelve a `false`.

### 3.2 Bugs encontrados durante el mapeo

#### B1 · La restauración de posición no funciona nunca

`rememberPdfViewerState` guarda página, zoom y pan (`PdfViewerState.kt:219`, `saver`). Pero tras
recrear la Activity, `loadDocument` llama a `state.reset()` (`PdfViewerController.kt:99` →
`PdfViewerState.kt:140`), que pone todo a cero. Resultado: rotar en `MainActivity` (sin
`configChanges`), o volver tras una muerte de proceso, lleva a la página 1 con zoom 1. El `Saver` es
código muerto en la práctica.

#### B2 · Modo de ajuste y modo noche se pierden al rotar

Consecuencia directa de V1: son `remember`, no `rememberSaveable` ni estado de un ViewModel.

#### B3 · El botón atrás se anuncia como «Cancelar»

`PdfViewerTopBar.kt:102`: `contentDescription = stringResource(R.string.cancel)`.

#### B4 · El error de carga muestra el nombre de la excepción

`PdfViewerDefaults.kt:98-117` muestra `error::class.simpleName` y un texto fijo en inglés. Un PDF
protegido con contraseña (`SecurityException`) le enseña al usuario la palabra
«SecurityException».

### 3.3 Sospechas a verificar (no confirmadas leyendo)

- **S1** · `PdfViewerActivity` es `singleTask` sin `taskAffinity` propia y su back llama a
  `finishAffinity()` (`PdfViewerActivity.kt:88`). Si Docucraft estaba en segundo plano, podría
  cerrar también la tarea de `MainActivity`.
- **S2** · `PdfViewerBottomToolbar.kt:109` aplica `WindowInsets.ime` además del padding de la barra
  de navegación que pone su contenedor: posible doble inset con el teclado abierto.
- **S3** · En tablet, abrir Ajustes (escena que reemplaza el layout) y volver previsiblemente
  recompone el visor → B1 → página 1.

### 3.4 Priorización

| Prioridad | Qué | Por qué ahí |
|---|---|---|
| **P0** | V1, V6 | Sin V1 no hay dónde poner D2 ni las funcionalidades; sin V6 no hay selección ni enlaces |
| **P1** | B1, B2, V2, V3, V4 | Bugs visibles y reglas del proyecto rotas (dominio puro, modales en la pila) |
| **P2** | V5, V7, V9, B3, B4 | Calidad percibida y rendimiento |
| **P3** | V8 | Solo desinforma |

**Prueba del algodón** — *¿se puede testear en JVM el paso crítico?* Hoy no: la selección y la
resolución de enlaces no existen, y el estado del visor vive en un composable. En la arquitectura
objetivo sí: selección, resolución de enlaces y resolución de ajustes son lógica pura.

### 3.5 ¿La separación lógica/UI es tan buena como parece?

**En `:composepdf`, sí.** Geometría pura y testeada (`PageLayoutSnapshotTest`,
`ViewerViewportCoordinatorTest`, `PlanComputerTest`), motor de render aislado, API pública con estado
elevado y specs inmutables, y `PdfTapEvent.kt:15-24` ya entrega **coordenadas de página
normalizadas**: el espacio exacto que necesitan enlaces y selección. `PdfPageLoadingOverlay.kt:53-85`
ya demuestra el patrón correcto para superponer cosas a las páginas (colocación en fase de layout
leyendo pan/zoom, sin recomponer).

**En `feature/pdfviewer`, no.** No hay nada que reutilizar bajo una UI nueva; hay que crearlo (V1).

---

## 4. Viabilidad técnica de texto y enlaces

### 4.1 Qué produce hoy el motor

Solo píxeles: `PageRenderer.kt:22,44` → `page.render(...)`. El documento abierto solo aporta tamaños
(`PdfDocumentSession.kt:60-69`). **Hoy el código no extrae texto ni enlaces en ningún sitio.**

### 4.2 Qué ofrece la plataforma

`android.graphics.pdf.PdfRenderer.Page`, desde API 35:

| Método | Devuelve |
|---|---|
| `getTextContents()` | Texto de la página con sus límites |
| `getLinkContents()` | Límites y URL de cada enlace externo |
| `getGotoLinks()` | Enlaces internos con destino (página y coordenadas) |
| `selectContent(...)`, `searchText(...)` | Selección y búsqueda nativas |

`PdfRendererPreV` expone las mismas APIs en versiones anteriores con SDK Extension. **Pendiente de
confirmar en el spike (paso *a*)**: el nivel de extensión exacto, la **granularidad** de
`getTextContents()` (¿línea, palabra o carácter?) y el tratamiento de páginas **rotadas**.

Como son métodos del mismo `Page` que `PdfDocumentManager.withPage` ya abre, **extraer contenido es
una lectura más sobre una página abierta, no otro motor.**

### 4.3 Alternativas evaluadas

| Opción | Cobertura | Coste | OCR futuro | Veredicto |
|---|---|---|---|---|
| **A · Motor actual + capa de contenido con APIs de plataforma** | API 35+ (31–34 por confirmar) | Bajo; sin dependencias nuevas | Neutral: el OCR es otro proveedor | ✅ Elegida |
| B · Migrar a `androidx.pdf` | minSdk 28 → obliga a subir el del proyecto | Alto: sustituir `:composepdf` entero (tiles, gestos, estilo, list-detail, tests) | Tiene `OcrProvider` + `MlKitOcrProvider`, **experimentales**; el texto reconocido queda dentro del visor | ❌ Hoy no |
| C · A + PdfBox-Android como proveedor para API < 35 | Todas | Medio: varios MB de APK, lento en documentos grandes | Neutral | ❌ Descartada por D1 |
| MuPDF | Todas | Licencia AGPL | — | ❌ |

Estado de `androidx.pdf` comprobado en sus notas de versión el 2026-09-23: **1.0.0-beta01
(2026-08-26)**. `PdfViewer`, `PdfViewerState`, `EditablePdfViewerFragment`, `AnnotationsView` y
`OcrProvider` siguen siendo `@ExperimentalPdfApi`. El backport a minSdk 28 cubre «read and
rendering»; la selección de imágenes exige SDK Extension 19. La interceptación de enlaces existe
desde alpha10. Para Compose exige AGP ≥ 9.2.0.

### 4.4 Compatibilidad con el OCR

- **Opción A: sin contraindicaciones**, siempre que la selección se calcule con un **modelo propio**
  (palabras con rectángulos normalizados), no con `selectContent()`.
- **Contraindicación clara, sea cual sea la opción**: construir la selección sobre
  `PdfRenderer.Page.selectContent()`. Solo conoce el texto propio del PDF y **bloquearía** el OCR.
- **Opción B, contraindicación parcial**: el OCR quedaría atado a un API experimental y a un modelo
  interno. No se ha podido confirmar que un `OcrProvider` propio pueda servir resultados ya
  calculados y guardados (p. ej. para que Home busque por contenido).

---

## 5. Estado de la UI frente a M3 Expressive

| Elemento | Hoy | Problema |
|---|---|---|
| Barra superior | `Surface` píldora `primaryContainer` (`PdfViewerTopBar.kt:83-91`) | Estética Expressive hecha a mano; B3 |
| Barra inferior | `HorizontalFloatingToolbar` vibrant | Métricas propias en 3 tramos con `BoxWithConstraints` (`PdfViewerBottomToolbar.kt:109,401-453`); icon buttons de 32 dp en compacto |
| Páginas | Anterior/siguiente + `BasicTextField` | Saltos instantáneos (`:127,148`); el campo abre el teclado dentro de la toolbar |
| Zoom | −/+ instantáneos, chip % con reset animado y **arrastre oculto** (`:338-358`) | Incoherente e indescubrible |
| Ajuste | Un botón que cicla 4 modos (`:198-206`) | No anuncia el modo actual |
| Chrome | Tap con tres estados (`PdfViewerScreen.kt:113-130`); el scroll oculta solo la barra superior | Asimétrico e impredecible |
| Hueco superior | Padding animado (V9) | Relayout por frame |
| Carga | `LoadingIndicator` | ✅ Correcto |
| Error | `PdfViewerDefaults.ErrorContent` | B4; sin acciones |
| Motion | `MaterialTheme.motionScheme` | ✅ En el origen; mezcla specs a mano en dos sitios |
| Documentos largos | — | No hay fast scroller |

---

## 6. Del escaneo al PDF

- `MlKitDocumentScanner.kt:88-96` pide `SCANNER_MODE_FULL` con `RESULT_FORMAT_PDF` (el
  `ScanRequest` por defecto solo pide PDF).
- El PDF de ML Kit **incrusta cada página como imagen JPEG y no tiene capa de texto.**
- `SaveScanDraftUseCase.kt:29-53` lo copia a `filesDir/…` y lo cataloga con una URI de
  `FileProvider`. Las imágenes de página no se guardan (`ScanArtifact.Pages` existe en el contrato
  pero no se usa).
- `gms-mlkit-text-recognition` ya está declarado (`gradle/libs.versions.toml:146`) y no lo usa nadie.

**Consecuencia**: todo documento propio de Docucraft dará «sin texto» hasta que llegue el OCR.

---

## 7. Fricción con las áreas ya estabilizadas

1. **Un documento recién escaneado no se abre en el visor, y hoy no podría.**
   `HomeViewModel.kt:235` solo notifica. `SaveScanDraftUseCase` devuelve `Result<ContentRef>` y
   `LocalDocumentsRepository.saveDocument` devuelve `Unit` (`:55`): no hay uuid con el que navegar
   a `PdfViewer(uuid)`.
2. **V5**: el visor pierde datos del catálogo.
3. **V4**: el sheet local incumple la regla de destinos modales. En `PdfViewerActivity` no hay pila
   (se resuelve con D5).
4. **B1 + V7**: rotar o volver de una escena que reemplaza el layout devuelve a la página 1.
5. **V8**: la analítica de pantalla vive en Home.
6. **V3**: dos implementaciones de compartir.
7. **Para el OCR**: el escáner no guarda las imágenes de página. El OCR puede rasterizar desde el PDF
   sin tocar el escáner. Si se quisiera más calidad, `ScanArtifact.Pages` ya existe.
8. **Aparte, fuera de alcance**: `PdfViewerActivity` es exportada, acepta `file://` y las acciones del
   visor re-exponen rutas `file://` por `FileProvider` al compartir. Merece una revisión de
   seguridad de intents propia.

---

## Fuentes

- [androidx.pdf — release notes](https://developer.android.com/jetpack/androidx/releases/pdf)
- [`PdfRenderer.Page`](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page)
- [`PdfRendererPreV`](https://developer.android.com/reference/android/graphics/pdf/PdfRendererPreV)
- [Cambios de API 35 en `PdfRenderer.Page`](https://developer.android.com/sdk/api_diff/35/changes/android.graphics.pdf.PdfRenderer.Page)
- [Android PDF viewer (guía)](https://developer.android.com/media/grow/pdf-viewer)

## Siguientes documentos

- [07 · Arquitectura objetivo](07-pdfviewer-target-architecture.md) — diseño y decisiones D1–D5.
- [08 · Plan de migración](08-pdfviewer-migration-plan.md) — pasos, verificación y riesgos.
