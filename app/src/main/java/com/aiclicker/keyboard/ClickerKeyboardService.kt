package com.aiclicker.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class ClickerKeyboardService : InputMethodService() {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var startedAt = 0L
    private var durationMs = 5000L
    private var intervalMs = 150L
    private var selectedKey = "a"
    private var clickCount = 0
    private var shifted = false
    private var symbols = false
    private lateinit var status: TextView
    private lateinit var keyInput: EditText
    private lateinit var durationInput: EditText
    private lateinit var speedInput: EditText
    private val keyBg = 0xFF30283F.toInt()
    private val accent = 0xFFB99AFF.toInt()

    override fun onCreateInputView(): android.view.View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(5,7,5,8); setBackgroundColor(0xFF15121F.toInt()) }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("AI", "Clipboard", "Clicker", "😊", "GIF").forEach { label ->
            toolbar.addView(makeButton(label, 11f) {
                when (label) {
                    "AI" -> showLocalTools(root)
                    "Clipboard" -> pasteClipboard()
                    "Clicker" -> showClicker(root)
                    "😊" -> showEmoji(root)
                    "GIF" -> openGifSearch()
                }
            }, LinearLayout.LayoutParams(0,48,1f))
        }
        root.addView(toolbar)
        renderKeys(root)
        status = TextView(this).apply { text = "AI Clicker • Ready"; textSize = 11f; setTextColor(0xFFBDB2D0.toInt()); setPadding(8,4,0,0) }
        root.addView(status)
        return root
    }

    private fun renderKeys(root: LinearLayout) {
        while (root.childCount > 1) root.removeViewAt(1)
        val rows = if (symbols) listOf("1234567890", "@#$%&-*+()", "!?:;'/\"") else listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        rows.forEachIndexed { index, chars ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            if (index == 2) row.addView(makeButton(if (shifted) "⇧●" else "⇧", 14f) { shifted = !shifted; renderKeys(root) }, LinearLayout.LayoutParams(0,47,1f))
            chars.forEach { ch ->
                val value = if (!symbols && shifted) ch.uppercaseChar().toString() else ch.toString()
                row.addView(makeButton(value,16f) { commit(value); if (shifted) { shifted = false; renderKeys(root) } }, LinearLayout.LayoutParams(0,47,1f).apply { setMargins(2,3,2,3) })
            }
            if (index == 2) row.addView(makeButton("⌫",15f) { currentInputConnection?.deleteSurroundingText(1,0) }, LinearLayout.LayoutParams(0,47,1f))
            root.addView(row)
        }
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(if (symbols) "ABC" else "?123", ",", "🌐", "space", ".", "↵").forEach { k ->
            bottom.addView(makeButton(k,14f) {
                when(k) {
                    "?123" -> { symbols = true; renderKeys(root) }
                    "ABC" -> { symbols = false; renderKeys(root) }
                    "space" -> commit(" ")
                    "↵" -> commit("\n")
                    "🌐" -> (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                    else -> commit(k)
                }
            }, LinearLayout.LayoutParams(0,48,if(k == "space") 4f else 1f))
        }
        root.addView(bottom)
    }

    private fun makeButton(label: String, size: Float, action: () -> Unit) = Button(this).apply {
        text = label; textSize = size; isAllCaps = false; setTextColor(Color.WHITE); setPadding(0,0,0,0)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(keyBg)); setOnClickListener { action() }
    }
    private fun commit(value: String) { currentInputConnection?.commitText(value,1) }
    private fun showMessage(value: String) { if (::status.isInitialized) status.text = value }
    private fun panel(root: LinearLayout): LinearLayout {
        if (root.childCount > 1) root.removeViewAt(1)
        return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8,8,8,8); setBackgroundColor(0xFF211B2D.toInt()); root.addView(this,1) }
    }
    private fun showClicker(root: LinearLayout) {
        val p = panel(root)
        p.addView(TextView(this).apply { text = "CLICKER • Repeat into the focused text field"; setTextColor(accent); textSize = 14f })
        keyInput = EditText(this).apply { setSingleLine(); hint = "Selected key or emoji"; setText(selectedKey); setTextColor(Color.WHITE) }
        durationInput = EditText(this).apply { setSingleLine(); hint = "Duration in seconds (1–300)"; inputType = 2; setText("5"); setTextColor(Color.WHITE) }
        speedInput = EditText(this).apply { setSingleLine(); hint = "Delay between clicks in ms (min 50)"; inputType = 2; setText("150"); setTextColor(Color.WHITE) }
        p.addView(keyInput); p.addView(durationInput); p.addView(speedInput)
        val actions = LinearLayout(this)
        actions.addView(makeButton("START",14f) { startRepeating() }, LinearLayout.LayoutParams(0,48,1f))
        actions.addView(makeButton("STOP",14f) { stopRepeating("Stopped by you") }, LinearLayout.LayoutParams(0,48,1f))
        p.addView(actions)
        p.addView(TextView(this).apply { text = "Only inserts text in the active field. It never presses Send or submits a form."; textSize = 11f; setTextColor(0xFFD1C6E0.toInt()) })
    }
    private fun startRepeating() {
        stopRepeating("")
        selectedKey = keyInput.text.toString().ifBlank { "a" }
        durationMs = (durationInput.text.toString().toLongOrNull() ?: 5L).coerceIn(1,300) * 1000L
        intervalMs = (speedInput.text.toString().toLongOrNull() ?: 150L).coerceAtLeast(50L)
        startedAt = System.currentTimeMillis(); clickCount = 0; running = true
        val task = object : Runnable {
            override fun run() {
                if (!running) return
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed >= durationMs) { stopRepeating("Finished • " + clickCount + " insertions"); return }
                val connection: InputConnection? = currentInputConnection
                if (connection == null) { stopRepeating("No focused text field"); return }
                connection.commitText(selectedKey,1); clickCount++
                status.text = "Running • " + clickCount + " insertions • " + ((durationMs - elapsed) / 1000) + "s left"
                handler.postDelayed(this, intervalMs)
            }
        }
        handler.post(task)
    }
    private fun stopRepeating(message: String) {
        running = false
        handler.removeCallbacksAndMessages(null)
        if (message.isNotBlank() && ::status.isInitialized) status.text = message
    }
    private fun pasteClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData? = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) commit(clip.getItemAt(0).coerceToText(this).toString()) else showMessage("Clipboard is empty")
    }
    private fun showEmoji(root: LinearLayout) {
        val p = panel(root)
        val emojis = listOf("😀","😁","😂","🥹","😊","😎","❤️","✨","🔥","👍","🙌","🎉","🌸","💜","🫶","😅","🤔","😭","🥳","🤩","🙏","💯","🚀","🌙","⭐")
        emojis.chunked(5).forEach { group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEach { emoji -> row.addView(makeButton(emoji,22f) { commit(emoji) }, LinearLayout.LayoutParams(0,48,1f)) }
            p.addView(row)
        }
    }
    private fun openGifSearch() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://tenor.com/search/funny-gifs")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            showMessage("Opening GIF search • choose a GIF and copy/paste it")
        } catch (_: Exception) { showMessage("Could not open GIF search") }
    }
    private fun showLocalTools(root: LinearLayout) {
        val p = panel(root)
        p.addView(TextView(this).apply { text = "OFFLINE SMART TOOLS"; setTextColor(accent); textSize = 14f })
        fun addTool(label: String, transform: (String) -> String) {
            p.addView(makeButton(label,13f) {
                val connection = currentInputConnection ?: return@makeButton
                val before = connection.getTextBeforeCursor(500,0)?.toString().orEmpty()
                if (before.isNotEmpty()) { connection.deleteSurroundingText(before.length,0); connection.commitText(transform(before),1) }
            })
        }
        addTool("Capitalize sentence") { it.replaceFirstChar { ch -> ch.uppercase() } }
        addTool("Add full stop") { if (it.endsWith(".") || it.endsWith("!") || it.endsWith("?")) it else it + "." }
        addTool("UPPERCASE") { it.uppercase() }
        p.addView(TextView(this).apply { text = "Basic offline text tools. Real offline generative AI is intentionally left out."; setTextColor(0xFFD1C6E0.toInt()); textSize = 11f })
    }
    override fun onFinishInput() { stopRepeating(""); super.onFinishInput() }
    override fun onDestroy() { stopRepeating(""); super.onDestroy() }
}
