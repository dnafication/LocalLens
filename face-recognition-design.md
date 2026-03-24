# Technical Solution Design: Android Face Recognition & Media Labeling App
**Version:** 1.0  
**Target Platform:** Android API 33+ (Android 13)  
**Media Scale:** Medium (5K–50K photos)  
**Cloud Strategy:** On-device + optional metadata backup  

---

## 1. System Overview

This app scans locally stored photos and videos, detects all faces, generates face embeddings, clusters those embeddings into people, and allows users to name, search, and query their media by person. All ML inference and storage runs on-device. An optional cloud backup stores only metadata (no raw media, no embeddings).

### High-Level Pipeline

```
MediaStore (API 33) 
    └─► MediaScanner (WorkManager)
            └─► Frame Extractor (photos + video keyframes)
                    └─► ML Kit Face Detector
                            └─► FaceNet TFLite (per detected face crop)
                                    └─► ObjectBox (embeddings + full data model)
                                            └─► DBSCAN Clusterer (WorkManager)
                                                    └─► UI (Jetpack Compose)
                                                            └─► Optional Cloud Backup
```

---

## 2. Tech Stack

| Layer | Technology | Rationale |
|---|---|---|
| Language | Kotlin | First-class Android support |
| UI | Jetpack Compose + Material 3 | Modern declarative UI |
| Face detection | ML Kit Face Detection (bundled) | On-device, fast, free, landmarks |
| Embedding model | FaceNet TFLite (128-dim) | Proven accuracy, small footprint |
| TFLite runtime | `org.tensorflow:tensorflow-lite` + GPU delegate | Hardware acceleration |
| Database | ObjectBox v4+ | Embedded HNSW vector search + object model |
| Background jobs | WorkManager | Battery-aware, persistent across reboots |
| Media access | MediaStore (API 33) | Scoped storage, no broad permissions |
| DI | Hilt | Standard Android DI |
| Async | Kotlin Coroutines + Flow | Native async, cancellation support |
| Image loading | Coil | Compose-native, fast thumbnails |
| Cloud backup | Firebase Firestore (optional, user opt-in) | Metadata only, no media |
| Testing | JUnit5 + MockK + Robolectric | Unit + integration |

---

## 3. Permissions

Declare in `AndroidManifest.xml`:

```xml
<!-- Required: Read photos and videos (API 33+ granular permissions) -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />

<!-- Required: Schedule background indexing -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Optional: Cloud backup -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Optional: Foreground service for large initial scan -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

**Runtime permission flow:**
- Request `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` before first scan.
- If denied, show rationale and re-request once. On permanent denial, deep-link to app settings.
- Never request `READ_MEDIA_VISUAL_USER_SELECTED` partial access — require full access to function correctly.
- Do NOT request `WRITE_EXTERNAL_STORAGE` — scoped storage on API 33 does not require it.

---

## 4. Data Model (ObjectBox)

### 4.1 Entity: `MediaFile`

Represents a single photo or video on-device.

```kotlin
@Entity
data class MediaFile(
    @Id var id: Long = 0,
    var mediaStoreId: Long = 0,         // MediaStore._ID — stable identifier
    var uri: String = "",               // content:// URI string
    var mimeType: String = "",          // image/jpeg, video/mp4, etc.
    var filePath: String = "",          // Absolute path (for TFLite input)
    var dateAdded: Long = 0,            // Epoch millis (from MediaStore)
    var dateModified: Long = 0,         // Epoch millis (for change detection)
    var durationMs: Long = 0,           // Videos only; 0 for photos
    var width: Int = 0,
    var height: Int = 0,
    var indexState: Int = IndexState.PENDING,  // See IndexState enum
    var indexedAt: Long = 0,            // Epoch millis of last successful index
    var errorMessage: String = ""       // Last error, if any
)

