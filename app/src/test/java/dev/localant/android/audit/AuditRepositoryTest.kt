package dev.localant.android.audit

import dev.localant.android.core.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuditRepositoryTest {

    private lateinit var repository: InMemoryAuditRepository

    @Before
    fun setUp() {
        repository = InMemoryAuditRepository()
    }

    @Test
    fun record_storesEntry() {
        val entry = AuditEntry(
            id = "a1",
            toolName = "shell_execute",
            risk = RiskLevel(3),
            timestampMs = 1_000_000L,
            callerSessionId = "s1",
            approvalResult = "APPROVED",
            durationMs = 150L,
            success = true,
            errorCode = null,
            inputSummary = mapOf("cmd" to "ls -la"),
        )
        repository.record(entry)
        val entries = repository.list()
        assertEquals(1, entries.size)
        assertEquals("a1", entries[0].id)
    }

    @Test
    fun list_returnsEntriesInInsertionOrder() {
        repository.record(auditEntry("1", "tool_a"))
        repository.record(auditEntry("2", "tool_b"))
        repository.record(auditEntry("3", "tool_c"))
        val names = repository.list().map { it.toolName }
        assertEquals(listOf("tool_a", "tool_b", "tool_c"), names)
    }

    @Test
    fun list_returnsDefensiveCopy() {
        repository.record(auditEntry("1", "tool_a"))
        val entries = repository.list()
        assertEquals(1, entries.size)
        assertEquals(1, repository.list().size)
    }

    @Test
    fun redactJson_removesPasswordField() {
        val input = """{"username":"admin","password":"secret123","host":"localhost"}"""
        val redacted = repository.redactJson(input)
        assertTrue(redacted.contains("username"))
        assertFalse("Redacted output still contains password: $redacted", redacted.contains("secret123"))
        assertTrue(redacted.contains("password"))
        assertTrue(redacted.contains("***"))
    }

    @Test
    fun redactJson_removesTokenField() {
        val input = """{"token":"abc-secret","action":"do"}"""
        val redacted = repository.redactJson(input)
        assertFalse("Redacted output still contains token: $redacted", redacted.contains("abc-secret"))
        assertTrue(redacted.contains("token"))
    }

    @Test
    fun redactJson_removesSecretField() {
        val input = """{"secret":"my-precious","name":"thing"}"""
        val redacted = repository.redactJson(input)
        assertFalse("Redacted output still contains secret: $redacted", redacted.contains("my-precious"))
        assertTrue(redacted.contains("secret"))
    }

    @Test
    fun redactJson_removesKeyField() {
        val input = """{"api_key":"sk-12345","url":"https://api"}"""
        val redacted = repository.redactJson(input)
        assertFalse("Redacted output still contains api_key: $redacted", redacted.contains("sk-12345"))
        assertTrue(redacted.contains("api_key"))
    }

    @Test
    fun redactJson_removesAuthField() {
        val input = """{"auth":"Bearer xyz","name":"test"}"""
        val redacted = repository.redactJson(input)
        assertFalse("Redacted output still contains auth: $redacted", redacted.contains("Bearer"))
    }

    @Test
    fun redactJson_redactsNestedObjects() {
        val input = """{"config":{"auth":{"token":"nested-secret"}}}"""
        val redacted = repository.redactJson(input)
        assertFalse("Redacted output still contains nested-secret: $redacted", redacted.contains("nested-secret"))
        assertTrue(redacted.contains("token"))
    }

    @Test
    fun redactJson_redactsNestedCredentialsInArray() {
        val input = """{"items":[{"name":"x","password":"p1"},{"name":"y","password":"p2"}]}"""
        val redacted = repository.redactJson(input)
        assertFalse("Redacted output still contains p1: $redacted", redacted.contains("p1"))
        assertFalse("Redacted output still contains p2: $redacted", redacted.contains("p2"))
    }

    @Test
    fun redactJson_doesNotAffectSafeKeys() {
        val input = """{"cmd":"ls -la","cwd":"/tmp","timeout":1000}"""
        val redacted = repository.redactJson(input)
        assertTrue(redacted.contains("ls -la"))
        assertTrue(redacted.contains("/tmp"))
        assertTrue(redacted.contains("1000"))
    }

    @Test
    fun redactPlainText_removesSensitivePatterns() {
        val input = "token=abc123 secretPassword=hide-me"
        val redacted = repository.redactPlainText(input)
        assertFalse("Redacted still contains abc123: $redacted", redacted.contains("abc123"))
        assertFalse("Redacted still contains hide-me: $redacted", redacted.contains("hide-me"))
    }

    @Test
    fun redactPlainText_keepsSafeContent() {
        val input = "Running command: ls -la /tmp"
        val redacted = repository.redactPlainText(input)
        assertTrue(redacted.contains("ls"))
        assertTrue(redacted.contains("/tmp"))
    }

    @Test
    fun record_redactedInputSummaryRoundTrips() {
        val entry = AuditEntry(
            id = "a1",
            toolName = "shell_execute",
            risk = RiskLevel(3),
            timestampMs = 1_000L,
            callerSessionId = "s1",
            approvalResult = "APPROVED",
            durationMs = 100L,
            success = true,
            errorCode = null,
            inputSummary = mapOf("cmd" to "curl -H 'Authorization: Bearer secret' example.com"),
        )
        repository.record(entry)
        val recorded = repository.list()[0]
        assertEquals("curl -H 'Authorization: ***' example.com", recorded.inputSummary["cmd"])
    }

    @Test
    fun listByIdSubstring_returnsMatchingEntry() {
        val entry = repository.recordForResult(
            toolName = "test_tool",
            risk = RiskLevel(0),
            callerSessionId = "s1",
            durationMs = 50L,
            success = true,
            inputSummary = "{}",
        )
        assertNotNull(repository.listByIdSubstring(entry.id.take(8)))
    }

    @Test
    fun entry_withErrorCodeStoresIt() {
        val entry = AuditEntry(
            id = "e1",
            toolName = "bad_tool",
            risk = RiskLevel(1),
            timestampMs = 1L,
            callerSessionId = "s1",
            approvalResult = "DENIED",
            durationMs = 0L,
            success = false,
            errorCode = "SHELL_REJECTED",
            inputSummary = emptyMap(),
        )
        repository.record(entry)
        assertEquals("SHELL_REJECTED", repository.list()[0].errorCode)
    }

    private fun auditEntry(id: String, toolName: String) = AuditEntry(
        id = id,
        toolName = toolName,
        risk = RiskLevel(0),
        timestampMs = 0L,
        callerSessionId = "s1",
        approvalResult = "AUTO",
        durationMs = 0L,
        success = true,
        errorCode = null,
        inputSummary = emptyMap(),
    )
}
