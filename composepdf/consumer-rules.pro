# Consumer proguard rules for the PDF Viewer library

# Keep public API classes and methods
-keep public class com.composepdf.PdfViewerKt {
    public *;
}
-keep public class com.composepdf.PdfSource { *; }
-keep public class com.composepdf.PdfSource$* { *; }
-keep public class com.composepdf.PdfViewerState { *; }
-keep public class com.composepdf.ViewerConfig { *; }
-keep public class com.composepdf.ScrollDirection { *; }
-keep public class com.composepdf.FitMode { *; }
