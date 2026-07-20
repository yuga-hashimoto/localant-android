package dev.localant.android.accessibility

class ProtectedPackagePolicy(
    additionalProtectedPackages: Set<String> = emptySet(),
) {
    private val exactPackages = DEFAULT_EXACT_PACKAGES + additionalProtectedPackages.map { it.lowercase() }

    fun isProtected(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return true
        val normalized = packageName.lowercase()
        if (normalized in exactPackages) return true
        return PROTECTED_HINTS.any { hint -> normalized.contains(hint) }
    }

    private companion object {
        val DEFAULT_EXACT_PACKAGES = setOf(
            "dev.localant.android",
            "com.google.android.apps.authenticator2",
            "com.azure.authenticator",
            "com.authy.authy",
            "com.google.android.apps.walletnfcrel",
            "com.x8bit.bitwarden",
            "com.bitwarden.app",
            "com.onepassword.android",
            "com.lastpass.lpandroid",
            "com.dashlane",
        )
        val PROTECTED_HINTS = setOf(
            "banking",
            ".bank.",
            "mobilebank",
            "wallet",
            "authenticator",
            "password",
            "bitwarden",
            "onepassword",
            "lastpass",
            "dashlane",
        )
    }
}