object IndexState {
    const val PENDING = 0
    const val PROCESSING = 1
    const val INDEXED = 2
    const val NO_FACES = 3        // Processed but no faces found
    const val ERROR = 4
    const val SKIPPED = 5         // e.g., screenshots explicitly excluded
}
```

**ToMany relation:** `MediaFile` → `FaceDetection` (one file has many detected faces).

```kotlin
@Entity
data class MediaFile(...) {
    @Backlink(to = "mediaFile")
    lateinit var faces: ToMany<FaceDetection>
}
```

---

### 4.2 Entity: `FaceDetection`

Represents a single face found within a media file.

```kotlin
@Entity
data class FaceDetection(
    @Id var id: Long = 0,

    // Relation to parent media
    lateinit var mediaFile: ToOne<MediaFile>,

    // For videos: which keyframe this was extracted from
    var frameTimestampMs: Long = 0,       // 0 for photos

    // Bounding box (normalized 0.0–1.0 relative to frame dimensions)
    var boxLeft: Float = 0f,
    var boxTop: Float = 0f,
    var boxRight: Float = 0f,
    var boxBottom: Float = 0f,

    // ML Kit quality signals
    var detectionConfidence: Float = 0f,
    var rollAngle: Float = 0f,           // Head roll; filter out extreme angles
    var yawAngle: Float = 0f,            // Head yaw; filter out profiles > 45°
    var pitchAngle: Float = 0f,

    // Assigned person (null = unclustered)
    lateinit var person: ToOne<Person>,

    // The face embedding vector (128 dimensions, FaceNet)
    @HnswIndex(dimensions = 128, distanceType = VectorDistanceType.COSINE)
    var embedding: FloatArray = floatArrayOf(),

    var embeddingVersion: Int = 1        // Bump when model changes to trigger re-indexing
)
```

---

### 4.3 Entity: `Person`

A cluster of faces believed to belong to the same individual.

```kotlin
@Entity
data class Person(
    @Id var id: Long = 0,
    var displayName: String = "",           // User-assigned name; empty = unnamed
    var isConfirmed: Boolean = false,       // True once user has named or confirmed
    var coverFaceDetectionId: Long = 0,     // ID of FaceDetection to use as avatar
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
    var faceCount: Int = 0,                 // Denormalized count for display
    var clusterVersion: Int = 0             // Incremented on re-cluster
)
```

**ToMany relation:** `Person` → `FaceDetection` (backlink via `FaceDetection.person`).

---

### 4.4 Entity: `ScanCheckpoint`

Tracks MediaStore sync state to enable incremental scanning.

```kotlin
@Entity
data class ScanCheckpoint(
    @Id var id: Long = 0,
    var lastScannedDateModified: Long = 0,  // Watermark for incremental scans
    var lastScanStartedAt: Long = 0,
    var lastScanCompletedAt: Long = 0,
    var totalMediaScanned: Long = 0,
    var totalFacesDetected: Long = 0
)
```

---

### 4.5 Entity: `CloudSyncRecord`

Tracks what metadata has been uploaded (only present if cloud backup is enabled).

```kotlin
@Entity
data class CloudSyncRecord(
    @Id var id: Long = 0,
    var entityType: String = "",            // "Person" | "FaceDetection" | "MediaFile"
    var entityId: Long = 0,
    var syncedAt: Long = 0,
    var remoteDocId: String = ""
)
```

---

## 5. Module Structure

```
app/
├── di/                         # Hilt modules
│   ├── DatabaseModule.kt
│   ├── MLModule.kt
│   └── RepositoryModule.kt
├── data/
│   ├── db/                     # ObjectBox setup and entity access
│   │   ├── AppDatabase.kt
│   │   └── entities/           # All @Entity classes
│   ├── media/
│   │   ├── MediaStoreScanner.kt
│   │   └── VideoFrameExtractor.kt
│   ├── ml/
│   │   ├── FaceDetector.kt     # ML Kit wrapper
│   │   ├── FaceEmbedder.kt     # FaceNet TFLite wrapper
│   │   └── ModelAssetManager.kt
│   ├── clustering/
│   │   └── DbscanClusterer.kt
│   └── repository/
│       ├── MediaRepository.kt
│       ├── PersonRepository.kt
│       └── FaceRepository.kt
├── work/
│   ├── MediaIndexWorker.kt     # Main indexing pipeline
│   ├── ClusteringWorker.kt
│   ├── IncrementalScanWorker.kt
│   └── CloudSyncWorker.kt
├── ui/
│   ├── home/
│   ├── people/
│   ├── person_detail/
│   ├── photo_detail/
│   ├── search/
│   └── settings/
└── util/
    ├── BitmapUtils.kt
    └── EmbeddingUtils.kt
