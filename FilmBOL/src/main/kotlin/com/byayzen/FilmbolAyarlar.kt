package com.byayzen

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey

object FilmbolAyarlar {
    private const val PREFS_PREFIX = "Filmbol_"
    private const val COOKIE_KEY = "${PREFS_PREFIX}COOKIE"

    private const val COLOR_BG = "#000000"      // Siyah Arkaplan
    private const val COLOR_PRIMARY = "#8842f3" // Mor (Ana Butonlar)
    private const val COLOR_FOCUS = "#00D9FF"   // Cyan (TV Odak Rengi)
    private const val COLOR_DELETE = "#D32F2F"  // Kırmızı (Silme)
    private const val COLOR_SAVE = "#2E7D32"    // Yeşil (Kaydet)
    private const val COLOR_TEXT_HINT = "#808080"
    fun getCookie(): String? = getKey<String>(COOKIE_KEY)

    fun showSettingsDialog(activity: AppCompatActivity, onSave: () -> Unit) {
        SettingsManager(activity, onSave).show()
    }

    private fun saveCookie(cookie: String) {
        setKey(COOKIE_KEY, cookie)
    }

    private fun clearCookie() {
        setKey(COOKIE_KEY, null)
    }
    private class SettingsManager(val context: AppCompatActivity, val onSave: () -> Unit) {
        // Navigation IDs
        private val ID_BTN_COOKIE = View.generateViewId()
        private val ID_BTN_SETTINGS = View.generateViewId()

