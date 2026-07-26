package com.drynav.app.presentation.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** Teardrop destination pin for tap-to-pin. */
fun createPinBitmap(color: Int): Bitmap {
    val size = 110
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val headRadius = size / 3.2f
    val headCy = size / 2.9f

    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.BLACK
        alpha = 50
    }
    canvas.drawOval(
        cx - headRadius / 2f, size - 12f, cx + headRadius / 2f, size - 4f, shadow
    )

    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    // Tail triangle down to the anchor point.
    val path = android.graphics.Path().apply {
        moveTo(cx - headRadius * 0.72f, headCy + headRadius * 0.55f)
        lineTo(cx, size - 8f)
        lineTo(cx + headRadius * 0.72f, headCy + headRadius * 0.55f)
        close()
    }
    canvas.drawPath(path, body)
    canvas.drawCircle(cx, headCy, headRadius, body)

    val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE }
    canvas.drawCircle(cx, headCy, headRadius / 2.4f, hole)
    return bitmap
}
