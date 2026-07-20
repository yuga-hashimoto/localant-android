package dev.localant.android.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedPackagePolicyTest {
    private val policy = ProtectedPackagePolicy()

    @Test
    fun localAntItself_isProtected() {
        assertTrue(policy.isProtected("dev.localant.android"))
    }

    @Test
    fun authenticatorWalletAndPasswordManagers_areProtected() {
        assertTrue(policy.isProtected("com.google.android.apps.authenticator2"))
        assertTrue(policy.isProtected("com.google.android.apps.walletnfcrel"))
        assertTrue(policy.isProtected("com.x8bit.bitwarden"))
        assertTrue(policy.isProtected("com.example.mobilebanking"))
    }

    @Test
    fun ordinaryApps_areNotProtected() {
        assertFalse(policy.isProtected("com.android.settings"))
        assertFalse(policy.isProtected("com.example.notes"))
    }

    @Test
    fun customProtectedPackage_isHonored() {
        val custom = ProtectedPackagePolicy(setOf("com.example.private"))
        assertTrue(custom.isProtected("com.example.private"))
    }
}
