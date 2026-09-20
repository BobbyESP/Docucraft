<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Auditoría del refactor de navegación

**Fecha del análisis:** 2026-09-20
**Rama auditada:** `refactor/navigation-decoupling` (HEAD `064026a`)
**Alcance:** los cinco commits `refactor(navigation)` / `feat(navigation)` desde `c8da7e0`.

Fotografía fechada del estado real del código, verificada fichero a fichero y contra las fuentes
de las librerías (`navigation3-ui` 1.1.3, `adaptive-navigation3` 1.3.0-rc01, `window-core` 1.5.0).
No se apoya en los mensajes de commit.

Objetivos que el refactor se propuso cerrar:

1. Descentralizar las rutas/keys, antes en una única clase sellada.
2. Separar responsabilidades entre keys, `NavEntry`, `SceneStrategy` y transiciones.
3. Adaptación a distintos factores de forma mediante `SceneStrategy`.
4. Animaciones coherentes con Material 3 Expressive, incluido predictive back.

---

## 1. Estado actual

### 1.1 Mapa

| Pieza | Dónde vive |
|---|---|
| Shell y único `NavDisplay` | `core/presentation/navigation/DocucraftApp.kt:47-88` |
| Back stack (uno solo) | `rememberNavBackStack(Home)` — `DocucraftApp.kt:47` |
| Abstracción de navegación | `core/presentation/navigation/Navigator.kt:28-77` |
| Keys | `feature/docscanner/navigation/HomeKey.kt:18`, `.../DocumentActionKeys.kt:24-30`, `feature/pdfviewer/navigation/PdfViewerKey.kt:18`, `core/presentation/screens/preferences/navigation/SettingsKeys.kt:20-26` |
| `entryProvider` | `HomeSection.kt:41`, `PdfViewerSection.kt:37`, `SettingsSection.kt:48`, `DocumentActionsSection.kt:51` |
| `SceneStrategy` en uso | `rememberOverlaySceneStrategy()` + `rememberListDetailSceneStrategy()` — `DocucraftApp.kt:48-49,76` |
| `SceneDecoratorStrategy` | `paneContextSceneDecorator()` — `core/presentation/navigation/pane/PaneContext.kt:66` |
| Transiciones | `core/presentation/navigation/motion/NavigationMotion.kt`, conectadas en `DocucraftApp.kt:85-87` |
| Superficie fuera del grafo | `feature/pdfviewer/presentation/PdfViewerActivity.kt` — actividad aparte, sin back stack, intencional |

Hay **exactamente un `NavDisplay`** en toda la app. El resto de menciones son KDoc o tests.

### 1.2 Qué cambió respecto a la centralización anterior

El fichero antiguo (`git show 7b4a7a9^:.../navigation/Route.kt`) era un
`sealed interface Route : NavKey` con `Home`, `PdfViewer`, `Settings` y dos objetos anidados
dentro de `Settings`.

- **Transformado por completo:** las keys (una clase sellada → cuatro ficheros por dueño); el paso
  de callbacks (`onOpenX` uno por arista → `Navigator`); el sheet de acciones (era un `NavDisplay`
  privado dentro de un `ModalBottomSheet` alimentado por una pila a mano en `HomeViewModel` → tres
  destinos reales: `DocumentActions`, `EditDocument`, `DeleteDocument`); las transiciones (copiadas
  por cada `NavDisplay` → un único `NavigationMotion`).
- **Igual y correcto:** un solo back stack; `rememberNavBackStack`; keys `@Serializable` que llevan
  identidad y nunca objetos de dominio.
- **A medias:** ver V1–V3 y V5.

### 1.3 Restos del sistema anterior

| Resto | Evidencia |
|---|---|
| `AGENTS.md:34` documenta `Route` en `core/presentation/common/Route.kt`; ese fichero no existe ni existió en esa ruta | `find . -name Route.kt` → vacío |
| `FullScreenLoading` es código muerto: solo existe su declaración | `feature/docscanner/presentation/components/FullScreenLoading.kt:19`, cero llamadas |
| Paquetes `sheet/` que ya no describen destinos-sheet | `components/sheet/DocumentActionsSheet.kt`, `screens/home/sheet/EditDocumentUiState.kt` |
| `DocumentActionsContent` sigue leyendo `LocalOrientation` | `components/sheet/DocumentActionsSheet.kt:102` → **V3** |

