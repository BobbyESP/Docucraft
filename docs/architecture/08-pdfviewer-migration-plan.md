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
| b8 | UI nueva (B3, B4) | Medio | ⏳ |
| c1 | `:document-content-api` + `TextSelection` | Nulo | ⏳ |
| c2 | Proveedores nativo y compuesto + DI con OCR a `null` | Bajo | ⏳ |
| c3 | Motor E3 (long press reclamable) + E4 (overlay en coordenadas de página) | **Medio-alto** | ⏳ |
| c4 | UI de selección, portapapeles y degradación | Medio | ⏳ |
| d1 | Enlaces en el proveedor + `ResolveLinkUseCase` | Bajo | ⏳ |
| d2 | Motor E3 (tap reclamable) + E5 (`animateScrollTo`) | Medio | ⏳ |
| d3 | D3 (aviso con dominio) + D4 (Custom Tabs) + accesibilidad | Medio | ⏳ |
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

- [ ] Barra superior, floating toolbar con *exit always*, chips de página y zoom, menú de modo de
  ajuste, fast scroller, indicador de página.
- [ ] Destino «Ir a página».
- [ ] Pantalla de error localizada con acciones (B4); `contentDescription` correcta (B3).

**Verificación**: capturas en la misma matriz que el paso 0; Accessibility Scanner (objetivos
táctiles de al menos 48 dp, etiquetas); TalkBack recorre las barras. **Riesgo: medio**, solo visual.

---

## Paso c1 · `:document-content-api` + `TextSelection`

- [ ] Módulo Kotlin puro con los modelos y el puerto (arquitectura §4).
- [ ] `TextSelection` con tests JVM: palabra bajo un punto, rango entre líneas, orden de lectura,
  saltos de línea en el texto copiado, fusión de rectángulos, `all()`, RTL básico.

**Riesgo: nulo** (no se cablea nada).

## Paso c2 · Proveedores + DI

- [ ] `PlatformPageContentProvider` (API 35+; `Unsupported` por debajo, D1), con `Mutex` y caché
  LRU.
- [ ] `LayeredPageContentProvider(recognized = null)` y `PageContentModule`.
- [ ] Tests instrumentados con los PDFs de prueba: las coordenadas coinciden con lo que se ve.

**Verificación**: en API < 35, `Unsupported` y sin ninguna llamada a métodos de API 35 sin comprobar
la versión (lint `NewApi` limpio). **Riesgo: bajo.**

## Paso c3 · Motor E3 (long press) + E4 (overlay en página)

- [ ] `PdfInteractionHandler`: `onLongPress` devuelve si reclama el gesto; `onDrag` y `onDragEnd`.
- [ ] `PdfOverlayScope`: dibujo en fase de draw y colocación en fase de layout, en coordenadas de
  página.

**Verificación**: sin handler, los gestos no cambian; con el gesto reclamado, arrastrar no desplaza
el documento; un resaltado sigue a la página durante pinch y fling sin recomposiciones (Layout
Inspector). **Riesgo: medio-alto**, porque vuelve a tocar los gestos. Commit propio.

## Paso c4 · UI de selección, portapapeles y degradación

- [ ] Extracción perezosa desde el ViewModel (visibles ±1, *debounce* en flings).
- [ ] Resaltado, tiradores, `LocalTextToolbar` (Copiar, Seleccionar todo), `LocalClipboard`,
  confirmación propia por debajo de API 33.
- [ ] Degradación: `NoText` y `Unsupported` con háptica y un aviso por documento.
- [ ] «Texto: …» en detalles.

**Verificación**: copiar del PDF con texto y pegar en otra app; en el escaneado de Docucraft aparece
el aviso una sola vez y sin errores; TalkBack anuncia la selección. **Riesgo: medio.**

---

## Paso d1 · Enlaces en el proveedor + `ResolveLinkUseCase`

- [ ] `getLinkContents()` y `getGotoLinks()` en la misma pasada de extracción.
- [ ] `ResolveLinkUseCase` con tests JVM: esquemas permitidos y bloqueados, host normalizado
  (userinfo, IDN a punycode), `http` marcado como no seguro, destinos internos fuera de rango.

**Riesgo: bajo.**

## Paso d2 · Motor E3 (tap reclamable) + E5

- [ ] `claimsTap`: el tap sobre un enlace se entrega al momento; fuera de un enlace se mantiene el doble
  tap.
- [ ] `animateScrollTo(pageIndex, position)`.

**Verificación**: doble tap para hacer zoom fuera de un enlace sigue funcionando; el tap sobre un
enlace responde sin retraso apreciable. **Riesgo: medio.**

## Paso d3 · D3 + D4 + accesibilidad

- [ ] Aviso anclado con el dominio (D3) y las acciones Abrir y Copiar enlace.
- [ ] Dependencia `androidx.browser`; `CustomTabsLinkOpener` con la cadena Custom Tabs →
  `ACTION_VIEW` → mensaje (D4); colores del `colorScheme`.
- [ ] `<queries>` en el manifest (D4).
- [ ] Enlaces internos: salto animado y snackbar «Volver a la página N».
- [ ] Nodos semánticos virtuales para los enlaces.

**Verificación**:
- `https` abre una Custom Tab y atrás vuelve al visor;
- con Chrome deshabilitado y otro navegador sin Custom Tabs, abre con `ACTION_VIEW`;
- sin navegador, muestra el mensaje;
- `mailto` y `tel` abren su app;
- `javascript:` no ofrece «Abrir»;
- TalkBack llega a los enlaces y abre el aviso.

**Riesgo: medio.**

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
