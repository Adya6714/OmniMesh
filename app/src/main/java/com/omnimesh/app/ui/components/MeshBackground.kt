package omnimesh.command1.ui.components

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.isActive

private class MeshPhysicsNode(
    var position: Offset,
    var velocity: Offset,
    val baseSpeed: Float,
    val baseOpacity: Float,
    var isTouched: Boolean = false,
)

/**
 * Interactive mesh backdrop: drifting nodes, distance-faded edges, touch attraction,
 * and brighter nodes near the finger. Capped by heap budget so low-RAM devices stay smooth.
 */
@Composable
fun MeshBackground(
    modifier: Modifier = Modifier,
    /** Soft cap before heap tier scaling; low-RAM devices reduce further. */
    nodeCount: Int = 28,
    connectionDistancePx: Float = 250f,
    nodeColor: Color = Color(0xFF4285F4),
    edgeColor: Color = Color(0xFF4285F4),
    nodeAlpha: Float = 0.18f,
    edgeAlpha: Float = 0.07f,
    /** Finger attraction radius — larger feels closer to “touch pulls the mesh”. */
    interactionRadiusPx: Float = 300f,
    edgeStrokeWidthPx: Float = 2f,
) {
    val effectiveNodeCount = remember(nodeCount) {
        val maxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        when {
            maxMb < 128 -> min(nodeCount, 15)
            maxMb < 256 -> (nodeCount * 0.66f).toInt().coerceAtLeast(8)
            else -> nodeCount
        }
    }

    var size by remember { mutableStateOf(IntSize.Zero) }
    var touchPoint by remember { mutableStateOf<Offset?>(null) }

    val nodes = remember(effectiveNodeCount) {
        MutableList(effectiveNodeCount) {
            MeshPhysicsNode(
                position = Offset.Zero,
                velocity = Offset(
                    x = Random.nextFloat() * 2f - 1f,
                    y = Random.nextFloat() * 2f - 1f,
                ).let { v ->
                    val len = hypot(v.x.toDouble(), v.y.toDouble()).toFloat().coerceAtLeast(0.01f)
                    v / len * (Random.nextFloat() * 0.45f + 0.35f)
                },
                baseSpeed = Random.nextFloat() * 0.35f + 0.45f,
                baseOpacity = 0.35f + Random.nextFloat() * 0.55f,
            )
        }
    }

    var initialized by remember(effectiveNodeCount) { mutableStateOf(false) }

    var lastFrameMs by remember { mutableLongStateOf(-1L) }
    var frameTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(effectiveNodeCount) {
        initialized = false
        lastFrameMs = -1L
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            if (size.width <= 0 || size.height <= 0) {
                withInfiniteAnimationFrameMillis { /* sync to vsync until sized */ }
                continue
            }

            withInfiniteAnimationFrameMillis { frameMs ->
                val dtMs =
                    if (lastFrameMs < 0L) {
                        16f
                    } else {
                        (frameMs - lastFrameMs).toFloat().coerceIn(4f, 48f)
                    }
                lastFrameMs = frameMs
                val dtNorm = dtMs / 16f

                val w = size.width.toFloat()
                val h = size.height.toFloat()

                if (!initialized) {
                    nodes.forEach { node ->
                        node.position = Offset(
                            Random.nextFloat() * w.coerceAtLeast(1f),
                            Random.nextFloat() * h.coerceAtLeast(1f),
                        )
                    }
                    initialized = true
                }

                val touch = touchPoint
                val touchRadius = interactionRadiusPx.coerceAtLeast(40f)

                nodes.forEach { node ->
                    if (touch != null) {
                        val dx = touch.x - node.position.x
                        val dy = touch.y - node.position.y
                        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        if (dist < touchRadius && dist > 2f) {
                            node.isTouched = true
                            val falloff = (1f - dist / touchRadius).coerceIn(0f, 1f)
                            val pull = falloff * 0.11f * dtNorm
                            node.velocity += Offset(dx / dist * pull * touchRadius * 0.18f, dy / dist * pull * touchRadius * 0.18f)
                        } else {
                            node.isTouched = false
                        }
                    } else {
                        node.isTouched = false
                    }

                    val speed = hypot(node.velocity.x.toDouble(), node.velocity.y.toDouble()).toFloat()
                    val damping = (1f - 0.022f * dtNorm).coerceIn(0.94f, 0.999f)
                    node.velocity = node.velocity * damping

                    if (speed > node.baseSpeed * 1.15f) {
                        node.velocity = node.velocity * 0.985f
                    } else if (speed < node.baseSpeed * 0.35f && speed > 0.001f) {
                        node.velocity = node.velocity * (1f + 0.018f * dtNorm)
                    }

                    node.velocity += Offset(
                        (Random.nextFloat() - 0.5f) * 0.018f * dtNorm,
                        (Random.nextFloat() - 0.5f) * 0.018f * dtNorm,
                    )

                    node.position += node.velocity * dtNorm

                    val pad = 6f
                    if (node.position.x < pad) {
                        node.velocity = node.velocity.copy(x = abs(node.velocity.x))
                        node.position = node.position.copy(x = pad)
                    }
                    if (node.position.x > w - pad) {
                        node.velocity = node.velocity.copy(x = -abs(node.velocity.x))
                        node.position = node.position.copy(x = (w - pad).coerceAtLeast(pad))
                    }
                    if (node.position.y < pad) {
                        node.velocity = node.velocity.copy(y = abs(node.velocity.y))
                        node.position = node.position.copy(y = pad)
                    }
                    if (node.position.y > h - pad) {
                        node.velocity = node.velocity.copy(y = -abs(node.velocity.y))
                        node.position = node.position.copy(y = (h - pad).coerceAtLeast(pad))
                    }

                    val cap = node.baseSpeed * 5f
                    val sp = hypot(node.velocity.x.toDouble(), node.velocity.y.toDouble()).toFloat()
                    if (sp > cap && sp > 0f) {
                        node.velocity = node.velocity / sp * cap
                    }
                }

                frameTick++
            }
        }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    touchPoint = down.position
                    drag(down.id) { change ->
                        touchPoint = change.position
                    }
                    touchPoint = null
                }
            },
    ) {
        @Suppress("UNUSED_VARIABLE")
        val redrawTick = frameTick

        if (!initialized || nodes.isEmpty()) return@Canvas
        val threshold = connectionDistancePx.coerceAtLeast(80f)

        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val a = nodes[i]
                val b = nodes[j]
                val dx = a.position.x - b.position.x
                val dy = a.position.y - b.position.y
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (dist < threshold && dist > 0.5f) {
                    val fade = (1f - dist / threshold).coerceIn(0f, 1f)
                    val blend = (a.baseOpacity + b.baseOpacity) * 0.5f
                    drawLine(
                        color = edgeColor.copy(alpha = edgeAlpha * fade * blend),
                        start = a.position,
                        end = b.position,
                        strokeWidth = edgeStrokeWidthPx,
                    )
                }
            }
        }

        val touch = touchPoint
        nodes.forEach { node ->
            val nearTouch =
                touch != null &&
                    hypot(
                        (touch.x - node.position.x).toDouble(),
                        (touch.y - node.position.y).toDouble(),
                    ) < interactionRadiusPx.toDouble()
            val brightness = when {
                node.isTouched -> 2.45f
                nearTouch -> 1.55f
                else -> 1f
            }
            val alphaMul = (nodeAlpha * node.baseOpacity * brightness).coerceIn(0f, 1f)
            drawCircle(
                color = nodeColor.copy(alpha = alphaMul * 0.42f),
                radius = 7f * brightness.coerceAtMost(2.8f),
                center = node.position,
            )
            drawCircle(
                color = nodeColor.copy(alpha = alphaMul),
                radius = 3f * brightness.coerceAtMost(2.6f),
                center = node.position,
            )
        }
    }
}
