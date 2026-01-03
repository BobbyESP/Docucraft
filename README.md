# Docucraft

Docucraft is a modern, efficient, and user-friendly Android application designed for high-quality document scanning and management. Built with the latest Android technologies, it leverages the power of Google's ML Kit to provide fast and accurate document detection and scanning directly on your device.

## 📱 Overview

Docucraft transforms your Android device into a portable scanner. Whether you need to digitize receipts, notes, business cards, or contracts, Docucraft makes it easy to capture, organize, and share your documents as PDFs. The app prioritizes privacy and performance by processing everything locally on your device.

## ✨ Features

*   **Smart Document Scanning:** Utilizes a Google-trained Machine Learning model to automatically detect document edges, correct perspective, and enhance image quality.
*   **On-Device Processing:** All scanning and image processing happen locally on your phone. No internet connection is required for scanning, and your documents are never uploaded to a server unless you choose to share them.
*   **PDF Management:**
    *   Organize your scanned documents in a clean, searchable list.
    *   View detailed metadata (file size, page count, creation date).
    *   Edit document titles and descriptions for better organization.
*   **Search & Filter:** Quickly find the document you need with built-in search and filtering options.
*   **Share & Export:** Easily share your scanned PDFs via email, messaging apps, or save them to your device's storage.
*   **Modern UI:** A beautiful and intuitive interface built with Jetpack Compose and Material 3 design principles.
*   **Dark Mode Support:** Fully supports system-wide dark mode for comfortable usage in low-light environments.

## 🛠️ How It Works

Docucraft uses the **Google ML Kit Document Scanner API** to handle the heavy lifting of image processing. When you initiate a scan:
1.  The app opens a camera view optimized for documents.
2.  ML algorithms detect the document's borders in real-time.
3.  Once captured, the image is automatically cropped and enhanced (e.g., removing shadows, correcting skew).
4.  You can scan multiple pages to create a single PDF file.
5.  The final PDF is saved locally to your device's private storage and indexed in a local database for quick access within the app.

## 💻 Tech Stack

Docucraft is built using modern Android development practices and libraries:

*   **Language:** [Kotlin](https://kotlinlang.org/)
*   **UI Toolkit:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
*   **Architecture:** Clean Architecture (Presentation, Domain, Data layers) with MVVM pattern.
*   **Dependency Injection:** [Koin](https://insert-koin.io/)
*   **Local Database:** [Room](https://developer.android.com/training/data-storage/room)
*   **Asynchronous Programming:** [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & [Flow](https://kotlinlang.org/docs/flow.html)
*   **ML & Scanning:** [Google ML Kit](https://developers.google.com/ml-kit/vision/doc-scanner)
*   **File Management:** [FileKit](https://github.com/vinceglb/FileKit)
*   **Navigation:** [Jetpack Navigation](https://developer.android.com/guide/navigation)

## 🚀 Setup & Build Instructions

To build and run this project locally, follow these steps:

### Prerequisites
*   Android Studio Ladybug or newer.
*   JDK 17 or newer.

### Steps
1.  **Clone the repository:**
    ```bash
    git clone https://github.com/yourusername/Docucraft.git
    cd Docucraft
    ```

2.  **Open in Android Studio:**
    *   Launch Android Studio.
    *   Select "Open" and navigate to the cloned directory.

3.  **Sync Gradle:**
    *   Allow Android Studio to download dependencies and sync the project.

4.  **Run the App:**
    *   Connect an Android device (Android 7.0 / API Level 24 or higher) or use an emulator.
    *   Click the "Run" button (green play icon) in the toolbar.

**Note:** Since this app relies on the camera and ML Kit, testing on a physical device is recommended for the best experience.

## 📖 Usage

1.  **Scan a Document:** Tap the floating "+" button on the home screen to open the scanner.
2.  **Capture:** Point your camera at the document. The app will auto-capture, or you can press the shutter button manually.
3.  **Edit & Save:** Review your scans, crop or rotate if necessary, and tap "Save".
4.  **Manage:** Tap on any card in the list to view options like Open, Share, Edit, or Delete.
5.  **Search:** Use the search bar at the top to filter your documents by name.

## ⚠️ Limitations & Notes

*   **Camera Requirement:** The app requires a device with a functioning back camera.
*   **Google Play Services:** The ML Kit Document Scanner relies on Google Play Services. Ensure your device or emulator has Google Play Services installed and updated.

## 📄 License

[GPL-3.0](LICENSE)

