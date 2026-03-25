package com.locallens.app.data.db.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class CloudSyncRecord(
    @Id var id: Long = 0,
    var entityType: String = "",
    var entityId: Long = 0,
    var syncedAt: Long = 0,
    var remoteDocId: String = ""
)
