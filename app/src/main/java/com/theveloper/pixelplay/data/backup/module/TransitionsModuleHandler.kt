package com.theveloper.pixelplay.data.backup.module

import com.theveloper.pixelplay.data.backup.model.BackupSection
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransitionsModuleHandler @Inject constructor() : BackupModuleHandler {

    override val section = BackupSection.TRANSITIONS

    override suspend fun export(): String = "[]"

    override suspend fun countEntries(): Int = 0

    override suspend fun snapshot(): String = export()

    override suspend fun restore(payload: String) {
        // No-op: transition rules removed
    }

    override suspend fun rollback(snapshot: String) {
        // No-op
    }
}
