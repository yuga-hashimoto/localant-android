package dev.localant.android.runtime

import android.content.Context
import androidx.room.Room
import dev.localant.android.audit.AuditRepository
import dev.localant.android.approval.ApprovalRepository
import dev.localant.android.persistence.LocalAntDatabase
import dev.localant.android.persistence.RoomApprovalRepository
import dev.localant.android.persistence.RoomAuditRepository
import dev.localant.android.security.AndroidKeystoreTokenBackend
import dev.localant.android.security.TokenStore

class LocalAntAppServices private constructor(context: Context) {
    private val appContext = context.applicationContext

    val database: LocalAntDatabase = Room.databaseBuilder(
        appContext,
        LocalAntDatabase::class.java,
        DATABASE_NAME,
    ).build()

    val tokenStore: TokenStore = TokenStore(AndroidKeystoreTokenBackend(appContext))
    val approvals: ApprovalRepository = RoomApprovalRepository(database)
    val audit: AuditRepository = RoomAuditRepository(database)

    companion object {
        private const val DATABASE_NAME = "localant.db"

        @Volatile
        private var instance: LocalAntAppServices? = null

        fun get(context: Context): LocalAntAppServices =
            instance ?: synchronized(this) {
                instance ?: LocalAntAppServices(context).also { instance = it }
            }
    }
}