        private val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(COLOR_BG))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 800)
            minimumWidth = 600
        }

        private lateinit var cookieView: View
        private lateinit var settingsView: View
        private lateinit var btnCookie: Button
        private lateinit var btnSettings: Button

        fun show() {
            val dialog = AlertDialog.Builder(context)
                .setView(createRootView())
                .setCancelable(false)
                .create()

            dialog.window?.setBackgroundDrawable(
                GradientDrawable().apply { setColor(Color.parseColor(COLOR_BG)); cornerRadius = 16f }
            )
            dialog.show()

            mainLayout.findViewWithTag<Button>("SAVE_BTN")?.setOnClickListener {
                onSave()
                dialog.dismiss()
            }
        }

        private fun createRootView(): View {
            // Header
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(10, 10, 10, 10)
            }

            btnCookie = createTabButton("Cookie Ayarları") { switchTab(0) }
            btnCookie.id = ID_BTN_COOKIE

            btnSettings = createTabButton("Genel Ayarlar") { switchTab(1) }
            btnSettings.id = ID_BTN_SETTINGS

            header.addView(btnCookie); header.addView(btnSettings)
            mainLayout.addView(header)

            // Content
            val container = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            cookieView = createCookieView()
            settingsView = createSettingsView()

            container.addView(cookieView); container.addView(settingsView)
            mainLayout.addView(container)

            // Footer
            val footer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 10, 20, 20)
                gravity = Gravity.CENTER
            }
            val btnSave = Button(context).apply {
                tag = "SAVE_BTN"
                text = "KAYDET VE ÇIK"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = createButtonDrawable(Color.parseColor(COLOR_SAVE))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                isFocusable = true
            }
            footer.addView(btnSave)
            mainLayout.addView(footer)

            switchTab(0)
            return mainLayout
        }

        private fun switchTab(index: Int) {
            cookieView.visibility = if (index == 0) View.VISIBLE else View.GONE
            settingsView.visibility = if (index == 1) View.VISIBLE else View.GONE

            val activeColor = Color.parseColor(COLOR_PRIMARY)
            val inactiveColor = Color.TRANSPARENT

            btnCookie.background = createButtonDrawable(if(index == 0) activeColor else inactiveColor)
            btnSettings.background = createButtonDrawable(if(index == 1) activeColor else inactiveColor)
        }

        private fun createCookieView(): View {
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 40, 40, 40)
                gravity = Gravity.CENTER_HORIZONTAL
            }

            val cookieId = View.generateViewId()
            val btnId = View.generateViewId()

            val currentCookie = getCookie() ?: ""

            val cookieInput = createNavigatableEditText("Cookie", cookieId).apply {
                setText(currentCookie)
                nextFocusDownId = btnId
                nextFocusUpId = ID_BTN_COOKIE
            }

            val btnConfirm = createActionButton("Kaydet") {
                val cookie = cookieInput.text.toString().trim()
                if (cookie.isNotEmpty()) {
                    saveCookie(cookie)
                    Toast.makeText(context, "Cookie başarıyla kaydedildi", Toast.LENGTH_SHORT).show()
                }
            }.apply {
                id = btnId
                nextFocusUpId = cookieId
            }

            layout.addView(createLabel("Filmbol Cookie Ayarları", 18f))
            layout.addView(createLabel("Lütfen aşağıya cookie bilgilerinizi girin:", 14f))
            layout.addView(cookieInput)
            layout.addView(btnConfirm)
            return layout
        }

        private fun createSettingsView(): View {
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 40, 40, 40)
            }

            val btnReset = Button(context).apply {
                text = "Cookie Sıfırla"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = createButtonDrawable(Color.parseColor(COLOR_DELETE))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                isFocusable = true
                setOnClickListener {
                    showCustomDialog(
                        "Cookie Sıfırla",
                        "Kayıtlı cookie bilgilerini silmek istediğinizden emin misiniz?",
                        isDelete = true
                    ) {
                        clearCookie()
                        Toast.makeText(context, "Cookie bilgileri sıfırlandı", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            layout.addView(createLabel("Genel Ayarlar", 18f))
            layout.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 40) })
            layout.addView(btnReset)
            return layout
        }

        private fun createNavigatableEditText(hintText: String, viewId: Int): EditText {
            return EditText(context).apply {
                id = viewId
                hint = hintText
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor(COLOR_TEXT_HINT))
                setSingleLine(false)
                maxLines = 5
                backgroundTintList = ColorStateList.valueOf(Color.parseColor(COLOR_PRIMARY))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 15 }

                isFocusable = true
                isFocusableInTouchMode = true
                showSoftInputOnFocus = false

                setOnClickListener {
                    showSoftInputOnFocus = true
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }

                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        showSoftInputOnFocus = false
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(windowToken, 0)
                    }
                }

                setOnKeyListener { v, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            val nextView = v.rootView.findViewById<View>(nextFocusUpId)
                            nextView?.requestFocus()
                            return@setOnKeyListener true
                        }
                        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            val nextView = v.rootView.findViewById<View>(nextFocusDownId)
                            nextView?.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                    false
                }
            }
        }

        private fun showCustomDialog(title: String, message: String, isDelete: Boolean, onConfirm: () -> Unit) {
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; setPadding(50, 40, 50, 40); minimumWidth = 600
                background = GradientDrawable().apply { setColor(Color.parseColor(COLOR_BG)); cornerRadius = 16f }
            }
            val titleView = TextView(context).apply { text = title; textSize = 18f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE); gravity = Gravity.CENTER }
            val msgView = TextView(context).apply { text = message; textSize = 16f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(0, 20, 0, 30) }

            val btnContainer = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            val color = if (isDelete) COLOR_DELETE else COLOR_PRIMARY

            val btnYes = Button(context).apply {
                text = "Evet"; setTextColor(Color.WHITE); background = createButtonDrawable(Color.parseColor(color))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 10 }
            }
            val btnNo = Button(context).apply {
                text = "Hayır"; setTextColor(Color.WHITE); background = createButtonDrawable(Color.DKGRAY)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 10 }
            }

            btnContainer.addView(btnYes); btnContainer.addView(btnNo)
            layout.addView(titleView); layout.addView(msgView); layout.addView(btnContainer)

            val dialog = AlertDialog.Builder(context).setView(layout).create()
            dialog.window?.setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })

            btnYes.setOnClickListener { onConfirm(); dialog.dismiss() }
            btnNo.setOnClickListener { dialog.dismiss() }

            dialog.show()
        }

        private fun createTabButton(text: String, onClick: () -> Unit): Button {
            return Button(context).apply {
                this.text = text; setTextColor(Color.WHITE); textSize = 13f
                minHeight = 0; minimumHeight = 0
                setPadding(15, 10, 15, 10)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 5 }
                setOnClickListener { onClick() }
                isFocusable = true
            }
        }

        private fun createActionButton(text: String, onClick: () -> Unit): Button {
            return Button(context).apply {
                this.text = text;
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = createButtonDrawable(Color.parseColor(COLOR_PRIMARY))
                setOnClickListener { onClick() }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                isFocusable = true
            }
        }

        private fun createLabel(text: String, size: Float = 16f): TextView {
            return TextView(context).apply { this.text = text; textSize = size; setTextColor(Color.WHITE); setPadding(0, 0, 0, 10) }
        }

        private fun createButtonDrawable(color: Int): StateListDrawable {
            return StateListDrawable().apply {
                val shape = GradientDrawable().apply { setColor(color); cornerRadius = 16f; setStroke(2, Color.TRANSPARENT) }
                val focusedShape = GradientDrawable().apply { setColor(Color.parseColor(COLOR_FOCUS)); cornerRadius = 16f; setStroke(2, Color.WHITE) }
                addState(intArrayOf(android.R.attr.state_focused), focusedShape)
                addState(intArrayOf(), shape)
            }
        }
    }
}