No hay dos formas conviviendo para resolver el mismo problema salvo V3. Un solo `TODO` en `:app`, y
no es de navegación (`SettingsScreen.kt:85`, suscripciones).

---

## 2. Verificación objetivo por objetivo

### 2.1 Keys modularizadas por feature — **HECHO**

Cada key vive con su dueño. `Route` eliminada. `proguard-rules.pro` se cambió **antes** de mover
las keys, pasando de reglas ancladas al paquete a reglas ancladas a la interfaz `NavKey`, que es lo
que hace el movimiento seguro en release: la restauración es por `Class.forName`.

Matiz, no error: `SettingsKeys.kt` vive bajo `core/presentation/screens/preferences/` porque
Settings no es un feature-módulo. Es la única key fuera de un paquete `feature/*/navigation/`.

### 2.2 `SceneStrategy` realmente adaptativas — **PARCIAL**

Son adaptativas de verdad, verificado en las fuentes de las librerías:

- `rememberListDetailSceneStrategy()` usa por defecto
  `calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())`, que devuelve
  `maxHorizontalPartitions = 2` **solo en Expanded (≥840 dp)**
  (`PaneScaffoldDirective.kt:55-75`, artefacto 1.3.0-rc01). En compacto devuelve `null` y cede al
  single-pane interno de `NavDisplay`.
- `OverlaySceneStrategy` decide sheet↔diálogo por
  `isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)` — `OverlaySceneStrategy.kt:101-107`.
- `ListDetailSceneSelectionTest` construye la estrategia con **los mismos defaults que producción**
  (`shouldHandleSinglePaneLayout = false`, `PopUntilScaffoldValueChange`), así que no fija un
  comportamiento distinto al real.

Fugas: **V4** (dos umbrales de «ancho») y **V6** (sheet sin animación de salida).

No hay `SupportingPaneSceneStrategy`; esta app no lo necesita.

### 2.3 Transiciones centralizadas y M3 Expressive — **HECHO**

`NavigationMotion.kt:38-82` implementa shared-axis X con `scheme.defaultSpatialSpec()` /
`defaultEffectsSpec()` leídos de `MaterialTheme.motionScheme` —no números en el call site—,
recorrido del 30 % del ancho, y `predictiveBack(edge)` que respeta `NavigationEvent.EDGE_RIGHT`.
Una sola fuente para las tres transiciones; ninguna pantalla contribuye nada.

No hay inconsistencia entre parejas de pantallas: `NavDisplay` renderiza las overlay scenes *fuera*
del `AnimatedContent` (`NavDisplay.kt:893-894`), así que el shared-axis no se aplica a sheets ni
diálogos. Eso es correcto, no un olvido.

`android:enableOnBackInvokedCallback="true"` está puesto (`AndroidManifest.xml:8`).

### 2.4 Scoping de ViewModels por entrada — **HECHO**

- `rememberSaveableStateHolderNavEntryDecorator()` va **antes** de
  `rememberViewModelStoreNavEntryDecorator()` (`DocucraftApp.kt:66-69`). El orden importa: el KDoc
  de la librería exige el primero para que los ViewModel con `SavedStateHandle` funcionen, y el
  decorator pasa `LocalSavedStateRegistryOwner` al owner
  (`ViewModelStoreNavEntryDecorator.kt:118-123`). Por tanto el `scan_in_flight` de `HomeViewModel`
  sobrevive muerte de proceso correctamente.
- `DocumentActionsViewModel` recibe el uuid desde la key vía `parametersOf`
  (`di/DocumentScannerViewModels.kt:33-44`), no desde el grafo.

Matiz, no bug: los tres overlays son tres entradas y por tanto **tres instancias** de
`DocumentActionsViewModel` sobre el mismo documento, cada una con su `observeDocument`. Funciona
—al borrar, todas emiten `CloseAll` y `goBackWhile` es idempotente— pero es duplicación.
Navigation 3 no tiene scoping por subgrafo; es el precio del diseño.

### 2.5 Pantallas que saben lo que no deben — **PARCIAL**

Bien: `HomeScreen` recibe callbacks y no conoce keys; `PdfViewerSection.kt:58` lee
`LocalPaneContext.current.providesOwnBackAffordance` en vez de medir la ventana; los overlays leen
`LocalOverlayPresentation` en vez de decidir por `if (ancho)`.

