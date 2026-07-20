package dev.localant.android.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommandGuardTest {

    companion object {
        private const val WS = "/data/local/tmp/workspace"
    }

    private lateinit var guard: DefaultCommandGuard

    @Before
    fun setUp() {
        guard = DefaultCommandGuard(WS)
    }

    // --- workspace root injection ---

    @Test
    fun constructor_acceptsWorkspaceRoot() {
        val g = DefaultCommandGuard("/foo")
        assertNotNull(g)
    }

    @Test
    fun safeNoPathCommand_allowed() {
        val result = guard.validate("ls -la")
        assertTrue(result.allowed)
    }

    @Test
    fun workspaceContainedRelativePath_allowed() {
        val result = guard.validate("cat file.txt")
        assertTrue(result.allowed)
    }

    @Test
    fun workspaceContainedAbsolutePath_allowed() {
        val result = guard.validate("cat $WS/file.txt")
        assertTrue(result.allowed)
    }

    @Test
    fun workspaceContainedRedirect_allowed() {
        val result = guard.validate("echo hello > $WS/out.txt")
        assertTrue(result.allowed)
    }

    // --- path rejection ---

    @Test
    fun absolutePathOutsideWorkspace_rejected() {
        val result = guard.validate("cat /etc/passwd")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun redirectOutsideWorkspace_rejected() {
        val result = guard.validate("echo hello > /etc/hosts")
        assertFalse("Redirect to absolute path outside workspace should be rejected", result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun redirectAppendOutsideWorkspace_rejected() {
        val result = guard.validate("echo hello >> /tmp/log")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun inputRedirectOutsideWorkspace_rejected() {
        val result = guard.validate("bash < /etc/config.sh")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun pathTraversalOutsideWorkspace_rejected() {
        val result = guard.validate("cat ../../../etc/passwd")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun pathTraversalWithRedirectOutsideWorkspace_rejected() {
        val result = guard.validate("echo hello > ../outside/file.txt")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    // --- quoted arguments ---

    @Test
    fun singleQuotedPathOutsideWorkspace_rejected() {
        val result = guard.validate("cat '/etc/passwd'")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun doubleQuotedPathOutsideWorkspace_rejected() {
        val result = guard.validate("cat \"/etc/passwd\"")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun singleQuotedPathWithinWorkspace_allowed() {
        val result = guard.validate("cat '$WS/file.txt'")
        assertTrue(result.allowed)
    }

    @Test
    fun doubleQuotedPathWithinWorkspace_allowed() {
        val result = guard.validate("cat \"$WS/file.txt\"")
        assertTrue(result.allowed)
    }

    // --- shell operators inspected ---

    @Test
    fun pipeWithPathOutsideWorkspace_rejected() {
        val result = guard.validate("cat /etc/shadow | grep root > $WS/out.txt")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun safePipeNoPaths_allowed() {
        val result = guard.validate("ls -la | grep foo")
        assertTrue(result.allowed)
    }

    @Test
    fun semicolonChainWithPathOutside_rejected() {
        val result = guard.validate("echo ok; cat /etc/hostname")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    // --- blocked command tokens still enforced ---

    @Test
    fun validate_emptyCommand_returnsRejected() {
        val result = guard.validate("")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun validate_blankCommand_returnsRejected() {
        val result = guard.validate("   ")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun validate_rebootCommand_rejected() {
        val result = guard.validate("reboot")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun validate_shutdownCommand_rejected() {
        val result = guard.validate("shutdown now")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun validate_rmRfRoot_rejected() {
        val result = guard.validate("rm -rf /")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun validate_mkfsCommand_rejected() {
        val result = guard.validate("mkfs.ext4 /dev/sda")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun validate_mountCommand_rejected() {
        val result = guard.validate("mount -o remount,rw /system")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun validate_ddBlockDevice_rejected() {
        val result = guard.validate("dd if=/dev/zero of=/dev/block/mmcblk0")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun validate_suCommand_rejected() {
        val result = guard.validate("su -c id")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun validate_setenforceCommand_rejected() {
        val result = guard.validate("setenforce 0")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun validate_pmInstall_rejected() {
        val result = guard.validate("pm install app.apk")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun validate_factoryReset_rejected() {
        val result = guard.validate("am broadcast -a android.intent.action.FACTORY_RESET")
        assertFalse(result.allowed)
        assertNotNull(result.message)
    }

    @Test
    fun validate_selfBlockedTokenInWord_rejected() {
        val result = guard.validate("some_reboot_script.sh")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun validate_commandWithBlockedTokenInArgument_rejected() {
        val result = guard.validate("echo reboot")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun guardResult_providesRejectionMessage() {
        val result = guard.validate("reboot")
        assertNotNull(result.message)
        assertTrue(result.message!!.isNotBlank())
    }

    @Test
    fun validate_commandWithSafeEnvironmentVariables_allowed() {
        val result = guard.validate("PATH=/usr/bin HOME=/tmp ls")
        assertTrue(result.allowed)
    }

    @Test
    fun validate_scriptWithDangerousContents_rejected() {
        val result = guard.validate("bash -c 'rm -rf /'")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    // --- shell expansion and redirect bypasses ---

    @Test
    fun parameterExpansion_rejected() {
        val result = guard.validate("cat \$TARGET")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun commandSubstitution_rejected() {
        val result = guard.validate("echo \$(id)")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun backticks_rejected() {
        val result = guard.validate("echo `id`")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun backslashEscapedCommandName_rejected() {
        val result = guard.validate("re\\boot")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun tildePath_rejected() {
        val result = guard.validate("cat ~/.ssh/config")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun unmatchedSingleQuote_rejected() {
        val result = guard.validate("echo 'unfinished")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun unmatchedDoubleQuote_rejected() {
        val result = guard.validate("echo \"unfinished")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun compactRedirectOutsideWorkspace_rejected() {
        val result = guard.validate("echo hello>/etc/hosts")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun compactAppendRedirectOutsideWorkspace_rejected() {
        val result = guard.validate("echo hello>>/tmp/out")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun fdPrefixedRedirect_rejected() {
        val result = guard.validate("echo hello 2>/etc/error")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun heredoc_rejected() {
        val result = guard.validate("cat <<EOF")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun hereString_rejected() {
        val result = guard.validate("cat <<<hello")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun symbolicLinkCreation_rejected() {
        val result = guard.validate("ln -s /etc/passwd link")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun hardLinkCreation_rejected() {
        val result = guard.validate("link /etc/passwd link")
        assertFalse(result.allowed)
        assertEquals(CommandGuard.SHELL_REJECTED, result.code)
    }

    @Test
    fun safeQuotedLiteral_allowed() {
        val result = guard.validate("echo 'hello world'")
        assertTrue(result.allowed)
    }

    @Test
    fun compactWorkspaceRedirect_allowed() {
        val result = guard.validate("echo hello>$WS/out.txt")
        assertTrue(result.allowed)
    }

    // --- resource constants preserved ---

    @Test
    fun maxTimeoutMs_is60Seconds() {
        assertEquals(60_000L, CommandGuard.MAX_TIMEOUT_MS)
    }

    @Test
    fun maxOutputBytes_is512KiB() {
        assertEquals(512 * 1024, CommandGuard.MAX_OUTPUT_BYTES)
    }

    @Test
    fun maxConcurrentProcesses_is2() {
        assertEquals(2, CommandGuard.MAX_CONCURRENT_PROCESSES)
    }
}
