package dev.anilbeesetti.nextplayer.feature.player.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPanBoundsTest {
    @Test
    fun `unrotated video can only pan once it is zoomed in`() {
        val notZoomed = panBounds(WIDTH, HEIGHT, zoom = 1f, rotationDegrees = 0f)
        assertEquals(0f, notZoomed.x, DELTA)
        assertEquals(0f, notZoomed.y, DELTA)

        val zoomed = panBounds(WIDTH, HEIGHT, zoom = 2f, rotationDegrees = 0f)
        assertEquals(WIDTH / 2f, zoomed.x, DELTA)
        assertEquals(HEIGHT / 2f, zoomed.y, DELTA)
    }

    @Test
    fun `rotation keeps a pan range without zooming`() {
        val rotated = panBounds(WIDTH, HEIGHT, zoom = 1f, rotationDegrees = 45f)

        assertTrue("horizontal pan should stay available", rotated.x > 0f)
        assertTrue("vertical pan should stay available", rotated.y > 0f)
        assertEquals(30.3301f, rotated.x, 0.01f)
        assertEquals(280.3301f, rotated.y, 0.01f)
    }

    @Test
    fun `rotating by whole turns restores the unrotated bounds`() {
        val rotated = panBounds(WIDTH, HEIGHT, zoom = 1f, rotationDegrees = 180f)

        assertEquals(0f, rotated.x, DELTA)
        assertEquals(0f, rotated.y, DELTA)
    }

    @Test
    fun `bounds never go negative when the video is smaller than the viewport`() {
        val sideways = panBounds(WIDTH, HEIGHT, zoom = 1f, rotationDegrees = 90f)

        assertEquals(0f, sideways.x, DELTA)
        assertEquals((WIDTH - HEIGHT) / 2f, sideways.y, DELTA)
    }

    companion object {
        private const val WIDTH = 1000f
        private const val HEIGHT = 500f
        private const val DELTA = 0.001f
    }
}