Sin cerrar: **V1**, **V2**, **V3** y **V5**.

### 2.6 Argumentos type-safe — **HECHO**

Todas las keys son `@Serializable` y llevan solo identidad. `PdfViewerSection.kt:41-43` re-observa
el documento desde el catálogo —en ventana expandida puede renombrarse o borrarse mientras está
abierto— y discrimina bien entre «aún no leído» y «borrado» (`PdfViewerSection.kt:48-52`).

Acoplamiento residual leve: el shell deriva `openDocumentId` de `(backStack.last() as? PdfViewer)`
y se lo pasa a `homeSection` (`DocucraftApp.kt:56-58,79`). Es el shell, así que es legítimo, pero
significa que cada apertura o cierre de documento recompone el `entryProvider` entero.

### 2.7 Unión navegación ↔ escáner — **PARCIAL**

La arquitectura está limpia: `MainActivity` solo presta su launcher, el escaneo no toca el back
stack, y la resiliencia (`scan_in_flight` + `resumePendingScan`) funciona gracias al orden de
decorators de 2.4.

La fricción está en el widget: ver **V7**.

---

## 3. Comportamiento

### 3.1 Tests

`./gradlew :app:testDebugUnitTest` → **BUILD SUCCESSFUL, 55 tests, 0 fallos**. De ellos **30 son de
navegación**:

| Clase | Tests | Qué fija |
|---|---|---|
| `NavigatorTest` | 8 | reglas del back stack: no duplicar el tope, no vaciar la pila, `goBackWhile` |
| `ListDetailSceneSelectionTest` | 9 | resolución de escenas por tamaño de ventana y por `sceneKey` |
| `OverlaySceneSelectionTest` | 6 | sheet vs diálogo, identidad de escena, overlay solo en la pila |
| `PaneContextSceneDecoratorTest` | 4 | que envolver escenas no colapse su identidad |
| `NavKeySerializationTest` | 3 | round-trip por `Class.forName(...).kotlin.serializer()` |

Son tests de decisión pura, no de render, que es la elección correcta: cubren cada breakpoint en
milisegundos en vez de un emulador por breakpoint.

**Carencias:**

- Cero tests instrumentados de navegación (`app/src/androidTest/` solo tiene el de ejemplo).
- Nada cubre transiciones ni predictive back.
- Nada cubre la restauración real del back stack tras muerte de proceso; el test de serialización
  cubre las keys, no la pila.
- `NavKeySerializationTest` lista las keys a mano —deliberadamente—, así que una key nueva sin
  añadir ahí no se detecta.

### 3.2 Riesgos en runtime

| Área | Estado |
|---|---|
| Back del sistema | **Seguro.** `NavDisplay.kt:564` hace `repeat(entries.size - scene.previousEntries.size) { onBack() }`, es decir puede llamar a `goBack()` varias veces; `BackStackNavigator.goBack()` se niega a vaciar la pila (`Navigator.kt:65`), que es justo lo que evita el crash. Razonado y testeado. |
| Predictive back | Habilitado, con `edge` respetado. **No verificado en dispositivo.** |
| Rotación / cambio de config | El back stack es saveable y el estado de formulario usa `rememberSaveable(documentUuid)` (`DocumentActionsSection.kt:78-81`), incluido «el sheet se vuelve diálogo mientras editas». Bien en código; no verificado en runtime. |
| Deep links | No existen hacia el grafo. Los PDFs externos entran por `PdfViewerActivity`, deliberadamente fuera del back stack. Navigation 3 no trae deep links de serie: es una ausencia, no una regresión. |

---

## 4. Violaciones y bugs

Numerados según la convención de `docs/README.md`. Prioridad por *qué bloquean*.

### P1 — impiden soportar una tablet hoy

**V1 · `SettingsScreen.onBack` cierra el panel equivocado**
`SettingsSection.kt:59` pasa `onBack = navigator::goBack`. Con el stack
`[Home, Settings, AppearanceSettings]` en ventana expandida ambos paneles son visibles; pulsar la
flecha del panel *lista* hace `removeLast()` y cierra **Apariencia**, no Settings. En móvil es
invisible porque nunca coexisten.

