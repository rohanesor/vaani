package com.vokie.cloud

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureCredentialStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var store: SecureCredentialStore

    @Before
    fun setUp() {
        store = SecureCredentialStore(context)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun storesAndReplacesTokenWithoutPlaintextPreferenceValue() {
        store.saveToken("header.payload.signature")
        assertEquals("header.payload.signature", store.token())

        store.saveToken("replacement.token.value")
        assertEquals("replacement.token.value", store.token())

        val raw = context.getSharedPreferences("vaani_secure_credentials", android.content.Context.MODE_PRIVATE)
            .getString("cloud_bearer_token", "") ?: ""
        assertTrue(raw.isNotEmpty())
        assertTrue(!raw.contains("replacement.token.value"))
    }

    @Test
    fun clearAndMissingCredentialReturnNull() {
        assertNull(store.token())
        store.saveToken("token")
        store.clear()
        assertNull(store.token())
    }

    @Test
    fun corruptedCiphertextFailsClosed() {
        context.getSharedPreferences("vaani_secure_credentials", android.content.Context.MODE_PRIVATE)
            .edit().putString("cloud_bearer_token", "not-valid-ciphertext").commit()
        assertNull(store.token())
    }

    @Test
    fun emptyTokenIsRejected() {
        try {
            store.saveToken("")
            throw AssertionError("empty credential was accepted")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