```

---

## 6. Media Scanning

### 6.1 Initial Full Scan (`MediaStoreScanner`)

Query MediaStore for all photos and videos. API 33 requires no broad storage permission — use `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO`.

```kotlin
class MediaStoreScanner(private val context: Context) {

    fun scanAll(): Flow<MediaFile> = flow {
        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATA,           // Absolute path
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Video.Media.DURATION         // null for images
        )

        for (uri in collections) {
            val cursor = context.contentResolver.query(
                uri, projection, null, null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} ASC"
            ) ?: continue

            cursor.use {
                while (it.moveToNext()) {
                    emit(it.toMediaFile(uri))
                }
            }
        }
    }

    // Incremental: only files modified after watermark
    fun scanSince(sinceEpochMillis: Long): Flow<MediaFile> = flow {
        val selection = "${MediaStore.MediaColumns.DATE_MODIFIED} > ?"
        val selectionArgs = arrayOf((sinceEpochMillis / 1000).toString()) // MediaStore uses seconds
        // ... same structure as scanAll() but with selection applied
    }
}
```

**Filtering rules (apply in scanner, before inserting to DB):**
- Skip MIME types that are not `image/jpeg`, `image/png`, `image/webp`, `image/heic`, `video/mp4`, `video/quicktime`, `video/x-matroska`.
- Skip files with `width == 0 || height == 0`.
- Skip files smaller than 10KB (likely corrupted or placeholder).
- Skip files whose path contains `/Screenshots/` or `/Screen recordings/` (these are user-configurable in Settings).

---

### 6.2 Incremental Scan

Run every 15 minutes via WorkManager `PeriodicWorkRequest`. Uses `ScanCheckpoint.lastScannedDateModified` as a watermark. After each scan, update the checkpoint with the highest `DATE_MODIFIED` value seen.

**Deletion handling:** Run a reconciliation pass once per day. Query all `MediaFile` records with `IndexState.INDEXED`, check if their `mediaStoreId` still exists in MediaStore. Delete `MediaFile`, `FaceDetection`, and update `Person.faceCount` for any missing files.

---

### 6.3 Video Frame Extraction (`VideoFrameExtractor`)

For videos, extract keyframes rather than every frame. Use `MediaMetadataRetriever`.

**Strategy:**
- Videos ≤ 10s: extract at 0s, 50%.
- Videos 10s–60s: extract every 5 seconds.
- Videos 60s–300s: extract every 15 seconds.
- Videos > 300s: extract every 30 seconds, capped at 50 frames total.

```kotlin
class VideoFrameExtractor {
    suspend fun extractFrames(filePath: String, durationMs: Long): List<Pair<Long, Bitmap>> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val timestamps = computeTimestamps(durationMs)
            timestamps.mapNotNull { ts ->
                val bmp = retriever.getFrameAtTime(
                    ts * 1000, // microseconds
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (bmp != null) Pair(ts, bmp) else null
            }
        } finally {
            retriever.release()
        }
    }

    private fun computeTimestamps(durationMs: Long): List<Long> { /* ... */ }
}
```

**Video deduplication:** After extracting all face embeddings from a video, apply cosine similarity (threshold 0.85). If two faces from different frames have similarity > 0.85, keep only the one with higher detection confidence. This prevents storing dozens of near-identical embeddings from the same person in a single video.

---

## 7. Face Detection (`FaceDetector`)

Wrap ML Kit's `FaceDetector` in a Kotlin suspend function.

```kotlin
class FaceDetector {

    private val detector: com.google.mlkit.vision.face.FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.08f)   // Ignore faces smaller than 8% of frame width
            .build()
        com.google.mlkit.vision.face.FaceDetection.getClient(options)
    }

    suspend fun detect(bitmap: Bitmap): List<Face> = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        detector.process(image)
            .addOnSuccessListener { faces -> cont.resume(faces) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }
}
```

**Quality filter (apply before passing to embedder):**
- Discard faces where bounding box area < 48×48 pixels (too small for reliable embedding).
- Discard faces where `yawAngle.absoluteValue > 45f` (too much profile angle).
- Discard faces where `pitchAngle.absoluteValue > 30f`.
- Discard faces where detection confidence < 0.7.

---

## 8. Face Embedding (`FaceEmbedder`)

### 8.1 Model Setup

Use the FaceNet 512-dim TFLite model (MobileFaceNet is an acceptable lighter alternative). Place the `.tflite` file in `app/src/main/assets/facenet.tflite`.

**Recommended model:** `facenet_512.tflite` (from the `sirius-face/facenet-pytorch` TFLite export, or Google FaceNet port). 128-dim is also acceptable and ~2× faster.

```kotlin
class FaceEmbedder(private val context: Context) {

