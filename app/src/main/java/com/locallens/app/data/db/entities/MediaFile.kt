package com.locallens.app.data.db.entities

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToMany

@Entity
data class MediaFile(
    @Id var id: Long = 0,
    var mediaStoreId: Long = 0,
    var uri: String = "",
    var mimeType: String = "",
    var filePath: String = "",
    var dateAdded: Long = 0,
    var dateModified: Long = 0,
    var durationMs: Long = 0,
    var width: Int = 0,
    var height: Int = 0,
    var indexState: Int = IndexState.PENDING,
    var indexedAt: Long = 0,
    var errorMessage: String = ""
) {
    @Backlink(to = "mediaFile")
    lateinit var faces: ToMany<FaceDetection>
}
