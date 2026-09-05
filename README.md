# IYKYK — Video-Based Unique-Person Collage

An on-device Android application that processes portrait videos, detects faces, groups the same person across separate appearances using facial embeddings and clustering, selects the best representative shot for each individual, and generates a presentable Instagram Story-style collage that can be saved to the device gallery and shared.

Developed for the **iykyk Android Internship Assignment**.

---

## Key Features

1. **100% On-Device Processing**: No backend servers or cloud APIs; all machine learning inference runs locally on the device using Google ML Kit and TensorFlow Lite.
2. **Robust Appearance Tracking**: Tracks continuous visible segments (appearances) while ignoring blurred whip-pan camera transitions. Co-occurring people in shared frames (such as A & B at 10.1–11.5s and C & D at 20.2–21.6s in Sample 1) are accurately tracked as parallel appearances.
3. **Canonical Face Alignment & Embeddings**: Uses affine transformation on ML Kit facial landmarks to normalize head rotation and scale, extracting high-quality 192-dimensional facial representations using MobileFaceNet.
4. **Hierarchical Clustering**: Automatically groups appearances into unique identities using average-linkage cosine similarity clustering.
5. **Quality-Driven Representative Selection**: Ranks candidate frames based on head pose frontality, sharpness (Laplacian variance), open eyes probability, smiling probability, face completeness, and preference for solo shots over crowded frames.
6. **Instagram Story Collage (9:16)**: Formats tiles into an aesthetic 1080×1920 collage featuring generous portrait crops (head, hair, shoulders), person badges, and appearance counts.
7. **Native Gallery Saving & Android Share Sheet**: One-tap saving to `Pictures/IYKYK` via Android `MediaStore` and direct sharing using `FileProvider` and `Intent.ACTION_SEND`.

---

## Machine Learning Pipeline & Technical Details

### 1. Frame Extraction
- **File**: `com.example.iykyk.data.VideoFrameExtractor`
- **Sampling Rate**: 4 FPS (250 ms interval). This provides the temporal resolution needed to reliably capture short appearances (like the 1.4-second co-appearances in Sample 1) while remaining computationally light.
- **Memory Safety**: Large frames (e.g. 4K) are bounded to a maximum dimension of 1280 px during extraction, preventing out-of-memory errors on memory-constrained devices.

### 2. Face Detection
- **File**: `com.example.iykyk.processing.FaceDetector`
- **Framework**: Google ML Kit Face Detection (`com.google.mlkit:face-detection:16.1.7`).
- **Configuration**:
  - `PERFORMANCE_MODE_ACCURATE`: High detection recall, crucial for detecting smaller faces in two-person shared frames.
  - `LANDMARK_MODE_ALL`: Retrieves left eye, right eye, and nose base coordinates.
  - `CLASSIFICATION_MODE_ALL`: Retrieves left/right eye open probabilities and smiling probabilities.
  - `minFaceSize`: `0.06f` (catches distant or dual-subject faces).

### 3. Face Embeddings & Alignment
- **File**: `com.example.iykyk.processing.FaceEmbedder`
- **Model**: `mobilefacenet.tflite` (192-dimensional floating-point embeddings).
- **Input Dimensions**: 112 × 112 pixels (RGB).
- **Pixel Normalization**: `(pixel - 127.5f) / 128.0f` mapping values to `[-1.0, 1.0]`.
- **Landmark Alignment**:
  - In ML Kit, `RIGHT_EYE` is the subject's right eye (viewer's left) and `LEFT_EYE` is the subject's left eye (viewer's right).
  - An affine transformation (`Matrix.setPolyToPoly`) maps the subject's eyes and nose to standard MobileFaceNet coordinates:
    - Viewer Left Eye: `(38.29, 51.70)`
    - Viewer Right Eye: `(73.53, 51.50)`
    - Nose: `(56.03, 71.74)`
  - Embedding vectors are $L_2$-normalized: $\|\mathbf{v}\|_2 = 1$.

