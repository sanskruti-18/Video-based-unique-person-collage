# IYKYK — Video-Based Unique-Person Collage

> **IYKYK** detects people across a portrait video, identifies recurring individuals using on-device face embeddings, counts their appearances, selects representative frames, and generates a shareable Instagram-style collage — completely on-device.

---

## ✨ Overview

IYKYK is an Android application that transforms a portrait video containing multiple people and repeated appearances into a visual summary of the **unique people present in the video**.

Instead of simply detecting faces frame-by-frame, the app combines:

- 🎥 Video frame extraction
- 🔍 On-device face detection
- 🧬 Face embeddings
- 🧠 Identity clustering
- 👤 Appearance tracking
- ⭐ Representative-frame selection
- 🖼️ Automatic collage generation
- 💾 Gallery saving
- 📤 Android sharing

The complete processing pipeline runs locally on the Android device.

**No video, face image, or embedding is uploaded to a server.**

---

## 🎯 Problem Statement

Given a portrait video containing multiple people who may appear repeatedly in different scenes:

1. Detect faces throughout the video.
2. Determine which detected faces belong to the same person.
3. Group continuous appearances of each person.
4. Count each person's appearances.
5. Select a representative frame for each unique person.
6. Generate a visually appealing portrait collage containing every unique person exactly once.
7. Allow the generated collage to be saved or shared.

The implementation is designed to work on arbitrary portrait videos rather than relying on hardcoded timestamps or identities.

---

## 🚀 Features

### 🎥 Portrait Video Processing

The user selects a portrait video from the device.

The video is processed asynchronously so that heavy computer-vision operations do not block the Android UI.

---

### 🔍 Face Detection

Faces are detected using **Google ML Kit Face Detection**.

The detector is configured to provide information useful for both identity processing and representative-shot selection, including:

- Face bounding box
- Head pose / Euler angles
- Eye-open probabilities
- Smile probability
- Facial landmarks

Detection is performed entirely on-device.

---

### 🧬 Face Embeddings

Detected faces are converted into numerical feature vectors using a lightweight **MobileFaceNet** model.

Each embedding represents facial characteristics in a compact numerical space.

The embeddings are:

1. Generated from aligned face regions.
2. Normalized.
3. Compared using cosine similarity.
4. Used for identity grouping.

This allows the same person to be recognized across separate scenes and different appearances.

---

### 🧠 Identity Clustering

Face embeddings are grouped using similarity-based clustering.

The implementation uses a conservative similarity threshold to reduce accidental merging of visually similar but different people.

Current identity similarity threshold:

```text
0.68
