package dev.localant.android.persistence

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ApprovalEntity::class, SessionGrantEntity::class, AuditEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LocalAntDatabase : RoomDatabase() {
    abstract fun approvalDao(): ApprovalDao
    abstract fun auditDao(): AuditDao
}
