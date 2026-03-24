package com.locallens.app.data.db

import android.content.Context
import com.locallens.app.data.db.entities.MyObjectBox
import io.objectbox.BoxStore

object AppDatabase {
    private var boxStore: BoxStore? = null

    fun init(context: Context): BoxStore {
        if (boxStore == null) {
            boxStore = MyObjectBox.builder()
                .androidContext(context)
                .build()
        }
        return boxStore!!
    }

    fun get(): BoxStore = boxStore ?: throw IllegalStateException("AppDatabase not initialized")
}