**V2 · `AppearanceScreen` siempre pinta su flecha de atrás**
`AppearanceScreen.kt:190-193`, sin consultar `LocalPaneContext`. En tablet, el panel de detalle
muestra flecha de atrás con la lista de Settings visible al lado. Es exactamente el problema que
`PaneContext` existe para resolver, aplicado solo a una de las dos pantallas de detalle.

**V3 · `DocumentActionsContent` maqueta por orientación del dispositivo**
`components/sheet/DocumentActionsSheet.kt:102` lee `LocalOrientation` para elegir Row vs Column. Es
«la pantalla mide el dispositivo» en su forma pura: si el overlay cayó en un contenedor estrecho de
una tablet en horizontal, se maqueta como si tuviera todo el ancho.

### P2 — calidad percibida y trampas armadas

**V4 · Dos definiciones de «ancho» en la capa de navegación**
Los overlays pasan a diálogo en ≥600 dp; el layout no se parte en dos paneles hasta ≥840 dp. Entre
600 y 839 dp hay diálogos centrados sobre un layout de un solo panel. Puede ser intencional, pero
son dos constantes en dos ficheros sin criterio escrito.

**V5 · `PaneContext.isSolePane` miente con el placeholder**
`PaneContext.kt:79` define `isSolePane = delegate.entries.size <= 1`. Cuando `Home` está sola en
ventana expandida la escena list-detail tiene **una** entrada —el placeholder no es entrada, lo
fija `ListDetailSceneSelectionTest:44-55`— y se reporta `isSolePane = true` teniendo un panel al
lado. Hoy es inofensivo porque ni `Home` ni `Settings` lo leen; es una trampa para quien lo use
después.

**V6 · El sheet no tiene animación de salida**
`OverlayScene` expone `suspend fun onRemove()` precisamente para esto —«Animations for exiting
overlays should be implemented within `onRemove`»— y `SheetScene`
(`OverlaySceneStrategy.kt:112-136`) no lo implementa. `NavDisplay.kt:915-918` lo llama y retira la
escena. Cerrando el sheet por código (botón, back del sistema, tras confirmar una edición)
desaparece de golpe. Solo se ve bien cuando el usuario lo arrastra, porque ahí anima el propio
`ModalBottomSheet` antes de pedir el dismiss.

### P3 — no bloquean, pero desinforman

**V7 · La petición del widget depende de qué destino esté activo**
`ACTION_SCAN_DOCUMENT` → `ScanRequestBus.request()` (`MainActivity.kt:170-174`) lo consume
`HomeViewModel.observeExternalScanRequests()`. El bus es un `Channel(BUFFERED)` sin replay y asume
un único consumidor —su propio KDoc lo dice—. Si la app se restaura con `[Home, PdfViewer]` en
móvil, `Home` no está compuesta, su ViewModel no existe y la petición **se encola**: el escaneo no
arranca, y cuando el usuario pulse atrás arrancará sin que lo haya pedido en ese momento.

**B3 · El visor saca del back stack lo que no es suyo** *(reportado en runtime, 2026-09-20)*
`PdfViewerSection` distinguía «aún no leído» de «borrado» con un `rememberSaveable` que sobrevive a
que la entrada salga de composición. Un predictive back hace que `NavDisplay` **componga la escena
a la que se vuelve** para poder animarla, así que la entrada del visor despierta con
`wasLoaded = true` restaurado y `document = null` —el `initialValue` del flow, antes de su primera
emisión— y concluye que su documento fue borrado. Llamaba a `goBack()`, que saca *lo que haya
encima*: una pantalla de ajustes, por ejemplo. Ocurre en el instante en que empieza el gesto, antes
de cualquier animación y fuera del alcance de una cancelación.

Dos errores en uno: inferir un estado de un `null` ambiguo, y decir «vuelve atrás» cuando se quería
decir «quítame a mí». `DocumentActionsViewModel` tiene la misma forma y **no** el mismo fallo,
porque su `wasLoaded` vive en el ViewModel y no se restaura al recomponer.

**B4 · El predictive back reproducía la animación de entrada** *(reportado en runtime, 2026-09-20)*
`NavigationMotion.predictiveBack(edge)` espejaba la transición sobre el borde del gesto
(`reversed = edge != EDGE_RIGHT`, con `EDGE_LEFT = 0` y `EDGE_RIGHT = 1`). Un swipe desde el borde
derecho daba `reversed = false`, que es exactamente la transformación de `forward()`. Volver atrás
se veía como ir hacia delante según por qué lado de la pantalla hubieras deslizado.

