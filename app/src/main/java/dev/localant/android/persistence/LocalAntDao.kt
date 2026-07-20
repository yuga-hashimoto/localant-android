package dev.localant.android.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ApprovalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertApproval(entity: ApprovalEntity)

    @Query("SELECT * FROM approvals WHERE id = :id LIMIT 1")
    fun findApproval(id: String): ApprovalEntity?

    @Query("UPDATE approvals SET approved = 1 WHERE id = :id")
    fun markApproved(id: String): Int

    @Query("DELETE FROM approvals WHERE id = :id")
    fun deleteApproval(id: String): Int

    @Query("DELETE FROM approvals WHERE expiresAtMs <= :nowMs")
    fun deleteExpired(nowMs: Long): Int

    @Query("SELECT * FROM approvals ORDER BY expiresAtMs ASC")
    fun listApprovals(): List<ApprovalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSessionGrant(entity: SessionGrantEntity)

    @Query(
        "SELECT COUNT(*) FROM session_grants " +
            "WHERE sessionId = :sessionId AND toolName = :toolName",
    )
    fun hasSessionGrant(sessionId: String, toolName: String): Int
}

@Dao
interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: AuditEntity)

    @Query("SELECT * FROM audit_entries ORDER BY timestampMs DESC")
    fun list(): List<AuditEntity>

    @Query("SELECT * FROM audit_entries WHERE id LIKE '%' || :substring || '%' LIMIT 1")
    fun findByIdSubstring(substring: String): AuditEntity?
}
