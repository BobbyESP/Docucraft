<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Fase 3 · Visor PDF — Arquitectura objetivo

> Basado en el [análisis 06](06-pdfviewer-analysis.md). El plan para llegar hasta aquí está en
> [08-pdfviewer-migration-plan.md](08-pdfviewer-migration-plan.md).
>
> **Objetivos**:
> 1. Reescribir la UI del visor siguiendo Material 3 Expressive.
> 2. Añadir **copiar texto** y **abrir enlaces**.
> 3. Que el **OCR** futuro entre como un proveedor más, sin rehacer nada del visor.
>
> **Decisiones de producto aprobadas el 2026-09-23**: D1–D5 (§2).

---

## 1. La decisión de diseño clave

> **El texto y los enlaces son una capa de contenido por página, en coordenadas de página
> normalizadas, independiente de quién la produzca. El motor aporta geometría y gestos; nunca sabe de
> dónde viene el texto.**

Todo lo demás se deriva de ahí:

- **Coordenadas normalizadas `[0,1] × [0,1]`, origen arriba a la izquierda**, porque es el espacio
  que `PdfTapEvent.pagePosition` ya usa, que no cambia con el zoom, y al que convergen las dos fuentes
  de texto: los puntos PDF (÷ tamaño de página) y los píxeles del OCR (÷ tamaño del bitmap).
