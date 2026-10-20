package dev.anilbeesetti.nextplayer.feature.player.state

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoRotationTest {
    @Test
    fun `rotation is wrapped into a single turn`() {
        assertEquals(10f, normalizeRotation(10f), DELTA)
        assertEquals(350f, normalizeRotation(-10f), DELTA)
        assertEquals(0f, normalizeRotation(360f), DELTA)
        assertEquals(45f, normalizeRotation(405f), DELTA)
    }

    @Test
    fun `rotation snaps to the nearest right angle within the threshold`() {
        assertEquals(0f, snapRotationToRightAngle(3f), DELTA)
        assertEquals(0f, snapRotationToRightAngle(357f), DELTA)
        assertEquals(90f, snapRotationToRightAngle(87f), DELTA)
        assertEquals(180f, snapRotationToRightAngle(184f), DELTA)
        assertEquals(270f, snapRotationToRightAngle(266f), DELTA)
        assertEquals(0f, snapRotationToRightAngle(0f), DELTA)
    }

    @Test
    fun `rotation between right angles is kept as is`() {
        assertEquals(45f, snapRotationToRightAngle(45f), DELTA)
        assertEquals(84f, snapRotationToRightAngle(84f), DELTA)
        assertEquals(100f, snapRotationToRightAngle(100f), DELTA)
    }

    companion object {
        private const val DELTA = 0.001f
    }
}
