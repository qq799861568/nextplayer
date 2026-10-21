package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Constraints
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.feature.player.extensions.copy
import dev.anilbeesetti.nextplayer.feature.player.extensions.next
import dev.anilbeesetti.nextplayer.feature.player.extensions.videoRotation
import dev.anilbeesetti.nextplayer.feature.player.extensions.videoZoom
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FULL_ROTATION_DEGREES = 360f
private const val QUARTER_ROTATION_DEGREES = 90f
private const val ROTATION_SNAP_THRESHOLD_DEGREES = 5f

/** Wraps any rotation in degrees into the `0f..<360f` range. */
internal fun normalizeRotation(degrees: Float): Float = ((degrees % FULL_ROTATION_DEGREES) + FULL_ROTATION_DEGREES) % FULL_ROTATION_DEGREES

/** Snaps a rotation to the nearest right angle when it is within [ROTATION_SNAP_THRESHOLD_DEGREES] of it. */
internal fun snapRotationToRightAngle(degrees: Float): Float {
    val nearestRightAngle = (degrees / QUARTER_ROTATION_DEGREES).roundToInt() * QUARTER_ROTATION_DEGREES
    val distance = abs(normalizeRotation(degrees) - normalizeRotation(nearestRightAngle))
    val shortestDistance = minOf(distance, FULL_ROTATION_DEGREES - distance)
    return if (shortestDistance <= ROTATION_SNAP_THRESHOLD_DEGREES) normalizeRotation(nearestRightAngle) else degrees
}

/**
 * How far the zoomed video may be panned before its edge leaves the viewport.
 *
 * The transformed video is bounded by its rotated bounding box, so a turned video keeps a usable pan
 * range instead of being pinned to the center.
 */
internal fun panBounds(
    width: Float,
    height: Float,
    zoom: Float,
    rotationDegrees: Float,
): Offset {
    val radians = rotationDegrees * PI.toFloat() / 180f
    val cos = abs(cos(radians))
    val sin = abs(sin(radians))

    val boundsWidth = zoom * (width * cos + height * sin)
    val boundsHeight = zoom * (height * cos + width * sin)

    return Offset(
        x = max(0f, (boundsWidth - width) / 2),
        y = max(0f, (boundsHeight - height) / 2),
    )
}

@UnstableApi
@Composable
fun rememberVideoZoomAndContentScaleState(
    player: Player,
    initialContentScale: VideoContentScale,
    enableZoomGesture: Boolean,
    enablePanGesture: Boolean,
    enableRotateGesture: Boolean,
    onEvent: (VideoZoomEvent) -> Unit = {},
): VideoZoomAndContentScaleState {
    val coroutineScope = rememberCoroutineScope()
    val videoZoomAndContentScaleState = remember {
        VideoZoomAndContentScaleState(
            player = player,
            initialContentScale = initialContentScale,
            enableZoomGesture = enableZoomGesture,
            enablePanGesture = enablePanGesture,
            enableRotateGesture = enableRotateGesture,
            onEvent = onEvent,
            coroutineScope = coroutineScope,
        )
    }
    LaunchedEffect(player) { videoZoomAndContentScaleState.observe() }
    return videoZoomAndContentScaleState
}