- **Un modelo de texto propio** (palabras con rectángulos), porque la selección nativa de la
  plataforma (`selectContent()`) solo conoce el texto propio del PDF y bloquearía el OCR
  ([análisis §4.4](06-pdfviewer-analysis.md#44-compatibilidad-con-el-ocr)).
- **El motor de renderizado no se cambia** ([análisis §4.3](06-pdfviewer-analysis.md#43-alternativas-evaluadas)).

---

## 2. Decisiones de producto

### D1 · Por debajo de API 35, copiar texto degrada

**Qué.** No se añade ningún proveedor de texto alternativo (PdfBox) ni se sube el `minSdk`. En
dispositivos sin las APIs de contenido de la plataforma, el visor se comporta como con un documento
escaneado: explica por qué no se puede seleccionar y no falla. Los enlaces siguen la misma regla: si
no se pueden leer, el documento simplemente no tiene zonas tocables.

**Por qué.**
- Subir el `minSdk` de 24 a 28 es razonable a medio plazo (y es lo que abriría la puerta a
  `androidx.pdf` cuando sea estable), pero implica revisar el motor y el resto de la app. No es el
  objetivo de esta fase.
- PdfBox-Android añade varios MB y un segundo parser de PDF para una franja de dispositivos que se
  reduce con el tiempo.
- La degradación **no es trabajo tirado**: es el mismo camino que necesitan hoy los PDF escaneados, y
  el que el OCR vendrá a cubrir.

**Consecuencia de diseño.** El contrato distingue `Unsupported` (el dispositivo no puede) de
`NoText` (la página no tiene), porque el mensaje al usuario es distinto y porque el OCR solo
resolverá el segundo caso… o los dos, ya que el OCR funciona en cualquier versión. El proveedor
compuesto (§4.3) consultará el OCR en ambos casos.

### D2 · Ajustes de visualización: predeterminados globales opcionales + memoria por documento en la sesión

**Qué.** Los ajustes del visor (**modo de ajuste** y **modo noche**) funcionan en dos niveles:

1. **Predeterminados globales**, en Ajustes → Visor de documentos:
   - Un switch **«Usar ajustes predeterminados»**.
   - Con el switch activo: selector de modo de ajuste y switch de modo noche, que se aplican **a todo
     documento al abrirlo por primera vez en la sesión**.
   - Con el switch inactivo, los controles se muestran deshabilitados (no ocultos, para que se
     descubran) y se usan los **valores de fábrica**.
2. **Memoria por documento durante la sesión**: cualquier cambio hecho en el visor se recuerda **para
   ese documento** mientras viva el proceso. Nunca se escribe en disco.

Al reabrir la aplicación se vuelve a los predeterminados (o a los de fábrica, con el switch
inactivo).

| Situación | Switch activo | Switch inactivo |
|---|---|---|
| Primera apertura de un documento en la sesión | Predeterminados de Ajustes | Valores de fábrica |
| El usuario cambia el ajuste en el visor | Se guarda en memoria **para ese documento** | Igual |
| Reabrir ese documento (mismo proceso) | Lo guardado en la sesión | Igual |
| Abrir otro documento | Sus propios valores (sesión o predeterminados) | Sus propios valores (sesión o fábrica) |
| Rotar, cambiar de panel, pasar por Ajustes | Se conserva | Se conserva |
| El usuario cierra la app (la quita de recientes) y la reabre | Predeterminados | Fábrica |
| Cambiar los predeterminados a mitad de sesión | Los documentos **ya ajustados** en la sesión conservan lo suyo; los demás toman los nuevos | — |
| Activar o desactivar el switch a mitad de sesión | Misma regla: lo ajustado en la sesión gana | Misma regla |

**Muerte de proceso con restauración: cuenta como la misma sesión** *(A1, confirmado el
2026-09-23)*. Si el sistema mata el proceso en segundo plano y el usuario vuelve, Android restaura la
pila y el visor que estaba abierto. **Ese visor** conserva sus ajustes: se guardan también en el
`SavedStateHandle` de su entrada y, al restaurar, se vuelven a sembrar en la memoria de sesión. El
resto de documentos, que no estaban en pantalla, vuelve a los predeterminados.
*Por qué*: el usuario no ha «reabierto la aplicación»; para él es la misma sesión, y un visor
restaurado con otro aspecto parecería un fallo. Solo cuenta como sesión nueva cuando el usuario
cierra la app (la quita de recientes) o la abre en frío.

**Valores de fábrica: ajustar al ancho** *(A2, confirmado el 2026-09-23)*. `ViewerFitMode.WIDTH`,
con el modo noche desactivado. El usuario puede cambiarlos por dos vías: en el visor (para ese
documento, durante la sesión) o en Ajustes, activando el switch y eligiendo otros predeterminados.
- *Por qué ancho*: es el modo de lectura estándar con scroll vertical, y el valor por defecto del
  propio motor (`PdfLayoutSpec.fitMode`). Hoy la app lo sobrescribía con `BOTH`, que en un móvil en
  vertical deja la página pequeña y con márgenes.
- *Por qué el mismo valor en cualquier ventana, y no «ancho en móvil vertical, página completa en
  otros casos»*: un valor por defecto que dependiera del tamaño de la ventana cambiaría al rotar, y
  entonces no quedaría claro qué debe recordar la memoria por documento. Además obligaría al visor a
  medir la ventana, que `AGENTS.md` prohíbe. Quien prefiera otro modo lo fija una vez en Ajustes.
- **Cambio de comportamiento deliberado**: los documentos pasan a abrirse ajustados al ancho en vez
  de a la página completa.

**Por qué este diseño.**
- **La memoria de sesión vive en el proceso, no en DataStore**, porque el requisito es explícitamente
  temporal: persistirla contradiría «al reabrir, predeterminados».
- **Se indexa por documento, no por entrada de navegación**, porque un mismo documento puede abrirse
  varias veces en la sesión (desde Home, desde otra app), cada vez con una entrada nueva.
- **La clave del documento** es el uuid para los catalogados y **la URI** para los externos: el uuid
  que `PdfViewerActivity` asigna hoy es aleatorio en cada intent y no identificaría el documento.
- **La memoria es compartida por `MainActivity` y `PdfViewerActivity`** (mismo proceso). Abrir un
  mismo PDF externo dos veces recuerda sus ajustes.
- **Los tipos del dominio no son los de `:composepdf`**: `ViewerFitMode` es un enum propio en
  `core/domain/model/`, mapeado a `com.composepdf.FitMode` solo en la presentación del visor. Es la
  misma regla que mantiene ML Kit fuera de `:app`: si mañana cambia el motor, las preferencias
  guardadas no cambian de formato.
- **Los predeterminados viven en `UserPreferences`** (core) porque son preferencias de usuario y
  `core` no puede depender de un feature.

### D3 · Aviso previo con el dominio antes de abrir un enlace externo

**Qué.** Un tap sobre un enlace externo **no lo abre directamente**. Muestra un aviso anclado al
enlace con:
- el **host real** del destino, destacado (en ASCII/punycode si es un nombre internacionalizado);
- la URL completa, truncada, como texto secundario;
- un aviso «Conexión no segura» si el esquema es `http`;
- las acciones **Abrir** (principal) y **Copiar enlace**.

`mailto:` muestra la dirección («Escribir correo»); `tel:` el número («Llamar»). Los **enlaces
internos no llevan aviso**: saltan directamente con un snackbar «Volver a la página N».

**Por qué.**
- En un PDF, **el texto visible de un enlace no tiene por qué coincidir con su destino** («mibanco.es»
  puede apuntar a otro sitio). El aviso enseña el destino real, que es lo que importa.
- Mostrar el **host parseado** desarma trucos como `https://mibanco.es@otro.com`, cuyo host real es
  `otro.com`, y mostrarlo en punycode delata los homógrafos.
- Además resuelve un conflicto de gestos: el tap también alterna el chrome. Con el aviso, un toque
  accidental sobre un enlace no saca al usuario de la app.
- Los enlaces internos son reversibles (el snackbar lleva de vuelta): pedir confirmación ahí sería
  fricción sin beneficio.

**No es un destino de navegación, y es deliberado.** `AGENTS.md` exige que un sheet o diálogo al que
se puede ir, salir y volver sea un destino. Este aviso es un **popup anclado a una posición de la
página**, como un menú contextual: se mueve con el pan y el zoom, se cierra con cualquier scroll y no
tiene sentido tras una muerte de proceso. Es estado de UI local, igual que el `DropdownMenu` de la
barra superior.

### D4 · Custom Tabs, con `ACTION_VIEW` como alternativa

**Qué.** Los enlaces `http(s)` se abren en una Custom Tab (`androidx.browser`, estable). Si no hay
ningún navegador que ofrezca Custom Tabs, se usa `Intent.ACTION_VIEW`. Si tampoco hay app para eso,
se muestra un mensaje («No hay ninguna app para abrir este enlace») con la opción de copiarlo.
`mailto:` → `ACTION_SENDTO`; `tel:` → `ACTION_DIAL` (no requiere permiso de llamada).

**Por qué.**
- La Custom Tab **se queda en la tarea de la app**: atrás devuelve al visor. Además comparte sesión,
  contraseñas y cookies con el navegador del usuario, y trae «Abrir en Chrome» de serie.
- `ACTION_VIEW` como alternativa garantiza que el enlace siempre se pueda abrir.
- Un WebView propio queda descartado: supondría más superficie de ataque, sin gestor de contraseñas y
  con mantenimiento propio.

**Detalles que no pueden olvidarse.**
- **Se lanza desde la Activity**, no desde el contexto de aplicación. Con `FLAG_ACTIVITY_NEW_TASK` la
  pestaña se abriría en otra tarea y se perdería justo lo que motiva D4.
- **Visibilidad de paquetes (API 30+, `targetSdk` 37)**: el manifest necesita `<queries>` para
  `android.support.customtabs.action.CustomTabsService`, `ACTION_VIEW` con `https`, `ACTION_SENDTO`
  con `mailto` y `ACTION_DIAL` con `tel`. Sin ellas, la detección de un proveedor de Custom Tabs
  devuelve «ninguno» en todos los dispositivos.
- **Colores**: la barra de la pestaña toma los colores del `colorScheme` activo (claro/oscuro y
  dinámico), para que la transición no rompa el tema.

### D5 · Pila de navegación propia en `PdfViewerActivity`

**Qué.** `PdfViewerActivity` pasa a tener su propio `NavDisplay` con una pila mínima: la raíz es
`ExternalPdfViewer(uri, displayName)` y encima pueden ir los destinos del visor (detalles, «Ir a
página»). Usa **los mismos** decoradores de entrada, `OverlaySceneStrategy` y `NavigationMotion` que
el shell principal.

**Por qué.**
- **Cumple la regla de AGENTS.md** sin excepciones: los destinos modales viven en la pila también
  aquí.
- **Un solo `PdfDetails` para los dos hosts**, con rotación, back predictivo y muerte de proceso
  resueltos por la librería en lugar de a mano.
- **El `ViewModel` del visor queda asociado a una entrada en ambos hosts** (lo da el decorador de
  `ViewModelStore`), así que D2 y el resto del estado funcionan igual en los dos.
- **No se reutiliza la pila de `MainActivity`**, porque la separación es deliberada
  ([auditoría de navegación](05-navigation-audit.md)): atrás desde un PDF externo debe volver a la
  app que lo abrió.

**Detalles.**
- En la raíz, atrás del sistema termina la Activity (el `NavDisplay` no intercepta con una sola
  entrada). El botón atrás de la barra recibe `onBack = activity::finish`, **no `finishAffinity()`**,
  lo que corrige también B5 (la sospecha S1 del análisis, confirmada en el paso 0 del plan).
- `onNewIntent` con otro documento **reemplaza la raíz** de la pila, en vez de cambiar el documento
  por debajo de un sheet abierto.
- Las keys nuevas se restauran por reflexión: hay que añadirlas a `proguard-rules.pro`, como las
  demás.

---

## 3. Capas y módulos

```
:document-content-api   (Kotlin puro, nuevo)
    modelos de contenido, puerto PageContentProvider, TextSelection (lógica pura)
        ▲                          ▲
        │                          │ (fase OCR)
:app ───┘                  :ocr-mlkit (futuro; único sitio con tipos de ML Kit Text Recognition)
  feature/pdfviewer/
    domain/     casos de uso, puertos de acciones, modelos del visor
    data/       PlatformPageContentProvider (único sitio con android.graphics.pdf.content.*),
                LayeredPageContentProvider, memoria de sesión D2, abridores (D4), impresora
    presentation/  PdfViewerViewModel, pantalla, componentes, secciones
    di/         PdfViewerModule, PageContentModule  ← punto único de intercambio del OCR
:composepdf             motor: render, geometría, gestos (+ ganchos genéricos, §6)
```

**Por qué un módulo para el contrato de contenido, y no un paquete.** El proveedor OCR vivirá en su
propio módulo para que ML Kit no pueda importarse desde `:app`, exactamente como `:scanner-mlkit`. Para
eso, el contrato tiene que estar en un módulo del que dependan ambos. Crearlo ahora cuesta poco y
evita moverlo después.

**Por qué el proveedor nativo vive en `:app/data` y no en `:composepdf`.**
- La extracción tiene que poder correr **sin UI**: la fase OCR querrá indexar en segundo plano y la
  búsqueda de Home podría usar el contenido.
- **No debe competir** con los workers de render por los 2 renderers del pool.
- `:composepdf` sigue siendo un visor genérico que no sabe qué es un texto.
- El coste es abrir el documento una segunda vez (un `PdfRenderer` propio por documento abierto), que
  es asumible.
- Alternativa descartada: exponer `PdfDocumentManager` fuera del motor.

---

## 4. Contrato de contenido (`:document-content-api`)

### 4.1 Modelos

```kotlin
/** Coordenadas de página normalizadas: [0,1]×[0,1], origen arriba-izquierda. */
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float)
data class NormalizedPoint(val x: Float, val y: Float)

/** Dónde vive el documento. Propio, para no depender de `:scanner-api`. */
@JvmInline value class DocumentSource(val value: String)

enum class ContentOrigin { EMBEDDED, RECOGNIZED }       // capa de texto del PDF / OCR

data class TextWord(val text: String, val bounds: NormalizedRect)
data class TextLine(val words: List<TextWord>)
data class PageText(
    val lines: List<TextLine>,                          // en orden de lectura
    val origin: ContentOrigin,
    val confidence: Float? = null,                      // solo OCR
)

sealed interface PageLink {
    val bounds: List<NormalizedRect>                    // un enlace puede ocupar varias líneas
    data class External(override val bounds: List<NormalizedRect>, val uri: String) : PageLink
    data class Internal(
        override val bounds: List<NormalizedRect>,
        val pageIndex: Int,
        val position: NormalizedPoint?,
    ) : PageLink
}

sealed interface PageContentResult {
    data class Available(val text: PageText?, val links: List<PageLink>) : PageContentResult
    data object NoText : PageContentResult               // página de solo imagen (escaneado)
    data object Unsupported : PageContentResult          // el dispositivo no puede leerla (D1)
    data class Failed(val cause: Throwable) : PageContentResult
}
```

**Por qué palabras y no caracteres.** Es el denominador común: el OCR de ML Kit entrega
bloque → línea → elemento (palabra), y la selección a nivel de palabra es la que usan los visores
de referencia sobre documentos escaneados. Si el spike (paso *a*) muestra que la plataforma entrega
cajas por carácter, se puede añadir `TextWord.glyphs` más adelante sin romper nada.

**Por qué `Available.text` es anulable.** Una página puede tener enlaces y no tener texto (una imagen
con un área enlazada).

**Por qué un resultado sellado y no `Result<T>`.** Es lo mismo que en la fase 1: «no hay texto» y
«no se puede leer» no son fallos, y la UI tiene que distinguirlos.

### 4.2 Puerto

```kotlin
interface PageContentProvider {
    val origin: ContentOrigin
    suspend fun open(document: DocumentSource): PageContentSession
}

interface PageContentSession : AutoCloseable {
    suspend fun page(index: Int): PageContentResult
}
```

**Por qué una sesión.** Abrir un documento es caro (descriptor + parseo). La sesión lo mantiene
abierto mientras el visor lo muestra y lo cierra en `onCleared()`. El proveedor OCR tendrá su propio
estado (el reconocedor, una caché persistente) con el mismo ciclo de vida.

### 4.3 Proveedores

| Proveedor | Dónde | Qué hace |
|---|---|---|
| `PlatformPageContentProvider` | `:app` `feature/pdfviewer/data/content/` | API 35+, con un `PdfRenderer` propio. Texto de `getTextContents()` (un bloque por página, líneas separadas por `\r\n`), partido en líneas y palabras. **Geometría de cada palabra con `selectContent()` por índice de carácter**, porque `getTextContents()` no trae rectángulos (verificado en el [paso a](08-pdfviewer-migration-plan.md#paso-a--spike-de-viabilidad)). Enlaces de `getLinkContents()` y `getGotoLinks()`. Todo se normaliza dividiendo por `page.width`/`height`. Por debajo de API 35 devuelve `Unsupported` (D1). El acceso a páginas se serializa con un `Mutex`, porque un `PdfRenderer` solo admite una página abierta a la vez |
| `LayeredPageContentProvider(embedded, recognized: PageContentProvider?)` | `:app` `data/content/` | **Por página**: primero `embedded`; si da `NoText` o `Unsupported` y hay `recognized`, lo consulta. Así cubre documentos mixtos y, con OCR, también los dispositivos antiguos |
| `MlKitOcrPageContentProvider` | **Futuro**, `:ocr-mlkit` | Rasteriza la página y la pasa a ML Kit Text Recognition; normaliza las cajas por el tamaño del bitmap |

**Caché.** LRU en memoria dentro de la sesión, acotada en número de páginas. El OCR añadirá
persistencia (Room, con clave uuid + página + huella del fichero) **dentro de su proveedor**, sin
tocar el contrato.

**Extracción perezosa.** El `ViewModel` pide contenido solo para las páginas visibles ±1, con
*debounce* durante los flings y cancelando lo que deja de ser visible. En un documento de 300 páginas
no se extrae nada que el usuario no mire.

### 4.4 Selección: lógica pura en el propio módulo

`TextSelection` (funciones puras sobre `PageText`):

- `wordAt(point)`: palabra bajo el dedo, con tolerancia.
- `range(anchor, focus)`: rango de palabras en orden de lectura, entre líneas.
- `text(range)`: texto con espacios y saltos de línea correctos.
- `highlightRects(range)`: rectángulos que resaltar, fusionados por línea.
- `all()`: selecciona toda la página.

**Por qué aquí.** Es comportamiento del modelo, no de un proveedor, y es **la** pieza que el OCR
reutilizará tal cual. Se testea en JVM pura (`./gradlew :document-content-api:test`), lo que resuelve
la prueba del algodón del análisis.

**Alcance v1**: selección dentro de una página. El modelo admite selección entre páginas (rango por
página) para una iteración posterior.

---

## 5. Feature `pdfviewer`

### 5.1 `PdfViewerViewModel`

`BaseViewModel<PdfViewerIntent, PdfViewerUiState, PdfViewerEffect>`, una instancia por entrada de
navegación (en ambos hosts, gracias a D5), con `parametersOf(ViewerDocumentRef)`.

```kotlin
@Serializable sealed interface ViewerDocumentRef {
    @Serializable data class Catalogued(val uuid: String) : ViewerDocumentRef
    @Serializable data class External(val uri: String, val displayName: String) : ViewerDocumentRef
}

data class PdfViewerUiState(
    val document: ViewerDocument?,                 // título, descripción, páginas, tamaño, fuente
    val display: ViewerDisplaySettings,            // D2: modo de ajuste + modo noche
    val pages: Map<Int, PageContentState>,         // solo las cercanas a lo visible
    val textAvailability: TextAvailability,        // Unknown / Some / None / Unsupported
    val selection: TextSelectionState?,            // página, rango, texto, rectángulos
    val linkPreview: LinkPreview?,                 // D3
)
```

- **Intents**: `DocumentLoaded`, `VisiblePagesChanged`, `SetFitMode`, `ToggleNightMode`,
  `LongPressed`, `SelectionDragged`, `SelectAll`, `ClearSelection`, `Tapped`, `OpenLink`,
  `CopyLink`, `DismissLinkPreview`, `Share`, `OpenWith`, `Print`.
- **Effects** (`effects`, descartables): `ScrollTo(page, position)`, `OpenLink(LinkAction)`,
  `Print`. Los que necesitan la Activity se ejecutan en la pantalla.
- **Eventos al usuario** (`defaultEvents`): «Texto copiado» (solo por debajo de API 33, donde el
  sistema no lo confirma), «Este documento no tiene texto seleccionable», «No hay app para abrir este
  enlace».

**Qué no va al ViewModel.** Pan, zoom y página actual siguen en `PdfViewerState` (como un
`LazyListState`: es estado de UI). La visibilidad del chrome va en `rememberSaveable` en la pantalla.
**Por qué**: son presentación pura. Subirlas al ViewModel solo añadiría viajes de ida y vuelta por
frame.

**Navegar** (detalles, «Ir a página») se hace directamente desde el callback de la pantalla, como
estableció B7 en la [auditoría de navegación](05-navigation-audit.md): ir a un sitio no es trabajo del
ViewModel.

### 5.2 Casos de uso (`feature/pdfviewer/domain/usecase/`)

| Caso de uso | Responsabilidad | Test JVM |
|---|---|---|
| `ObserveViewerDocumentUseCase` | `ViewerDocumentRef` → `ViewerDocument`; los catalogados salen del catálogo (con `pageCount`, `sizeBytes`: arregla V5) | Sí |
| `ObserveViewerDisplaySettingsUseCase` | D2: `sesión[doc] ?: (switch ? predeterminados : fábrica)`, como `Flow` | Sí, toda la tabla de D2 |
| `UpdateViewerDisplaySettingsUseCase` | D2: escribe en la memoria de sesión | Sí |
| `ResolveLinkUseCase` | `PageLink` → `LinkAction` (`OpenWeb`, `ComposeEmail`, `Dial`, `GoTo`, `Blocked`) con **lista de esquemas permitidos** (`http`, `https`, `mailto`, `tel`); host normalizado a ASCII para D3 | Sí |

**Por qué `ResolveLinkUseCase` es de dominio.** Qué se puede abrir y qué se bloquea
(`javascript:`, `file:`, `intent:`, `content:`, `data:`) es una regla de seguridad. Tiene que estar en
un sitio con tests, no dispersa en un callback de UI.

### 5.3 Puertos de acciones (arreglan V2 y V3)

| Puerto (dominio) | Implementación (data) | Necesita Activity |
|---|---|---|
| `DocumentSharer` (**el existente**) | `AndroidDocumentSharer` | No |
| `DocumentOpener` («Abrir con») | `AndroidDocumentOpener` | No |
| `DocumentPrinter` | `AndroidDocumentPrinter` + `PdfPrintDocumentAdapter` (sale de `domain/`) | **Sí** |
| `LinkOpener` (D4) | `CustomTabsLinkOpener` | **Sí** |

Los que necesitan Activity se inyectan con `koinInject { parametersOf(activity) }` en la pantalla y
se ejecutan en respuesta a un *effect*. **Por qué**: el ViewModel no puede retener una Activity, y la
fase 1 ya resolvió el mismo problema con `ActivityResultHost`.

**Cambio de comportamiento deliberado**: compartir un documento externo con esquema `file://` deja de
re-exponerlo por `FileProvider` (análisis §7.8). Solo se comparten URIs `content://`.

### 5.4 Memoria de sesión (D2)

- Puerto `ViewerSessionSettings` en dominio; implementación `InMemoryViewerSessionSettings` en
  `data/settings/`, un `single` de Koin sobre `MutableStateFlow<Map<DocumentKey, ViewerDisplaySettings>>`.
- `DocumentKey` = uuid (catalogados) o URI (externos).
- El ViewModel escribe también los ajustes efectivos en su `SavedStateHandle` y, al restaurarse tras
  una muerte de proceso, los vuelve a sembrar en la memoria (interpretación adoptada en D2).
- **Preferencias persistentes** (`SettingsRepository` + `UserPreferences`): `viewerDefaultsEnabled`,
  `viewerDefaultFitMode`, `viewerDefaultNightMode`, con claves nuevas en DataStore.
- **Pantalla de Ajustes**: un nuevo destino hermano `DocumentViewerSettings` en `SettingsKeys.kt`,
  con su entrada en `SettingsScreen`, siguiendo el patrón de `AppearanceSettings`.

---

## 6. Cambios en `:composepdf`

Todos son **genéricos**: el motor sigue sin saber qué es un texto o un enlace.

| # | Cambio | Resuelve | Por qué así |
|---|---|---|---|
| E1 | La primera carga **respeta** página, zoom y pan restaurados: se aplican cuando el layout está listo, en vez de ponerlos a cero | B1 | El `Saver` ya existe; solo hay que dejar de pisarlo |
| E2 | Los gestos **respetan el consumo**: si un hijo del overlay consume el *down* o el arrastre, el visor no hace pan | V6 | Es lo que permite tiradores y popups interactivos encima de las páginas |
| E3 | Gancho de interacción: `claimsTap(event)` entrega el tap **al momento**, sin esperar al doble tap; `onLongPress(event): Boolean` **reclama** el gesto y los arrastres llegan como `onDrag/onDragEnd` en vez de hacer pan | V6 | Enlaces sin retraso; selección que no desplaza el documento. Fuera de un enlace, el doble tap se mantiene |
| E4 | `PdfOverlayScope`: dibujar en coordenadas de página en **fase de draw** (resaltados) y colocar composables anclados a un punto de página en **fase de layout** (tiradores, aviso de enlace) | — | Generaliza el patrón de `PdfPageLoadingOverlay`: seguir pan y zoom sin recomponer |
| E5 | `animateScrollTo(pageIndex, position: Offset?)` | Enlaces internos | Hoy solo se puede centrar una página entera |
| E6 | `contentPadding` en `PdfLayoutSpec`: las páginas pasan por debajo de las barras sin redimensionar el viewport | V9 | Elimina el padding animado y el relayout por frame |

Interfaz de E3, a título indicativo:

```kotlin
interface PdfInteractionHandler {
    fun claimsTap(event: PdfTapEvent): Boolean = false
    fun onTap(event: PdfTapEvent) {}
    fun onLongPress(event: PdfTapEvent): Boolean = false   // true: los arrastres son míos
    fun onDrag(event: PdfTapEvent) {}
    fun onDragEnd() {}
}
```

**Lo que no cambia**: `PdfRenderer`, el `RenderEngine`, los tiles, la geometría y el API de specs.

---

## 7. Interfaz (Material 3 Expressive)

- **Estructura**: el visor a pantalla completa, de borde a borde, con `contentPadding` (E6) para las
  barras.
- **Barra superior**:
  - título y subtítulo (descripción, o «N páginas»);
  - **Compartir** como única acción destacada (tonal);
  - menú desplegable con Imprimir, Abrir con y Detalles;
  - botón atrás anunciado como «Atrás» (B3), visible solo si `providesOwnBackAffordance`;
  - colores de los tokens de superficie, no `primaryContainer` a mano.
- **Barra inferior**: `HorizontalFloatingToolbar` con comportamiento *exit always* (se oculta al
  hacer scroll y reaparece con un tap):
  - chip **«3 / 12»** que abre el destino «Ir a página»; sustituye al campo de texto de la toolbar;
  - chip de **zoom** con el porcentaje, que al pulsarlo lo restablece con animación;
  - menú de **modo de ajuste** con etiquetas y el modo actual marcado (anunciado por TalkBack);
  - conmutador de **modo noche**;
  - desaparecen los −/+ (pinch, doble tap y quick scale ya cubren el zoom) y el arrastre oculto del
    chip.
- **Documentos largos**: fast scroller vertical con etiqueta de página, más un indicador temporal
  arriba al hacer scroll.
- **Chrome**: un tap alterna **las dos barras juntas**, con un único booleano.
- **Carga**: `LoadingIndicator`, más los indicadores por página que ya pinta el motor.
- **Error** (arregla B4): pantalla propia con icono y mensaje localizado por tipo (protegido con
  contraseña, dañado, sin permiso de lectura) y las acciones «Abrir con otra app» y «Volver».
- **Detalles**: destino en la pila (arregla V4) con datos del catálogo, que además muestra
  «Texto: incrustado / no disponible / no compatible con este dispositivo».
- **Selección**:
  - long press sobre una palabra, con háptica, la selecciona;
  - resaltado con `LocalTextSelectionColors`, adaptado al modo noche;
  - dos tiradores;
  - menú del sistema (`LocalTextToolbar`) con **Copiar** y **Seleccionar todo**;
  - portapapeles con `LocalClipboard`.
- **Sin texto**: el long press da háptica de rechazo y, una vez por documento, el aviso «Este
  documento es una imagen escaneada: no tiene texto seleccionable» (o «…no es compatible con esta
  versión de Android», por D1). El sitio para la futura acción «Reconocer texto» queda reservado.
- **Enlaces**:
  - al pulsarlos, su zona se resalta un instante;
  - aviso D3 para los externos; salto directo y snackbar «Volver a la página N» para los internos;
  - para TalkBack, cada enlace es un nodo semántico virtual («Enlace: dominio»).
- **Motion**: todo desde `MaterialTheme.motionScheme`. Las transiciones de pantalla siguen solo en
  `NavigationMotion.kt`.

---

## 8. DI: el punto único de intercambio

```kotlin
// feature/pdfviewer/di/PageContentModule.kt
single<PageContentProvider> {
    LayeredPageContentProvider(
        embedded = PlatformPageContentProvider(androidContext()),
        recognized = null,          // fase OCR: MlKitOcrPageContentProvider(...)
    )
}
```

**Integrar el OCR será cambiar esa línea** y añadir el módulo `:ocr-mlkit`. Nada del visor, del
`ViewModel` ni de la selección cambia: el texto reconocido llega como `PageText(origin =
RECOGNIZED)` y la UI solo añade la marca «texto reconocido; puede contener errores».

---

## 9. Solo UI frente a lógica o datos

| Solo UI (sin tocar datos ni motor) | Toca lógica, datos o motor |
|---|---|
| Barras, toolbar, fast scroller, indicador de página, carga y error, resaltado, tiradores, menú de selección, aviso de enlace, animaciones | `PdfViewerViewModel`, casos de uso y puertos (§5) |
| | Memoria de sesión, preferencias y pantalla de Ajustes (D2) |
| | `:document-content-api`, proveedores (§4) |
| | `:composepdf` E1–E6 (§6) |
| | Keys nuevas y pila propia de `PdfViewerActivity` (D5) |
| | Manifest: `<queries>` (D4) |

---

## 10. Qué deja preparado para el OCR, y qué hará esa fase

**Queda hecho en esta fase**: el contrato con `ContentOrigin`, `confidence` y `NoText`/`Unsupported`;
el proveedor compuesto por página; la selección independiente del origen; el punto único de DI; el
hueco en la UI para «Reconocer texto» y para la marca de texto reconocido.

**Decidirá la fase OCR**:
- si el reconocimiento se lanza **al guardar un escaneo** (en segundo plano, persistido en Room, lo
  que permitiría buscar por contenido desde Home) o **bajo demanda** en el visor. El contrato admite
  ambas;
- si el escáner debe pedir también las imágenes de página (`ScanArtifact.Pages`) para mejorar la
  calidad, o basta con rasterizar desde el PDF;
- el esquema de Room para el texto reconocido (con la exportación de esquema al día).

---

## 11. Puntos abiertos

| # | Punto | Propuesta |
|---|---|---|
| A1 | Muerte de proceso y D2 | ✅ **Resuelto**: cuenta como la misma sesión; el visor restaurado conserva sus ajustes (§2, D2) |
| A2 | Valores de fábrica | ✅ **Resuelto**: ajustar al ancho en cualquier ventana, modo noche desactivado; cambiable en el visor y en Ajustes (§2, D2) |
| A3 | ¿Selección al rotar? | Descartarla (es efímera, como la del texto del sistema) |
| A4 | Abrir el documento al terminar de escanear | Fuera de alcance. Requiere que el guardado devuelva el uuid ([análisis §7.1](06-pdfviewer-analysis.md#7-fricción-con-las-áreas-ya-estabilizadas)) |
| A5 | ¿Recordar también la página por documento en la sesión? | No pedido. El diseño de D2 lo admitiría añadiendo un campo |