    private val EMBEDDING_DIM = 128  // or 512
    private val INPUT_SIZE = 160      // FaceNet expects 160x160

    private val interpreter: Interpreter by lazy {
        val model = FileUtil.loadMappedFile(context, "facenet.tflite")
        val options = Interpreter.Options().apply {
            addDelegate(GpuDelegate())           // Fall back to NNAPI or CPU if unavailable
            numThreads = 2
        }
        Interpreter(model, options)
    }

    fun embed(faceBitmap: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)
        val input = preprocessToFloatArray(scaled)   // Normalize to [-1, 1]
        val output = Array(1) { FloatArray(EMBEDDING_DIM) }
        interpreter.run(input, output)
        return l2Normalize(output[0])                // Always L2-normalize before storing
    }

    private fun preprocessToFloatArray(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        // Shape: [1, 160, 160, 3], normalized to [-1.0, 1.0]
        val input = Array(1) { Array(INPUT_SIZE) { Array(INPUT_SIZE) { FloatArray(3) } } }
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = bitmap.getPixel(x, y)
                input[0][y][x][0] = (Color.red(pixel) - 128f) / 128f
                input[0][y][x][1] = (Color.green(pixel) - 128f) / 128f
                input[0][y][x][2] = (Color.blue(pixel) - 128f) / 128f
            }
        }
        return input
    }

    private fun l2Normalize(vec: FloatArray): FloatArray {
        val norm = sqrt(vec.sumOf { (it * it).toDouble() }).toFloat()
        return if (norm > 0f) FloatArray(vec.size) { vec[it] / norm } else vec
    }
}
```

**Face crop extraction:** Before passing to the embedder, crop the face from the full bitmap using the bounding box from ML Kit. Add 20% padding around the bounding box (clamped to image boundaries) to include forehead and chin context.

---

## 9. Background Processing (WorkManager)

### 9.1 Worker: `MediaIndexWorker`

Processes a batch of unindexed `MediaFile` records. Runs as a chained, expedited foreground service during the initial scan.

```kotlin
@HiltWorker
class MediaIndexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mediaRepo: MediaRepository,
    private val faceDetector: FaceDetector,
    private val faceEmbedder: FaceEmbedder,
    private val videoExtractor: VideoFrameExtractor
) : CoroutineWorker(context, params) {

    companion object {
        const val BATCH_SIZE = 20
        const val KEY_MEDIA_FILE_IDS = "media_file_ids"
    }

    override suspend fun doWork(): Result {
        val ids = inputData.getLongArray(KEY_MEDIA_FILE_IDS) ?: return Result.failure()

        ids.forEach { mediaFileId ->
            try {
                processMediaFile(mediaFileId)
            } catch (e: Exception) {
                mediaRepo.markError(mediaFileId, e.message ?: "Unknown error")
                // Continue with next file — don't fail entire batch
            }
        }

        return Result.success()
    }

    private suspend fun processMediaFile(id: Long) {
        val mediaFile = mediaRepo.getById(id) ?: return
        mediaRepo.markProcessing(id)

        val frames: List<Pair<Long, Bitmap>> = when {
            mediaFile.mimeType.startsWith("image/") ->
                listOf(Pair(0L, BitmapUtils.loadAndOrient(mediaFile.filePath)))
            mediaFile.mimeType.startsWith("video/") ->
                videoExtractor.extractFrames(mediaFile.filePath, mediaFile.durationMs)
            else -> return mediaRepo.markSkipped(id)
        }

        val seenEmbeddings = mutableListOf<FloatArray>()

        for ((timestampMs, bitmap) in frames) {
            val faces = faceDetector.detect(bitmap)
            for (face in faces) {
                if (!face.passesQualityFilter()) continue

                val crop = BitmapUtils.cropFace(bitmap, face.boundingBox, padding = 0.20f)
                val embedding = faceEmbedder.embed(crop)

                // Video deduplication: skip if too similar to an already-seen embedding
                if (seenEmbeddings.any { cosineSimilarity(it, embedding) > 0.85f }) continue
                seenEmbeddings.add(embedding)

                mediaRepo.saveFaceDetection(
                    FaceDetection(
                        frameTimestampMs = timestampMs,
                        boxLeft = face.boundingBox.left / bitmap.width.toFloat(),
                        boxTop = face.boundingBox.top / bitmap.height.toFloat(),
                        boxRight = face.boundingBox.right / bitmap.width.toFloat(),
                        boxBottom = face.boundingBox.bottom / bitmap.height.toFloat(),
                        detectionConfidence = face.headEulerAngleY, // use as proxy
                        rollAngle = face.headEulerAngleZ,
                        yawAngle = face.headEulerAngleY,
                        pitchAngle = face.headEulerAngleX,
                        embedding = embedding,
                        embeddingVersion = 1
                    ).apply { mediaFile.put(mediaRepo.getById(id)!!) }
                )
            }
        }

        val state = if (seenEmbeddings.isEmpty()) IndexState.NO_FACES else IndexState.INDEXED
        mediaRepo.markIndexed(id, state)
    }
}
```

**Scheduling logic (enqueue from `AppStartup` and `MediaChangeObserver`):**

```kotlin
// Initial scan: process all PENDING files in batches of 20
fun enqueuePendingWork(pendingIds: List<Long>) {
    val batches = pendingIds.chunked(20)
    val requests = batches.map { batch ->
        OneTimeWorkRequestBuilder<MediaIndexWorker>()
            .setInputData(workDataOf(KEY_MEDIA_FILE_IDS to batch.toLongArray()))
            .setConstraints(Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }
    WorkManager.getInstance(context).enqueue(requests)
}

// Incremental scan: every 15 minutes
fun enqueueIncrementalScan() {
    val request = PeriodicWorkRequestBuilder<IncrementalScanWorker>(15, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build())
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "incremental_scan",
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}
```

---

### 9.2 Worker: `ClusteringWorker`

Triggered after each indexing batch completes (via `then()` chaining) and also runs daily.

```kotlin
@HiltWorker
class ClusteringWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val faceRepo: FaceRepository,
    private val clusterer: DbscanClusterer,
    private val personRepo: PersonRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Fetch all unassigned face embeddings
        val unassigned = faceRepo.getUnassignedEmbeddings()
        if (unassigned.size < 5) return Result.success() // Not enough data to cluster

        // Also fetch existing person centroids to assign to existing clusters
        val existingPersons = personRepo.getAll()

        val assignments = clusterer.cluster(
            unassigned = unassigned,
            existingPersons = existingPersons
        )

        personRepo.applyClusteringResult(assignments)
        return Result.success()
    }
}
```

---

## 10. Clustering (`DbscanClusterer`)

### 10.1 Algorithm

Use **DBSCAN** on cosine distance. Do not use k-means (requires knowing k upfront).

**Key parameters:**
- `epsilon = 0.40` (cosine distance threshold — faces within this distance are the same cluster). **This is the most important tuning parameter.**
- `minSamples = 1` (allow single-face clusters — everyone appears at least once).

```kotlin
class DbscanClusterer {

