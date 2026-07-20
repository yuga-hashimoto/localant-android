package dev.localant.android.security

interface TokenBackend {
    fun read(): String?
    fun write(token: String)
}