@Stable
class VideoZoomAndContentScaleState(
    private val player: Player,
    initialContentScale: VideoContentScale,
    private val enableZoomGesture: Boolean = true,
    private val enablePanGesture: Boolean = true,
    private val enableRotateGesture: Boolean = true,
    private val onEvent: (VideoZoomEvent) -> Unit,
    private val coroutineScope: CoroutineScope,
) {
    companion object Companion {
        private const val MIN_ZOOM = 0.25f
        private const val MAX_ZOOM = 4f
        private const val CONTENT_SCALE_INDICATOR_DURATION_MS = 1000L
    }

    var videoContentScale: VideoContentScale by mutableStateOf(initialContentScale)
        private set

    var zoom: Float by mutableFloatStateOf(1f)
        private set

    var offset: Offset by mutableStateOf(Offset.Zero)
        private set

    var rotation: Float by mutableFloatStateOf(0f)
        private set

    var isZooming: Boolean by mutableStateOf(false)
        private set

    var showContentScaleIndicator: Boolean by mutableStateOf(false)
        private set

    private var showContentScaleJob: Job? = null

    private var lastConstraints: Constraints? = null

    fun onVideoContentScaleChanged(newContentScale: VideoContentScale) {
        videoContentScale = newContentScale
        zoom = 1f
        offset = Offset.Zero
        onEvent(VideoZoomEvent.ContentScaleChanged(videoContentScale))
        updateVideoScaleMetadataAndSendEvent()
        showContentScaleIndicator()
    }

    private fun showContentScaleIndicator() {
        showContentScaleJob?.cancel()
        showContentScaleIndicator = true
        showContentScaleJob = coroutineScope.launch {
            delay(CONTENT_SCALE_INDICATOR_DURATION_MS)
            showContentScaleIndicator = false
            showContentScaleJob = null
        }
    }

    fun switchToNextVideoContentScale() {
        onVideoContentScaleChanged(videoContentScale.next())
    }

    fun onZoomPanGesture(
        constraints: Constraints,
        panChange: Offset,
        zoomChange: Float,
        rotationChange: Float = 0f,
    ) {
        if (player.duration == C.TIME_UNSET) return
        if (!enableZoomGesture) return

        isZooming = true
        lastConstraints = constraints

        if (enableRotateGesture && rotationChange != 0f) {
            rotation = normalizeRotation(rotation + rotationChange)
        }

        zoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)

        if (enablePanGesture) {
            offset = Offset(
                x = offset.x + zoom * panChange.x,
                y = offset.y + zoom * panChange.y,
            )
            clampOffsetToPanBounds(constraints)
        }
    }

    fun onZoomPanGestureEnd() {
        isZooming = false
        rotation = snapRotationToRightAngle(rotation)
        // Snapping and un-rotating both shrink the pan range, so a stale offset could leave the video
        // off center with no way to drag it back.
        lastConstraints?.let(::clampOffsetToPanBounds)
        updateVideoScaleMetadataAndSendEvent()
    }

    private fun clampOffsetToPanBounds(constraints: Constraints) {
        val bounds = panBounds(
            width = constraints.maxWidth.toFloat(),
            height = constraints.maxHeight.toFloat(),
            zoom = zoom,
            rotationDegrees = rotation,
        )
        offset = Offset(
            x = offset.x.coerceIn(-bounds.x, bounds.x),
            y = offset.y.coerceIn(-bounds.y, bounds.y),
        )
    }

    suspend fun observe() {
        zoom = player.currentMediaItem?.mediaMetadata?.videoZoom ?: 1f
        rotation = storedRotation()
        player.listen { events ->
            if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
                zoom = player.currentMediaItem?.mediaMetadata?.videoZoom ?: 1f
                rotation = storedRotation()
            }
        }
    }

    /** A stored rotation only applies while the rotate gesture is enabled, so turning it off restores the video. */
    private fun storedRotation(): Float {
        if (!enableRotateGesture) return 0f
        return player.currentMediaItem?.mediaMetadata?.videoRotation ?: 0f
    }

    private fun updateVideoScaleMetadataAndSendEvent(zoom: Float = this.zoom) {
        val currentMediaItem = player.currentMediaItem ?: return
        player.replaceMediaItem(
            player.currentMediaItemIndex,
            currentMediaItem.copy(videoZoom = zoom, videoRotation = rotation),
        )
        onEvent(VideoZoomEvent.ZoomChanged(currentMediaItem, zoom))
    }
}

sealed interface VideoZoomEvent {
    data class ContentScaleChanged(val contentScale: VideoContentScale) : VideoZoomEvent
    data class ZoomChanged(val mediaItem: MediaItem, val zoom: Float) : VideoZoomEvent
}
