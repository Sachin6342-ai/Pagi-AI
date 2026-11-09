package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.JarvisBrainService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Pagi AI", appName)
    }

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
}
