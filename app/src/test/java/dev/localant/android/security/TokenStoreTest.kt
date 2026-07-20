package dev.localant.android.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenStoreTest {

    private class InMemoryTokenBackend : TokenBackend {
        private var stored: String? = null
        override fun read(): String? = stored
        override fun write(token: String) { stored = token }
    }

    @Test
    fun current_returnsNullWhenNothingStored() {
        val store = TokenStore(InMemoryTokenBackend())
        assertNull(store.current())
    }

    @Test
    fun rotate_storesTokenAndReturnsIt() {
        val store = TokenStore(InMemoryTokenBackend())
        val token = store.rotate()
        assertNotNull(token)
        assertEquals(token, store.current())
    }

    @Test
    fun rotate_generatesAtLeast43Base64urlChars() {
        val store = TokenStore(InMemoryTokenBackend())
        val token = store.rotate()
        assertTrue("Token too short: ${token.length}", token.length >= 43)
    }

    @Test
    fun rotate_generatesTokenWithoutPadding() {
        val store = TokenStore(InMemoryTokenBackend())
        val token = store.rotate()
        assertEquals("Token must not contain padding '='", false, token.contains("="))
    }

    @Test
    fun rotate_generatesTokenWithOnlyBase64urlChars() {
        val store = TokenStore(InMemoryTokenBackend())
        val token = store.rotate()
        val valid = token.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }
        assertTrue("Token contains invalid chars: $token", valid)
    }

    @Test
    fun rotate_generatesUniqueTokens() {
        val store = TokenStore(InMemoryTokenBackend())
        val tokens = (1..10).map { store.rotate() }.toSet()
        assertEquals(10, tokens.size)
    }

    @Test
    fun current_returnsLastRotatedToken() {
        val store = TokenStore(InMemoryTokenBackend())
        val first = store.rotate()
        val second = store.rotate()
        assertEquals(second, store.current())
        assertTrue(first != second)
    }
}
