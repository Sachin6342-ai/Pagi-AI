package com.example

import com.example.ai.GeminiErrorCategory
import com.example.ai.JarvisBrainService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ExampleRobolectricTest {

    @Test
    fun `offline command fallback parses multi-command and arrival message`() {
        val brain = JarvisBrainService()
        val msgResult = brain.buildOfflineFallbackResult("Send Rahul a WhatsApp message that I will arrive in 15 minutes")
        assertEquals(1, msgResult.actions.size)
        assertEquals("SEND_WHATSAPP", msgResult.actions[0].actionType)
        assertEquals("Rahul", msgResult.actions[0].targetName)
        assertTrue(msgResult.actions[0].requiresConfirmation)

        val multiResult = brain.buildOfflineFallbackResult("Turn on flashlight and check battery")
        assertTrue(multiResult.actions.any { it.actionType == "FLASHLIGHT_ON" })
        assertTrue(multiResult.actions.any { it.actionType == "CHECK_BATTERY" })
    }

    @Test
    fun `offline command fallback surfaces specific diagnostic error when cloud fails`() {
        val brain = JarvisBrainService()
        val diagnostic = "Firebase is not initialized because app/google-services.json was missing when the APK was built."
        val fallback = brain.buildOfflineFallbackResult("Explain quantum computing", diagnostic)
        assertEquals(diagnostic, fallback.spokenReply)
        assertTrue(fallback.detailedReply.contains(diagnostic))
    }

    @Test
    fun `classifyException returns actionable categories for network, timeout, auth, quota, and server errors`() {
        val brain = JarvisBrainService()

        val timeoutErr = brain.classifyException(SocketTimeoutException("Read timed out"))
        assertEquals(GeminiErrorCategory.TIMEOUT, timeoutErr.category)
        assertTrue(timeoutErr.userFriendlyMessage.contains("timed out", ignoreCase = true))

        val netErr = brain.classifyException(UnknownHostException("Unable to resolve host"))
        assertEquals(GeminiErrorCategory.NETWORK_CONNECTION, netErr.category)

        val authErr = brain.classifyException(IllegalStateException("HTTP 403 PERMISSION_DENIED: API_KEY_INVALID"))
        assertEquals(GeminiErrorCategory.INVALID_API_KEY_OR_AUTH, authErr.category)

        val quotaErr = brain.classifyException(IllegalStateException("HTTP 429 RESOURCE_EXHAUSTED: Quota exceeded"))
        assertEquals(GeminiErrorCategory.QUOTA_EXCEEDED, quotaErr.category)

        val serverErr = brain.classifyException(IllegalStateException("HTTP 503 Service Unavailable"))
        assertEquals(GeminiErrorCategory.SERVER_ERROR, serverErr.category)

        val firebaseMissingErr = brain.classifyException(
            IllegalStateException("Default FirebaseApp is not initialized in this process. Make sure to call FirebaseApp.initializeApp(Context) first.")
        )
        assertEquals(GeminiErrorCategory.FIREBASE_CONFIG_MISSING, firebaseMissingErr.category)
    }
}


