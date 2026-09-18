/*
 * HylianBox - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.hylianbox.tracker.ui.components

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import br.com.redclaw.hylianbox.ui.switchui.AccentManager
import kotlin.math.min

/**
 * Accent-colored C-button for the tracker equip dialog: circle, centered icon (or label when no
 * icon), bottom-right ammo badge, accent focus border and pressed state. Used only inside
 * [br.com.redclaw.hylianbox.tracker.ui.tabs.ItemsTab].
 */
class TrackerCButtonView(context: Context, val label: String) : View(context) {

    var icon: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    var badgeCount: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    private val density = Resources.getSystem().displayMetrics.density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                color = AccentManager.getOnAccentColor(context)
            }
    private val badgeBgPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCC000000.toInt()
                style = Paint.Style.FILL
            }
    private val badgeBorderPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
            }
    private val badgeTextPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                color = Color.WHITE
            }
    private val focusPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f * density
            }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        // Ensure we get pressed state
        isEnabled = true
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (88 * density).toInt()
        val w = resolveSize(desired, widthMeasureSpec)
        val h = resolveSize(desired, heightMeasureSpec)
        val size = min(w, h)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - 2f * density
        if (radius <= 0) return

        // Background circle: pressed vs normal
        val accent = AccentManager.getAccentColor(context)
        bgPaint.color = if (isPressed) darken(accent) else accent
        bgPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // Focus border follows the same configured accent.
        if (isFocused) {
            focusPaint.color = AccentManager.getAccentColor(context)
            focusPaint.style = Paint.Style.STROKE
            canvas.drawCircle(cx, cy, radius + 1f * density, focusPaint)
        }

        val currentIcon = icon
        if (currentIcon != null && !currentIcon.isRecycled) {
            val half = radius * 0.68f
            canvas.drawBitmap(
                    currentIcon,
                    null,
                    RectF(cx - half, cy - half, cx + half, cy + half),
                    null
            )
        } else {
            textPaint.color = AccentManager.getOnAccentColor(context)
            textPaint.textSize = radius * 0.55f
            canvas.drawText(
                    label,
                    cx,
                    cy - (textPaint.ascent() + textPaint.descent()) / 2,
                    textPaint
            )
        }
        drawBadge(canvas, cx, cy, radius)
    }

    private fun darken(color: Int): Int =
            Color.rgb(
                    (Color.red(color) * 0.78f).toInt(),
                    (Color.green(color) * 0.78f).toInt(),
                    (Color.blue(color) * 0.78f).toInt()
            )

    private fun drawBadge(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val count = badgeCount ?: return
        val text = count.toString()
        badgeTextPaint.textSize = radius * 0.38f
        val textWidth = badgeTextPaint.measureText(text)
        val padH = radius * 0.16f
        val padV = radius * 0.10f
        val halfW = textWidth / 2f + padH
        val halfH = badgeTextPaint.textSize / 2f + padV
        val badgeCx = cx + radius * 0.62f
        val badgeCy = cy + radius * 0.62f
        val rect = RectF(badgeCx - halfW, badgeCy - halfH, badgeCx + halfW, badgeCy + halfH)
        val corner = halfH
        canvas.drawRoundRect(rect, corner, corner, badgeBgPaint)
        canvas.drawRoundRect(rect, corner, corner, badgeBorderPaint)
        canvas.drawText(
                text,
                badgeCx,
                badgeCy - (badgeTextPaint.ascent() + badgeTextPaint.descent()) / 2,
                badgeTextPaint
        )
    }
}
