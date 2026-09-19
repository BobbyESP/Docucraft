<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Fase 2 · Limpiar la frontera del dominio (V7 y V8)

> **Fecha**: 2026-09-19 · **Rama**: `refactor/scanner-decoupling`
>
> Continúa las violaciones **V7** y **V8** del [análisis de la fase 1](01-scanner-analysis.md), que
> se priorizaron como P3 y quedaron deliberadamente fuera del subsistema de escaneo.

---

## 1. Qué queda sucio

La fase 1 dejó el camino de escaneo y guardado limpio, pero el resto de `domain/` sigue lleno de
Android. Inventario exacto:

### V7 · «Use cases» que son framework puro

| Archivo | Qué es en realidad | Qué importa |
|---|---|---|
| `ShareDocumentUseCase` | Un `startActivity` | `Context`, `Intent` |
| `OpenDocumentInViewerUseCase` | Otro `startActivity` | `Context`, `Intent` |
| `ExportDocumentUseCase` | **Abre un diálogo del sistema** y copia | `Environment`, FileKit dialogs |
| `DeleteDocumentUseCase` | Sí tiene lógica, pero borra archivos a mano | `Uri`, `Log`, FileKit |

Ninguno es un caso de uso: son **puertos** sin declarar. `ExportDocumentUseCase` es el peor, porque
abre interfaz de usuario desde la capa de dominio.

### V8 · Modelos contaminados

| Archivo | Contaminación |
|---|---|
| `SortOption` | `@Composable`, `stringResource`, `Icons`, `ImageVector`, `R` — **un enum de dominio que renderiza UI** |
| `FilterOptions` | `@Stable`, y un `Pair<Long, Long>` que no dice qué significa |
| `ScannedDocument` | `android.net.Uri`, `@Immutable` |
| `BasicDocument` | `@Stable` |
| `DocumentOperationsService` | `Bitmap`, `File`, `@IntRange` en un contrato de dominio |
| `LocalDocumentsRepository` | `android.net.Uri` en las firmas |

### Sobre las anotaciones de Compose

Aquí hay una trampa: `@Immutable` y `@Stable` **no estaban de adorno**. Compose no puede inferir la
estabilidad de `android.net.Uri` ni de `Pair`, porque son tipos externos, así que sin la anotación
esos modelos causarían recomposiciones de más.

> **La anotación era el síntoma, no la enfermedad.** Al sustituir `Uri` por `ContentRef` (value
> class sobre `String`) y `Pair` por un `DateRange` propio, Compose infiere la estabilidad solo y la
> anotación sobra de verdad. Quitarla sin arreglar el tipo habría sido una regresión de rendimiento
> silenciosa.

### Código muerto encontrado al mapear

- `GetDocumentUseCase.invoke(documentPath: Uri)` — sin llamantes.
- `LocalDocumentsRepository.getDocument(path: Uri)` — solo lo usaba lo anterior.
- `ScannedDocumentDao.getByPath` — solo lo usaba lo anterior.
- `FileRepository` / `FileRepositoryImpl` / `fileManagementModule` — existen únicamente para
  `DeleteDocumentUseCase`; cuando ese pase por el puerto de almacenamiento, sobran enteros.

---

## 2. Plan

| Paso | Qué | Riesgo | Estado |
|---|---|---|---|
| 0 | Borrar el código muerto de arriba | Muy bajo | ✅ |
| 1 | `DocumentOperationsService` baja a `data/` | Muy bajo | ✅ |
| 2 | Sacar Compose de `SortOption`, `FilterOptions` y `BasicDocument` | Bajo | ✅ |
| 3 | `DocumentSharer` (abrir fuera resultó ser código muerto) | Bajo | ✅ |
| 4 | `DocumentExporter` + `ExportOutcome` | Bajo | ✅ |
| 5 | Borrado por el puerto de almacenamiento; muere `FileRepository` | Medio | ✅ |
| 6 | `ScannedDocument`: `Uri` → `ContentRef` | Medio | ✅ |

### Principios heredados de la fase 1

- **Desenlaces sellados, no excepciones**, para lo que el usuario puede cancelar. Exportar tiene
  exactamente la misma forma que escanear: `Saved` / `Cancelled` / `Failed`.