**B5 · Abrir las acciones de un documento apagaba su highlight** *(reportado en runtime,
2026-09-20)*
`DocucraftApp` derivaba el documento abierto con `(backStack.lastOrNull() as? PdfViewer)`, y el
comentario que lo acompañaba defendía explícitamente que «solo el tope cuenta». Confunde *encima*
con *en lugar de*: un overlay flota sobre la disposición sin sustituirla, así que al abrir
`DocumentActions` el visor sigue en pantalla bajo el sheet pero el tope deja de ser `PdfViewer` y la
lista pierde la marca del documento del que trata el propio sheet. Misma familia que B3: una
derivación del back stack que no distingue apilar de reemplazar.

**B6 · Cerrar un sheet costaba dos backs** *(reportado en runtime, 2026-09-20)*
`ModalBottomSheet` trata el back como «colapsa, luego cierra» mientras exista un estado parcialmente
expandido (`ModalBottomSheet.kt:126-132`). Con contenido que no cabe en la altura colapsada, el
primer back es peor que inútil: esconde lo que el usuario estaba leyendo. Y contradice el modelo —
un overlay es *una* entrada del back stack, así que back tiene un solo trabajo: sacarla.

**B1 · `AGENTS.md:34` describe una arquitectura que ya no existe** (ver 1.3).

**B2 · `FullScreenLoading` es código muerto** y los paquetes `sheet/` están mal nombrados
(ver 1.3).

### Lo que NO está roto

Añadir un destino nuevo limpio —key + `entry` en su sección, cero ficheros del shell tocados— y
cambiar una transición sin tocar pantallas —un fichero: `NavigationMotion.kt`— funcionan hoy. Esos
dos objetivos están genuinamente cerrados.

---

## Decisión: un `NavDisplay` dentro de un modal

Registrada aquí porque es la que más bugs ha causado en este código, y porque la regla en
`AGENTS.md` sin el razonamiento detrás es una superstición.

**No está prohibido.** Las propias recetas de Navigation 3 muestran displays anidados, y son la
herramienta correcta para un sub-flujo genuinamente autónomo: un onboarding dentro de un diálogo,
un asistente que se descarta entero. La pregunta no es «¿anidado o no?», es **«¿esto es un destino
de la app o un paso interno de un widget?»**.

- Si el usuario puede llegar, salir, girar la pantalla y esperar encontrarlo — es un destino. Va en
  la pila única, con su key, y back significa «sácalo».
- Si es un paso interno que se descarta entero y no sobrevive a nada — puede tener su propio
  display. Entonces su back tiene que estar contenido en su ventana, que es justo lo que
  `ModalBottomSheet` y `Dialog` dan gratis: son ventanas flotantes con su propio
  `NavigationEventDispatcherOwner`, así que el back de dentro no llega al `NavDisplay` de fuera.

Lo que había aquí era lo primero disfrazado de lo segundo: una pila a mano en `HomeViewModel`,
renderizada por un `NavDisplay` dentro de un `ModalBottomSheet`. Cuatro consecuencias, todas
comprobadas en este repo:

1. **La pila no se guardaba.** No era serializable ni estaba en el `SavedStateHandle`. Matar el
   proceso con el sheet abierto la perdía. Se escribía `active_sheet_doc_id` en el
   `SavedStateHandle` y no lo leía nadie.
2. **Sin scoping.** Los decoradores de Navigation 3 dan `ViewModel` y `rememberSaveable` *por
   entrada*. Las páginas de una pila privada no son entradas, así que no reciben ninguna de las
   dos cosas.
3. **La ventana cambia de forma.** Sheet-o-diálogo y la pila interna eran dos estados separados que
   tenían que ponerse de acuerdo. Con una pila única, rotar es el mismo entry en otro contenedor —
   y el estado del formulario lo lleva un `rememberSaveable` de la entrada.
4. **Back deja de significar una cosa.** Con dos pilas hay que *enrutar* cada back a mano: ¿saca de
   la interna o de la externa? Esa decisión escrita a mano es exactamente la clase de código que
   produce B3 (se cerró lo que no era) y B6 (hacían falta dos pulsaciones).

