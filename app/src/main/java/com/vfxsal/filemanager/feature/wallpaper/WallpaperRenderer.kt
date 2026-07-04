package com.vfxsal.filemanager.feature.wallpaper

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import com.vfxsal.filemanager.feature.wallpaper.data.WallpaperDesign
import com.vfxsal.filemanager.feature.wallpaper.data.WallpaperStyle
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
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
            WallpaperStyle.CONSTELLATION -> drawConstellation(canvas, w, h, accentA, rng)
            WallpaperStyle.HEXAGON_RINGS -> drawHexagonRings(canvas, w, h, accentA)
            WallpaperStyle.SPIRAL -> drawSpiral(canvas, w, h, accentA)
            WallpaperStyle.RIPPLE_RINGS -> drawRippleRings(canvas, w, h, accentA)
            WallpaperStyle.SPLIT_GRADIENT -> drawSplitGradient(canvas, w, h, accentA, accentB)
            WallpaperStyle.ORBIT_RINGS -> drawOrbitRings(canvas, w, h, accentA, rng)
            WallpaperStyle.MOUNTAIN_SKYLINE -> drawMountainSkyline(canvas, w, h, accentA, rng)
            WallpaperStyle.CROSSHAIR_BURST -> drawCrosshairBurst(canvas, w, h, accentA)
            WallpaperStyle.NEBULA_CLOUD -> drawNebulaCloud(canvas, w, h, accentA, accentB, rng)
            WallpaperStyle.STARFIELD_DRIFT -> drawStarfieldDrift(canvas, w, h, accentA, rng)
            WallpaperStyle.CIRCUIT_LINES -> drawCircuitLines(canvas, w, h, accentA, rng)
            WallpaperStyle.DIAGONAL_STRIPES -> drawDiagonalStripes(canvas, w, h, accentA)
            WallpaperStyle.HONEYCOMB -> drawHoneycomb(canvas, w, h, accentA)
            WallpaperStyle.METEOR_SHOWER -> drawMeteorShower(canvas, w, h, accentA, rng)
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

    private fun drawConstellation(canvas: Canvas, w: Int, h: Int, accentColor: Int, rng: Random) {
        val pointCount = 26
        val points = List(pointCount) { PointF(rng.nextFloat() * w, rng.nextFloat() * h) }
        val maxLinkDist = min(w, h) * 0.18f
        val linkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        linkPaint.color = colorWithAlpha(accentColor, 70)
        linkPaint.style = Paint.Style.STROKE
        linkPaint.strokeWidth = min(w, h) * 0.0015f
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val dx = points[i].x - points[j].x
                val dy = points[i].y - points[j].y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < maxLinkDist) {
                    canvas.drawLine(points[i].x, points[i].y, points[j].x, points[j].y, linkPaint)
                }
            }
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        dotPaint.color = colorWithAlpha(accentColor, 230)
        for (point in points) {
            canvas.drawCircle(point.x, point.y, min(w, h) * 0.004f, dotPaint)
        }
    }

    private fun drawHexagonRings(canvas: Canvas, w: Int, h: Int, accentColor: Int) {
        val cx = w * 0.5f
        val cy = h * 0.45f
        val baseRadius = min(w, h) * 0.12f
        for (ring in 0 until 4) {
            val radius = baseRadius * (1f + ring * 0.6f)
            val alpha = (200 - ring * 40).coerceAtLeast(30)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = colorWithAlpha(accentColor, alpha)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = radius * 0.03f
            if (ring == 0) paint.maskFilter = BlurMaskFilter(radius * 0.1f, BlurMaskFilter.Blur.NORMAL)
            val path = Path()
            for (i in 0 until 6) {
                val angle = Math.toRadians((60 * i - 90).toDouble())
                val x = cx + radius * cos(angle).toFloat()
                val y = cy + radius * sin(angle).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    private fun drawSpiral(canvas: Canvas, w: Int, h: Int, accentColor: Int) {
        val cx = w * 0.5f
        val cy = h * 0.42f
        val maxRadius = min(w, h) * 0.42f
        val turns = 4.5
        val steps = 400
        val path = Path()
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val angle = t * turns * 2 * Math.PI
            val radius = maxRadius * t
            val x = cx + (radius * cos(angle)).toFloat()
            val y = cy + (radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = colorWithAlpha(accentColor, 220)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxRadius * 0.01f
        paint.maskFilter = BlurMaskFilter(maxRadius * 0.02f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(path, paint)
    }

    private fun drawRippleRings(canvas: Canvas, w: Int, h: Int, accentColor: Int) {
        val cx = w * 0.5f
        val cy = h * 0.55f
        val maxRadius = min(w, h) * 0.55f
        val ringCount = 6
        for (i in 1..ringCount) {
            val radius = maxRadius * i / ringCount
            val alpha = (180 * (1f - i.toFloat() / ringCount)).toInt().coerceAtLeast(20)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = colorWithAlpha(accentColor, alpha)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = min(w, h) * 0.004f
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    private fun drawSplitGradient(canvas: Canvas, w: Int, h: Int, accentA: Int, accentB: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, h.toFloat(), w.toFloat(), 0f,
            intArrayOf(
                colorWithAlpha(accentA, 130),
                colorWithAlpha(accentA, 0),
                colorWithAlpha(accentB, 0),
                colorWithAlpha(accentB, 130),
            ),
            floatArrayOf(0f, 0.45f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        seamPaint.color = colorWithAlpha(accentA, 200)
        seamPaint.style = Paint.Style.STROKE
        seamPaint.strokeWidth = min(w, h) * 0.006f
        seamPaint.maskFilter = BlurMaskFilter(min(w, h) * 0.015f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawLine(0f, h.toFloat(), w.toFloat(), 0f, seamPaint)
    }

    private fun drawOrbitRings(canvas: Canvas, w: Int, h: Int, accentColor: Int, rng: Random) {
        val cx = w * 0.5f
        val cy = h * 0.4f
        val orbitCount = 3
        for (i in 0 until orbitCount) {
            val rx = min(w, h) * (0.2f + i * 0.13f)
            val ry = rx * 0.4f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = colorWithAlpha(accentColor, 120 - i * 20)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = min(w, h) * 0.0025f
            val oval = RectF(cx - rx, cy - ry, cx + rx, cy + ry)
            canvas.drawOval(oval, paint)

            val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
            val dotX = cx + rx * cos(angle)
            val dotY = cy + ry * sin(angle)
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            dotPaint.color = colorWithAlpha(accentColor, 255)
            dotPaint.maskFilter = BlurMaskFilter(min(w, h) * 0.01f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(dotX, dotY, min(w, h) * 0.012f, dotPaint)
        }
    }

    private fun drawMountainSkyline(canvas: Canvas, w: Int, h: Int, accentColor: Int, rng: Random) {
        val horizonY = h * 0.7f
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        glowPaint.shader = LinearGradient(
            0f, horizonY, 0f, 0f,
            intArrayOf(colorWithAlpha(accentColor, 120), colorWithAlpha(accentColor, 0)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), horizonY, glowPaint)

        val layerCount = 2
        for (layer in 0 until layerCount) {
            val baseY = horizonY + layer * h * 0.05f
            val amplitude = h * (0.1f - layer * 0.03f)
            val path = Path()
            path.moveTo(0f, h.toFloat())
            path.lineTo(0f, baseY)
            val segments = 6
            val segmentWidth = w.toFloat() / segments
            for (i in 0 until segments) {
                val peakX = segmentWidth * i + segmentWidth * 0.5f
                val peakY = baseY - rng.nextFloat() * amplitude
                path.lineTo(peakX, peakY)
                path.lineTo(segmentWidth * (i + 1), baseY)
            }
            path.lineTo(w.toFloat(), h.toFloat())
            path.close()
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            fillPaint.color = AndroidColor.BLACK
            canvas.drawPath(path, fillPaint)
        }
    }

    private fun drawCrosshairBurst(canvas: Canvas, w: Int, h: Int, accentColor: Int) {
        val cx = w * 0.5f
        val cy = h * 0.45f
        val maxRadius = min(w, h) * 0.5f
        val rayCount = 16
        for (i in 0 until rayCount) {
            val angle = Math.toRadians((360.0 / rayCount) * i)
            val innerRadius = maxRadius * 0.15f
            val outerRadius = maxRadius * (0.5f + (i % 3) * 0.15f)
            val x1 = cx + (innerRadius * cos(angle)).toFloat()
            val y1 = cy + (innerRadius * sin(angle)).toFloat()
            val x2 = cx + (outerRadius * cos(angle)).toFloat()
            val y2 = cy + (outerRadius * sin(angle)).toFloat()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = colorWithAlpha(accentColor, 150)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = maxRadius * 0.006f
            canvas.drawLine(x1, y1, x2, y2, paint)
        }
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        corePaint.color = colorWithAlpha(accentColor, 255)
        corePaint.maskFilter = BlurMaskFilter(maxRadius * 0.08f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(cx, cy, maxRadius * 0.05f, corePaint)
    }

    private fun drawNebulaCloud(canvas: Canvas, w: Int, h: Int, accentA: Int, accentB: Int, rng: Random) {
        repeat(5) { index ->
            val cx = rng.nextFloat() * w
            val cy = rng.nextFloat() * h * 0.8f
            val radius = min(w, h) * (0.2f + rng.nextFloat() * 0.25f)
            val cloudColor = if (index % 2 == 0) accentA else accentB
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(colorWithAlpha(cloudColor, 90), colorWithAlpha(cloudColor, 30), colorWithAlpha(cloudColor, 0)),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    private fun drawStarfieldDrift(canvas: Canvas, w: Int, h: Int, accentColor: Int, rng: Random) {
        repeat(160) {
            val x = rng.nextFloat() * w
            val y = rng.nextFloat() * h
            val isBright = rng.nextFloat() > 0.92f
            val radius = if (isBright) min(w, h) * (0.006f + rng.nextFloat() * 0.006f) else min(w, h) * 0.0015f
            val alpha = if (isBright) 255 else (rng.nextFloat() * 150 + 60).toInt().coerceIn(0, 255)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = colorWithAlpha(accentColor, alpha)
            if (isBright) paint.maskFilter = BlurMaskFilter(radius * 2.5f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(x, y, radius, paint)
        }
    }

    private fun drawCircuitLines(canvas: Canvas, w: Int, h: Int, accentColor: Int, rng: Random) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        linePaint.color = colorWithAlpha(accentColor, 140)
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = min(w, h) * 0.003f
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        dotPaint.color = colorWithAlpha(accentColor, 220)

        var x = rng.nextFloat() * w
        var y = rng.nextFloat() * h
        canvas.drawCircle(x, y, min(w, h) * 0.006f, dotPaint)
        repeat(9) {
            val nextX = rng.nextFloat() * w
            val nextY = rng.nextFloat() * h
            canvas.drawLine(x, y, nextX, y, linePaint)
            canvas.drawLine(nextX, y, nextX, nextY, linePaint)
            canvas.drawCircle(nextX, nextY, min(w, h) * 0.006f, dotPaint)
            x = nextX
            y = nextY
        }
    }

    private fun drawDiagonalStripes(canvas: Canvas, w: Int, h: Int, accentColor: Int) {
        val stripeCount = 7
        val spacing = (w + h).toFloat() / stripeCount
        for (i in 0 until stripeCount) {
            val offset = i * spacing
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = colorWithAlpha(accentColor, (180 - i * 18).coerceAtLeast(25))
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = spacing * 0.25f
            paint.maskFilter = BlurMaskFilter(spacing * 0.08f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawLine(offset - h, h.toFloat(), offset, 0f, paint)
        }
    }

    private fun drawHoneycomb(canvas: Canvas, w: Int, h: Int, accentColor: Int) {
        val hexRadius = min(w, h) * 0.09f
        val hexHeight = hexRadius * 1.732f
        val cols = (w / (hexRadius * 1.5f)).toInt() + 2
        val rows = (h / hexHeight).toInt() + 2
        val focusX = w * 0.7f
        val focusY = h * 0.3f
        val maxDist = min(w, h) * 0.9f
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val cx = col * hexRadius * 1.5f
                val cy = row * hexHeight + if (col % 2 == 1) hexHeight / 2f else 0f
                val dx = cx - focusX
                val dy = cy - focusY
                val dist = sqrt(dx * dx + dy * dy)
                val alpha = (120 * (1f - (dist / maxDist)).coerceIn(0f, 1f)).toInt()
                if (alpha <= 4) continue
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = colorWithAlpha(accentColor, alpha)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = hexRadius * 0.04f
                val path = Path()
                for (i in 0 until 6) {
                    val angle = Math.toRadians((60 * i).toDouble())
                    val x = cx + hexRadius * cos(angle).toFloat()
                    val y = cy + hexRadius * sin(angle).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun drawMeteorShower(canvas: Canvas, w: Int, h: Int, accentColor: Int, rng: Random) {
        repeat(8) {
            val startX = rng.nextFloat() * w
            val startY = rng.nextFloat() * h * 0.6f
            val length = min(w, h) * (0.15f + rng.nextFloat() * 0.2f)
            val angle = Math.toRadians(35.0)
            val endX = startX + (length * cos(angle)).toFloat()
            val endY = startY + (length * sin(angle)).toFloat()

            val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            trailPaint.shader = LinearGradient(
                startX, startY, endX, endY,
                intArrayOf(colorWithAlpha(accentColor, 0), colorWithAlpha(accentColor, 220)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            trailPaint.style = Paint.Style.STROKE
            trailPaint.strokeWidth = min(w, h) * 0.004f
            trailPaint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(startX, startY, endX, endY, trailPaint)

            val headPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            headPaint.color = colorWithAlpha(accentColor, 255)
            headPaint.maskFilter = BlurMaskFilter(min(w, h) * 0.015f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(endX, endY, min(w, h) * 0.006f, headPaint)
        }
    }

    private fun colorWithAlpha(baseColor: Int, alpha: Int): Int =
        AndroidColor.argb(
            alpha.coerceIn(0, 255),
            AndroidColor.red(baseColor),
            AndroidColor.green(baseColor),
            AndroidColor.blue(baseColor),
        )
}