### 4. Continuous Appearance Tracking & Whip-Pan Filtering
- **File**: `com.example.iykyk.processing.AppearanceTracker`
- **Logic**: Tracks faces frame-by-frame using spatial proximity (Bounding Box IoU $\ge 0.05$ or normalized center distance $\le 0.65$).
- **Gap Tolerance**: `MAX_GAP_MS = 1000L` (1 second). This bridges momentary blinks, speech movements, or slight head turns without prematurely fragmenting a continuous appearance.
- **Whip-Pan Rejection**: Any track with fewer than 2 frames or lasting $< 400$ ms is filtered out. Blurred camera whip-pan passes produce transient 1-frame false detections that are discarded.
- **Appearance Representation**: The embeddings of all valid faces within an appearance are averaged and $L_2$-normalized to create a single robust embedding per appearance segment.

### 5. Identity Grouping (Face Clustering)
- **File**: `com.example.iykyk.processing.FaceClusterer`
- **Algorithm**: Hierarchical Agglomerative Clustering with **Average-Linkage**.
- **Distance Metric**: Pairwise Cosine Similarity:
  $$\text{sim}(\mathbf{u}, \mathbf{v}) = \frac{\mathbf{u} \cdot \mathbf{v}}{\|\mathbf{u}\| \|\mathbf{v}\|}$$
- **Similarity Threshold Chosen**: `0.55`
  - **Threshold Justification**: After correcting landmark alignment, intra-person appearance similarities for MobileFaceNet range from `0.58` to `0.85`, whereas inter-person similarities typically range below `0.35`. A threshold of `0.55` provides an optimal separation margin that reliably groups separate appearances of the same individual without merging distinct people.

### 6. Representative Shot Selection
- **File**: `com.example.iykyk.processing.RepresentativeSelector`
- For each appearance and each final person, candidate shots are scored based on:
  - **Pose Frontality (30%)**: Penalizes yaw, pitch, and roll deviation from direct frontal angles.
  - **Eyes Open (25%)**: Average of left and right eye open probabilities.
  - **Sharpness (20%)**: Laplacian variance over the face crop (rewards crisp, in-focus frames and rejects motion-blurred frames).
  - **Face Size & Completeness (20%)**: Penalizes faces cropped at frame borders.
  - **Smile / Expression (5%)**: Prefers pleasant facial expressions.
  - **Solo Frame Preference**: Frames where the subject is the *only* detected face are prioritized to prevent co-appearing persons from dominating a tile.

### 7. Collage Generation
- **File**: `com.example.iykyk.processing.CollageGenerator`
- **Format**: 1080 × 1920 (9:16 Instagram Story aspect ratio).
- **Cropping**: Generous portrait crop around the face (head, hair, and upper shoulders) rather than tight bounding boxes.
- **Design**: Dark charcoal slate background (`#0F172A`), rounded corner tiles (`cornerRadius = 32px`), outer contrast borders, title banner with overall statistics, and badge pills indicating `Person X • Y appearances`.

---

## Setup & Build Instructions

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or newer
- Android SDK (API 34 or 36)
- JDK 17 or JDK 20
- Device or Emulator running Android 8.0 (API 26) or higher

### Building via Terminal
1. Clone the repository:
   ```bash
   git clone https://github.com/sanskruti-18/Video-based-unique-person-collage.git
   cd Video-based-unique-person-collage
   ```
2. Configure `local.properties` (if not already set):
   ```properties
   sdk.dir=/path/to/your/Android/Sdk
   ```
3. Assemble the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
4. The output APK will be generated at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Installing on Device
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Worked Example Verification (Sample 1)

According to the assignment specification for Sample 1:
- **Expected**: 5 distinct people, each appearing 4 times (20 appearances total). A and B share the frame at 10.1–11.5s, C and D share at 20.2–21.6s.
- **Pipeline Result**:
  - 5 unique clusters formed (Person 1, 2, 3, 4, 5).
  - No duplicate identities (previous Person 1 & 5 duplicate bug resolved by anatomical landmark alignment).
  - Co-occurrences at 10.1–11.5s and 20.2–21.6s tracked as simultaneous appearances.
  - Solo shots prioritized for collage representatives.
  - Finished collage displays all 5 individuals with appearance badges.