    private val EPSILON = 0.40f
    private val MIN_SAMPLES = 1

    data class ClusteringInput(
        val faceDetectionId: Long,
        val embedding: FloatArray
    )

    data class ClusteringResult(
        val assignments: Map<Long, Int>,   // faceDetectionId -> clusterId (-1 = noise)
        val centroids: Map<Int, FloatArray> // clusterId -> mean embedding
    )

    fun cluster(
        unassigned: List<ClusteringInput>,
        existingPersons: List<Person>
    ): ClusteringResult {
        if (unassigned.isEmpty()) return ClusteringResult(emptyMap(), emptyMap())

        // First: attempt to assign unassigned faces to existing confirmed persons
        val remaining = mutableListOf<ClusteringInput>()
        val assignments = mutableMapOf<Long, Int>()

        for (face in unassigned) {
            val match = existingPersons.firstOrNull { person ->
                val centroid = person.computedCentroid() ?: return@firstOrNull false
                cosineDistance(face.embedding, centroid) < EPSILON
            }
            if (match != null) {
                assignments[face.faceDetectionId] = match.id.toInt()
            } else {
                remaining.add(face)
            }
        }

        // Run DBSCAN on the remainder
        val dbscanResult = runDbscan(remaining)
        assignments.putAll(dbscanResult.assignments)

        return ClusteringResult(assignments, dbscanResult.centroids)
    }

