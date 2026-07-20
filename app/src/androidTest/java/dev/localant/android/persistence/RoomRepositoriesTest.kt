package dev.localant.android.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.localant.android.approval.Clock
import dev.localant.android.core.model.RiskLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRepositoriesTest {
    private lateinit var database: LocalAntDatabase
    private val clock = MutableClock(1_000)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LocalAntDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun approvalPersistsAndConsumesOnlyAfterApproval() {
        val repository = RoomApprovalRepository(database, clock) { "approval-1" }
        val pending = repository.request("device_tap", RiskLevel(2), "s1", "tap")

        assertNull(repository.consume(pending.id))
        assertTrue(repository.approve(pending.id, sessionGrant = true))
        assertTrue(repository.listPending().isEmpty())
        assertEquals(pending, repository.find(pending.id))
        assertTrue(repository.getSessionGrants("s1", "device_tap"))
        assertFalse(repository.getSessionGrants("s1", "device_swipe"))
        assertNotNull(repository.consume(pending.id))
        assertNull(repository.consume(pending.id))
    }

    @Test
    fun approvalAtExpiryCannotBeApproved() {
        val repository = RoomApprovalRepository(database, clock) { "approval-1" }
        val pending = repository.request("device_tap", RiskLevel(2), "s1", "tap")
        clock.value = pending.expiresAtMs

        assertFalse(repository.approve(pending.id, sessionGrant = true))
        assertFalse(repository.getSessionGrants("s1", "device_tap"))
        assertTrue(repository.listPending().isEmpty())
    }

    @Test
    fun auditIsRedactedBeforePersistence() {
        val repository = RoomAuditRepository(database, nowMs = { 42 }, idGenerator = { "audit-1" })

        repository.recordForResult(
            toolName = "shell_execute",
            risk = RiskLevel(3),
            callerSessionId = "s1",
            durationMs = 7,
            success = false,
            inputSummary = "authorization=Bearer secret-token",
            errorCode = "DENIED",
        )

        val entry = repository.listByIdSubstring("audit")!!
        assertEquals(42, entry.timestampMs)
        assertFalse(entry.inputSummary.getValue("input").contains("secret-token"))
    }

    private class MutableClock(var value: Long) : Clock {
        override fun nowMs(): Long = value
    }
}