El cuarto punto es el importante, y conviene notar que **B6 no venía de una pila anidada**: venía
de que `ModalBottomSheet` tiene su propio concepto de back —colapsar— conviviendo con el del back
stack —sacar la entrada—. Dos significados para el mismo gesto en la misma superficie. El mismo
defecto en miniatura, y se arregló igual: quitándole el segundo significado
(`enabledValues = setOf(Hidden, Expanded)`).

La regla operativa, entonces: **un destino, una entrada, una pulsación de back.** Si algo en la UI
quiere darle a back un segundo significado dentro de la misma superficie, es señal de que o bien
falta un destino, o bien sobra un estado.

---

## 5. Plan de cierre

Pasos pequeños e independientes. No se reescribe lo que está bien.

| # | Cierra | Paso | Verificación |
|---|---|---|---|
| 1 | V5 | `PaneContext`: sustituir `entries.size <= 1` por una señal explícita de la escena, para que «Home sola con placeholder» no se reporte como panel único | Test en `PaneContextSceneDecoratorTest` |
| 2 | V2 | `AppearanceScreen`: mostrar la flecha según `LocalPaneContext.current.providesOwnBackAffordance`, como ya hace el visor | Visual en tablet + preview |
| 3 | V1 | `SettingsSection`: dar a `SettingsScreen` un `onBack` que salga de *toda* el área de settings, no `goBack` a secas | Test en `NavigatorTest` con `[Home, Settings, AppearanceSettings]` |
| 4 | V3 | `DocumentActionsContent`: quitar `LocalOrientation` y decidir la maqueta desde lo que el contenedor le dice | Previews sheet y diálogo |
| 5 | V6 | `SheetScene`: hoistear `rememberModalBottomSheetState` e implementar `onRemove()` con `hide()` antes de salir de composición | Manual: cerrar el sheet con el botón y ver que desliza |
| 6 | V4 | Unificar el umbral, o dejarlo en Medium **con un comentario** que diga por qué difiere del scaffold | `OverlaySceneSelectionTest` ya parametriza por ancho |
| 7 | V7 | Que el shell observe el bus y vuelva a Home antes de delegar el escaneo, en vez de depender de que Home esté compuesta | Test de `Navigator` + prueba manual con el widget sobre un PDF abierto |
| 8 | B1, B2 | Actualizar `AGENTS.md:34`, borrar `FullScreenLoading.kt`, renombrar los paquetes `sheet/` | `assembleDebug` + `spotlessCheck` |

**Orden acordado con el propietario (2026-09-20):** primero P1 (pasos 2, 3, 4), después el resto.

Los pasos 1–4 son independientes entre sí. Los pasos 5 y 6 tocan `OverlaySceneStrategy.kt` y
conviene hacerlos juntos. El paso 7 es el único que toca el escáner.

### Progreso

- [x] Paso 1 — V5, `PaneContext` explícito *(2026-09-20)*
- [x] Paso 2 — V2, flecha de `AppearanceScreen` *(2026-09-20)*
- [x] Paso 3 — V1, salida del área de settings *(2026-09-20)*
- [x] Paso 4 — V3, maqueta de acciones por contenedor *(2026-09-20)*
- [x] Paso 5 — V6, animación de salida del sheet *(2026-09-20)*
- [x] Paso 6 — V4, umbrales nombrados y justificados *(2026-09-20)*
- [x] B3 — el visor se quita a sí mismo, no lo de encima *(2026-09-20)*
- [x] B4 — el predictive back deja de espejarse sobre el borde *(2026-09-20)*
- [x] B5 — el highlight sobrevive a los overlays *(2026-09-20)*
- [x] B6 — un back cierra el sheet *(2026-09-20)*
- [x] Paso 7 — V7, widget y destino activo *(2026-09-20)*
- [x] Paso 8 — B1/B2, limpieza y documentación *(2026-09-20)*

#### B3 y B4 corregidos — 2026-09-20

Ambos salieron probando en dispositivo, no de la auditoría: son de comportamiento en runtime y
ninguno de los 38 tests de navegación los tocaba.

**B4.** `predictiveBack()` pierde el parámetro `edge` y devuelve `backward()`. Un eje compartido
describe la *jerarquía*, no el gesto; el propio `defaultPredictivePopTransitionSpec` de la librería
recibe el borde y lo ignora.

