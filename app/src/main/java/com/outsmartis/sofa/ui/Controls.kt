package com.outsmartis.sofa.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.outsmartis.sofa.tv.Keys
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Sends keys and gives the press a little haptic tick. */
class Presser(val key: (code: Int, direction: Int) -> Unit, val haptic: () -> Unit) {
    fun tap(code: Int) {
        haptic()
        key(code, Keys.SHORT)
    }
}

@Composable
fun rememberPresser(haptics: Boolean, key: (Int, Int) -> Unit): Presser {
    val view = LocalView.current
    val k by rememberUpdatedState(key)
    return remember(haptics) {
        Presser({ c, d -> k(c, d) }) { if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    }
}

/**
 * A button that repeats while held (arrows, volume), or sends a long press
 * (START_LONG / END_LONG) when [longPress] is set, like holding OK on the real remote.
 */
fun Modifier.remoteKey(presser: Presser, code: Int, repeat: Boolean = false, longPress: Boolean = false, onPressed: (Boolean) -> Unit = {}): Modifier =
    pointerInput(code, repeat, longPress) {
        coroutineScope {
            awaitEachGesture {
                awaitFirstDown()
                onPressed(true)
                presser.haptic()
                val start = System.currentTimeMillis()
                var longSent = false
                val job = if (repeat || longPress) {
                    launch {
                        delay(if (repeat) 380 else 550)
                        if (repeat) {
                            while (true) {
                                presser.key(code, Keys.SHORT)
                                delay(110)
                            }
                        } else {
                            longSent = true
                            presser.haptic()
                            presser.key(code, Keys.START_LONG)
                        }
                    }
                } else null
                waitForUpOrCancellation()
                job?.cancel()
                onPressed(false)
                when {
                    longSent -> presser.key(code, Keys.END_LONG)
                    repeat && System.currentTimeMillis() - start >= 380 -> Unit
                    else -> presser.key(code, Keys.SHORT)
                }
            }
        }
    }

@Composable
fun KeyButton(
    presser: Presser,
    code: Int,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    color: Color = Surface2,
    size: Dp = 56.dp,
    repeat: Boolean = false,
    longPress: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(if (pressed) Surface3 else color)
            .remoteKey(presser, code, repeat, longPress) { pressed = it },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun KeyIcon(presser: Presser, code: Int, icon: ImageVector, label: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface, size: Dp = 56.dp, repeat: Boolean = false) {
    KeyButton(presser, code, modifier, size = size, repeat = repeat) {
        Icon(icon, contentDescription = label, tint = tint)
    }
}

@Composable
fun DPad(presser: Presser, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(1f).clip(CircleShape).background(Surface1)) {
        val arrow = Modifier.size(84.dp)
        KeyButton(presser, Keys.UP, arrow.align(Alignment.TopCenter), color = Color.Transparent, size = 84.dp, repeat = true) {
            Icon(Icons.Rounded.KeyboardArrowUp, "Up", Modifier.size(36.dp), tint = TextDim)
        }
        KeyButton(presser, Keys.DOWN, arrow.align(Alignment.BottomCenter), color = Color.Transparent, size = 84.dp, repeat = true) {
            Icon(Icons.Rounded.KeyboardArrowDown, "Down", Modifier.size(36.dp), tint = TextDim)
        }
        KeyButton(presser, Keys.LEFT, arrow.align(Alignment.CenterStart), color = Color.Transparent, size = 84.dp, repeat = true) {
            Icon(Icons.Rounded.KeyboardArrowLeft, "Left", Modifier.size(36.dp), tint = TextDim)
        }
        KeyButton(presser, Keys.RIGHT, arrow.align(Alignment.CenterEnd), color = Color.Transparent, size = 84.dp, repeat = true) {
            Icon(Icons.Rounded.KeyboardArrowRight, "Right", Modifier.size(36.dp), tint = TextDim)
        }
        KeyButton(presser, Keys.CENTER, Modifier.align(Alignment.Center), color = Surface2, size = 104.dp, longPress = true) {
            Text("OK", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        }
    }
}

/** Swipe to move focus, tap for OK, long-press for the OK long press. Faster swipes move further. */
@Composable
fun Touchpad(presser: Presser, modifier: Modifier = Modifier) {
    val step = 70f
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(28.dp))
            .background(Surface1)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { presser.tap(Keys.CENTER) },
                    onLongPress = {
                        presser.haptic()
                        presser.key(Keys.CENTER, Keys.START_LONG)
                        presser.key(Keys.CENTER, Keys.END_LONG)
                    },
                )
            }
            .pointerInput(Unit) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(onDragStart = { dx = 0f; dy = 0f }) { change, drag ->
                    change.consume()
                    dx += drag.x
                    dy += drag.y
                    while (abs(dx) >= step && abs(dx) > abs(dy)) {
                        presser.tap(if (dx > 0) Keys.RIGHT else Keys.LEFT)
                        dx -= if (dx > 0) step else -step
                        dy = 0f
                    }
                    while (abs(dy) >= step && abs(dy) >= abs(dx)) {
                        presser.tap(if (dy > 0) Keys.DOWN else Keys.UP)
                        dy -= if (dy > 0) step else -step
                        dx = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("Swipe to move · tap for OK", color = TextDim, fontSize = 13.sp)
    }
}

/** Tall pill with + and − (volume, channel). */
@Composable
fun Rocker(presser: Presser, up: Int, down: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.width(64.dp).clip(RoundedCornerShape(32.dp)).background(Surface2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        KeyButton(presser, up, color = Color.Transparent, size = 64.dp, repeat = true) { Text("+", fontSize = 26.sp) }
        Text(label, color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
        KeyButton(presser, down, color = Color.Transparent, size = 64.dp, repeat = true) { Text("−", fontSize = 26.sp) }
    }
}
