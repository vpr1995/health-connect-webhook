package com.hcwebhook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebhookManagerTest {

    @Test
    fun buildAuthHeader_returnsBearerToken_whenTokenPresent() {
        assertEquals("Bearer token123", WebhookManager.buildAuthHeader("token123"))
    }

    @Test
    fun buildAuthHeader_returnsNull_whenTokenMissing() {
        assertNull(WebhookManager.buildAuthHeader("   "))
        assertNull(WebhookManager.buildAuthHeader(null))
    }
}
