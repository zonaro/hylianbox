/*
 * HylianBox - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package br.com.redclaw.hylianbox.ui.switchui

import android.content.Context
import android.graphics.Rect
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import androidx.core.content.ContextCompat
import br.com.redclaw.hylianbox.HylianBoxApp
import br.com.redclaw.hylianbox.R

/**
 * Reusable Nintendo Switch-style modal dialog: a centered box on a scrim, with an optional header
 * (icon + title), an optional message, an optional single-choice list, and an optional
 * positive/negative button row.
 *
 * Built for reuse across Phases D (Settings confirmations), E (Store) and F (RetroAchievements):
 * every focusable element (list rows and buttons) shows the configured accent border, focus traversal
 * plays the focus-move "toc", activation plays select, and BACK plays the back sound before
 * dismissing.
 *
 * The component is a thin builder over a plain [AlertDialog] whose content view is the Switch
 * dialog layout, so it inherits the standard dialog lifecycle (BACK / outside-tap dismissal) while
 * presenting the custom Switch surface.
 *
 * @param context the host context (an Activity for proper theming).
 */
class SwitchDialog(private val context: Context) {

    private val sfx = runCatching { HylianBoxApp.sfxManager }.getOrNull()

    private var titleText: String? = null
    private var iconRes: Int? = null
    private var messageText: String? = null
    private var positiveText: String? = null
    private var positiveAction: ((SwitchDialog) -> Unit)? = null
    private var negativeText: String? = null
    private var negativeAction: ((SwitchDialog) -> Unit)? = null
    private var choiceItems: List<String> = emptyList()
    private var choiceChecked: Int = -1
    private var choiceAction: ((Int) -> Unit)? = null
    private var customContent: View? = null

    private var dialog: AppCompatDialog? = null
    private val closeHelper = SwitchBackButton()

    /** Replaces the single-choice list with an arbitrary view (e.g. 3 C buttons). */
    fun customView(view: View): SwitchDialog = apply { customContent = view }

    /** Sets the dialog title (localized by the host). */
    fun title(text: String): SwitchDialog = apply { titleText = text }

    /** Sets the header icon (tinted with the accent token). */
    fun icon(resId: Int): SwitchDialog = apply { iconRes = resId }

    /** Sets the optional body message. */
    fun message(text: String?): SwitchDialog = apply { messageText = text }

    /** Sets the positive button; defaults to dismissing when [action] is null. */
    fun positiveButton(text: String, action: ((SwitchDialog) -> Unit)? = null): SwitchDialog =
            apply {
                positiveText = text
                positiveAction = action
            }

    /** Sets the negative button; defaults to dismissing when [action] is null. */
    fun negativeButton(text: String, action: ((SwitchDialog) -> Unit)? = null): SwitchDialog =
            apply {
                negativeText = text
                negativeAction = action
            }

    /**
     * Configures a single-choice list. Selecting an item invokes [onSelect] and dismisses the
     * dialog (mirrors AlertDialog single-choice behavior).
     */
    fun singleChoice(
            items: List<String>,
            checkedIndex: Int,
            onSelect: (Int) -> Unit
    ): SwitchDialog = apply {
        choiceItems = items
        choiceChecked = checkedIndex
        choiceAction = onSelect
    }

    /** Builds and shows the dialog. */
    fun show(): SwitchDialog {
        val fullscreenGameplay = GameplayFullscreenDialog.isGameplayContext(context)
        val view = LayoutInflater.from(context).inflate(R.layout.switch_dialog, null) as FrameLayout
        val theme =
                if (fullscreenGameplay) R.style.GameplayFullscreenDialogTheme
                else R.style.SwitchDialogTheme
        val dialog = AppCompatDialog(context, theme)
        AccentManager.applyThemeOverlay(dialog.context)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(!fullscreenGameplay)
        dialog.setOnDismissListener {
            closeHelper.detach()
            this.dialog = null
        }
        this.dialog = dialog

        // Size the window to fill (the scrim) so the box can be centered.
        dialog.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
        )

        bindHeader(view)
        bindMessage(view)
        bindList(view)
        bindButtons(view)
        (GameplayFullscreenDialog.activity(context) as? androidx.appcompat.app.AppCompatActivity)
                ?.let { activity ->
                    closeHelper.attach(
                            dialog,
                            activity,
                            view.findViewById(R.id.dialog_close),
                            onBack = { dismiss() }
                    )
                }

