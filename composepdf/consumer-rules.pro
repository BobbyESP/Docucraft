# Consumer proguard rules for the PDF Viewer library

# Keep public API classes and methods
-keep public class com.composepdf.PdfViewerKt {
    public *;
}
-keep public class com.composepdf.source.PdfSource { *; }
-keep public class com.composepdf.source.PdfSource$* { *; }
-keep public class com.composepdf.state.PdfViewerState { *; }
-keep public class com.composepdf.state.ViewerConfig { *; }
-keep public class com.composepdf.state.ScrollDirection { *; }
-keep public class com.composepdf.state.FitMode { *; }
