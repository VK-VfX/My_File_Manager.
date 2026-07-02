package com.vfxsal.filemanager.feature.wallpaper

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import com.vfxsal.filemanager.feature.wallpaper.data.WallpaperDesign
import com.vfxsal.filemanager.feature.wallpaper.data.WallpaperStyle
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Every wallpaper is generated on-device from a small set of parameters rather than shipped as
 * image assets - keeps the app self-contained (no licensing/sourcing concerns, no network) and
 * every design renders crisply at any resolution. Background is always pure black (#000000) so
 * OLED panels turn those pixels fully off, which is the whole point of an "AMOLED wallpaper."
 *
 * Note: draw functions below deliberately avoid parameter names like "color" inside
 * Paint().apply { } blocks, since Kotlin resolves an unqualified name there against the Paint
 * receiver's own "color" property first, silently shadowing an outer parameter of the same name.
 */
object WallpaperRenderer {

    fun render(design: WallpaperDesign, width: Int, height: Int): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.BLACK)
        val rng = Random(design.seed)
        val accentA = design.primary.toArgb()
        val accentB = design.secondary.toArgb()
        when (design.style) {
            WallpaperStyle.ORB_GLOW -> drawOrbGlow(canvas, w, h, accentA)
            WallpaperStyle.DUAL_ORB -> drawDualOrb(canvas, w, h, accentA, accentB)
            WallpaperStyle.LINE_WAVE -> drawLineWave(canvas, w, h, accentA, rng)
            WallpaperStyle.RING -> drawRing(canvas, w, h, accentA)
            WallpaperStyle.PARTICLES -> drawParticles(canvas, w, h, accentA, rng)
            WallpaperStyle.AURORA_EDGE -> drawAuroraEdge(canvas, w, h, accentA, accentB)
            WallpaperStyle.TRIANGLE_OUTLINE -> drawTriangleOutline(canvas, w, h, accentA)
        }
        return bitmap
    }

    private fun drawOrbGlow(canvas: Canvas, w: Int, h: Int, accentColor: Int) {
        val cx = w * 0.72f
        val cy = h * 0.28f
        val radius = min(w, h) * 0.55f
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        glowPaint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(colorWithAlpha(accentColor, 200), colorWithAlpha(accentColor, 60), colorWithAlpha(accentColor, 0)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), glowPaint)

        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        corePaint.color = colorWithAlpha(accentColor, 255)
        corePaint.maskFilter = BlurMaskFilter(radius * 0.12f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(cx, cy, radius * 0.1f, corePaint)
    }

    private fun drawDualOrb(canvas: Canvas, w: Int, h: Int, accentA: Int, accentB: Int) {
        drawSoftOrb(canvas, w * 0.25f, h * 0.22f, min(w, h) * 0.42f, accentA)
        drawSoftOrb(canvas, w * 0.78f, h * 0.78f, min(w, h) * 0.5f, accentB)
    }

    private fun drawSoftOrb(canvas: Canvas, cx: Float, cy: Float, radius: Float, accentColor: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(colorWithAlpha(accentColor, 170), colorWithAlpha(accentColor, 40), colorWithAlpha(accentColor, 0)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, paint)
    }

    private fun drawLineWave(canvas: Canvas, w: Int, h: Int, accentColor: Int, rng: Random) {
        val baseY = h * (0.35f + rng.nextFloat() * 0.3f)
        repeat(4) { index ->
            val path = Path()
            val amplitude = h * (0.05f + index * 0.015f)
            val yOffset = baseY + index * h * 0.06f
            path.moveTo(-w * 0.1f, yOffset)
            var x = -w * 0.1f
            val step = w * 0.1f
            var up = true
            while (x < w * 1.1f) {
                val nextX = x + step
                val controlY = yOffset + if (up) -amplitude else amplitude
                path.quadTo(x + step / 2f, controlY, nextX, yOffset)
                x = nextX
                up = !up
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = colorWithAlpha(accentColor, (220 - index * 50).coerceAtLeast(40))
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = h * 0.006f
            paint.maskFilter = BlurMaskFilter(h * 0.01f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawPath(path, paint)
        }
    }

    private fun drawRing(canvas: Canvas, w: Int, h: Int, accentColor: Int) {
        val cx = w * 0.5f
        val cy = h * 0.42f
        val radius = min(w, h) * 0.28f
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        glow.color = colorWithAlpha(accentColor, 255)
        glow.style = Paint.Style.STROKE
        glow.strokeWidth = radius * 0.06f
        glow.maskFilter = BlurMaskFilter(radius * 0.18f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(cx, cy, radius, glow)

        val sharp = Paint(Paint.ANTI_ALIAS_FLAG)
        sharp.color = colorWithAlpha(accentColor, 255)
        sharp.style = Paint.Style.STROKE
        sharp.strokeWidth = radius * 0.02f
        canvas.drawCircle(cx, cy, radius, sharp)
    }

    private fun drawParticles(canvas: Canvas, w: Int, h: Int, accentColor: Int, rng: Random) {
        repeat(90) {
            val x = rng.nextFloat() * w
            val y = rng.nextFloat() * h
            val radius = rng.nextFloat() * min(w, h) * 0.006f + min(w, h) * 0.002f
            val alpha = (rng.nextFloat() * 200 + 40).toInt().coerceIn(0, 255)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = colorWithAlpha(accentColor, alpha)
            if (rng.nextFloat() > 0.7f) paint.maskFilter = BlurMaskFilter(radius * 3f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(x, y, radius, paint)
        }
    }

    private fun drawAuroraEdge(canvas: Canvas, w: Int, h: Int, accentA: Int, accentB: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, h.toFloat(), w * 0.3f, h * 0.2f,
            intArrayOf(colorWithAlpha(accentA, 190), colorWithAlpha(accentB, 90), colorWithAlpha(accentB, 0)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    private fun drawTriangleOutline(canvas: Canvas, w: Int, h: Int, accentColor: Int) {
        val cx = w * 0.5f
        val cy = h * 0.45f
        val r = min(w, h) * 0.3f
        val path = Path()
        for (i in 0 until 3) {
            val angle = Math.toRadians((-90 + i * 120).toDouble())
            val x = cx + r * cos(angle).toFloat()
            val y = cy + r * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        glow.color = colorWithAlpha(accentColor, 255)
        glow.style = Paint.Style.STROKE
        glow.strokeWidth = r * 0.05f
        glow.maskFilter = BlurMaskFilter(r * 0.15f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(path, glow)

        val sharp = Paint(Paint.ANTI_ALIAS_FLAG)
        sharp.color = colorWithAlpha(accentColor, 255)
        sharp.style = Paint.Style.STROKE
        sharp.strokeWidth = r * 0.015f
        canvas.drawPath(path, sharp)
    }

    private fun colorWithAlpha(baseColor: Int, alpha: Int): Int =
        AndroidColor.argb(
            alpha.coerceIn(0, 255),
            AndroidColor.red(baseColor),
            AndroidColor.green(baseColor),
            AndroidColor.blue(baseColor),
        )
}