        // BACK plays the back sound; let the dialog handle the actual dismissal.
        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                sfx?.back()
            }
            false
        }

        // Tapping the scrim (outside the box) dismisses; the box is made
        // clickable below so taps inside it are consumed and do not dismiss.
        if (!fullscreenGameplay) view.setOnClickListener { dismiss() }

        if (fullscreenGameplay) GameplayFullscreenDialog.show(dialog) else dialog.show()
        // After show, constrain the box width (window is MATCH_PARENT for the
        // scrim; the box itself is centered and width-bounded via its layout).
        sizeBox(view, fullscreenGameplay)

        // Request focus on the first list item so D-pad navigation works immediately.
        // This is essential for controller/DPad navigation in single-choice dialogs.
        if (choiceItems.isNotEmpty()) {
            view.findViewById<ViewGroup>(R.id.dialog_list).getChildAt(0)?.requestFocus()
        }

        return this
    }

    /** Dismisses the dialog if showing. */
    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    private fun bindHeader(view: View) {
        val header = view.findViewById<ViewGroup>(R.id.dialog_header)
        if (titleText != null) {
            header.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.dialog_title).text = titleText
            val icon = view.findViewById<ImageView>(R.id.dialog_icon)
            iconRes?.let {
                icon.setImageResource(it)
                icon.setColorFilter(AccentManager.getAccentColor(context))
                icon.visibility = View.VISIBLE
            }
        } else {
            header.visibility = View.GONE
        }
    }

    private fun bindMessage(view: View) {
        val message = view.findViewById<TextView>(R.id.dialog_message)
        if (!messageText.isNullOrBlank()) {
            message.visibility = View.VISIBLE
            message.text = messageText
        } else {
            message.visibility = View.GONE
        }
    }

    private fun bindList(view: View) {
        val scroll = view.findViewById<View>(R.id.dialog_list_scroll)
        val list = view.findViewById<ViewGroup>(R.id.dialog_list)
        val custom = customContent
        if (custom != null) {
            scroll.visibility = View.VISIBLE
            list.removeAllViews()
            list.addView(custom)
            // Focus first focusable child for D-pad
            custom.post {
                custom.findViewById<View>(custom.id)?.requestFocus()
                        ?: run {
                            // find first focusable descendant
                            var first: View? = null
                            fun find(v: ViewGroup) {
                                for (i in 0 until v.childCount) {
                                    val c = v.getChildAt(i)
                                    if (c.isFocusable && first == null) first = c
                                    if (c is ViewGroup) find(c)
                                }
                            }
                            if (custom is ViewGroup) find(custom)
                            first?.requestFocus()
                        }
            }
            return
        }
        if (choiceItems.isEmpty()) {
            scroll.visibility = View.GONE
            return
        }
        scroll.visibility = View.VISIBLE
        list.removeAllViews()
        choiceItems.forEachIndexed { index, text ->
            val row =
                    DialogRowView(view.context, text, index == choiceChecked) {
                        sfx?.select()
                        val action = choiceAction
                        dismiss()
                        action?.invoke(index)
                    }
            list.addView(row)
        }
    }

    private fun bindButtons(view: View) {
        val buttonRow = view.findViewById<ViewGroup>(R.id.dialog_buttons)
        val positive = view.findViewById<Button>(R.id.dialog_positive)
        val negative = view.findViewById<Button>(R.id.dialog_negative)
        val hasPositive = positiveText != null
        val hasNegative = negativeText != null
        if (!hasPositive && !hasNegative) {
            buttonRow.visibility = View.GONE
            return
        }
        buttonRow.visibility = View.VISIBLE
        if (hasPositive) {
            positive.visibility = View.VISIBLE
            positive.text = positiveText
            positive.background = AccentManager.createSwitchButtonBackground(context)
            positive.setOnClickListener {
                sfx?.select()
                val action = positiveAction
                dismiss()
                action?.invoke(this)
            }
        } else {
            positive.visibility = View.GONE
        }
        if (hasNegative) {
            negative.visibility = View.VISIBLE
            negative.text = negativeText
            negative.background = AccentManager.createSwitchButtonBackground(context)
            negative.setOnClickListener {
                sfx?.select()
                val action = negativeAction
                dismiss()
                action?.invoke(this)
            }
        } else {
            negative.visibility = View.GONE
        }
    }

    private fun sizeBox(view: View, fullscreenGameplay: Boolean) {
        val box = view.findViewById<View>(R.id.dialog_box) ?: return
        if (fullscreenGameplay) {
            view.setBackgroundColor(ContextCompat.getColor(context, R.color.switch_bg))
            box.setBackgroundColor(ContextCompat.getColor(context, R.color.switch_bg))
            box.layoutParams =
                    FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            box.isClickable = true
            return
        }
        val metrics = context.resources.displayMetrics
        val minW = context.resources.getDimensionPixelSize(R.dimen.switch_side_panel_min_width)
        val maxW = context.resources.getDimensionPixelSize(R.dimen.dialog_menu_max_width)
        val target = (metrics.widthPixels * 0.4f).toInt()
        val width = target.coerceIn(minW, maxW)
        box.layoutParams =
                FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = android.view.Gravity.CENTER
                }
        // Consume taps inside the box so they do not fall through to the scrim
        // (which would dismiss the dialog).
        box.isClickable = true
    }

    /** A focusable, clickable single-choice row with a dynamic accent focus border. */
    private class DialogRowView(
            context: Context,
            text: String,
            checked: Boolean,
            private val onClickAction: () -> Unit
    ) : FrameLayout(context) {

        private val border: View

        init {
            LayoutInflater.from(context).inflate(R.layout.switch_dialog_row, this, true)
            border = findViewById(R.id.dialog_row_border)
            // Apply dynamic accent color to focus border
            border.background = AccentManager.createFocusBorder(context)
            findViewById<TextView>(R.id.dialog_row_text).text = text
            val check = findViewById<ImageView>(R.id.dialog_row_check)
            // Apply dynamic accent color to check mark
            check.setColorFilter(AccentManager.getAccentColor(context))
            if (checked) {
                border.visibility = View.VISIBLE
                check.visibility = View.VISIBLE
            }
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            setOnClickListener { onClickAction() }
        }

        override fun onFocusChanged(
                gainFocus: Boolean,
                direction: Int,
                previouslyFocusedRect: Rect?
        ) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            border.visibility = if (gainFocus) View.VISIBLE else View.GONE
        }
    }
}
