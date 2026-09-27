<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Fase 4 · Visor PDF — Plan de migración

> Desde el [análisis 06](06-pdfviewer-analysis.md) hasta la
> [arquitectura objetivo 07](07-pdfviewer-target-architecture.md).
>
> **Regla del plan**: nada de *big bang*. Cada paso compila y se puede verificar por separado, y está
> pensado para caber en un commit revertible. Arreglar un bug y mover código **nunca** van en el
> mismo commit ([checklist](../method/stabilization-checklist.md)).

---

## Orden y por qué

El orden sugerido —**(a)** viabilidad, **(b)** UI sin funciones nuevas, **(c)** copiar texto,
**(d)** enlaces— se mantiene, con dos matices:

- **La lógica se crea en (b), no se «conserva».** El análisis mostró que el feature no tiene capa de
  lógica ([V1](06-pdfviewer-analysis.md#v1--el-feature-no-tiene-capa-de-lógica--p0)). Por eso (b)
  empieza creando el `ViewModel` **sin ningún cambio visual**. Así se cumple el objetivo original de
  (b): demostrar que la lógica se sostiene sola antes de cambiar la UI.
- **Los arreglos del motor que no dependen de la UI van al principio de (b)** (B1, consumo de
  gestos), aislados, para que cualquier regresión posterior no se confunda con ellos.

## Estado del plan

| Paso | Descripción | Riesgo | Estado |
|---|---|---|---|
| 0 | Red de seguridad: PDFs de prueba, test rojo de B1, comprobar S1–S3 | Nulo | ✅ Hecho (pendientes: escaneo neutro, fixtures RTL/CJK) |
| a | Spike de viabilidad de las APIs de contenido | Nulo | ✅ Hecho: se sigue. Internal links confirmed unavailable (2026-09-24) |
| b1 | Motor E1: arreglar B1 | Bajo | ✅ Done (2026-09-24) |
| b2 | Motor E2: los gestos respetan el consumo | Medio | ✅ Done (2026-09-24) |
| b3 | Acciones detrás de puertos (V2, V3) | Bajo | ✅ Done (2026-09-24) |
| b4 | `PdfViewerViewModel` sin cambio visual (V1, B2, V8) | Bajo | ✅ Done (2026-09-24) |
| b5 | D2: ajustes por sesión y globales + pantalla de Ajustes | Medio | ✅ Done (2026-09-24) |
| b6 | D5: pila propia en `PdfViewerActivity` + detalles como destino (V4, V5, B5) | Medio | ✅ Done (2026-09-24) |
| b7 | Motor E6: `contentPadding` (V9) | **Medio-alto** | ✅ Done (2026-09-24) |
| b8 | UI nueva (B3, B4) | Medio | ✅ Done (2026-09-25) |
| c1 | `:document-content-api` + `TextSelection` | Nulo | ✅ Done (2026-09-25) |
| c2 | Proveedores nativo y compuesto + DI con OCR a `null` | Bajo | ✅ Done (2026-09-25), plus cross-page selection |
| c3 | Motor E3 (long press reclamable) + E4 (overlay en coordenadas de página) | **Medio-alto** | ✅ Done (2026-09-25) |
| c4 | UI de selección, portapapeles y degradación | Medio | ✅ Done (2026-09-27) |
| c5 | Character-by-character selection, as in Google Drive *(added 2026-09-27)* | Medium | ✅ Done (2026-09-27) |
| d1 | Enlaces en el proveedor + `ResolveLinkUseCase` | Bajo | ✅ Done (2026-09-27) |
| d2 | Motor E3 (tap reclamable) + E5 (`animateScrollTo`) | Medio | ✅ Done (2026-09-27) |
| d3 | D3 (aviso con dominio) + D4 (Custom Tabs) + accesibilidad | Medio | ✅ Done (2026-09-27) |
| cierre | `AGENTS.md`, `docs/README.md`, verificación en dispositivo | Nulo | ⏳ |

---

## Paso 0 · Red de seguridad

- [x] PDFs de prueba, generados por `testing/pdf-fixtures/generate_fixtures.py` (ver su
  [README](../../testing/pdf-fixtures/README.md)) en `composepdf/src/androidTest/assets/fixtures/`,
  con un `manifest.json` de posiciones esperadas:
  1. ✅ Con capa de texto, enlaces externos (`https`, `http`, `mailto`, `tel`, `javascript:`) e
     internos (con y sin posición de destino), más uno en dos líneas.
  2. ⏳ **Un escaneo real de Docucraft**. Hay uno **simulado** (`scanned-image-only.pdf`); el real
     requiere escanear en un dispositivo.
  3. ✅ Largo: 320 páginas.
  4. ✅ Protegido con contraseña.
  5. ✅ Páginas rotadas y de tamaños mixtos (A4, A5, apaisada, `/Rotate 90`, CropBox).
  6. ⏳ Texto en árabe (RTL) y CJK: requieren fuentes incrustadas con licencia redistribuible.
  7. ✅ Mixto: unas páginas con texto y otras escaneadas.
- [x] `PdfFixturesTest` (instrumentado): los 7 PDF abren con el número de páginas del manifiesto y el
  protegido se rechaza con `SecurityException`. **Verde.**
- [x] Test instrumentado **en rojo** de B1 (`PdfViewerStateRestorationTest`): tras
  `emulateSavedInstanceStateRestore()`, *expected 5 but was 0*. **Rojo por el motivo previsto.**
- [x] Sospechas, comprobadas en el emulador (Pixel 10 Pro XL, API 37):
  - **S1 → confirmado, pasa a ser B5** (ver abajo).
  - **S2 → confirmado en dispositivo real** (Pixel 9 Pro XL, API 37). Sin teclado, la toolbar queda
    a 40 dp del borde (24 dp de barra de navegación + 16 dp de margen). Con el teclado abierto queda
    también a unos 40 dp **por encima del teclado**, cuando deberían ser 16: el inset del IME ya
    incluye la barra de navegación y el contenedor la vuelve a sumar. Son unos 24 dp de más; es
    menor. El paso b8 sustituye el campo de texto por el destino «Ir a página», así que desaparece.
    (En el emulador no se pudo ver: tiene teclado físico y Gboard solo muestra su barra flotante.)
  - **S3 → confirmado en dispositivo real, junto con B2.** Con un documento de 2 páginas abierto en
    la página 2 y el modo noche activado, girar a horizontal (la app pasa a lista y detalle en
    paralelo) deja el visor en la **página 1** y con el **modo noche desactivado**.
- [x] **Los escaneos reales no tienen capa de texto** (Pixel 9 Pro XL, build de debug beta.18).
  Inspeccionados en el propio teléfono sin copiarlos: los 8 PDF de `files/scans/` tienen **0
  `/Font`** y todas sus páginas son imágenes `DCTDecode` (JPEG). Confirma el análisis §6.
  - Observación fuera de alcance: hay 8 PDF en disco y solo 2 documentos en el catálogo.
    Probablemente son restos anteriores al arreglo de B8 (fase 2, «los PDF nunca se borraban»).
  - Los escaneos del dispositivo contienen **datos personales**: no sirven como PDF de prueba
    versionado. El punto 2 de los PDF de prueba sigue pendiente de un escaneo con contenido neutro.
- [x] Capturas de referencia de `PdfViewerActivity` en
  [`assets/pdfviewer-before/`](assets/pdfviewer-before/): vertical, horizontal y tema oscuro.
  El host principal (lista-detalle, modo noche) se ha **visto** en el dispositivo real, pero sus
  capturas **no se guardan**, porque muestran documentos personales. Se rehacen con un documento
  neutro cuando exista.

**Por qué primero.** Sin PDFs de prueba no se puede verificar ningún paso posterior, y el test rojo
documenta el bug antes de arreglarlo.

### B5 · Cerrar el visor externo cierra la app entera *(encontrado en el paso 0)*

`PdfViewerActivity` es `singleTask` y no declara `taskAffinity`, así que comparte la afinidad de la
app. Con Docucraft en segundo plano, abrir un PDF desde otra app lo coloca **en la misma tarea,
encima de `MainActivity`** (comprobado con `dumpsys activity activities`: tarea #38, `MainActivity`
en la raíz). La flecha de volver llama a `finishAffinity()` (`PdfViewerActivity.kt:88`), que termina
todas las actividades con esa afinidad: tras pulsarla no quedaba **ninguna** actividad de Docucraft
viva. El usuario pierde el estado de la app solo por haber abierto un PDF desde fuera.

Se corrige en el paso b6 (D5): `finish()` en la raíz de la pila propia. Decidir allí también si
`PdfViewerActivity` debe tener tarea propia (`taskAffinity` vacía y `documentLaunchMode`), para que un
PDF externo nunca aparezca encima de la biblioteca.

### Observaciones de las capturas

- En horizontal, «página completa» (`BOTH`) deja la página en un tercio del ancho, y la barra inferior
  tapa casi la mitad de la altura visible. Confirma la decisión A2 (ajustar al ancho).
- La flecha de volver se anuncia como «Cancel» en el árbol de accesibilidad (B3, confirmado).

## Paso a · Spike de viabilidad

Solo tests instrumentados; **nada de código de producción**.

- [x] Volcado de `getTextContents()`, `getLinkContents()`, `getGotoLinks()` y `selectContent()` de
  cada PDF de prueba, en el emulador (API 37; las APIs son de API 35).
- [x] Lo verificado queda fijado en `composepdf/.../platform/PlatformContentTest.kt` (4 tests
  verdes). El volcado de diagnóstico sigue en la misma clase, desactivado con `@Ignore`, para
  repetirlo en otra versión de Android.
- [x] ~~Emulador API 33 (`PdfRendererPreV`)~~ → **aplazado por decisión del 2026-09-23**. Se aplica
  D1 tal cual.

**Salida: se sigue adelante**, con un ajuste en el proveedor nativo (el primer hallazgo) y una
incertidumbre en los enlaces internos (el tercero).

### Resultados

| Pregunta | Respuesta |
|---|---|
| **Granularidad del texto** | **Ninguna.** `getTextContents()` devuelve **un único bloque por página**, con las líneas separadas por `\r\n`, y una lista de rectángulos **vacía**. Por sí solo no sirve para seleccionar |
| **¿Cómo se obtiene la geometría?** | Con `selectContent(SelectionBoundary(inicio), SelectionBoundary(fin))`, por **índice de carácter**. Los índices son los del texto de `getTextContents()`, `\r\n` incluidos. Devuelve el texto exacto de la palabra y su rectángulo (caja de tinta del glifo). Las 41 palabras de la página de prueba coinciden |
| **Espacio de coordenadas** | Puntos de la **página tal como se muestra**: CropBox y `/Rotate` aplicados, origen arriba a la izquierda. `page.width`/`height` son las dimensiones mostradas (842×595 en la página con `/Rotate 90`, 495×742 en la del CropBox). **Dividir por ellas da exactamente el espacio normalizado del visor**: diferencia ≤ 0,002 en enlaces y ≤ 0,006 en palabras frente al manifiesto |
| **Coste** | Texto: 14 ms para las **320 páginas** (0,05 ms/página). Enlaces: 4 ms para las 320. Geometría por palabra: **0,07–0,15 ms/palabra** en caliente, es decir 40–75 ms para una página muy densa (unas 500 palabras) |
| **Página de solo imagen** | Un bloque con **texto vacío**. Así se reconoce `NoText`. `getImageContents()` devolvió 0 imágenes también en páginas que sí las tienen: **no sirve** para detectar escaneos |
| **Enlaces externos** | Correctos: URI tal cual (también `javascript:`, que hay que bloquear nosotros) y rectángulos en puntos enteros |
| **Enlaces en varias líneas** | **Un solo rectángulo** que abarca todas las líneas: se ignoran los `QuadPoints`. Puede incluir texto que no es parte del enlace |
| **Enlaces internos** | **`getGotoLinks()` devuelve siempre una lista vacía** para las cuatro formas probadas: `/Dest` con `XYZ` sin zoom, con `Fit`, con zoom explícito, y `/A /GoTo`. No se ha podido comprobar con un PDF generado por una herramienta real (Edge headless está bloqueado en este equipo). **No verificado** |

### Qué cambia en el diseño

1. **Proveedor nativo (paso c2).** No basta con leer `getTextContents()`. El proveedor:
   - parte el texto de la página en líneas (`\r\n`) y palabras;
   - pide la geometría de cada palabra con `selectContent` por índice;
   - lo hace **una vez por página**, fuera del hilo principal, solo para las páginas visibles ±1, y
     guarda el resultado en caché.

   **No contradice la regla de §4.4 de la arquitectura**: `selectContent` se usa *dentro* del
   proveedor para medir palabras; la selección del usuario sigue calculándose con el modelo propio,
   que el OCR alimentará igual.
2. **Enlaces en varias líneas.** Se aceptan como rectángulo único en v1. Mejora posible: cuando la
   página tiene texto, recortar el rectángulo a las palabras que caen dentro, para obtener un
   rectángulo por línea.
3. **Enlaces internos (fase d).** Quedan **condicionados**: si la plataforma no los entrega, un
   enlace interno no será tocable (degradación, como D1). Antes de la fase d hay que repetir la
   prueba con un PDF real que tenga enlaces internos, por ejemplo un índice exportado desde Word o
   Google Docs. Si tampoco llegan, se documenta como limitación de la plataforma.
4. **Detección de escaneos**: por texto en blanco, nunca por `getImageContents()`.

---

## Paso b1 · Motor E1: arreglar B1

- [x] `loadDocument` deja de pisar la posición restaurada: guarda la posición pendiente y la aplica
  cuando el layout está listo.
- [x] El test rojo del paso 0 pasa a verde.

**Verificación**: rotar en `MainActivity` y forzar muerte de proceso (`adb shell am kill`) conservan
página y zoom. **Riesgo: bajo.** Commit propio.

> *From here on, progress notes are written in English (decision of 2026-09-24).*

### Done — 2026-09-24

**What changed.** The position is no longer saved as raw pan, which only means something for the
viewport that produced it. It is saved as a **reading anchor**: the page under the viewport's
leading edge (top when vertical, left when horizontal) and how far into it, as a fraction of its
length along the scroll axis, plus the zoom.

- `PageLayoutSnapshot.anchorAtViewportStart` / `panForAnchor` convert between pan and anchor. They
  are pure, so they are covered on the JVM (6 new tests in `PageLayoutSnapshotTest`, including the
  round trip across two different layouts, which is what a rotation is).
- `PdfViewerState.pendingPosition` holds the restored position, or the one requested through the
  constructor: `rememberPdfViewerState(initialPage, initialZoom)` was overwritten by the same bug
  and now works too.
- `PdfViewerController.applyPendingPosition` takes it up once there are page sizes *and* a measured
  viewport. Either can arrive last, so both paths call it. It runs in the same frame the document
  becomes visible, so page 1 never flashes first. The first load consumes it; a later load (a
  different document) still starts from the top.
- Leading edge rather than centre, because the line at the top is the one a reader expects to find
  where they left it.

**Verification.**
- `PdfViewerStateRestorationTest`: red → **green**. `:composepdf` instrumented suite: 10 tests,
  0 failures, 1 skipped (the diagnostic dump).
- `:composepdf:testDebugUnitTest`: 32 tests, 0 failures (26 before).
- On the emulator, end to end: the external viewer opened on `long-320-pages.pdf`, jump to page 120,
  Home, `adb shell am kill`, back to the task. It comes back showing *Página 120 de 320* at the same
  position.
- Not verified yet: rotation in `MainActivity` on a real device. It needs a build with this change on
  a device with catalogued documents. The mechanism is the same (a recreation with restored state),
  and that is what the instrumented test exercises.

**Revised in step b8 (2026-09-24): the anchor is now the centre of the content area, not the
leading edge.** Anchoring the top line made the *current page* change across a rotation, because the
current page is read at the centre and `scrollToPage` centres pages: on page 200 of 320, rotating
showed 199. Anchoring at the centre keeps the page the reader is on. See "Layout changes without a
recreation" under step b8.

**Known limitation, deliberate.** The cross-axis position is not restored: after a recreation, a
page zoomed in and panned sideways comes back centred across. Restoring it would need a second
anchor, and it only matters above fit zoom.

**Rotation on a real device — 2026-09-24.** Verified by the maintainer on the Pixel 9 Pro XL with this
build: the reading position and the zoom survive rotating `MainActivity` into the list-detail layout.

### Internal links: confirmed unavailable from the platform — 2026-09-24

Step *a* left one question open: whether `getGotoLinks()` reported nothing because of how the
generated fixtures were written. It was settled with `prueba_motor_pdf.pdf`, added by the
maintainer and produced independently by ReportLab (40 pages; a table of contents and "back to
index / go to cover" links on every section, all explicit `/Dest` arrays).

- `getGotoLinks()` returns **no internal links on any page** of any fixture.
- The dump is **byte-for-byte identical** on the emulator and on a real Pixel 9 Pro XL (both API 37,
  MediaProvider module 17). The test APK was installed on the phone only for the dump and removed
  right after.
- External links, text and special characters (accents, `± × ÷`, `« »`, curly quotes) come through
  correctly.

**Consequence for phase d.** Internal links are **unavailable**, not merely unverified. Unless the
platform changes, they will not be tappable, which is the same degradation as D1. Alternatives
(parsing link annotations ourselves, or a bundled PDF library) cost far more than the feature is
worth today, and are left for the moment a minimum SDK raise or a stable `androidx.pdf` makes them
cheap. `PlatformContentTest.internalLinksAreNotReported_revisitIfThisFails` is a canary: it fails
the day the platform starts reporting them.

The platform dump is now opt-in (`-Pandroid.testInstrumentationRunnerArguments.dumpPlatformContent=true`)
instead of `@Ignore`d, so it can actually be run by hand; the command is in its KDoc.

## Paso b2 · Motor E2: respetar el consumo

- [x] `PdfGestures`: si un hijo consume el *down*, el visor no inicia el gesto; si consume los
  arrastres, no hay pan.

**Verificación**: sin overlays interactivos, pan, fling, pinch, doble tap y quick scale se comportan
igual en dispositivo (hoy no hay ningún overlay que consuma, así que no debería cambiar nada visible).
Un test de UI con un overlay que consume. **Riesgo: medio**, porque toca la máquina de gestos. Commit
propio.

### Done — 2026-09-24

**What changed.** Overlay children receive every pointer event before the viewer's gesture layer
(Compose's main pass runs child-first). The viewer now yields to them in three places:

- **The first touch** is consumed by a child (a button, for instance): the viewer does not start a
  gesture at all.
- **Any event before the viewer has decided** what the gesture is (still `UNDECIDED`) is consumed by a
  child: a drag detector that crossed its own slop first, or a click consuming its release. The
  viewer abandons the gesture, delivering no pan, tap or long press. Once the viewer has decided
  (pan or transform), it consumes the pointer itself, so a child can no longer claim it mid-gesture.
- **The second touch of a would-be double tap** is consumed by a child: it is not a double tap, and
  the first touch is delivered as a tap.

**Verification.**
- `PdfGesturesConsumptionTest` (new): a drag kept by an overlay child does not pan; a click on an
  overlay child does not reach `onTap`; a drag elsewhere still pans (control). The first two were
  **red before the fix**, the control green.
- `PdfGesturesTest` (new, the regression net for b2, c3 and d2): tap after the double-tap window,
  double-tap zoom, pinch zoom, quick scale, long press. All green. Pinch is multi-touch, which
  `adb input` cannot produce, hence tests rather than a manual check.
- Real app on the emulator: a slow drag moves from page 1 to 2, a fling to page 11.
- `:composepdf` instrumented suite: 19 tests, 0 failures, 1 skipped (the opt-in dump).

**No visible change in the app today**: nothing it puts in the overlay consumes pointers yet. This is
groundwork for the selection handles (c4) and the link preview (d3).

## Paso b3 · Acciones detrás de puertos (V2, V3)

- [x] `DocumentOpener`, `DocumentPrinter` en dominio; implementaciones en `data/`.
  `PdfPrintDocumentAdapter` sale de `domain/`.
- [x] Compartir usa el `DocumentSharer` existente. `PdfDocumentActions` se borra.
- [x] Dejar de re-exponer `file://` por `FileProvider` (cambio deliberado, arquitectura §5.3).

**Verificación**: compartir, imprimir y abrir con, desde Home y desde `PdfViewerActivity`.
**Riesgo: bajo.**

### Done — 2026-09-24

- Ports in `feature/pdfviewer/domain/actions/DocumentHandOff.kt`: `DocumentOpener`, `DocumentPrinter`,
  and the rule `ContentRef.canBeHandedOff()` (only `content://` leaves the app). Pure Kotlin; the
  rule has a JVM test (`DocumentHandOffTest`).
- Implementations in `feature/pdfviewer/data/actions/`: `AndroidDocumentOpener` (a singleton on the
  application context, like `AndroidDocumentSharer`) and `AndroidDocumentPrinter`, which needs the
  activity and is therefore a Koin `factory` taking `parametersOf(activity)`.
  `PdfPrintDocumentAdapter` moved there unchanged.
- New `pdfViewerModule`, registered in `App.kt`. Sharing reuses the catalogue's `DocumentSharer`
  binding (V3), so there is one share implementation in the app.
- `feature/pdfviewer/domain/` now holds no framework code (V2).
- The screen reads the activity from `LocalActivity` rather than unwrapping `LocalContext`.
- **Deliberate behaviour change**: for a document that cannot leave the app (`file://`), *Share* and
  *Open with* are hidden instead of re-exposing the path through the app's `FileProvider`. *Print*
  stays, since it reads the file itself.

**Verification.** On the emulator, from `PdfViewerActivity` with a `content://` document: *Share*
and *Open with* open the system chooser; *Print* opens the print spooler. The catalogue path shares
through the same `DocumentSharer` the document actions sheet already uses. Unit tests: `app` 90,
`:composepdf` 32, `scanner-mlkit` 11, all green.

## Paso b4 · `PdfViewerViewModel` sin cambio visual (V1, B2, V8)

- [x] ViewModel con `ViewerDocumentRef` por `parametersOf`; `ObserveViewerDocumentUseCase`.
- [x] Estado, analítica y *effects* fuera del composable. La pantalla actual pasa a leer del
  ViewModel **sin cambiar ni un píxel**.
- [x] `logScreenView` se registra en la entrada del visor, no en el tap de Home (V8).
- [x] Tests JVM del ViewModel.

**Verificación**: capturas idénticas a las de referencia; rotar conserva el modo de ajuste y el modo
noche (B2). **Riesgo: bajo.** **Cambio de comportamiento deliberado**: las aperturas desde
`PdfViewerActivity` empiezan a contar en la analítica.

**Por qué antes de la UI nueva.** Es la prueba de que la lógica se sostiene sola: si algo se rompe
aquí, es lógica, no diseño.

### Done — 2026-09-24

**What changed.**
- `PdfViewerViewModel` (`BaseViewModel<PdfViewerIntent, PdfViewerUiState, PdfViewerEffect>`), one per
  navigation entry, keyed by a `ViewerDocumentRef` passed through `parametersOf`. In
  `PdfViewerActivity` it lives in the activity's store, keyed by the document's URI, until step b6
  gives that activity its own back stack.
- `ViewerDocumentRef` (`Catalogued(uuid)` / `External(uri, displayName)`, serializable for b6's keys)
  and `ObserveViewerDocumentUseCase` in `feature/pdfviewer/domain/`. An external document is now keyed
  by its URI instead of a random uuid per intent, which D2 needs.
- `ViewerFitMode` in `core/domain/model/`: the app's own type, mapped to the engine's `FitMode` only
  in the screen. Same constant names, so analytics values do not change.
- The `Loading / Gone / Open` distinction moved from a private type in `PdfViewerSection` into the
  contract (`ViewerDocumentState`). The section still removes its own entry on `Gone`, as B3 of the
  navigation audit requires. The state now lives in the ViewModel, which survives the entry being
  composed afresh.
- The screen holds no business state: fit mode, night mode, share, open with and print go through
  intents; analytics is logged by the ViewModel. Printing is an effect, since it needs the activity.
  What stays in the composition is presentation: chrome visibility, the details sheet flag, and
  `PdfViewerState`.
- The screen view is logged by the ViewModel (V8), so documents opened from other apps count too.
  `HomeScreen` no longer logs it.
- A failed share or open-with now tells the user (same message as the document actions) instead of
  failing silently.

**Verification.**
- `PdfViewerViewModelTest`: 10 JVM tests (loading/gone/open, external documents, settings and their
  analytics, hand-off and its `file://` refusal, failed share, print effect, screen view).
- All unit tests: `app` 100, `:composepdf` 32, `scanner-mlkit` 11, green.
- On the emulator, B2: night mode switched on in the external viewer, then the system theme changed,
  which recreates the activity (`uiMode` is not in its `configChanges`). It comes back with night
  mode on; before this step it came back off. The UI is unchanged.

**Not covered yet, on purpose.** After a *process death* the fit mode and night mode still go back to
their defaults: that is A1, and it belongs to step b5 together with the session memory.

**Pre-existing gap, noted for b6.** In-app messages are shown by the `Toaster` in `MainActivity`
only. In `PdfViewerActivity` they are emitted but not displayed. b6, which gives that activity its
own host, adds one.

## Paso b5 · D2: ajustes por sesión y globales

- [x] `ViewerFitMode` y `ViewerDisplaySettings` en `core/domain/model/`; campos nuevos en
  `UserPreferences` y `SettingsRepository`; claves de DataStore nuevas.
- [x] `ViewerSessionSettings` (puerto) + `InMemoryViewerSessionSettings` (`single`).
- [x] `ObserveViewerDisplaySettingsUseCase` / `UpdateViewerDisplaySettingsUseCase`, con tests JVM que
  cubren **toda la tabla de D2**.
- [x] Siembra desde el `SavedStateHandle` al restaurar (A1: la muerte de proceso es la misma sesión).
- [x] Valores de fábrica: `WIDTH` + noche desactivado (A2). La pantalla deja de sobrescribir el
  modo de ajuste del motor con `BOTH`.
- [x] Destino `DocumentViewerSettings` en `SettingsKeys.kt` + pantalla + entrada en `SettingsScreen`
  + `proguard-rules.pro`.

**Verificación**:
- con el switch activo, dos documentos abiertos arrancan con los predeterminados; cambiar uno no
  afecta al otro; volver al primero conserva su cambio; quitar la app de recientes y reabrirla
  devuelve los predeterminados;
- con el switch inactivo, lo mismo partiendo de los valores de fábrica;
- con muerte de proceso, el visor abierto conserva sus ajustes (A1).

**Riesgo: medio** (preferencias persistentes nuevas).

### Done — 2026-09-24

**What changed.**
- `core/domain/model/ViewerDisplaySettings.kt`: `ViewerDisplaySettings` (fit mode + night mode, with
  `Factory` = fit width, no night mode, per A2) and `ViewerDefaults` (the switch plus the chosen
  defaults; `effective` resolves which applies). `UserPreferences.viewerDefaults`, three new
  `SettingsRepository` operations and three DataStore keys (`viewer_defaults_enabled`,
  `viewer_default_fit_mode`, `viewer_default_night_mode`).
- `ViewerSessionSettings` (port) and `InMemoryViewerSessionSettings`, a Koin `single`: one per process,
  shared by `MainActivity` and `PdfViewerActivity`. Keyed by uuid for catalogued documents and by
  location for external ones.
- `ObserveViewerDisplaySettingsUseCase` resolves *session choice → defaults if enabled → factory*,
  as a flow, and reports whether the result was a choice. `UpdateViewerDisplaySettingsUseCase`
  records one.
- `PdfViewerViewModel` takes its settings from that flow. `PdfViewerUiState.display` is `null` until
  known, and the document is only shown once it is (`readyDocument`), so it is never laid out with
  the factory settings first.
- **A1**: any *choice* the ViewModel sees is copied to its entry's `SavedStateHandle`, and a handle
  that has one seeds the session memory on creation. A document left alone keeps nothing, so it
  goes on following the defaults.
- Settings → **Document viewer** (`DocumentViewerSettings` key, `DocumentViewerSettingsScreen`,
  `DocumentViewerSettingsViewModel`): the switch, the fit mode as four labelled radio rows, and night
  mode. With the switch off, both are shown disabled and the switch explains the factory settings.
  `SettingSwitch` gained an optional `enabled` parameter. Strings in English and Spanish. No
  `proguard-rules.pro` change was needed: keys are kept through the `NavKey` interface since the
  navigation phase.

**Bug found while verifying, fixed before committing.** The first version wrote the settings to
the saved state only in the entry where the user changed them. Changing night mode, closing the
viewer and reopening the same document showed the choice (from the session memory), but the new
entry had nothing saved, so a process death brought the defaults back. The use case now says
whether a value is a choice, and every entry that sees one keeps it.
`aReopenedDocumentKeepsTheSessionsChoiceInItsOwnSavedState` reproduces it.

**Verification.**
- JVM: `ViewerDisplaySettingsTest` covers the D2 table row by row (8 tests); `PdfViewerViewModelTest`
  adds readiness, per-document memory, saved state, the reopen case, and A1 (15 tests in total);
  navigation tests cover the new key. `app`: 115 unit tests, green.
- On the emulator, with the defaults on (whole page, night mode on):
  1. cold start → night mode on (defaults);
  2. night mode switched off in the viewer → off;
  3. viewer closed and reopened in the same session → still off (session memory);
  4. Home, `am kill`, back → still off (A1);
  5. app force-stopped and reopened → on again (defaults).
  With the switch off, a cold start opens without night mode (factory).

**Deliberate behaviour change**: with nothing configured, documents now open fitted to width
instead of as a whole page (A2).

## Paso b6 · D5: pila propia + detalles como destino (V4, V5, B5)

- [x] Extraer del shell los decoradores y estrategias de escena a una función reutilizable, para que
  ambos hosts se comporten igual.
- [x] Keys `ExternalPdfViewer(uri, displayName)` y `PdfDocumentDetails(ref)`; `proguard-rules.pro`.
- [x] `PdfViewerActivity` con `NavDisplay` propio; la raíz se reemplaza en `onNewIntent`; atrás en la
  raíz hace `finish()`, no `finishAffinity()` (B5).
- [x] Detalles como destino, con los datos del catálogo (V5); se borra el `ModalBottomSheet` local.
- [x] Tests de navegación: abrir detalles, atrás, rotar con el sheet abierto, restauración de las
  keys nuevas.

**Verificación**: en ambos hosts, detalles como sheet en móvil y como diálogo en tablet; back
predictivo; abrir un PDF externo con Docucraft en segundo plano no cierra la app (B5).
**Riesgo: medio.**

### Done — 2026-09-24

Three commits, so moving code, fixing B5 and adding the destination can be reverted separately.

1. **`DocucraftNavDisplay`** (refactor, no behaviour change): the entry decorators in the order the
   library requires, back through a `Navigator`, and the transitions from `NavigationMotion`,
   extracted from `DocucraftApp` so a second host renders its stack the same way.
2. **B5** (fix): `PdfViewerActivity` gets `taskAffinity=""`, so it runs in a **task of its own**, as
   the maintainer decided on 2026-09-24. An external document never lands on top of the library.
   `autoRemoveFromRecents` drops its card when it finishes, and the back arrow calls `finish()`
   instead of `finishAffinity()`.
3. **D5, V4, V5**:
   - New keys `ExternalPdfViewer(uri, displayName)` and `PdfDocumentDetails(ViewerDocumentRef)`.
   - `PdfViewerActivity` renders its own back stack through `DocucraftNavDisplay`, with the
     overlay strategy only. A document arriving through `onNewIntent` replaces the stack (the new
     root is added before the rest is removed: a `NavDisplay` must never see it empty).
   - The details are a destination (`pdfDocumentDetailsSection`), registered by both hosts. The
     overlay strategy picks a sheet or a dialog, back closes it, and it survives recreation.
     `PdfDetailsSheet` and the screen's `showDetails` flag are gone.
   - `ObserveViewerDocumentDetailsUseCase`: a catalogued document's details come from the catalogue
     (V5). An external one's are read once from the file through the `DocumentFactsReader` port
     (`AndroidDocumentFactsReader`: size from the provider, page count from `PdfRenderer`). No more
     content-resolver queries from a composable.
   - The external viewer now has its own `Toaster`, so its in-app messages are shown (a gap noted
     in b4).

**Verification.**
- JVM: `ViewerDocumentDetailsTest` (4 tests); the new keys in `NavKeySerializationTest`. `app`: 119
  unit tests, green.
- Emulator, B5: with the library in the background, the external viewer opens in a separate task,
  and its back arrow leaves `MainActivity` alive.
- Emulator, D5: details open as a sheet (320 pages, 116 kB, from the file); they survive an activity
  recreation; back closes only the details, then back at the root closes the viewer and removes its
  Recents card; a second PDF delivered through `onNewIntent` while the details are open replaces the
  stack.
- **Not verified on a device**: the details of a *catalogued* document (the emulator has none; the
  JVM tests cover the mapping) and the external viewer's `Toaster` actually showing a message
  (nothing easy triggers one).

## Paso b7 · Motor E6: `contentPadding` (V9)

- [x] `PdfLayoutSpec.contentPadding`; `clampPan` y `centeredPanForPage` lo tienen en cuenta.
- [x] `PageLayoutSnapshotTest` ampliado.
- [x] La pantalla deja de animar el padding del visor.

**Verificación**: la página 1 no queda tapada por la barra; ya no se reconstruye el layout en cada
frame al mostrar u ocultar barras (Layout Inspector o traza). **Riesgo: medio-alto**, porque toca la
geometría de clamp. **Es el paso de mayor riesgo de (b)**: va aislado y el resto de (b) no depende de
él.

### Done — 2026-09-24

**Engine.** `PdfLayoutSpec.contentPadding: PaddingValues` (default zero), with `LazyColumn`
semantics: pages are fitted to the area inside the padding, the first and last pages stop at it at
either end of the document, and pages still draw underneath while scrolling.
- `ViewportMetrics` carries the padding (`ContentPaddingPx`, resolved at composition for density and
  layout direction) and exposes `contentWidth` / `contentHeight`. The layout fits pages to those, and
  `clampPan`, `centeredPanForPage`, `currentPageAtViewportCenter`, the fit-zoom helpers and B1's
  reading anchor all work in the content area. Visibility and tile planning keep the whole viewport,
  since pages under a bar are still on screen.
- A padding change counts as a layout change in `PdfViewerController.updateConfig`.
- With zero padding everything is exactly as before: the 32 existing unit tests pass unchanged.

**App.** The screen measures its bars once (`onSizeChanged`, keeping the last non-zero height while a
bar is hidden) and passes them as constant `contentPadding`. The animated `padding(top = …)` on the
viewer, and the `hasScrolled` flag that fed it, are gone (V9).

**Verification.**
- `PageLayoutSnapshotTest`: 6 new JVM tests (clamp at both ends with bars, centring a small
  document in the content area, centred page, anchor, current page). `:composepdf` unit tests: 38.
- `PdfContentPaddingTest` (instrumented, real engine): the first page starts at the top padding;
  horizontal padding fits pages to the inner width; zero padding changes nothing. `:composepdf`
  instrumented suite: 22 tests, 0 failures, 1 skipped (the opt-in dump).
- Emulator: the first page starts right below the top bar; tapping to hide the bars leaves the
  document where it is (the first line of text stays on pixel row 515; it used to slide up as the
  padding animated); at the end of the document the last page stops at the bottom bar's edge instead
  of underneath it.

**Behaviour change, deliberate**: the last page is no longer hidden under the bottom toolbar at the
end of a document.

## Paso b8 · UI nueva (B3, B4)

- [x] Barra superior, floating toolbar con *exit always*, chips de página y zoom, menú de modo de
  ajuste, fast scroller, indicador de página.
- [x] Destino «Ir a página».
- [x] Pantalla de error localizada con acciones (B4); `contentDescription` correcta (B3).

**Verificación**: capturas en la misma matriz que el paso 0; Accessibility Scanner (objetivos
táctiles de al menos 48 dp, etiquetas); TalkBack recorre las barras. **Riesgo: medio**, solo visual.

### Done — 2026-09-25

The design, and the reasons behind it, are in
[09-pdfviewer-ui-design.md](09-pdfviewer-ui-design.md), which replaces §7 of the target
architecture. Five commits:

**8a · Top bar and one visibility for both bars** (`fbd7b7c`). A standard `TopAppBar` in surface
colours: the document's name, its description as the subtitle, and the actions in an `AppBarRow`.
Below 600 dp of the bar's own width it shows Share and the overflow menu; wider, all four actions.
It measures the bar, not the window, so a list-detail pane gets the right answer. Every action has a
tooltip and a TalkBack label. Back is announced as "Back", no longer as "Cancel" (B3). Both bars
share one visibility (`ViewerChromeState`): reading forward hides them, scrolling back or a tap
shows them. The state watches the viewer's nested scroll without consuming it.

**8b · Floating toolbar and *Go to page*** (`0a2fe9d`). The bottom toolbar was rewritten on the
component's default metrics:
- a page chip that opens *Go to page*;
- the zoom group only when the toolbar has 600 dp of its own width; narrower, a zoom chip only while
  the zoom is not the fitted one, which resets it;
- a menu with the four fit modes by name;
- night mode as a toggle button.

*Go to page* is a destination (`GoToPage` key). It shows as a sheet on narrow windows and as a
dialog on wide ones. It hands the page back through `ViewerPageRequests`, a retained request per
document that the viewer consumes once it has scrolled there (the `ScanRequestBus` lesson). This
also removes the text field that raised the keyboard underneath the toolbar.

**Engine fix found here** (`33301dd`). Rotating the external viewer on page 200 of 320 landed on
page 91. `PdfViewerActivity` handles rotation itself, so nothing is recreated and B1's restore never
runs, while the pan stays in pixels. The controller now takes the reading anchor before a change of
page layout (viewport width, fit mode, spacing, padding) and puts it back after. The anchor moved
from the viewport's leading edge to the centre of the content area, so the page being read is the
page the reader comes back to. B1's restore uses the same anchor. `PdfLayoutChangeTest` covers a
resize and a fit-mode change; both failed before the fix.

**8c · Fast scroller and page pill** (`daed9e7`). `PdfFastScroller` sits on the trailing edge,
inside the content padding, for documents of three or more pages. It shows while the document moves
and fades 1.2 s after. Dragging its thumb jumps through the document with the page beside it, and
TalkBack sees it as an adjustable control (`setProgress`). It replaces the engine's passive
indicator. While the bars are hidden, a "N / total" pill shows for a moment at the top when the page
changes. Both are built only on `PdfViewerState`'s public API.

**8d · Loading and errors (B4)** (`a3d4ca9`, `1f1facb`).
- **Engine.** A failed load is now a `PdfLoadException` with a `reason`: `NOT_FOUND`,
  `ACCESS_DENIED`, `PASSWORD_PROTECTED`, `DAMAGED` or `UNKNOWN`. The platform throws
  `SecurityException` both for a revoked permission and for an encrypted file. The engine therefore
  classifies the failure where it still knows which step failed: getting at the bytes, or parsing
  them. A cancelled load is no longer recorded as an error.
- **App.** `ViewerErrorContent` shows an icon, a title and a message for each reason, never the
  exception's name.
  - *Open with another app* appears only when the document can leave the app and another app could
    reach the file, so not for "not found" or "access denied".
  - *Back* appears when the viewer shows a way back.
  - For a file the app cannot reach, Share and Open with also leave the top bar. Print leaves it
    until there is a document.
  - The bottom toolbar hides on error. It stays while loading, because its height is the content
    padding, and learning it after the layout would move a restored position.
  - Loading uses the expressive `LoadingIndicator`.

**Verification.**
- Tests, all green:
  - `:composepdf` unit: 44, including `PdfLoadExceptionTest`.
  - `:composepdf` instrumented on the emulator: 27, with 1 skipped (the opt-in dump). They include
    `PdfLoadErrorTest`, which checks each reason against the real platform: the password fixture,
    a missing file, and bytes that are not a PDF.
  - `:app` unit: 131, including `ViewerChromeStateTest`, `ViewerPageRequestsTest` and
    `ViewerLoadErrorTest`.
- Checked on the emulator:
  - the bars and tooltips; *Go to page* 200;
  - the zoom group in landscape;
  - page 200 surviving three rotations;
  - the fast scroller while scrolling and dragging (label beside the thumb, jump to the page), and
    under the top bar when the bars are shown;
  - the pill with the bars hidden;
  - the error screens for a password-protected file, a damaged file, and a document opened without
    a read grant ("access denied": no *Open with*, no Share);
  - Back closing the external viewer.
- **Not done:** an Accessibility Scanner pass and a full TalkBack walk-through. Labels and states
  were checked through `uiautomator` dumps instead.
- **Not checked on a device:** "not found". The shell cannot grant a URI that does not exist, so it
  rests on `PdfLoadErrorTest`.

**Behaviour changes, deliberate.**
- Both bars hide while reading forward and come back when scrolling back.
- The fast scroller replaces the passive scroll indicator.
- The bottom toolbar is gone from a document that failed to load.
- `PdfViewerState.error` is now a `PdfLoadException` for load failures, with the platform's
  exception as its `cause`.

---

## Paso c1 · `:document-content-api` + `TextSelection`

- [x] Módulo Kotlin puro con los modelos y el puerto (arquitectura §4).
- [x] `TextSelection` con tests JVM: palabra bajo un punto, rango entre líneas, orden de lectura,
  saltos de línea en el texto copiado, fusión de rectángulos, `all()`, RTL básico.

**Riesgo: nulo** (no se cablea nada).

### Done — 2026-09-25

**Module.** `:document-content-api`, plain Kotlin like `:scanner-api`, package
`com.bobbyesp.documentcontent`. It holds the models and the port exactly as in §4.1–4.2 of the
target architecture:
- geometry: `NormalizedRect`, `NormalizedPoint`;
- document and origin: `DocumentSource`, `ContentOrigin`;
- text: `TextWord`, `TextLine`, `PageText`;
- links and results: `PageLink`, `PageContentResult`;
- the port: `PageContentProvider` and `PageContentSession`.

Two small additions: `PageText.isBlank`, which is how a provider recognises `NoText`, and
`NormalizedRect.union` / `distanceTo`. Nothing depends on the module yet. `:app` picks it up in c2.

**`TextSelection`.**
- A selection is a `WordRange`: indices into the page's words in *reading order*, as the provider
  lists them. Geometry only decides which word a finger is on; everything after follows reading
  order. That is why a backwards drag, a selection across lines and a right-to-left line all copy in
  the order they are read.
- `wordAt(point, tolerance)` is for a long press. It returns the word the point is on or, failing
  that, the closest word within the tolerance (default 0.03 normalized units), or nothing.
- `nearestWord(point)` is for a dragged handle and always lands on a word. It picks the closest
  line, then the closest word along it. Past a line's end it is that line's last word on that side,
  and above or below the text it is the first or last line.
- `range(anchor, focus)` orders its ends. `text(range)` joins words with a space and lines with
  `
`, keeping a blank line where it separates paragraphs. `highlightRects(range)` returns one
  rectangle per line, gaps between words included. `all()` covers the whole page.

**Verification.** `./gradlew :document-content-api:test`: 17 JVM tests, all green. They cover:
- the word under a point, including just off a word and far from every word;
- handles past a line's end, between lines, above and below the text;
- ranges across lines and backwards drags;
- copied line breaks and a blank line between paragraphs;
- one rectangle per line;
- `all()`, and a page without words;
- a right-to-left line.

`AGENTS.md` lists the module, and its test command now runs these tests too.

**Known limit, deliberate:** a line that mixes directions (bidi) is highlighted as one rectangle
from its first selected word to its last. Selection is within one page, as §4.4 scopes v1 —
superseded in c2, where selection across pages entered v1 at the maintainer's request.

## Paso c2 · Proveedores + DI

- [x] `PlatformPageContentProvider` (API 35+; `Unsupported` por debajo, D1), con `Mutex` y caché
  LRU.
- [x] `LayeredPageContentProvider(recognized = null)` y `PageContentModule`.
- [x] Tests instrumentados con los PDFs de prueba: las coordenadas coinciden con lo que se ve.
- [x] *(added 2026-09-25)* Selection across pages in the model (`DocumentSelection`).

**Verificación**: en API < 35, `Unsupported` y sin ninguna llamada a métodos de API 35 sin comprobar
la versión (lint `NewApi` limpio). **Riesgo: bajo.**

### Done — 2026-09-25

**Selection across pages** (maintainer's request, 2026-09-25; §4.4 had left it for later). It
lives in `:document-content-api`, like the rest of selection.
- `TextPosition(page, word)` is ordered by page, then by reading order.
- `DocumentSelection(start, end)` keeps only its two ends. Each page works out its share from its
  own `TextSelection` when needed (`rangeOn`): the rest of the first page, all of the middle ones,
  and the start of the last. A selection over many pages therefore costs nothing until it is drawn,
  and only the pages on screen are drawn.
- `text { page -> … }` copies the selection with pages separated by a line break, leaving out pages
  without text. It asks for every page in the range, so the caller loads them all before copying.
- 8 JVM tests; the module now has 25.

**Native provider** (`feature/pdfviewer/data/content/PlatformPageContentProvider`), as step *a*
found it had to work:
- It opens its own `PdfRenderer` through the content resolver.
- It splits the page text into lines and words (`splitPageText`, pure, JVM-tested) and measures each
  word with `selectContent` by character index.
- It normalizes by the displayed page size and maps external and internal links. The platform
  reports no internal links, and their position on the target page is left out.
- A blank page is `NoText`, or `Available(text = null)` when it has links.
- One `Mutex` per session, since a renderer has one page open at a time, and an LRU of 12 pages.
  Failures are not cached.
- `close()` waits for a page being read. Whoever last holds the lock closes, and a closed session
  answers `Failed`.
- Below API 35 the session answers `Unsupported` without opening anything. The version check is an
  `@ChecksSdkIntAtLeast` function over an injectable `sdkInt`, so the fallback can be tested on any
  device and lint can follow it.
- A document that cannot be opened (password, missing) gives a session whose pages are all
  `Failed`: `open` does not throw, as the port now says.

**Layered provider** (`LayeredPageContentProvider`):
- It uses the document's own text first. Only for a page with none (`NoText`, `Unsupported`, or
  links only) does it ask `recognized`, which it opens lazily, once per session.
- Recognized text keeps the document's links.
- Recognition finding no text beats the platform being unable to look. A failed recognition leaves
  the document's own answer.
- With `recognized = null` it returns the embedded session itself.

**DI.** `pageContentModule` binds `PageContentProvider` to
`LayeredPageContentProvider(PlatformPageContentProvider, recognized = null)`. That is the line text
recognition will change. `:app` now depends on `:document-content-api`. Nothing in the viewer
uses the module yet: that is c4.

**Verification.**
- JVM:
  - `PageTextSplitterTest`: 5 tests.
  - `LayeredPageContentProviderTest`: 8 tests, with fake providers.
  - `:app` unit total: 144.
- Instrumented, on the emulator: `PlatformPageContentProviderTest`, 10 tests, all green on API 37.
  The app's `androidTest` reads the engine's fixtures (an extra assets directory), so there is one
  set of test PDFs. The tests check:
  - every word of `text-and-links.pdf`'s three pages, in order and where the manifest places it
    (tolerance 0.01);
  - the `TARGET` word on rotated and cropped pages;
  - external links, with their address and bounds (tolerance 0.004);
  - scanned and mixed pages giving `NoText`;
  - password-protected and missing documents failing every page;
  - pages out of range failing;
  - a page read once per session (the same result instance);
  - a closed session reading nothing;
  - the API < 35 fallback;
  - a selection copied across the real break between pages 1 and 2.
- Lint: no `NewApi` or `InlinedApi` findings. `lintDebug` does fail, on `MissingTranslation`:
  - 38 of the 70 errors predate this plan;
  - 32 are viewer strings this branch added in English and Spanish only;
  - whether to translate them into the other 12 languages is the maintainer's call.

**Cost, known:** copying a selection over many pages reads every page in it, and measuring words is
the expensive part (about 0.1 ms a word). If copying long ranges proves slow, the port could offer
a text-only read for copying. Not needed yet.

## Paso c3 · Motor E3 (long press) + E4 (overlay en página)

- [x] `PdfInteractionHandler`: `onLongPress` devuelve si reclama el gesto; `onDrag` y `onDragEnd`.
- [x] `PdfOverlayScope`: dibujo en fase de draw y colocación en fase de layout, en coordenadas de
  página.

**Verificación**: sin handler, los gestos no cambian; con el gesto reclamado, arrastrar no desplaza
el documento; un resaltado sigue a la página durante pinch y fling sin recomposiciones (Layout
Inspector). **Riesgo: medio-alto**, porque vuelve a tocar los gestos. Commit propio.

### Done — 2026-09-25

**E3 · Claimable long press.** New public `PdfInteractionHandler`, passed as
`PdfViewer(interactionHandler = …)`.
- `onLongPress(event): Boolean` returns whether the handler claims the gesture.
- If claimed, the finger leaves the gesture state machine (`followClaimedDrag`). Its movement
  comes to `onDrag` with the same hit-test data as a tap. Every change is consumed, so neither the
  document nor anything watching nested scroll (the app's bars) sees it. Other fingers are ignored,
  so it cannot turn into a pinch.
- `onDragEnd()` always runs, from a `finally`, also when the gesture is cancelled.
- If not claimed, the viewer's own `onLongPress` is called and the finger can still pan, as before.
- `claimsTap` is not here: tap claiming is d2.

Two small additions to `PdfViewerState`, which c4's auto-scroll needs:
- `hitTest(position)`: a viewer point resolved as a tap there would be;
- `panBy(delta)`: moves the document at once, within bounds, and returns how far it moved. It uses
  the finger's convention: positive `y` moves the document down. It is deliberately not named
  `scrollBy`, whose sign is the opposite.

**E4 · `PdfOverlayScope`.** The `overlay` slot's receiver is now a `PdfOverlayScope`, which is a
`BoxScope` plus:
- `DrawOnPages { }` is a viewer-sized layer that draws over each visible page, clipped to it. It
  gets `pageIndex`, `pageBounds` and `toViewer(…)` to map page-normalized points and rectangles.
- `Modifier.anchorTo(page, position, alignment)` places an element so that its alignment point
  sits on a page-normalized point. For example, `TopCenter` hangs it below the point.

Both locate pages through `pageRectInViewer`, which reads pan, zoom and the layout snapshot.
Called inside a draw or placement block, that makes only that block run again when the document
moves.

The overlay moved inside the overscroll box, so it stretches with the pages at the ends of the
document. Its alignment is now top-start. Nothing used the slot before, so nothing changes for
existing callers.

**Bugs found by the tests, before commit:**
- The claimed drag reported no movement: it consumed the changes before asking whether the finger
  moved, and a consumed change reports no movement.
- `anchorTo` loosened the minimum constraints, so an element without content measured 0×0.

**Verification.** `:composepdf` instrumented suite on the emulator: 37 tests, 0 failures, 1 skipped
(the opt-in dump).
- `PdfInteractionHandlerTest` (4 tests):
  - a claimed long press hands over 8 drag events, in order down the page, and 1 end. The document
    does not move, a nested-scroll watcher around the viewer sees nothing, and the viewer's own
    callback is not called.
  - an unclaimed long press still pans;
  - a second finger cannot pinch a claimed gesture;
  - removing the viewer mid-drag still ends the drag.
- `PdfOverlayScopeTest` (6 tests):
  - an anchored element sits on its page point and follows it after a pan;
  - its alignment point is what sits on the anchor;
  - it receives a click where it is drawn, and the pages stay still. This is what selection
    handles rely on.
  - drawing reaches every visible page at its on-screen bounds;
  - ten pans over several pages plus a zoom redraw the overlay without recomposing it. This
    replaces the Layout Inspector check: the same state paths as pinch and fling, but measured
    by the test.
  - `hitTest` and `panBy` work in viewer coordinates.
- The existing gesture tests (`PdfGesturesTest`, `PdfGesturesConsumptionTest`) pass unchanged. So
  do the unit tests of both modules.

## Paso c4 · UI de selección, portapapeles y degradación

- [x] Extracción perezosa desde el ViewModel (visibles ±1, *debounce* en flings).
- [x] Resaltado, tiradores, `LocalTextToolbar` (Copiar, Seleccionar todo), `LocalClipboard`,
  confirmación propia por debajo de API 33.
- [x] *(added 2026-09-25)* Selection across pages: a handle dragged onto another page moves the
  selection's end there (`DocumentSelection`), the document scrolls when a handle nears the top or
  bottom edge, each visible page highlights its share, and copying loads every page in the range
  first.
- [x] Degradación: `NoText` y `Unsupported` con háptica y un aviso por documento.
- [x] «Texto: …» en detalles.

**Verificación**: copiar del PDF con texto y pegar en otra app; en el escaneado de Docucraft aparece
el aviso una sola vez y sin errores; TalkBack anuncia la selección. **Riesgo: medio.**

### Done — 2026-09-27

**Where each piece lives.**
- **Pure logic, JVM-tested.**
  - `presentation/selection/SelectionInteraction` decides, from the pages whose text is known:
    - what a long press does: select a word, leave the press to the viewer, say why there is no
      text, or not claim it yet;
    - what a drag selects: from the anchor to the nearest word, possibly on another page;
    - where the handles go;
    - what *Select all* covers.
  - `PageTextState` is a page's text as selection sees it.
- **ViewModel.**
  - It opens a content session on first need and closes it in `onCleared`.
  - It keeps the text of the visible pages ±1, plus the pages the selection ends on, and forgets
    the rest.
  - It holds the selection, so the selection survives rotation.
  - It copies, reading pages no longer on screen if the selection runs through them. The text
    reaches the screen as a `CopyText` effect, because the clipboard is UI.
  - New intents: `VisiblePagesChanged`, `Select`, `SelectAll`, `ClearSelection`, `CopySelection`,
    `NothingToSelect`.
- **Screen.** It reports the visible pages after 150 ms of stillness, so a fling only reads where
  it stops. It also provides:
  - the long-press `PdfInteractionHandler` (E3), decided synchronously from the text already read;
  - `TextSelectionLayer` in the overlay (E4): the highlight, two drop-shaped handles with 48 dp
    targets, the system `TextToolbar`, and a live region for TalkBack;
  - `SelectionDrag`, shared by the long-press drag and the handles, which scrolls the document at
    the top and bottom edges of the content area. The speed grows with depth into a 56 dp zone,
    up to 1200 dp/s.
  - Back and a tap both let go of a selection first, through one `NavigationBackHandler` that is
    enabled only while there is a selection.
- **Degradation.**
  - A long press on a page without text gives a rejection haptic and, once per document for each
    reason, a notice: "This page is an image…" or "Selecting text in PDFs needs Android 15…".
  - The press is not claimed, so the finger can still pan.
- **Details.** `DetectDocumentTextUseCase` looks at the first 5 pages. The details come out at once
  with "Text: Checking…", then with the answer: Selectable, Recognized, None, Unsupported or
  unknown.

**Decisions taken here.**
- **Select all** selects the whole of every page the selection touches, not the whole document.
  Copying a whole long document would mean measuring every word of it, about 0.1 ms a word.
- Copying lets go of the selection.
- A long press on a page whose text has not been read yet is not claimed. The visible pages ±1 are
  read within about 150 ms of the document settling, well inside the long-press timeout, so in
  practice the text is there.
- **Night mode** highlights with `inversePrimary` at 45 %. The text-field selection colour is made
  for light backgrounds and hardly showed on inverted pages, a problem found on the emulator.
- The handle's drag point aims 4 dp above the handle's tip, inside the line, so the nearest line is
  the line being pointed at. Handles report the finger in the viewer's coordinates, not their own,
  which move under the finger as the selection changes.

**Verification.**
- **JVM tests.** `:app` has 168 unit tests, 24 of them new:
  - `SelectionInteractionTest`: 11;
  - `DetectDocumentTextUseCaseTest`: 6;
  - `PdfViewerViewModelTest`: 6 new tests on the pages read and forgotten, copying across pages
    past a scanned one, *Select all*, the notice given once, and the session closed with the
    ViewModel;
  - `ViewerDocumentDetailsTest`: 1 new test on the text row following the rest of the details.
- **Emulator (API 37), `text-and-links.pdf`:**
  - A long press on "prueba" and a drag over three lines gave one highlight per line, handles at
    both ends, and the toolbar after release.
  - Copy, then paste into Home's search field, gave exactly
    `prueba de texto y enlaces.⏎Acentos: canción, año, pingüino.⏎Web segura:`, accents included.
  - A drag from page 1's last line onto page 2 auto-scrolled once the finger reached the bottom
    edge zone. The paste was page 1's last line, a line break, then page 2 in reading order.
  - Dragging the end handle two lines down extended the selection to the aimed word. The toolbar
    was hidden during the drag, and the document stayed still.
  - A tap and Back each let go of the selection, and the viewer stayed open.
  - Night mode: the highlight was readable after the fix.
  - Details: "Text: Selectable".
- **Emulator, `scanned-image-only.pdf`:**
  - The notice appeared on the first long press and not on the second.
  - Details: "None: the pages are images".
- **Not checked on a device:**
  - TalkBack reading the live region aloud: the node exists, but nobody walked it with TalkBack.
  - The "Text copied" notice below API 33: the emulator is API 37.
  - The `Unsupported` path below API 35: covered by the JVM tests and by c2's instrumented test of
    the provider's fallback.

## Step c5 · Character-by-character selection *(added 2026-09-27)*

The maintainer asked for this after c4, before phase d. They wanted selection to behave like the
reference viewers, Google Drive's among them: c4 moved both ends a whole word at a time.

### Done — 2026-09-27

**Behaviour.**
- A long press still selects the whole word under the finger.
- From then on the ends move character by character: the same finger dragging on, and either handle
  later.
- After a long press, the pressed word stays selected whichever way the finger goes. This is
  `DocumentSelection.extending`.
- What is copied is exactly the characters between the handles. Spaces or line breaks that a handle
  sitting between words takes in at either end are trimmed.

**Model (`:document-content-api`).** This supersedes §4.1's "words, not characters" and fills in
its planned `TextWord.glyphs`.
- `TextWord.glyphs` is optional: one box per character, in reading order.
- `TextSelection` exposes the page's text as it will be pasted (`text`). Selections are `TextSpan`s,
  pairs of carets in that text, half-open.
- `caretAt(point)` finds the nearest character boundary on the nearest line; between words, it
  takes the edge of the nearer word.
- `startHandle` and `endHandle` put a handle at the leading edge of the first selected character
  and the trailing edge of the last.
- Highlights and handles take their height from the line, not the glyph. Ink boxes follow the
  letters, so an "o" is shorter than a "d".
- A word without glyphs has its box shared evenly among its characters, reversed for a
  right-to-left word. That is exact for monospaced text and close otherwise. It matters for OCR
  that only gives word boxes.
- `DocumentSelection` is now two `TextCaret(page, offset)`s, with `spanOn` per page. `WordRange`
  and `TextPosition` are gone.

**Provider.**
- `PlatformPageContentProvider` measures each character with `selectContent(k, k + 1)`. The word's
  box is their union, at no extra cost.
- If any character of a word cannot be measured, it measures the word whole and leaves its glyphs
  to the even share-out.
- **Cost**, on the densest fixture (`prueba_motor_pdf.pdf`, ReportLab, about 2,700 characters a
  page), on the emulator: 180 ms a page on average and 358 ms at worst, over 10 pages. The visible
  page is read first, 150 ms after the document settles, so its text is there before a long press
  fires (500 ms). If denser documents prove slow, glyphs could be measured only for the words at
  the selection's ends, when a handle reaches them.

**Screen.**
- Handles take their drag offset from the finger's first touch. `detectDragGestures` reported the
  finger only after the touch slop, so the handle was losing its first ~8 dp: found on the
  emulator, where a three-character drag moved one character.
- A handle's down is consumed at once, so the viewer yields the touch (E2).
- Each handle hangs outward: the start one to the left of its point, the end one to the right, as
  in Drive. With both 48 dp targets centred on the text, a short selection put the end handle's
  target over the start one's, and a touch meant for the start moved the end. Also found on the
  emulator.

**Verification.**
- Tests, all green:
  - `:document-content-api`: 27 tests. `TextSelectionTest` was rewritten for carets and covers
    boundaries inside a word, the gap between words, measured glyphs against the even share-out,
    line-height highlights, handles on spaces, and right-to-left.
  - `:app` unit: 169 tests. `SelectionInteractionTest` was rewritten, and the ViewModel tests copy
    from inside words.
  - `PlatformPageContentProviderTest`: 13 instrumented tests on the emulator, 3 of them new. Every
    character has its own box, inside its word and in reading order. A finger on a real glyph
    boundary lands on it. A selection across pages runs from inside a word to inside another. The
    cost is logged.
- Emulator, `text-and-links.pdf`:
  - The long press selected "prueba".
  - Dragging the end handle three characters left left "pru".
  - Dragging the start handle one character right, then Copy and paste into Settings' search, gave
    exactly `ru`.
  - A long press on "texto" dragged into "enlaces" selected `texto y enl`.

### Fix — margin text and tables (2026-09-27)

**Found with a real document.** The maintainer supplied a phone-company invoice. It is not in the
repository, because it holds personal data. On it, handles jumped to the wrong place and copying
failed. The provider's dump showed two things:
- **The margin note.** The page had a note along the left margin, set vertically and reading
  upwards. It came as a single platform line, as tall as the page body but only 0.013 wide. A
  handle picked its line by vertical distance alone, so that line was at distance 0 almost
  everywhere, won every tie, and took every drag.
- **The table.** Its text comes in column order: labels first, then values. Some platform lines run
  on from a value to an address a column away.

**Change (`TextSelection`, geometry only; the public API is unchanged).**
- Geometry now works on **runs**: the words of one platform line that sit together (they overlap
  across their direction, and the gap is at most 2 text heights) and read the same way.
- Each word's direction comes from its glyphs: left to right, right to left, top to bottom or
  bottom to top. A one-letter word takes the direction of its neighbours, and failing that its
  shape and letters.
- A finger picks the run closest **across** that run's direction: height for lines, width for
  vertical text. Near-ties (within 0.004) go to the run nearest **along** its direction, as for the
  columns of a table row.
- Within the run, carets, highlights and handles all work along the run's own axis. A line that
  runs on into another column is highlighted as two boxes, not one bridging the gap.
- **Text order is unchanged:** the document's own, as in the reference viewers. Selecting down the
  invoice's value column therefore also takes in whatever the document stores in between, such as
  the address block and the total. That is a decision, recorded under "Open question" below.

**Verification.**
- `:document-content-api`: 33 tests. The 6 new ones use an invented page shaped like the invoice:
  - a margin note reading upwards does not capture a finger on the body;
  - a finger on the margin moves along it, upwards;
  - a line running into another column makes two highlight boxes;
  - in the gap between columns, the nearer column wins;
  - vertical text is highlighted along its height;
  - a one-letter word reads like its neighbours.

  All 6 fail against the previous `TextSelection`, so they do cover the bug.
- Emulator, on the invoice:
  - Dragging along a table row stays on the row. Copied and pasted, it gave exactly the selected
    text.
  - Selecting down the header's value column follows the document's order. The copy matched the
    highlight, which was checked at 250% zoom.
- `PlatformPageContentProviderTest`: 13 tests, still green.

**Open question.**
- Keep document order, which is what Drive does and what keeps multi-column text right?
- Or add a visual order for forms and tables: row by row, top to bottom, left to right?
- Left to the maintainer.

---

## Paso d1 · Enlaces en el proveedor + `ResolveLinkUseCase`

- [x] `getLinkContents()` y `getGotoLinks()` en la misma pasada de extracción.
- [x] `ResolveLinkUseCase` con tests JVM: esquemas permitidos y bloqueados, host normalizado
  (userinfo, IDN a punycode), `http` marcado como no seguro, destinos internos fuera de rango.

**Riesgo: bajo.**

### Done — 2026-09-27

**Extraction.** Already in place since c2: the platform provider reads links in the same pass as
text. d1 adds the rest of the plumbing. The ViewModel now keeps `pageLinks` for the visible pages
±1, read together with their text and forgotten together with it. So d2 and d3 are UI only.

**`ResolveLinkUseCase`** (`feature/pdfviewer/domain/links/`) turns a `PageLink` into a
`LinkAction`: `OpenWeb(url, host, secure)`, `ComposeEmail`, `Dial`, `GoTo` or `Blocked(reason)`.
- **Schemes.** Only four are followed: `http`, `https`, `mailto` and `tel`. Everything else is
  `Blocked(Scheme)`: `javascript:`, `file:`, `intent:`, `content:`, `data:`, `ftp:`, `market:`…
  Anything that is not a valid RFC 3986 scheme is `Blocked(Malformed)`.
- **The host shown is the host a browser opens.** It is parsed by hand, following WHATWG, which is
  how browsers read URLs:
  - the userinfo is dropped: `https://mybank.com@elsewhere.com` shows `elsewhere.com`;
  - a backslash ends the host as a slash does, so `https://mybank.com\@elsewhere.com` shows
    `mybank.com`, the host Chrome opens;
  - tabs and line breaks are removed;
  - the port, case and a trailing dot are dropped;
  - IPv6 is kept whole;
  - internationalized names become punycode, so a look-alike such as `аpple.com` with a Cyrillic
    "а" shows as `xn--pple-43d.com`.
- **`http`** is `secure = false`, which is what D3's warning will show.
- **`mailto`** gives the decoded address, and **`tel`** the number without its parameters. With
  nothing to reach, each is `Blocked(Malformed)`.
- **A bare `www.` address** is opened as `https`.
- **Internal links** outside the document are `Blocked(OutsideDocument)`.

**Verification.**
- `ResolveLinkUseCaseTest`: 18 JVM tests. `:app` unit total: 187.
- `PlatformPageContentProviderTest`: 14 instrumented tests on the emulator. The new one follows the
  fixture's links end to end, from the platform through the use case, and checks the exact list:
  - two `https` links and one `http` link, all to `example.com`, the `http` one flagged;
  - the email address and the phone number;
  - `javascript:alert(1)` refused.

  The internal links never arrive, because the platform does not report them.

**Known limit, unchanged since step a.** A link over two lines comes as one rectangle enclosing
both. On the fixture, that rectangle also covers text before the link on its first line. The
platform ignores `QuadPoints`, and a PDF does not say which characters a link covers, so this is
left as it is for now.

## Paso d2 · Motor E3 (tap reclamable) + E5

- [x] `claimsTap`: el tap sobre un enlace se entrega al momento; fuera de un enlace se mantiene el doble
  tap.
- [x] `animateScrollTo(pageIndex, position)`.

**Verificación**: doble tap para hacer zoom fuera de un enlace sigue funcionando; el tap sobre un
enlace responde sin retraso apreciable. **Riesgo: medio.**

### Done — 2026-09-27

**E3, the second half: claimable taps.** `PdfInteractionHandler` gains `claimsTap(event)` and
`onTap(event)`.
- The engine asks when the finger lifts from a tap, before the double-tap wait. A claimed tap goes
  to `onTap` at once, and the viewer's own `onTap` is not called for it.
- Anywhere not claimed, nothing changes: a double tap zooms, and a single tap is delivered after
  the window.
- A double tap *on* a claimed spot is two claimed taps: on a link, the first one acts.

**E5: `PdfViewerState.animateScrollTo(pageIndex, position)`.**
- It brings a page-normalized point to the start of the content area, just below the top bar. That
  is where readers put a link's target.
- Across the scroll axis the document stays put unless the point would be off screen; then it is
  centred.
- Without a position, the page's start goes there. Unlike `animateScrollToPage`, which centres the
  page, this shows the first lines of a page taller than the screen.
- The zoom does not change. The target is clamped before animating, so at the end of the document
  the animation stops where the document does.
- The geometry is `PageLayoutSnapshot.panForPagePoint`, pure and JVM-tested.

**Verification.**
- `:composepdf` unit tests: 47. The 3 new ones check that the point lands under the top padding,
  that the page start is used without a position, and that the other axis moves only for a point
  off screen.
- `:composepdf` instrumented tests on the emulator: 43, with 0 failures and 1 skipped (the opt-in
  dump). The new ones:
  - a claimed tap arrives in the first frame, and the viewer's tap is not called;
  - an unclaimed tap arrives only after the double-tap window, the other side of the same test;
  - a double tap outside the claimed area still zooms;
  - `PdfScrollToTest`: a point on page 150 lands at the content start, below a 64 dp bar; page 42's
    start lands there without a position; the last page stops at the end of the document.
- The existing gesture tests pass unchanged.
- **Not yet on a device:** the app does not claim taps until d3 wires the links in. The engine
  side is covered by the tests above.

## Paso d3 · D3 + D4 + accesibilidad

- [x] Aviso anclado con el dominio (D3) y las acciones Abrir y Copiar enlace.
- [x] Dependencia `androidx.browser`; `CustomTabsLinkOpener` con la cadena Custom Tabs →
  `ACTION_VIEW` → mensaje (D4); colores del `colorScheme`.
- [x] `<queries>` en el manifest (D4).
- [x] Enlaces internos: salto animado y snackbar «Volver a la página N».
- [x] Nodos semánticos virtuales para los enlaces.

**Verificación**:
- `https` abre una Custom Tab y atrás vuelve al visor;
- con Chrome deshabilitado y otro navegador sin Custom Tabs, abre con `ACTION_VIEW`;
- sin navegador, muestra el mensaje;
- `mailto` y `tel` abren su app;
- `javascript:` no ofrece «Abrir»;
- TalkBack llega a los enlaces y abre el aviso.

**Riesgo: medio.**

### Done — 2026-09-27

**Tap → preview (D3).**
- The screen's `PdfInteractionHandler` claims a tap on a link, using d2's E3, so it answers without
  the double-tap wait. While text is selected it does not claim: that tap lets go of the selection.
- The tap goes to the ViewModel (`TapLink`), which resolves it with `ResolveLinkUseCase`.
  - An **internal link** is followed at once (`GoToPage`). The screen scrolls with
    `animateScrollTo` and shows "Page N" with a "Back to page M" action. That action returns to
    the exact point that was at the top of the content area, not just the page.
  - **Any other link** becomes `linkPreview` in the state, and nothing opens yet.
- The preview is a card anchored under the link (`anchorTo(stayInside = true)`), with the link's
  area marked. It shows, depending on the link:
  - web: the host (in ASCII) as the title, the URL beneath it, "Connection not secure" for `http`,
    and **Copy link** and **Open**;
  - email or phone: the address or number, with **Write** or **Call**;
  - refused: why, with only **Copy link**, and nothing for a page outside the document.
- Moving the document, tapping elsewhere or pressing Back closes it. Back does this through the
  same single `NavigationBackHandler` as the selection: the preview first, then the selection.

**Opening (D4).** `LinkOpener` is a domain port, and `AndroidLinkOpener` implements it, built per
activity like the printer.
- Web: a Custom Tab from the default browser, or any browser that offers them, with the bar
  coloured from `colorScheme.surfaceContainer` and light or dark to match. If no browser offers
  them: `ACTION_VIEW` with `CATEGORY_BROWSABLE`. With nothing at all: "No app can open this link".
- Email: `ACTION_SENDTO`. Phone: `ACTION_DIAL`, which needs no permission and calls nothing by
  itself.
- `androidx.browser` 1.10.0, and `<queries>` for the Custom Tabs service, `VIEW` on `http` and
  `https`, `SENDTO` on `mailto`, and `DIAL` on `tel`.

**TalkBack.** Each link on the visible pages gets an invisible node over its area, using the new
`coverArea`: a button described by where it leads ("Link: example.com", "Email link: …", "Phone
link: …", "Link to page N", "Blocked link"), whose action opens the preview. The card is a pane
with a heading.

**Engine (E4 additions).**
- `anchorTo(stayInside = true)` slides an element along the viewer's edge instead of letting it be
  cut off. It uses the viewer's size from the engine, so it does not depend on the modifier order
  (found by a test).
- `Modifier.coverArea(page, area)` sizes and places an element over a page area. It is measured
  from the page, so it re-measures as the document moves and never recomposes.

**Verification.**
- Tests, all green:
  - `:app` unit: 193. The 6 new ViewModel tests cover the preview before anything opens, Open,
    Copy, a refused link never opening, an internal link followed with the way back, and a link
    tap letting go of the selection.
  - `:composepdf` instrumented: 45, 1 skipped. The 2 new ones: an element kept inside slides along
    the right edge; a covering element matches its area, zoomed or not.
  - `PlatformPageContentProviderTest`: 14.
- Emulator, `text-and-links.pdf`:
  - `https`: preview with `example.com` and the full URL. Open gave a Chrome Custom Tab in the
    viewer's own task (t110, on top of `PdfViewerActivity`), themed, and Back returned to the
    viewer.
  - `http`: "Connection not secure".
  - `javascript:`: "Docucraft won't open this link", with no Open.
  - `tel`: the dialler, with +34 600 00 00 00 typed in.
  - `mailto`: Gmail opened.
  - With Chrome disabled: "No app can open this link". Chrome was re-enabled afterwards.
  - The preview closed on a scroll, on a tap elsewhere and on Back, and the viewer stayed open.
  - TalkBack's nodes: all six links present with their descriptions. The dialler and Gmail checks
    were driven through those nodes.
- **Not checked on a device:**
  - `ACTION_VIEW` with a browser that lacks Custom Tabs: the emulator has none.
  - Internal links: the platform reports none (see step a). The ViewModel side is tested, and
    `animateScrollTo` is tested in d2, but the "Back to page" notice has only been seen in code.
  - A walk-through with TalkBack itself.

**Behaviour change.** A tap on a link no longer shows or hides the bars: it opens the preview.

---

## Cierre

- [ ] `AGENTS.md`:
  - regla «los tipos de `android.graphics.pdf.content` solo en `PlatformPageContentProvider`»;
  - punto único de intercambio del contenido en `PageContentModule`;
  - pila propia de `PdfViewerActivity`.
- [ ] `docs/README.md`: fase 3 completada.
- [ ] Verificación completa en dispositivo (móvil y tablet) y anotarla aquí.
- [ ] El análisis 06 **no se reescribe**.

---

## Casos límite a probar

| Caso | Qué se espera |
|---|---|
| Escaneo de Docucraft (solo imagen) | Long press sin selección, aviso una vez, detalles «Texto: no disponible». Ningún error ni spinner eterno |
| Documento mixto | Disponibilidad decidida por página |
| API < 35 | `Unsupported` con su motivo; sin crash |
| Enlaces internos y externos | Internos con y sin posición; varias líneas; en un borde a zoom alto |
| Esquemas peligrosos | `javascript:`, `file:`, `intent:`, `content:`, `data:` bloqueados |
| Documento de más de 300 páginas | Solo se extraen las páginas visibles ±1; la extracción no roba renderers al render; el scroll rápido no acumula trabajo |
| Pinch y fling con selección | Resaltado y tiradores pegados a la página |
| Modo noche | Resaltado legible sobre la página invertida |
| Rotación con selección activa | Se descarta (A3) |
| Muerte de proceso | Posición (B1) y ajustes (A1) conservados en el visor abierto |
| Documento borrado mientras se ve (tablet) | `Gone` lo quita de la pila (ya resuelto en navegación) |
| Protegido con contraseña, dañado, sin permiso | Error localizado con acciones |
| TalkBack | Anuncia barras, selección y enlaces; alternativa a los tiradores (Seleccionar todo) |

## Riesgos de regresión y mitigación

| Riesgo | Mitigación |
|---|---|
| Tocar la máquina de gestos (b2, c3, d2) rompe gestos existentes | Tres commits separados, cada uno verificado en dispositivo con la misma lista de gestos |
| La geometría de `contentPadding` (b7) descuadra el clamp | Tests JVM de `PageLayoutSnapshot`; paso aislado del que no depende nada |
| La extracción compite con el render | El proveedor usa **su propio** `PdfRenderer`, no el pool del motor |
| Llamadas a API 35 en dispositivos antiguos | Comprobación de versión en un único sitio (el proveedor) + lint `NewApi` |
| Keys nuevas que no se restauran en release | `proguard-rules.pro` + test de serialización de keys |
| Sin `<queries>`, Custom Tabs nunca se detecta | Checklist del paso d3 + prueba en dispositivo |

## Huecos preexistentes que este plan no arregla

Se listan para no confundirlos después con regresiones:

- **A4**: el documento no se abre al terminar de escanear. El guardado no devuelve el uuid.
- `PdfViewerActivity` acepta `file://` siendo exportada. La revisión de seguridad de intents queda
  pendiente (solo se deja de re-exponer por `FileProvider`, paso b3).
- `minSdk = 24`: la cobertura de copiar texto depende de API 35 (D1). Subir el `minSdk` se valorará
  en otra fase.

## Cambios de comportamiento deliberados

- La analítica de pantalla del visor pasa a registrarse en el visor (y cuenta también las aperturas
  externas).
- Compartir un documento externo `file://` deja de estar disponible.
- Los enlaces externos piden confirmación (D3).
- Nueva pantalla de Ajustes del visor (D2).
- Los documentos se abren ajustados al ancho en vez de a la página completa (A2).