    private fun runDbscan(points: List<ClusteringInput>): ClusteringResult { /* DBSCAN impl */ }

    private fun cosineDistance(a: FloatArray, b: FloatArray): Float =
        1f - (a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }.toFloat())
}
```

### 10.2 ObjectBox Vector Search for Cluster Assignment

For scale, leverage ObjectBox's HNSW index instead of O(n²) DBSCAN for large libraries.

When a new face comes in:
1. Use ObjectBox nearest-neighbor search to find the top-5 closest existing `FaceDetection` records.
2. If the nearest neighbor has cosine distance < `EPSILON` and belongs to a confirmed `Person`, assign the new face to that person.
3. If no match is found, mark as unassigned for the next batch DBSCAN run.

```kotlin
// ObjectBox nearest-neighbor query
val query = faceDetectionBox.query(
    FaceDetection_.embedding.nearestNeighbors(newEmbedding, maxResultCount = 5)
).build()
val candidates = query.findWithScores()
// scores are distances; filter by threshold
```

### 10.3 Re-clustering

When the user merges two `Person` clusters or splits one, recompute the centroids for affected persons and re-evaluate all their `FaceDetection` records. Do not run a full global re-cluster — only re-evaluate affected faces.

---

## 11. Repository Layer

### `MediaRepository`

```kotlin
interface MediaRepository {
    fun getAllMediaFiles(): Flow<List<MediaFile>>
    fun getPendingFiles(): List<MediaFile>
    fun getById(id: Long): MediaFile?
    fun upsert(mediaFile: MediaFile): Long
    fun markProcessing(id: Long)
    fun markIndexed(id: Long, state: Int)
    fun markError(id: Long, message: String)
    fun markSkipped(id: Long)
    fun saveFaceDetection(face: FaceDetection)
    fun deleteByMediaStoreId(mediaStoreId: Long)
}
```

### `PersonRepository`

```kotlin
interface PersonRepository {
    fun getAll(): List<Person>
    fun getById(id: Long): Person?
    fun getConfirmedPersons(): Flow<List<Person>>
    fun createPerson(name: String): Person
    fun rename(id: Long, newName: String)
    fun mergePeople(sourceId: Long, targetId: Long)   // Move all FaceDetections from source to target, delete source
    fun splitFace(faceDetectionId: Long): Person      // Create new Person with just this face
    fun deletePerson(id: Long)                        // Unassign all faces, delete Person record
    fun applyClusteringResult(result: ClusteringResult)
    fun updateCoverPhoto(personId: Long, faceDetectionId: Long)
}
```

### `FaceRepository`

```kotlin
interface FaceRepository {
    fun getByPerson(personId: Long): List<FaceDetection>
    fun getByMediaFile(mediaFileId: Long): List<FaceDetection>
    fun getUnassignedEmbeddings(): List<DbscanClusterer.ClusteringInput>
    fun searchByEmbedding(embedding: FloatArray, maxResults: Int = 10): List<FaceDetection>
    fun getMediaFilesForPerson(personId: Long): Flow<List<MediaFile>>
}
```

---

## 12. UI Screens

### Screen 1: Home / Media Grid
- Infinite lazy grid of all media files (by date descending).
- Chip filters: "All", "People", by named person.
- Tapping a photo opens Photo Detail.

### Screen 2: People List
- Grid of person avatars (cover photo face crop) with name or "Unknown #N".
- Unnamed persons sorted to the bottom.
- "Suggest names" nudge card shown until user names at least 3 people.
- FAB to manually create a person.

### Screen 3: Person Detail
- Hero: person avatar + name (editable inline).
- Scrollable media grid of all photos/videos containing this person.
- "This isn't [Name]" button on each photo (calls `splitFace` or `unassignFace`).
- Long-press on avatar to change cover photo.
- Overflow menu: Merge with another person, Delete person.

### Screen 4: Photo Detail
- Full-screen photo with face bounding box overlays (drawn as rounded rectangles).
- Tap a bounding box → bottom sheet showing that person's name (or "Unknown") + assign/change person action.
- "Who's in this photo?" summary chips below image.

### Screen 5: Search
- Text search by person name.
- Future: multi-person query ("Photos with Alice AND Bob").

### Screen 6: Settings
- Toggle screenshot indexing.
- Toggle video indexing.
- Re-run full clustering.
- Re-index all media (nuclear option).
- Cloud backup: sign in, enable, last synced timestamp.
- Delete all face data (keeps media untouched).

---

## 13. Clustering UX Flow (Critical)

This is the highest-risk UX surface. Design it carefully.

### 13.1 Initial Naming Flow

After the first indexing + clustering completes, surface an onboarding sheet:

1. Show the largest unnamed cluster.
2. Display a 3×3 grid of faces from that cluster.
3. Prompt: "Who is this?" with a text field.
4. On name entry → confirm → move to next unnamed cluster.
5. "Skip" moves the cluster to the bottom of the queue.
6. "These aren't all the same person" → enter split mode.

### 13.2 Correction Gestures

| Action | Trigger | System response |
|---|---|---|
| "This isn't [Name]" | Button on Photo Detail face chip | Unassign face from person; add to unassigned pool |
| "Mark as same person" | Long-press two face chips in same photo | Merge the two Persons |
| "Split cluster" | Button in Person Detail overflow | Enter selection mode; user selects faces to move to new Person |
| Rename | Tap name in Person Detail | Inline text field |

### 13.3 Confidence Indicator

Display a subtle "?" badge on person chips for faces where the nearest-neighbor cosine distance was between 0.35 and 0.40 (borderline assignment). This signals to the user that confirmation would be helpful.

---

## 14. Cloud Backup (Optional, User Opt-In)

**What is backed up (metadata only):**
- `Person` records (id, name, coverFaceDetectionId, faceCount).
- `FaceDetection` ↔ `Person` assignments (faceDetectionId, personId, mediaStoreId).
- `MediaFile` index states.

**What is NOT backed up:**
- Raw embedding vectors (privacy-sensitive, large).
- Media files (user's responsibility / Google Photos handles this).

**Implementation:**

Use Firebase Firestore with Firestore Security Rules scoped to `userId` (Firebase Auth anonymous auth is acceptable for no-login flow).

Firestore collections:
```
users/{userId}/persons/{personId}
users/{userId}/assignments/{faceDetectionId}
```

`CloudSyncWorker` runs on `NetworkType.UNMETERED` (Wi-Fi only by default, user-configurable). It is a `PeriodicWorkRequest` with a 1-hour interval. It reads `CloudSyncRecord` to determine what has changed since last sync and uploads only deltas.

**Restore flow:** On app reinstall, after granting media permissions and completing a fresh index, download person names and assignments from Firestore and re-apply them by matching `mediaStoreId` (which is stable across reinstalls on the same device).

---

## 15. Performance Considerations

| Concern | Mitigation |
|---|---|
| Initial scan of 50K photos is slow | Run in foreground service with progress notification; process in batches of 20; estimate ~5–8ms per photo on mid-range device |
| TFLite GPU delegate unavailable | Gracefully fall back: GPU → NNAPI → CPU (4 threads) |
| DBSCAN is O(n²) on full library | Use ObjectBox HNSW nearest-neighbor search to pre-filter candidates; only run full DBSCAN on unassigned batch, not full library |
| Memory pressure when processing video | Release `Bitmap` and `MediaMetadataRetriever` explicitly in `finally` blocks; cap concurrent video workers to 1 |
| Large initial WorkManager queue | Use `WorkManager.enqueueUniqueWork` with `ExistingWorkPolicy.KEEP` to prevent duplicate queuing on app restart |
| ObjectBox write amplification on re-cluster | Batch all assignment updates in a single ObjectBox transaction |

---

## 16. Error Handling & Edge Cases

| Scenario | Handling |
|---|---|
| Media file deleted between scan and index | Catch `FileNotFoundException` in `MediaIndexWorker`; mark `IndexState.ERROR`; reconciliation pass will clean up |
| Face detected but embedding fails | Mark face with `embeddingVersion = 0`; exclude from clustering; retry on next indexing pass |
| GPU delegate crash (OOM) | Catch `IllegalStateException`; disable GPU delegate for the session; log to analytics |
| MediaStore URI revoked | Catch `SecurityException` on open; mark `IndexState.ERROR` |
| App killed mid-indexing | WorkManager retries; `IndexState.PROCESSING` files are re-queued on next worker start |
| Two workers running concurrently | Use `WorkManager.enqueueUniqueWork` with `ExistingWorkPolicy.KEEP`; ObjectBox handles concurrent writes safely |
| Embedding model version update | Bump `EMBEDDING_VERSION` constant; query all `FaceDetection` where `embeddingVersion < CURRENT`; re-embed and re-cluster |
| Video with no decodable frames | `MediaMetadataRetriever.getFrameAtTime` returns null; skip gracefully |
| Person with 0 remaining faces after correction | Auto-delete `Person` record |
| Identical twins | No mitigation — this is a fundamental limitation of face embeddings. Document clearly. |

---

## 17. Gradle Dependencies

```groovy
// ObjectBox
implementation("io.objectbox:objectbox-android:3.8.0")
kapt("io.objectbox:objectbox-processor:3.8.0")

