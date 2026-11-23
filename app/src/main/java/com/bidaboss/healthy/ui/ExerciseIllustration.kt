package com.bidaboss.healthy.ui

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.bidaboss.healthy.core.progress.Mood
import com.bidaboss.healthy.core.workout.ExerciseFamily
import com.bidaboss.healthy.ui.theme.HealthyTheme

/**
 * Pousse fait le mouvement (fil Design, A1) : deux positions par famille d'exercice, en boucle douce.
 * La tenue suit le thème (c'est le même dessin que la mascotte). Si les animations du téléphone sont
 * coupées, Pousse reste dans la position d'effort.
 */
@Composable
fun ExerciseIllustration(exerciseName: String, modifier: Modifier = Modifier, width: Dp = 220.dp) {
    val family = remember(exerciseName) { ExerciseFamily.of(exerciseName) }
    val (from, to) = remember(family) { posesOf(family) }
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val t = if (animate) {
        val transition = rememberInfiniteTransition(label = "exercice")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "position",
        )
        value
    } else {
        1f
    }
    val theme = HealthyTheme.ambiance.theme
    val sparkle = HealthyTheme.ambiance.sparkle
    val limbColor = PousseInk
    Canvas(
        modifier
            .size(width, width * (SCENE_H / SCENE_W))
            .semantics { contentDescription = "Pousse montre le mouvement : $exerciseName" },
    ) {
        scale(size.width / SCENE_W, pivot = Offset.Zero) {
            val pose = from.lerp(to, t)
            // Sol et ombre.
            drawLine(limbColor.copy(alpha = 0.15f), Offset(4f, GROUND + 2f), Offset(SCENE_W - 4f, GROUND + 2f), strokeWidth = 3f, cap = StrokeCap.Round)
            drawOval(Color.Black.copy(alpha = 0.12f), topLeft = Offset(pose.center.x - 26f, GROUND - 3f), size = Size(52f, 8f))
            pose.limbs.forEach { limb -> drawLimb(limb, limbColor) }
            // Pousse : rayon du corps 38 sur la grille 120, centré en (60, 72).
            val s = BODY_RADIUS / 38f
            translate(pose.center.x - 60f * s, pose.center.y - 72f * s) {
                rotate(pose.angle, pivot = Offset(60f * s, 72f * s)) {
                    scale(s, pivot = Offset.Zero) { drawPousse(Mood.HAPPY, theme, sparkle, shadow = false) }
                }
            }
        }
    }
}

private const val SCENE_W = 160f
private const val SCENE_H = 120f
private const val GROUND = 110f
private const val BODY_RADIUS = 22f

/** Un bras ou une jambe : attache, coude ou genou, main ou pied. */
private data class Limb(val root: Offset, val joint: Offset, val end: Offset) {
    fun lerp(other: Limb, t: Float) = Limb(lerpOffset(root, other.root, t), lerpOffset(joint, other.joint, t), lerpOffset(end, other.end, t))
}

private data class Pose(val center: Offset, val angle: Float, val limbs: List<Limb>) {
    fun lerp(other: Pose, t: Float) = Pose(
        lerpOffset(center, other.center, t),
        lerp(angle, other.angle, t),
        limbs.zip(other.limbs) { a, b -> a.lerp(b, t) },
    )
}

private fun lerpOffset(a: Offset, b: Offset, t: Float) = Offset(lerp(a.x, b.x, t), lerp(a.y, b.y, t))

