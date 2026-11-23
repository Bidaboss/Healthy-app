package com.bidaboss.healthy.workout.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Vibration légère et bip court du chrono (fil Design A2). Le bip passe par le flux des notifications et
 * se tait si le téléphone est en silencieux ou en vibreur.
 */
class TimerFeedback(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    private val tones: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME) }.getOrNull()

    fun play(cue: TimerCue) {
        val (vibrateMs, toneMs) = when (cue) {
            TimerCue.COUNTDOWN -> 40L to 120
            TimerCue.END -> 220L to 400
        }
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(vibrateMs, VibrationEffect.DEFAULT_AMPLITUDE)) }
        if (audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            runCatching { tones?.startTone(ToneGenerator.TONE_PROP_BEEP, toneMs) }
        }
    }

    fun release() {
        tones?.release()
    }

    private companion object {
        const val TONE_VOLUME = 70
    }
}

/** Gros chrono circulaire qui se vide, le temps restant au centre. */
@Composable
internal fun CountdownRing(secondsLeft: Int, totalSeconds: Int, text: String, modifier: Modifier = Modifier) {
    val target = if (totalSeconds > 0) (secondsLeft.toFloat() / totalSeconds).coerceIn(0f, 1f) else 0f
    val fraction by animateFloatAsState(target, tween(400), label = "chrono")
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp)) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(text, style = MaterialTheme.typography.displayLarge, color = color)
    }
}
