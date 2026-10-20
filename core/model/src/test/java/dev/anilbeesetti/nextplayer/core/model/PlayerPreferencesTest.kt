package dev.anilbeesetti.nextplayer.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPreferencesTest {
    @Test
    fun `existing preferences keep tunneled playback disabled`() {
        val preferences = Json.decodeFromString<PlayerPreferences>("""{"autoplay":false,"preferredAudioLanguage":"en"}""")

        assertFalse(preferences.enableTunneledPlayback)
        assertFalse(preferences.autoplay)
        assertEquals("en", preferences.preferredAudioLanguage)
    }

    @Test
    fun `tunneled playback survives serialization`() {
        val preferences = PlayerPreferences(enableTunneledPlayback = true)
        val restored = Json.decodeFromString<PlayerPreferences>(Json.encodeToString(preferences))

        assertTrue(restored.enableTunneledPlayback)
        assertEquals(preferences, restored)
    }

    @Test
    fun `rotate gesture stays opt in for existing preferences`() {
        val preferences = Json.decodeFromString<PlayerPreferences>("""{"useZoomControls":true}""")

        assertFalse(preferences.enableRotateGesture)
    }

    @Test
    fun `rotate gesture survives serialization`() {
        val preferences = PlayerPreferences(enableRotateGesture = true)
        val restored = Json.decodeFromString<PlayerPreferences>(Json.encodeToString(preferences))

        assertTrue(restored.enableRotateGesture)
        assertEquals(preferences, restored)
    }
}
