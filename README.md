# IYKYK — Video-Based Unique Person Collage

> An on-device Android application that detects people in portrait videos, identifies the same person across different scenes, counts their appearances, selects their best representative frame, and generates a shareable Instagram-style collage.

---

## 📱 Overview

**IYKYK** automatically transforms a portrait video into a visual summary of the unique people who appeared in it.

Instead of manually going through a video to find every person and their best frame, IYKYK performs the complete pipeline automatically:

**Video → Face Detection → Face Embeddings → Appearance Tracking → Identity Clustering → Best Shot Selection → Collage Generation**

The complete processing pipeline runs **on-device**, keeping the user's video and facial data private.

---

## ✨ Features

- 🎥 Select any portrait video from the device
- 🔍 Detect faces using Google ML Kit
- 🧬 Generate face embeddings using MobileFaceNet
- 👥 Identify the same person across separate scenes
- 🎞️ Track continuous appearances
- 🔢 Count appearances for every unique person
- ⭐ Select representative frames based on image quality
- 👁️ Consider eye openness and head pose
- 🙂 Prefer pleasant facial expressions
- 🖼️ Generate an Instagram-story-style collage
- 📐 Dynamically adapt the collage layout to the number of people
- 💾 Save the generated collage to the device
- 📤 Share the collage using Android's share sheet
- 🔒 Process everything locally on the device
- ⚡ Perform heavy processing away from the main UI thread

---

# 🎯 Problem Statement

Given a portrait-oriented video containing multiple people appearing at different times, the application should:

1. Detect every visible face.
2. Determine which detected faces belong to the same person.
3. Group continuous detections into appearances.
4. Count separate appearances for each person.
5. Select the best frame representing each person.
6. Generate one final collage containing every unique person exactly once.
7. Allow the resulting collage to be saved or shared.

IYKYK implements this complete workflow using on-device computer vision and machine learning.

---

# 🧠 How It Works

The application follows a multi-stage computer-vision pipeline:

```text
                    PORTRAIT VIDEO
                           │
                           ▼
                 ┌──────────────────┐
                 │ Frame Extraction │
                 └────────┬─────────┘
                          │
                          ▼
                 ┌──────────────────┐
                 │  Face Detection  │
                 │     ML Kit       │
                 └────────┬─────────┘
                          │
                          ▼
                 ┌──────────────────┐
                 │ Face Alignment   │
                 │   & Landmarks    │
                 └────────┬─────────┘
                          │
                          ▼
                 ┌──────────────────┐
                 │ Face Embedding   │
                 │   MobileFaceNet  │
                 └────────┬─────────┘
                          │
                          ▼
                 ┌──────────────────┐
                 │ Appearance       │
                 │ Tracking         │
                 └────────┬─────────┘
                          │
                          ▼
                 ┌──────────────────┐
                 │ Identity         │
                 │ Clustering       │
                 └────────┬─────────┘
                          │
                          ▼
                 ┌──────────────────┐
                 │ Representative   │
                 │ Frame Selection  │
                 └────────┬─────────┘
                          │
                          ▼
                 ┌──────────────────┐
                 │ Collage          │
                 │ Generation       │
                 └────────┬─────────┘
                          │
                          ▼
                    SAVE / SHARE
