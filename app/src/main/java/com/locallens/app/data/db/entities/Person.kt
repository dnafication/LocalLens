package com.locallens.app.data.db.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class Person(
    @Id var id: Long = 0,
    var displayName: String = "",
    var isConfirmed: Boolean = false,
    var coverFaceDetectionId: Long = 0,
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
    var faceCount: Int = 0,
    var clusterVersion: Int = 0
)