- **`ContentRef` como localizador opaco**, para que el dominio no sepa qué es un `Uri`.
- **Borrar antes de refactorizar.**
- **Un puerto se prueba con un fake**, no con un mock del framework.

### Lo que este plan NO toca

- `DocumentOperationsService` sigue codificando WEBP en archivos `.png`. Es el pendiente que
  acordamos dejar **para el final**, porque afecta a las miniaturas ya generadas.
- `:composepdf` y `feature/pdfviewer`: son la fase 3.

---

## 3. Bugs encontrados al implementar

Tres, y dos de ellos llevaban tiempo perdiendo datos en silencio.

### B7 · Cancelar una exportación se mostraba como error

`ExportDocumentUseCase` devolvía `Result.failure(DocumentExportFailure.Cancelled())`, y el ViewModel
pintaba en rojo el mensaje de cualquier fallo. Cerrar el diálogo de guardado sacaba un error que
decía «Export cancelled by user».

**Exactamente el mismo fallo de modelado que B1 en la fase 1**: tratar una cancelación como un
error. Por eso `ExportOutcome` es sellado, con `Cancelled` aparte.

### B8 · Los PDF nunca se borraban del disco

`DeleteDocumentUseCase` resolvía la ruta con `FileRepository.getFilePathFromUri`, que consulta la
columna `_data` del `ContentResolver`. **`FileProvider` no expone `_data`** —solo `_display_name` y
`_size`—, así que devolvía siempre `null`, el use case registraba «Invalid file path from URI» y
salía sin borrar nada.

Resultado: borrar un documento quitaba la fila de Room y **dejaba el PDF en el disco para siempre**.

La solución es pedírselo al propio provider: `contentResolver.delete(uri, null, null)`, que en un
`FileProvider` borra el archivo subyacente.

### B9 · Las miniaturas tampoco

El borrado ni siquiera lo intentaba con el preview. Cada documento borrado dejaba su `.png` en
`files/previews`. `DeleteDocumentUseCase` ahora borra los dos.

> Los tres salieron de **leer el código para moverlo**, no de buscarlos. Es el argumento más fuerte
> a favor del mapeo previo del método.

## 4. Código muerto que apareció por el camino

- `OpenDocumentInViewerUseCase` — solo estaba registrado en Koin; los documentos se abren en el
  visor interno por navegación. No hizo falta portarlo: se borró.
- `FileRepository`, `FileRepositoryImpl`, `fileManagementModule` — `readBytesFromUri` no lo llamaba
  nadie y `getFilePathFromUri` solo servía al borrado roto. Módulo entero fuera.
- `GetDocumentUseCase(Uri)`, `LocalDocumentsRepository.getDocument(Uri)`, `Dao.getByPath`.

## 5. Resultado

`domain/` son 18 archivos, y sus **únicos** imports son:

```
com.bobbyesp.docucraft.feature.docscanner.domain.*   (él mismo)
com.bobbyesp.scanner                                 (el contrato de escaneo)
com.bobbyesp.docucraft.core.util                     (DateTime, que es java.time puro)
kotlinx.coroutines
```

Ni Android, ni Compose, ni FileKit, ni la capa de datos. **62 tests**, 0 fallos.

Efecto secundario agradable: los tests ya no necesitan `mockk<Uri>()` para construir un
`ScannedDocument`. Ese mock existía únicamente porque el modelo llevaba un tipo de Android.

### ⚠️ Pendiente de verificación en dispositivo

| Qué | Por qué importa |
|---|---|
| Compartir un documento | Cambió de use case a puerto |
| Exportar y **cancelar** el diálogo | B7: cancelar ya no debe pintar un error rojo |
| Exportar de verdad | Ruta y nombre sugerido pasaron por el puerto |
| **Borrar un documento** | B8/B9: ahora sí debe desaparecer el PDF **y** su miniatura |
| Ordenar y filtrar la lista | `SortOption` perdió sus métodos `@Composable` |
| Abrir un documento en el visor | `BasicDocument.uri` ahora viene de `ContentRef` |

> **Nota sobre B8.** El arreglo hace que los borrados **futuros** limpien el disco. Los archivos
> huérfanos que ya dejaron los borrados anteriores siguen ahí: no hay migración que los recoja. Si
> el espacio importa, hace falta una limpieza puntual que compare `files/scans/pdf` y
> `files/previews` contra la tabla. Queda anotado, no hecho.
