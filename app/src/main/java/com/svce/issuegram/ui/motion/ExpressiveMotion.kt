// Expressive Kit motion recipes for Jetpack Compose. Hand-written; see docs/motion.md for when to use each.
// Requires androidx.compose.material3 1.4+ (MotionScheme) and androidx.activity:activity-compose 1.8+
// (PredictiveBackHandler). Everything reads springs from MaterialTheme.motionScheme, so it follows
// whichever scheme ExpressiveTheme installs, and drops movement when the system turns animations off.
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.svce.issuegram.ui.motion

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.FontRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Reduced motion
// ---------------------------------------------------------------------------------------------

/**
 * True when the person has turned animations off (Settings > Accessibility > Remove animations,
 * which sets the animator duration scale to 0). Drop movement, bounce, rotation and ambient loops;
 * keep short fades.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

// ---------------------------------------------------------------------------------------------
// Haptics
// ---------------------------------------------------------------------------------------------

/** Semantic haptic events. Map product moments onto these, never onto raw constants. */
enum class ExpressiveHaptic {
    /** A value passing a detent: dial steps, picker notches, a drum graduation. */
    Tick,
    /** A light acknowledgement: keypad digits, a segment changing. */
    Tap,
    /** Something finished successfully: reset, save, an alarm dismissed. */
    Confirm,
    /** Something was refused: an invalid entry, a limit reached. */
    Reject,
    /** Entering a mode by long press: selection mode, drag to reorder. */
    LongPress,
    /** A process started: a timer or stopwatch running. */
    Start,
    /** A process stopped or paused. */
    Stop,
}

fun View.expressiveHaptic(kind: ExpressiveHaptic) {
    val r = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    val constant = when (kind) {
        ExpressiveHaptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
        ExpressiveHaptic.Tap -> HapticFeedbackConstants.KEYBOARD_TAP
        ExpressiveHaptic.LongPress -> HapticFeedbackConstants.LONG_PRESS
        ExpressiveHaptic.Confirm -> if (r) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        ExpressiveHaptic.Reject -> if (r) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        ExpressiveHaptic.Start -> if (r) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.KEYBOARD_TAP
        ExpressiveHaptic.Stop -> if (r) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.KEYBOARD_TAP
    }
    performHapticFeedback(constant)
}

/** `val haptic = rememberHaptics(); haptic(ExpressiveHaptic.Tick)` */
@Composable
fun rememberHaptics(): (ExpressiveHaptic) -> Unit {
    val view = LocalView.current
    return remember(view) { { kind -> view.expressiveHaptic(kind) } }
}

// ---------------------------------------------------------------------------------------------
// State-change pulse
// ---------------------------------------------------------------------------------------------

/**
 * A short squash-and-settle whenever [key] changes, e.g. an alarm card switching on, a stopwatch
 * face starting. Skips the first composition so nothing pulses on screen entry.
 */