// ML Kit
implementation("com.google.mlkit:face-detection:16.1.7")

// TensorFlow Lite
implementation("org.tensorflow:tensorflow-lite:2.14.0")
implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

// Hilt
implementation("com.google.dagger:hilt-android:2.50")
kapt("com.google.dagger:hilt-compiler:2.50")
implementation("androidx.hilt:hilt-work:1.1.0")

// WorkManager
implementation("androidx.work:work-runtime-ktx:2.9.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// Compose
implementation(platform("androidx.compose:compose-bom:2024.01.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.activity:activity-compose:1.8.2")

// Coil
implementation("io.coil-kt:coil-compose:2.5.0")

// Firebase (optional, cloud backup only)
implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
implementation("com.google.firebase:firebase-firestore-ktx")
implementation("com.google.firebase:firebase-auth-ktx")
```

---

## 18. Testing Strategy

| Test type | Target | Tool |
|---|---|---|
| Unit | `DbscanClusterer` with synthetic embeddings | JUnit5 + MockK |
| Unit | `FaceEmbedder` preprocessing/normalization | JUnit5 |
| Unit | `MediaStoreScanner` cursor parsing | Robolectric |
| Integration | `MediaIndexWorker` full pipeline | WorkManager `TestDriver` + Robolectric |
| Integration | ObjectBox vector search accuracy | JUnit5 with in-memory ObjectBox |
| UI | People list, clustering correction flow | Compose UI Testing |

**Critical test cases for clustering:**
- Two embeddings from same person → same cluster.
- Two embeddings from different people → different clusters.
- Epsilon boundary: embedding at distance 0.39 (should cluster), 0.41 (should not).
- Merge + split operations leave DB in consistent state.
- Re-clustering after person rename preserves name.

---

## 19. Open Decisions for Implementer

The following decisions were intentionally left open for the implementer or future product iteration:

1. **FaceNet model variant:** 128-dim (faster, ~5ms/face) vs 512-dim (more accurate, ~12ms/face). Recommend starting with 128-dim and measuring false-merge rate.
2. **DBSCAN epsilon tuning:** 0.40 is a starting point. Expose a debug screen to visualize cluster cohesion and adjust.
3. **Screenshot exclusion:** Implemented as path-based heuristic (`/Screenshots/`). If users want finer control, add a folder picker in Settings.
4. **Multi-person query:** "Photos with Alice AND Bob" requires intersecting two `getMediaFilesForPerson` result sets. The data model supports it; the UI does not yet.
5. **Face aging:** The same person at age 5 and age 35 will not cluster together. No mitigation; inform users via in-app copy.
6. **Backup encryption:** If cloud backup is enabled, consider encrypting person names client-side before upload. Firestore Security Rules alone are not end-to-end encrypted.
