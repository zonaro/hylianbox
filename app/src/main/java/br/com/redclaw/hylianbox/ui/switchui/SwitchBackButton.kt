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
import android.content.res.Configuration
import android.app.Dialog
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import br.com.redclaw.hylianbox.HylianBoxApp
import br.com.redclaw.hylianbox.input.InputDeviceUtils
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Reusable on-screen back-button behaviour for non-HOME Switch-style screens.
 *
 * Wires a [View] so that:
 *  - tapping it plays the Switch "back" SFX and runs [onBack] (the call sites
 *    pass [AppCompatActivity.finish], since these are top-level navigable
 *    screens launched from HOME/dock);
 *  - when a physical controller is connected the button hides (it is redundant
 *    with the controller's B / back key); and
 *  - any touch on the screen makes the button reappear and stay visible, so a
 *    touch user always gets the affordance back.
 *
 * The [InputManager.InputDeviceListener] is cleaned up automatically via a
 * [LifecycleEventObserver] on [Lifecycle.Event.ON_DESTROY], so host activities
 * need no manual teardown. The helper is self-contained and holds no screen
 * specific logic.
 */
class SwitchBackButton {

    private var button: View? = null
    private var inputManager: InputManager? = null
    private var hostActivity: AppCompatActivity? = null
    private var dialogWindow: Window? = null
    private var originalWindowCallback: Window.Callback? = null

    /** True only while the button is hidden because a controller is present. */
    private var hiddenByController = false

    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            if (InputDeviceUtils.isPhysicalController(InputDevice.getDevice(deviceId))) hide()
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            // Only reveal the button once no physical controller remains.
            button?.context?.let { if (shouldShowForTouch(it)) show() else hide() }
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            // A device may have become (or stopped being) a controller.
            button?.context?.let { if (shouldShowForTouch(it)) show() else hide() }
        }
    }

    /**
     * Binds [button] to the back action. Registers a controller listener and
     * applies the initial visibility based on whether a controller is already
     * connected at attach time.
     */
    fun attach(activity: AppCompatActivity, button: View, onBack: () -> Unit) {
        detach()
        hostActivity = activity
        this.button = button
        register(activity, this)
        button.setOnClickListener {
            HylianBoxApp.sfxManager?.back()
            onBack()
        }

        inputManager = activity.getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager?.registerInputDeviceListener(deviceListener, null)

        if (shouldShowForTouch(activity)) show() else hide()

        activity.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    inputManager?.unregisterInputDeviceListener(deviceListener)
                    unregister(activity, this@SwitchBackButton)
                    activity.lifecycle.removeObserver(this)
                }
            }
        })
    }

    /** Binds a close button owned by a separate Dialog Window and tracks its input modality. */
    fun attach(dialog: Dialog, activity: AppCompatActivity, button: View, onBack: () -> Unit) {
        attach(activity, button, onBack)
        val window = dialog.window ?: return
        val delegate = window.callback ?: return
        val wrapper =
                object : Window.Callback by delegate {
                    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                        onTouch(event)
                        return delegate.dispatchTouchEvent(event)
                    }

                    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                        if (event.action == android.view.KeyEvent.ACTION_DOWN) onNonTouchInput()
                        return delegate.dispatchKeyEvent(event)
                    }

                    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
                        onNonTouchInput()
                        return delegate.dispatchGenericMotionEvent(event)
                    }
                }
        dialogWindow = window
        originalWindowCallback = delegate
        window.callback = wrapper
    }

    /** Releases listener/registry references when a dialog-owned button is dismissed. */
    fun detach() {
        val window = dialogWindow
        val original = originalWindowCallback
        if (window != null && original != null) window.callback = original
        dialogWindow = null
        originalWindowCallback = null
        inputManager?.unregisterInputDeviceListener(deviceListener)
        hostActivity?.let { unregister(it, this) }
        hostActivity = null
        inputManager = null
        button = null
    }

    /**
     * Call from the host activity's [AppCompatActivity.dispatchTouchEvent]
     * override. Any touch reveals a button that was hidden because a physical
     * controller was connected, satisfying "touch usage => button visible".
     */
    fun onTouch(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return
        val hasTouchscreen =
                button?.resources?.configuration?.touchscreen !=
                        Configuration.TOUCHSCREEN_NOTOUCH
        if (TouchClosePolicy.afterTouch(hasTouchscreen)) show() else hide()
    }

    /** Hides the touch-only affordance again when navigation comes from a controller. */
    fun onNonTouchInput() {
        if (!TouchClosePolicy.afterNonTouch()) hide()
    }

    private fun hide() {
        val b = button ?: return
        hiddenByController = true
        b.visibility = View.GONE
        b.isClickable = false
    }

    private fun show() {
        val b = button ?: return
        hiddenByController = false
        b.visibility = View.VISIBLE
        b.isClickable = true
    }

    private fun shouldShowForTouch(context: Context): Boolean =
            TouchClosePolicy.initial(
                    context.resources.configuration.touchscreen !=
                            Configuration.TOUCHSCREEN_NOTOUCH,
                    InputDeviceUtils.hasConnectedController()
            )

    companion object {
        private val attached =
                WeakHashMap<AppCompatActivity, MutableList<WeakReference<SwitchBackButton>>>()

        private fun register(activity: AppCompatActivity, helper: SwitchBackButton) {
            synchronized(attached) {
                val helpers = attached.getOrPut(activity) { mutableListOf() }
                helpers.removeAll { it.get() == null || it.get() === helper }
                helpers += WeakReference(helper)
            }
        }

        private fun unregister(activity: AppCompatActivity, helper: SwitchBackButton) {
            synchronized(attached) {
                attached[activity]?.removeAll { it.get() == null || it.get() === helper }
                if (attached[activity].isNullOrEmpty()) attached.remove(activity)
            }
        }

        /** Routes the host Activity's touch modality to every attached close affordance. */
        fun dispatchTouch(activity: AppCompatActivity, event: MotionEvent) {
            snapshot(activity).forEach { it.onTouch(event) }
        }

        /** Routes controller/D-pad navigation so the touch-only close affordance disappears. */
        fun dispatchNonTouch(activity: AppCompatActivity) {
            snapshot(activity).forEach { it.onNonTouchInput() }
        }

        private fun snapshot(activity: AppCompatActivity): List<SwitchBackButton> =
                synchronized(attached) {
                    val helpers = attached[activity] ?: return@synchronized emptyList()
                    helpers.removeAll { it.get() == null }
                    helpers.mapNotNull { it.get() }
                }

        /** Binds a dialog-owned close view and releases it automatically when the Window detaches. */
        fun bindDialog(dialog: Dialog, button: View, onBack: () -> Unit): SwitchBackButton? {
            val activity = GameplayFullscreenDialog.activity(dialog.context) as? AppCompatActivity
                    ?: return null
            val helper = SwitchBackButton()
            helper.attach(dialog, activity, button, onBack)
            dialog.window?.decorView?.addOnAttachStateChangeListener(
                    object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(view: View) = Unit

                        override fun onViewDetachedFromWindow(view: View) {
                            view.removeOnAttachStateChangeListener(this)
                            helper.detach()
                        }
                    }
            )
            return helper
        }
    }
}
