package com.vokie.cloud

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudSyncAccountIsolationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun syncOptInIsExplicitAndSessionGenerationRotates() {
        val prefs = CloudSyncPreferences(context)
        prefs.setEnabled(false)
        val before = prefs.sessionGeneration
        assertFalse(prefs.enabled.value)

        prefs.rotateSession()
        val after = prefs.sessionGeneration
        assertNotEquals(before, after)

        prefs.setEnabled(true)
        assertTrue(prefs.enabled.value)
        prefs.setEnabled(false)
        assertFalse(prefs.enabled.value)
    }
}
