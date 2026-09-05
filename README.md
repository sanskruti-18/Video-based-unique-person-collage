# IYKYK — Video-Based Unique Person Collage

> An Android application that detects unique people in portrait videos, identifies the same person across separate appearances, counts their appearances, selects a representative frame, and generates a shareable Instagram-style collage — entirely on-device.

## Features

- 🎥 Select a portrait video from the device
- 🔍 Detect faces using Google ML Kit
- 🧬 Generate face embeddings using MobileFaceNet
- 👥 Identify the same person across different scenes
- 🎞️ Track continuous appearances
- 🔢 Count separate appearances for each person
- ⭐ Select the best representative frame
- 👁️ Consider eye openness and head pose
- 🙂 Prefer clear and pleasant expressions
- 🖼️ Generate a dynamic portrait collage
- 💾 Save the generated collage to the gallery
- 📤 Share the collage using Android's share sheet
- 🔒 Perform the complete processing pipeline on-device
- ⚡ Run computationally expensive operations off the main thread

---

## Tech Stack

| Technology | Purpose |
|---|---|
| Kotlin | Application development |
| Jetpack Compose | UI |
| Android SDK | Application platform |
| Google ML Kit Face Detection | Face detection and facial landmarks |
| TensorFlow Lite | On-device ML inference |
| MobileFaceNet | Face embedding generation |
| Kotlin Coroutines | Background processing |
| Android Media APIs | Video and image processing |
| Android Share APIs | Sharing the generated collage |

---

## How It Works

The application follows the pipeline:

```text
Portrait Video
      ↓
Frame Extraction
      ↓
Face Detection — ML Kit
      ↓
Face Alignment
      ↓
Face Embedding — MobileFaceNet
      ↓
Appearance Tracking
      ↓
Identity Clustering
      ↓
Representative Frame Selection
      ↓
Dynamic Collage Generation
      ↓
Save / Share
