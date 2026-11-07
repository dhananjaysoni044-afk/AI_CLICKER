package com.aiclicker.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.content.res.Configuration
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.os.Handler
import android.os.Looper

class ClickerKeyboardService : InputMethodService() {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var shifted = false
    private var symbols = false
    private var savedText = ""
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatActive = false
    private var repeatRunnable: Runnable? = null
    private var repeatButtonLabel: TextView? = null
    private val bg = 0xFF171421.toInt()
    private val key = 0xFF302A3D.toInt()
    private val accent = 0xFF8E75FF.toInt()
    private val textColor = Color.WHITE
    private val landscape get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreateInputView(): View {
        val barHeight = if (landscape) 38 else 44
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundColor(bg)
            minimumHeight = dp(if (landscape) 150 else 245)
        }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("AI", "Clipboard", "Clicker", "😊", "GIF").forEach { label ->
            toolbar.addView(chip(label, if (label == "Clicker") accent else key, 11f) {
                when (label) {
                    "AI" -> showTools()
                    "Clipboard" -> pasteClipboard()
                    "Clicker" -> showSavedText()
                    "😊" -> showEmoji()
                    "GIF" -> openGifSearch()
                }
            }, LinearParams(0, barHeight, 1f))
        }
        root.addView(toolbar)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        root.addView(content, LinearParams(-1, 0, 1f))
        status = TextView(this).apply {
            text = "AI CLICKER  •  Ready"
            textSize = 10f
            setTextColor(0xFFBDB5CD.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), 0, 0, 0)
        }
        root.addView(status, LinearParams(-1, dp(18)))
        showKeyboard()
        return root
    }

    private fun LinearParams(w: Int, h: Int, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, if (h == 0) 0 else dp(h), weight)

    private fun chip(label: String, color: Int = key, size: Float = 15f, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = size
            setTextColor(textColor)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(8).toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            setPadding(dp(2), 0, dp(2), 0)
        }

    private fun addRow(parent: LinearLayout, labels: List<String>, height: Int, action: (String) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        labels.forEach { label ->
            val view = chip(label, size = if (label.length > 1) 13f else 17f) { action(label) }
            val lp = LinearLayout.LayoutParams(0, dp(height), 1f)
            lp.setMargins(dp(2), dp(2), dp(2), dp(2))
            row.addView(view, lp)
        }
        parent.addView(row)
    }

    private fun showKeyboard() {
        content.removeAllViews()
        val h = if (landscape) 34 else 46
        if (!symbols) {
            addRow(content, "qwertyuiop".map { it.toString() }, h) { typeKey(it) }
            addRow(content, "asdfghjkl".map { it.toString() }, h) { typeKey(it) }
            val third = listOf("⇧") + "zxcvbnm".map { it.toString() } + "⌫"
            addRow(content, third, h) {
                when (it) {
                    "⇧" -> { shifted = !shifted; showKeyboard() }
                    "⌫" -> currentInputConnection?.deleteSurroundingText(1, 0)
                    else -> typeKey(it)
                }
            }
        } else {
            addRow(content, "1234567890".map { it.toString() }, h) { typeKey(it) }
            addRow(content, listOf("@","#","$","%","&","-","+","(",")","/"), h) { typeKey(it) }
            addRow(content, listOf("ABC","*","? ",":",";","!","?","apos","⌫"), h) {
                when (it) { "ABC" -> { symbols = false; showKeyboard() }; "⌫" -> currentInputConnection?.deleteSurroundingText(1,0); else -> typeKey(it) }
            }
        }
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf(if (symbols) "ABC" else "?123", ",", "🌐", "space", ".", "↵").forEach { label ->
            bottom.addView(chip(label, size = 13f) {
                when (label) {
                    "?123" -> { symbols = true; showKeyboard() }
                    "ABC" -> { symbols = false; showKeyboard() }
                    "🌐" -> (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                    "space" -> commit(" ")
                    "↵" -> commit("\n")
                    else -> commit(label)
                }
            }, LinearLayout.LayoutParams(0, dp(if (landscape) 36 else 43), if (label == "space") 3f else 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
        }
        content.addView(bottom)
    }

    private fun typeKey(value: String) {
        val output = if (shifted && !symbols) value.uppercase() else value
        commit(output)
        if (shifted) { shifted = false; showKeyboard() }
    }

    private fun commit(value: String) { currentInputConnection?.commitText(value, 1) }

    private fun panel(title: String): LinearLayout {
        content.removeAllViews()
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(3), dp(5), dp(3))
        }
        p.addView(TextView(this).apply {
            text = title; textSize = 13f; setTextColor(0xFFBFAEFF.toInt())
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        }, LinearParams(-1, 25))
        content.addView(p, LinearParams(-1, -1))
        p.addView(chip("← Keyboard", accent, 12f) { showKeyboard() }, LinearParams(-1, 34))
        return p
    }

    private fun showSavedText() {
        val p = panel("SAVED TEXT • Insert into the active field")
        val field = EditText(this).apply {
            setText(savedText); hint = "Type or paste your comment/text here"
            setTextColor(Color.WHITE); setHintTextColor(0xFFAAA2B8.toInt())
            textSize = 14f; minLines = 1; maxLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(8), dp(5), dp(8), dp(5))
        }
        p.addView(field, LinearParams(-1, if (landscape) 42 else 54))
        p.addView(chip("INSERT ONCE", key, 12f) {
            savedText = field.text.toString()
            if (savedText.isNotBlank()) {
                val ok = currentInputConnection?.commitText(savedText, 1) ?: false
                status.text = if (ok) "Text inserted once" else "No active text field"
            } else status.text = "Enter text first"
        }, LinearParams(-1, 34))
        val repeatButton = chip("START 🔵  •  2/sec", accent, 13f) {
            savedText = field.text.toString()
            if (repeatActive) {
                stopRepeating()
                repeatButtonLabel?.text = "START 🔵  •  2/sec"
            } else if (savedText.isBlank()) {
                status.text = "Enter text first"
            } else if (currentInputConnection == null) {
                status.text = "Select a text field first"
            } else {
                repeatActive = true
                repeatButtonLabel?.text = "STOP ■"
                status.text = "Repeating saved text • 2 times/sec"
                val task = object : Runnable {
                    override fun run() {
                        if (!repeatActive) return
                        val connection = currentInputConnection
                        if (connection == null) {
                            stopRepeating()
                            repeatButtonLabel?.text = "START 🔵  •  2/sec"
                            status.text = "Stopped • no active text field"
                            return
                        }
                        connection.commitText(savedText, 1)
                        repeatHandler.postDelayed(this, 500L)
                    }
                }
                repeatRunnable = task
                repeatHandler.post(task)
            }
        }
        repeatButtonLabel = repeatButton
        p.addView(repeatButton, LinearParams(-1, 40))
        p.addView(TextView(this).apply {
            text = "Blue button repeats the saved text every 0.5 sec. Press STOP to end. It only types in the selected field; Send stays manual."
            textSize = 10f; setTextColor(0xFFD0C8DA.toInt()); gravity = Gravity.CENTER
        }, LinearParams(-1, 34))
    }

    private fun stopRepeating() {
        repeatActive = false
        repeatRunnable?.let { repeatHandler.removeCallbacks(it) }
        repeatRunnable = null
        status.text = "Repeating stopped"
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopRepeating()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        stopRepeating()
        repeatHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun showTools() {
        val p = panel("OFFLINE TEXT TOOLS")
        fun tool(label: String, transform: (String) -> String) {
            p.addView(chip(label) {
                val ic = currentInputConnection ?: return@chip
                val before = ic.getTextBeforeCursor(500, 0)?.toString().orEmpty()
                if (before.isNotEmpty()) {
                    ic.deleteSurroundingText(before.length, 0)
                    ic.commitText(transform(before), 1)
                } else status.text = "Type some text first"
            }, LinearParams(-1, 34))
        }
        tool("Capitalize sentence") { it.replaceFirstChar { c -> c.uppercase() } }
        tool("Add full stop") { if (it.endsWith(".") || it.endsWith("!") || it.endsWith("?")) it else "$it." }
        tool("UPPERCASE") { it.uppercase() }
    }

    private fun showEmoji() {
        val p = panel("EMOJI")
        listOf("😀","😁","😂","🥹","😊","😎","❤️","✨","🔥","👍","🙌","🎉","🌸","💜","🫶","😅","🤔","😭","🥳","🤩","🙏","💯","🚀","🌙","⭐").chunked(5).forEach { group ->
            addRow(p, group, if (landscape) 30 else 38) { commit(it) }
        }
    }

    private fun pasteClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData? = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            commit(clip.getItemAt(0).coerceToText(this).toString())
            status.text = "Clipboard text pasted"
        } else status.text = "Clipboard is empty"
    }

    private fun openGifSearch() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://tenor.com/search/funny-gifs")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            status.text = "Opening GIF search"
        } catch (_: Exception) { status.text = "Could not open GIF search" }
    }
}