**B3.** Tres cambios:

1. `Navigator.removeDestination(key)` — saca una destinación de donde esté, todas sus apariciones,
   sin vaciar la pila. Es una operación distinta de `goBack` y ahora se puede decir cuál se quiere.
2. `PdfViewerSection` deja de inferir: un `OpenDocument` privado con `Loading`, `Gone` y `Open`
   hace explícito lo que antes eran dos `null` indistinguibles. `Loading` ya no es motivo para
   irse.
3. El efecto llama a `removeDestination(route)`, no a `goBack()`.

Cinco tests nuevos en `NavigatorTest` (13 en total) fijan la operación nueva, incluido que no vacíe
la pila y que no toque nada si la destinación no está.

**Verificación.** 68 tests, 0 fallos. `assembleDebug` y `spotlessCheck` correctos. Instalado en
dispositivo; **los dos gestos siguen pendientes de comprobación manual**, que es lo único que
cierra estos dos.

#### P1 cerrado — 2026-09-20

**Paso 2 (V2).** `AppearanceScreen` gana `showBackButton: Boolean = true`, igual que
`PdfViewerScreen`. Quien lo decide es la sección, no la pantalla:
`SettingsSection.kt` lo alimenta con `LocalPaneContext.current.providesOwnBackAffordance`.

**Paso 3 (V1).** Nueva `internal fun Navigator.leaveSettings()` en `SettingsSection.kt`: vacía los
detalles de settings que haya encima y luego sale de `Settings`. Es lo que recibe `SettingsScreen`
como `onBack`. `AppearanceSettings` y `SubscriptionSettings` siguen con `goBack` porque desde un
detalle una sola salida sí es correcta. Fijado por `SettingsNavigationTest` (4 tests), incluido el
caso «settings abierto sobre un documento», donde el documento debe sobrevivir.

**Paso 4 (V3).** `LocalOverlayPresentation` se sustituye por `LocalOverlayContext`, que lleva la
presentación *y* `hasRoomToStack`. La estrategia calcula el segundo desde
`HEIGHT_DP_MEDIUM_LOWER_BOUND` (480 dp) y lo mete en la clave de escena, porque `NavDisplay`
conserva la primera escena overlay que ve para una clave dada y descarta las posteriores
(`NavDisplay.kt:684-691`): sin eso, girar el móvil con el sheet abierto seguiría pintando lo que se
le dijo antes de girar. `DocumentActionsContent` pasa a recibir `stacked: Boolean` y ya no importa
`Configuration` ni `LocalOrientation`.

**Verificación.** `:app:testDebugUnitTest` → 61 tests, 0 fallos (navegación: 30 → 36).
`:app:assembleDebug` y `spotlessCheck` correctos. Sin verificar en dispositivo: la apariencia real
en tablet y en móvil apaisado.

**Efecto colateral, ya resuelto:** `LocalOrientation` quedó sin consumidores y se ha retirado de
`core/presentation/common/CompositionLocals.kt` junto con el `LocalConfiguration` que lo alimentaba.

#### P2 cerrado — 2026-09-20

**Paso 1 (V5).** La heurística `entries.size <= 1` desaparece. `SinglePaneScene` y
`ThreePaneScaffoldScene` son ambas `internal`, así que un decorador de escenas no puede
distinguirlas por tipo: la pregunta no se puede responder *después*, hay que hacérsela a quien tomó
la decisión. Nueva `SceneStrategy<T>.sharingTheWindow()` en `PaneContext.kt`, que envuelve la
estrategia en vez de decorar todas las escenas. Es correcta porque `ListDetailSceneStrategy`
construye su scaffold y devuelve `null` salvo que `paneCount` salga mayor que uno
(`ListDetailSceneStrategy.kt:192-196`) mientras `shouldHandleSinglePaneLayout` siga en `false`: todo
lo que entrega son dos paneles en pantalla, y todo lo que rechaza cae al fallback single-pane, donde
el valor por defecto de `LocalPaneContext` ya dice lo correcto.

Consecuencia: `sceneDecoratorStrategies` se retira de `DocucraftApp`; el shell compone la lista de
estrategias en un `remember`, que además deja de reconstruirla en cada recomposición.