private fun DrawScope.drawLimb(limb: Limb, color: Color) {
    val path = Path().apply {
        moveTo(limb.root.x, limb.root.y)
        lineTo(limb.joint.x, limb.joint.y)
        lineTo(limb.end.x, limb.end.y)
    }
    drawPath(path, color, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(color, radius = 4.5f, center = limb.end)
}

private fun limb(rx: Float, ry: Float, jx: Float, jy: Float, ex: Float, ey: Float) =
    Limb(Offset(rx, ry), Offset(jx, jy), Offset(ex, ey))

/** Debout, vue de face : la position de départ de la plupart des mouvements. */
private val Standing = Pose(
    Offset(80f, 50f), 0f,
    listOf(
        limb(62f, 58f, 56f, 72f, 54f, 86f),
        limb(98f, 58f, 104f, 72f, 106f, 86f),
        limb(72f, 70f, 70f, 90f, 68f, GROUND),
        limb(88f, 70f, 90f, 90f, 92f, GROUND),
    ),
)

/** Les deux positions de chaque famille : départ, puis effort. */
private fun posesOf(family: ExerciseFamily): Pair<Pose, Pose> = when (family) {
    ExerciseFamily.SQUAT -> Standing to Pose(
        Offset(80f, 68f), 0f,
        listOf(
            limb(62f, 70f, 46f, 70f, 32f, 70f),
            limb(98f, 70f, 114f, 70f, 128f, 70f),
            limb(72f, 86f, 58f, 96f, 66f, GROUND),
            limb(88f, 86f, 102f, 96f, 94f, GROUND),
        ),
    )
    ExerciseFamily.LUNGE -> Standing.copy(
        limbs = listOf(
            limb(62f, 58f, 56f, 68f, 66f, 74f),
            limb(98f, 58f, 104f, 68f, 94f, 74f),
            limb(74f, 70f, 70f, 90f, 68f, GROUND),
            limb(86f, 70f, 90f, 90f, 92f, GROUND),
        ),
    ) to Pose(
        Offset(80f, 64f), 0f,
        listOf(
            limb(62f, 68f, 56f, 78f, 66f, 84f),
            limb(98f, 68f, 104f, 78f, 94f, 84f),
            limb(74f, 84f, 56f, 90f, 54f, GROUND),
            limb(86f, 84f, 100f, 104f, 118f, 108f),
        ),
    )
    ExerciseFamily.PUSH_UP -> Pose(
        Offset(46f, 60f), 0f,
        listOf(
            limb(46f, 76f, 46f, 94f, 46f, GROUND),
            limb(54f, 76f, 54f, 94f, 54f, GROUND),
            limb(64f, 70f, 100f, 88f, 140f, 106f),
            limb(64f, 74f, 100f, 91f, 138f, 108f),
        ),
    ) to Pose(
        Offset(46f, 82f), 0f,
        listOf(
            limb(46f, 94f, 30f, 100f, 46f, GROUND),
            limb(54f, 94f, 38f, 102f, 54f, GROUND),
            limb(64f, 90f, 100f, 98f, 140f, 106f),
            limb(64f, 94f, 100f, 101f, 138f, 108f),
        ),
    )
    ExerciseFamily.PLANK -> Pose(
        Offset(44f, 80f), 0f,
        listOf(
            limb(48f, 94f, 48f, 108f, 68f, GROUND),
            limb(54f, 94f, 54f, 108f, 74f, GROUND),
            limb(62f, 88f, 100f, 98f, 140f, 106f),
            limb(62f, 92f, 100f, 101f, 138f, 108f),
        ),
    ) to Pose(
        Offset(44f, 77f), 0f,
        listOf(
            limb(48f, 91f, 48f, 108f, 68f, GROUND),
            limb(54f, 91f, 54f, 108f, 74f, GROUND),
            limb(62f, 85f, 100f, 95f, 140f, 106f),
            limb(62f, 89f, 100f, 98f, 138f, 108f),
        ),
    )
    ExerciseFamily.ABS -> Pose(
        Offset(40f, 92f), -90f,
        listOf(
            limb(34f, 80f, 24f, 76f, 22f, 86f),
            limb(40f, 80f, 30f, 74f, 28f, 84f),
            limb(58f, 100f, 84f, 76f, 108f, GROUND),
            limb(58f, 104f, 88f, 80f, 112f, GROUND),
        ),
    ) to Pose(
        Offset(52f, 82f), -45f,
        listOf(
            limb(60f, 80f, 74f, 78f, 86f, 74f),
            limb(62f, 84f, 76f, 82f, 88f, 78f),
            limb(62f, 100f, 84f, 76f, 108f, GROUND),
            limb(62f, 104f, 88f, 80f, 112f, GROUND),
        ),
    )
    ExerciseFamily.BRIDGE -> Pose(
        Offset(34f, 94f), -90f,
        listOf(
            limb(40f, 104f, 24f, 108f, 8f, GROUND),
            limb(40f, 106f, 26f, 109f, 12f, GROUND),
            limb(54f, 104f, 80f, 82f, 98f, GROUND),
            limb(54f, 106f, 84f, 86f, 102f, GROUND),
        ),
    ) to Pose(
        Offset(34f, 94f), -90f,
        listOf(
            limb(40f, 104f, 24f, 108f, 8f, GROUND),
            limb(40f, 106f, 26f, 109f, 12f, GROUND),
            limb(54f, 86f, 82f, 76f, 98f, GROUND),
            limb(54f, 88f, 86f, 80f, 102f, GROUND),
        ),
    )
    ExerciseFamily.JUMP -> Standing to Pose(
        Offset(80f, 42f), 0f,
        listOf(
            limb(64f, 34f, 54f, 20f, 48f, 6f),
            limb(96f, 34f, 106f, 20f, 112f, 6f),
            limb(72f, 62f, 62f, 84f, 54f, 106f),
            limb(88f, 62f, 98f, 84f, 106f, 106f),
        ),
    )
    ExerciseFamily.STRETCH -> Standing.copy(
        limbs = listOf(
            limb(66f, 36f, 64f, 20f, 64f, 6f),
            limb(94f, 36f, 96f, 20f, 96f, 6f),
            Standing.limbs[2],
            Standing.limbs[3],
        ),
    ) to Pose(
        Offset(84f, 52f), 15f,
        listOf(
            limb(72f, 36f, 78f, 20f, 86f, 8f),
            limb(98f, 40f, 106f, 26f, 112f, 14f),
            Standing.limbs[2],
            Standing.limbs[3],
        ),
    )
    // Marche sur place : un genou monte, les bras balancent.
    ExerciseFamily.GENERIC -> Standing to Standing.copy(
        center = Offset(80f, 48f),
        limbs = listOf(
            limb(62f, 56f, 54f, 64f, 58f, 52f),
            limb(98f, 56f, 104f, 70f, 106f, 84f),
            limb(72f, 68f, 62f, 80f, 70f, 96f),
            limb(88f, 68f, 90f, 90f, 92f, GROUND),
        ),
    )
}