@Composable
fun Modifier.statePulse(key: Any?, squash: Float = 0.96f): Modifier {
    val scale = remember { Animatable(1f) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val reduced = rememberReducedMotion()
    val first = remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first.value) {
            first.value = false
            return@LaunchedEffect
        }
        if (reduced) return@LaunchedEffect
        scale.snapTo(squash)
        scale.animateTo(1f, spec)
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

// ---------------------------------------------------------------------------------------------
// Icon micro-motion
// ---------------------------------------------------------------------------------------------

/** A one-shot motion that fits the icon's meaning. */
enum class IconMotion {
    /** A ringing bell or alarm: a quick decaying shake. */
    Wiggle,
    /** An hourglass or refresh: half a turn. Use with icons that look the same upside down. */
    Flip,
    /** A stopwatch, sync or settings cog: one full turn. */
    Spin,
}

/** Plays [motion] each time [trigger] changes to a non-zero value (increment it on tap). */
@Composable
fun Modifier.iconMotion(trigger: Int, motion: IconMotion): Modifier {
    val rotation = remember { Animatable(0f) }
    val fast = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val default = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val reduced = rememberReducedMotion()
    LaunchedEffect(trigger) {
        if (trigger == 0 || reduced) return@LaunchedEffect
        rotation.snapTo(0f)
        when (motion) {
            IconMotion.Wiggle -> for (angle in listOf(-6f, 6f, -3f, 0f)) {
                rotation.animateTo(angle, tween(durationMillis = 70, easing = LinearEasing))
            }
            IconMotion.Flip -> rotation.animateTo(180f, fast)
            IconMotion.Spin -> rotation.animateTo(360f, default)
        }
        rotation.snapTo(0f)
    }
    return graphicsLayer { rotationZ = rotation.value }
}

// ---------------------------------------------------------------------------------------------
// Digit roll
// ---------------------------------------------------------------------------------------------

private class RollDirection(var last: String, var dir: Int)

/**
 * A number that rolls digit by digit. Only changed characters move; increasing values roll up,
 * decreasing values roll down, and simultaneous changes start right to left [staggerMillis] apart.
 * Use a tabular numeral style so widths never jump.
 */
@Composable
fun DigitRoll(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    contentDescription: String = text,
    staggerMillis: Int = 30,
) {
    val holder = remember { RollDirection(text, 1) }
    val dir = remember(text) {
        val a = holder.last.filter(Char::isDigit).toLongOrNull()
        val b = text.filter(Char::isDigit).toLongOrNull()
        holder.dir = if (a == null || b == null || b >= a) 1 else -1
        holder.last = text
        holder.dir
    }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val reduced = rememberReducedMotion()
    Row(modifier.clearAndSetSemantics { this.contentDescription = contentDescription }) {
        text.forEachIndexed { index, char ->
            val fromRight = text.length - 1 - index
            key(fromRight) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val delay = fromRight * staggerMillis
                        val transform = if (reduced) {
                            fadeIn(effects) togetherWith fadeOut(effects)
                        } else {
                            (slideInVertically(spatial) { h -> h * dir } + fadeIn(tween(90, delay))) togetherWith
                                (slideOutVertically(tween(180, delay, FastOutSlowInEasing)) { h -> -h * dir } +
                                    fadeOut(tween(120, delay)))
                        }
                        transform using SizeTransform(clip = true)
                    },
                    label = "digitRoll",
                ) { c -> Text(c.toString(), style = style) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Animated font weight (variable fonts)
// ---------------------------------------------------------------------------------------------

private val weightCache = HashMap<Triple<Int, Int, Int>, FontFamily>()

/**
 * A variable-font family whose weight animates, so text can get visibly lighter or heavier with
 * state (an alarm that is off reads lighter than one that is on). Steps of 10 keep the cache small.
 */
@Composable
fun animateVariableWeight(@FontRes resId: Int, opticalSize: Float, targetWeight: Int): FontFamily {
    val weight by animateIntAsState(
        targetValue = targetWeight,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "variableWeight",
    )
    val stepped = (weight / 10) * 10
    return remember(resId, opticalSize, stepped) {
        weightCache.getOrPut(Triple(resId, opticalSize.toInt(), stepped)) {
            FontFamily(
                Font(
                    resId = resId,
                    weight = FontWeight(stepped.coerceIn(1, 1000)),
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(stepped.coerceIn(1, 1000)),
                        FontVariation.Setting("opsz", opticalSize.coerceIn(6f, 144f)),
                    ),
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Ambient loops
// ---------------------------------------------------------------------------------------------

/**
 * A 0..1 breathing value for a selected map marker, a recording dot or a "live" badge. Returns a
 * constant 0 when animations are off. Use at most one ambient loop per screen.
 */
@Composable
fun rememberBreathing(periodMillis: Int = 2400): Float {
    if (rememberReducedMotion()) return 0f
    val transition = rememberInfiniteTransition(label = "breathing")
    val value by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathing",
    )
    return value
}

// ---------------------------------------------------------------------------------------------
// Predictive back
// ---------------------------------------------------------------------------------------------

/**
 * Follows the system back gesture while [enabled]. The returned progress goes 0..1 as the person
 * drags; apply it with [predictiveBack]. Releasing past the threshold calls [onBack] (animate the
 * panel out from there); cancelling springs it back to 0.
 */
@Composable
fun rememberPredictiveBackProgress(enabled: Boolean, onBack: () -> Unit): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    val spring = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(enabled) { if (enabled) progress.snapTo(0f) }
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event -> progress.snapTo(event.progress) }
            currentOnBack()
        } catch (e: CancellationException) {
            // Gesture cancelled: spring the panel back to rest.
            progress.animateTo(0f, spring)
        }
    }
    return progress
}

/** Shrinks, nudges and rounds a full-screen panel as the back gesture progresses. */
fun Modifier.predictiveBack(progress: Float, cornerRadius: Dp = 28.dp): Modifier = graphicsLayer {
    val scale = 1f - 0.1f * progress
    scaleX = scale
    scaleY = scale
    translationX = 8.dp.toPx() * progress
    shape = RoundedCornerShape(lerp(0.dp, cornerRadius, progress))
    clip = progress > 0f
}

// ---------------------------------------------------------------------------------------------
// Swipe to reveal and delete
// ---------------------------------------------------------------------------------------------

/**
 * A row that reveals an action when swiped toward the start and deletes on a full swipe or a fast
 * fling. The drag keeps its velocity into the settle spring. Set [showHint] once (for example on the
 * first row the person ever adds) to slide the row open and closed so the gesture is discoverable.
 * A haptic tick marks the moment a release would delete.
 */
@Composable
fun SwipeToRevealRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    revealWidth: Dp = 80.dp,
    showHint: Boolean = false,
    onHintShown: () -> Unit = {},
    action: @Composable BoxScope.() -> Unit = {
        Text(
            "Delete",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onError,
            modifier = Modifier.align(Alignment.Center),
        )
    },
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val revealPx = with(density) { revealWidth.toPx() }
    val flingPx = with(density) { 1100.dp.toPx() }
    val offset = remember { Animatable(0f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val settle = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val exit = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val reduced = rememberReducedMotion()
    val haptic = rememberHaptics()

    LaunchedEffect(showHint) {
        if (!showHint || reduced) return@LaunchedEffect
        delay(350)
        offset.animateTo(-revealPx, settle)
        delay(500)
        offset.animateTo(0f, settle)
        onHintShown()
    }

    BoxWithConstraints(modifier.clip(MaterialTheme.shapes.large)) {
        val width = constraints.maxWidth.toFloat()
        val x = if (dragging) dragX else offset.value
        if (x < -1f) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.error),
            ) {
                Box(Modifier.align(Alignment.CenterEnd).width(revealWidth).fillMaxHeight(), content = action)
            }
        }
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), 0) }
                .draggable(
                    enabled = !deleting,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        dragX = (dragX + delta).coerceIn(-width, 0f)
                        val nowArmed = dragX < -width * 0.55f
                        if (nowArmed != armed) {
                            armed = nowArmed
                            haptic(ExpressiveHaptic.Tick)
                        }
                    },
                    onDragStarted = {
                        offset.stop()
                        dragX = offset.value
                        dragging = true
                    },
                    onDragStopped = { velocity ->
                        offset.snapTo(dragX)
                        dragging = false
                        if (armed || (velocity < -flingPx && dragX < -revealPx * 0.3f)) {
                            deleting = true
                            offset.animateTo(-width, exit, initialVelocity = velocity)
                            onDelete()
                        } else {
                            val target = if (velocity > flingPx || dragX > -revealPx * 0.35f) 0f else -revealPx
                            offset.animateTo(target, settle, initialVelocity = velocity)
                        }
                        armed = false
                    },
                ),
        ) { content() }
    }
}
