package com.locallens.app.data.db.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.relation.ToOne

@Entity
data class FaceDetection(
    @Id var id: Long = 0,
    var frameTimestampMs: Long = 0,
    var boxLeft: Float = 0f,
    var boxTop: Float = 0f,
    var boxRight: Float = 0f,
    var boxBottom: Float = 0f,
    var detectionConfidence: Float = 0f,
    var rollAngle: Float = 0f,
    var yawAngle: Float = 0f,
    var pitchAngle: Float = 0f,
    @HnswIndex(dimensions = 128)
    var embedding: FloatArray = floatArrayOf(),
    var embeddingVersion: Int = 1
) {
    lateinit var mediaFile: ToOne<MediaFile>
    lateinit var person: ToOne<Person>

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FaceDetection
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
