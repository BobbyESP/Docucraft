<div align="center">
    <img src="./assets/Docucraft Logo.png" alt="Docucraft logo" width="120" height="120"/>
    <h1><b>Docucraft</b></h1>
    <p><b>An AI-powered document scanner. Fast, intuitive, private, no watermarks.</b></p>

<p>
    <a href="https://kotlinlang.org/">
        <img src="https://img.shields.io/badge/Kotlin-2.3.0-7F52FF?style=flat&logo=kotlin&logoColor=white" alt="Kotlin" />
    </a>
    <a href="https://developer.android.com/jetpack/compose">
        <img src="https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
    </a>
    <a href="https://developers.google.com/ml-kit">
        <img src="https://img.shields.io/badge/ML_Kit-On_Device-FF6F00?style=flat&logo=google&logoColor=white" alt="ML Kit" />
    </a>
    <a href="./LICENSE">
        <img src="https://img.shields.io/badge/License-GPLv3-blue?style=flat" alt="License" />
    </a>
    <img src="https://img.shields.io/badge/Android-Min_SDK_24-3DDC84?style=flat&logo=android&logoColor=white" alt="Android Badge" />
</p>
</div>

<div align="center">
   <img src="./assets/horizontal/Docucraft Functions Banner.png" alt="Docucraft main functions banner" style="border-radius: 10px; margin-top: 20px; margin-bottom: 20px;"/>
</div>

> **Docucraft** is a modern, efficient, and user-friendly Android application designed for high-quality document scanning and management. Built with the latest Android technologies, it leverages the power of Google's ML Kit to provide fast and accurate document detection directly on your device.

---

## 📢 Help us put the app in the Play Store!

We need your help to graduate from testing! If you’re up for it, please join the beta.

> **How to help:**
> 1. Fill out the quick form below (Email is the only required field).
> 2. I'll add you to the private testers list.
> 3. You get early access to features! 👀

[![Join Beta](https://img.shields.io/badge/🚀_Join_the_Beta_Testers-Click_Here-2ea44f?style=for-the-badge)](https://forms.gle/dfqW22W88gyC9yth8)

---

## 📱 Overview

Docucraft transforms your Android device into a portable scanner. Whether you need to digitize receipts, notes, business cards, or contracts, Docucraft makes it easy to capture, organize, and share your documents as PDFs.

**Privacy First:** The app prioritizes privacy and performance by processing everything locally on your device.

## ✨ Key Features

* **📷 Smart Document Scanning:** Utilizes a Google-trained Machine Learning model to automatically detect document edges, correct perspective, and enhance image quality.
* **🔒 On-Device Processing:** No internet connection required for scanning. Your documents are never uploaded to a server unless you explicitly choose to share them.
* **📂 PDF Management:**
    * Organize scans in a clean, searchable list.
    * View metadata (size, pages, date).
    * Edit titles and descriptions.
* **🔍 Search & Filter:** Quickly find documents with built-in search tools.
* **📤 Share & Export:** Export as PDF via email, WhatsApp, or save to device storage.
* **🎨 Modern UI:** Built with **Material 3 Expressive** and **Jetpack Compose**.
* **🌙 Dark Mode:** Fully supported for low-light environments.

## 🛠️ How It Works

Docucraft uses the **Google ML Kit Document Scanner API** to handle the heavy lifting:

1.  **Launch:** App opens a camera view optimized for documents.
2.  **Detect:** ML algorithms detect borders in real-time.
3.  **Process:** Image is auto-cropped and enhanced (shadow removal, skew correction).
4.  **Compile:** Scan multiple pages into a single PDF.
5.  **Save:** Indexed in a local `Room` database and stored in private storage.

## 💻 Tech Stack

Docucraft is built using modern Android architecture and libraries:

| Category | Technology | Description |
| :--- | :--- | :--- |
| **Language** | [Kotlin](https://kotlinlang.org/) | 100% Kotlin codebase. |
| **UI Toolkit** | [Jetpack Compose](https://developer.android.com/jetpack/compose) | Material 3 Design system. |
| **Architecture** | MVVM and MVI | Clean Architecture (Presentation, Domain, Data). |
| **DI** | [Koin](https://insert-koin.io/) | Lightweight dependency injection. |
| **Database** | [Room](https://developer.android.com/training/data-storage/room) | Local data persistence. |
| **Async** | [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & Flow | Asynchronous programming. |
| **ML / Vision** | [Google ML Kit](https://developers.google.com/ml-kit/vision/doc-scanner) | Document Scanner API. |
| **File I/O** | [FileKit](https://github.com/vinceglb/FileKit) | Simplified file management. |
| **Navigation** | [Jetpack Navigation](https://developer.android.com/guide/navigation) | Type-safe navigation. |

## 🚀 Setup & Build Instructions

To build and run this project locally, follow these steps:

### Prerequisites
* Android Studio Ladybug (or newer).
* JDK 17 (or newer).
* Android Device (Android 7.0+ / API 24+).

### Steps

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/yourusername/Docucraft.git](https://github.com/BobbyESO/Docucraft.git)
    cd Docucraft
    ```

2.  **Open in Android Studio:**
    * Launch Android Studio -> Select "Open" -> Select cloned directory.

3.  **Sync & Run:**
    * Let Gradle sync dependencies.
    * Connect your physical device (Recommended due to Camera/ML requirements).
    * ⚠️ You may have to create a Firebase project for Crashlytics
    * Click **Run** ▶️.

## 📖 Usage Guide

1.  **Scan:** Tap the floating `+` button.
2.  **Capture:** Auto-capture or manual shutter.
3.  **Edit:** Crop, rotate, or retake pages.
4.  **Save:** Tap "Save" to generate the PDF.
5.  **Manage:** Tap any card to Share, Edit, or Delete.

## ⚠️ Limitations & Notes

* **Hardware:** Requires a functioning back camera.
* **Dependencies:** Relies on **Google Play Services**. Ensure your device/emulator has Play Services installed and updated for ML Kit to function.

## 📄 License

Distributed under the **GPL-3.0** License. See [LICENSE](LICENSE) for more information.

---
<div align="center">
    <p>Built with ❤️ by Bobby</p>
    <p><i>If you like this project, please give it a star! 🌟</i></p>
</div>
