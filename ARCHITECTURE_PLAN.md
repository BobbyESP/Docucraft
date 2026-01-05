# Docucraft Technical Execution Plan & Architecture Design

## 1. The Master Plan

### Phase 1: Document Scanning (Current)
1.  **Core Setup**: Establish the `core` module structure for shared abstractions (Domain models, Result wrappers, Dispatchers).
2.  **Domain Definition**: Define the `DocumentScannerRepository` interface in `core/domain`. This contract defines *what* we do (scan), not *how* (ML Kit).
3.  **Data Implementation**: Implement `MlKitDocumentScannerRepository` in `core/data`. This encapsulates the Google ML Kit dependency.
4.  **Feature Implementation (Scanner)**:
    *   Create `ScanDocumentUseCase` in `feature/pdfscanner/domain`.
    *   Build `ScannerViewModel` in `feature/pdfscanner/presentation`.
    *   Implement the Camera UI (using CameraX or the ML Kit Activity intent if using the unbundled scanner). *Note: For high control, we often use CameraX + ML Kit, but ML Kit now offers a drop-in UI. We will assume the drop-in UI for simplicity unless custom UI is required, but the architecture supports both.*
5.  **Storage**: Implement a local database (Room) to persist scanned document metadata.

### Phase 2: OCR Integration (Future)
1.  **Domain Expansion**: Add `TextRecognitionRepository` interface in `core/domain`.
2.  **Data Expansion**: Implement `MlKitTextRecognizer` in `core/data`.
3.  **Feature Creation**: Create `feature/ocr` or extend `feature/pdfscanner` to include an "Extract Text" action.
4.  **Integration**: The `ScannerViewModel` can simply invoke a new `ExtractTextUseCase` on the result of the scan, without modifying the scanning logic.

## 2. The Directory Tree

```text
com.bobbyesp.docucraft
├── core
│   ├── data
│   │   ├── repository
│   │   │   ├── MlKitDocumentScannerRepository.kt  <-- Implements Domain Interface
│   │   │   └── (Phase 2) MlKitTextRecognizer.kt
│   │   └── local
│   │       └── db ...
│   ├── domain
│   │   ├── model
│   │   │   ├── ScannedDocument.kt
│   │   │   └── (Phase 2) RecognizedText.kt
│   │   └── repository
│   │       ├── DocumentScannerRepository.kt       <-- Abstraction for Phase 1
│   │       └── (Phase 2) TextRecognitionRepository.kt
│   └── util
│       └── Resource.kt (State wrapper)
├── feature
│   ├── pdfscanner (Phase 1)
│   │   ├── data
│   │   │   └── repository
│   │   │       └── LocalDocumentRepository.kt     <-- For saving to DB
│   │   ├── domain
│   │   │   ├── repository
│   │   │   │   └── DocumentRepository.kt
│   │   │   └── usecase
│   │   │       └── ScanDocumentUseCase.kt
│   │   └── presentation
│   │       ├── ScannerViewModel.kt
│   │       └── ScannerScreen.kt
│   └── ocr (Phase 2)
│       ├── domain
│       │   └── usecase
│       │       └── ExtractTextUseCase.kt
│       └── presentation
│           └── OcrViewModel.kt
```

## 3. Key Abstractions (Code)

### Domain Layer (Core)

**File:** `core/domain/model/ScannedDocument.kt`
Represents the result of a scan, agnostic of the source.

**File:** `core/domain/repository/DocumentScannerRepository.kt`
The contract for any scanning mechanism.

### Domain Layer (Feature)

**File:** `feature/pdfscanner/domain/usecase/ScanDocumentUseCase.kt`
Encapsulates the business logic of initiating a scan and handling the result.

**File:** `feature/pdfscanner/domain/repository/DocumentRepository.kt`
Contract for saving the scanned results to local storage.

