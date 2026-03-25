package com.locallens.app.data.db.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class ScanCheckpoint(
    @Id var id: Long = 0,
    var lastScannedDateModified: Long = 0,
    var lastScanStartedAt: Long = 0,
    var lastScanCompletedAt: Long = 0,
    var totalMediaScanned: Long = 0,
    var totalFacesDetected: Long = 0
)