**Paso 5 (V6).** `SheetScene` hoistea su `SheetState` y lo esconde en `onRemove()`, siguiendo el
`AnimatedBottomSheetSample` de la propia librería (incluido su `lateinit`, con guarda por si la
escena se saca antes de componerse). `NavDisplay` mantiene el overlay compuesto hasta que esa
suspensión vuelve (`NavDisplay.kt:915-918`). De paso, `rememberModalBottomSheetState` está
deprecado en Material3 1.5.0-alpha22, así que se usa
`rememberBottomSheetState(initialValue = SheetValue.Hidden)`.

**Paso 6 (V4).** Los dos umbrales pasan a `OverlayBreakpoints`, con la explicación de por qué son
dos preguntas distintas y no una escrita dos veces: una columna centrada cabe en medium (600 dp),
dos paneles no llegan hasta expanded (840 dp), y entre ambos un overlay es un diálogo sobre un solo
panel. Es deliberado.

**Verificación.** `:app:testDebugUnitTest` → 63 tests, 0 fallos (navegación: 30 → 38).
`:app:assembleDebug` y `spotlessCheck` correctos, sin warnings de deprecación.
`PaneContextSceneDecoratorTest` se sustituye por `PaneContextSceneStrategyTest` (6 tests), que fija
el caso que antes se respondía mal y que el `null` de la estrategia sobreviva al envoltorio.

**Sin verificar en dispositivo:** la animación de salida del sheet y el aspecto en tablet. Ambas
cosas necesitan emulador.

#### B5 corregido — 2026-09-20

La regla pasa a ser «la entrada de visor más alta que haya en la pila», no «el tope de la pila»,
extraída a `List<NavKey>.openDocumentId()` para poder fijarla. Lo que sí reemplaza la disposición
—ajustes, que es una escena propia— se lleva la lista fuera de pantalla, así que un highlight
apuntando a un visor enterrado es uno que nadie ve, y vuelve a ser correcto cuando la lista
reaparece.

`OpenDocumentHighlightTest`, 7 tests. Total: 75, 0 fallos.

#### B6 corregido — 2026-09-20

`SheetScene` pasa `enabledValues = setOf(Hidden, Expanded)`: sin estado intermedio, el sheet abre a
la altura de su contenido y back lo cierra de una. Vale para los tres overlays que hay (acciones,
editar, borrar), todos de contenido corto y fijo. Un sheet que de verdad necesitara media altura
arrastrable sería otro tipo de destino y tendría que declararlo.

#### P3 cerrado — 2026-09-20

**Paso 7 (V7).** `ScanRequestBus` deja de ser un `Channel` de un solo consumidor y pasa a ser una
petición **retenida** (`StateFlow<Boolean>` + `take()`). El canal se equivocaba en dos cosas a la
vez: solo se enteraba quien leyera primero, y una petición hecha con el catálogo fuera de pantalla
no tenía lector — esperaba, y arrancaba el escáner sin pedirlo cuando el usuario volvía.

Ahora la petición queda en pie y hay dos observadores con papeles distintos:
`ScanRequestNavigation` —un composable sin UI que el shell hospeda, en
`feature/docscanner/navigation/`— trae el catálogo a pantalla sin consumirla, y `HomeViewModel` la
**toma** y escanea. Dónde hay que honrar una petición es una pregunta de navegación, así que la
responde la capa de navegación y no un state holder que no ve el back stack.

`ScanRequestBusTest` (6 tests) y dos tests nuevos en `HomeViewModelTest`, incluido el caso que
fallaba: una petición hecha antes de que el catálogo existiera se honra al llegar.

**Paso 8 (B1/B2).** `AGENTS.md` reescribe sus reglas de navegación —apuntaba a un `Route.kt` que no
existe— e incorpora la regla de los modales con enlace al registro de decisión de arriba.
`FullScreenLoading.kt` borrado. `EditDocumentUiState` sale de `screens/home/sheet/`: su overlay es
sheet *o* diálogo, así que el paquete mentía; se va junto a quien lo consume, en
`screens/home/actions/`.

`components/sheet/` **se queda como está**, a propósito: `DocumentActionsSheet.kt` es
`AlwaysSheet` y `DocumentActionSheetSkeleton.kt` solo lo usan las variantes sheet. Ahí el nombre
dice la verdad, y renombrar por simetría habría sido ruido.
