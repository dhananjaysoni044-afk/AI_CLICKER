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
import android.widget.ScrollView
import org.json.JSONArray
import org.json.JSONObject
import android.os.Handler
import android.os.Looper

class ClickerKeyboardService : InputMethodService() {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var capsLock = false
    private var symbols = false
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var spaceRepeatActive = false
    private var spaceRepeatRunnable: Runnable? = null
    private val clipboardItems = mutableListOf<ClipboardEntry>()
    private lateinit var clipboardManager: ClipboardManager
    private data class ClipboardEntry(val text: String, val time: Long, var pinned: Boolean)
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener { captureClipboard() }
    private val clipboardPrefs by lazy { getSharedPreferences("aiclicker_clipboard", Context.MODE_PRIVATE) }
    private val expiryMs = 24L * 60L * 60L * 1000L
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
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        loadClipboard()
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("AI", "Clipboard", "😊", "GIF").forEach { label ->
            toolbar.addView(chip(label, if (label == "Clipboard") accent else key, 12f) {
                when (label) {
                    "AI" -> showTools()
                    "Clipboard" -> showClipboard()
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
            val view = chip(label, size = if (label.length > 1) 15f else 21f) { action(label) }
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
            val third = listOf(if (capsLock) "⇪" else "⇧") + "zxcvbnm".map { it.toString() } + "⌫"
            addRow(content, third, h) {
                when (it) {
                    "⇧", "⇪" -> { capsLock = !capsLock; showKeyboard() }
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
        listOf(if (symbols) "ABC" else "?123", ",", "🌐", "␠ SPACE • 5/s", ".", "↵").forEach { label ->
            val isSpace = label.startsWith("␠")
            bottom.addView(chip(label, if (isSpace) accent else key, if (isSpace) 12f else 14f) {
                when {
                    label == "?123" -> { symbols = true; showKeyboard() }
                    label == "ABC" -> { symbols = false; showKeyboard() }
                    label == "🌐" -> (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                    isSpace -> toggleSpaceRepeat()
                    label == "↵" -> commit("\\n")
                    else -> commit(label)
                }
            }, LinearLayout.LayoutParams(0, dp(if (landscape) 38 else 46), if (isSpace) 3f else 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
        }
        content.addView(bottom)
    }

    private fun typeKey(value: String) {
        val output = if (capsLock && !symbols) value.uppercase() else value
        commit(output)
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

    private fun toggleSpaceRepeat() {
        if (spaceRepeatActive) {
            stopSpaceRepeat()
            status.text = "Space repeat stopped"
            return
        }
        if (currentInputConnection == null) {
            status.text = "Select a text field first"
            return
        }
        spaceRepeatActive = true
        status.text = "Typing spaces • 5 per second • tap blue SPACE to stop"
        val task = object : Runnable {
            override fun run() {
                if (!spaceRepeatActive) return
                val connection = currentInputConnection
                if (connection == null) {
                    stopSpaceRepeat()
                    status.text = "Space repeat stopped • no active field"
                    return
                }
                connection.commitText(" ", 1)
                repeatHandler.postDelayed(this, 200L)
            }
        }
        spaceRepeatRunnable = task
        repeatHandler.post(task)
    }

    private fun stopSpaceRepeat() {
        spaceRepeatActive = false
        spaceRepeatRunnable?.let { repeatHandler.removeCallbacks(it) }
        spaceRepeatRunnable = null
    }

    private fun loadClipboard() {
        clipboardItems.clear()
        val raw = clipboardPrefs.getString("items", "[]") ?: "[]"
        try {
            val array = JSONArray(raw)
            val now = System.currentTimeMillis()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val text = obj.optString("text")
                val time = obj.optLong("time")
                val pinned = obj.optBoolean("pinned")
                if (text.isNotBlank() && (pinned || now - time < expiryMs)) {
                    clipboardItems.add(ClipboardEntry(text, time, pinned))
                }
            }
        } catch (_: Exception) { }
        saveClipboard()
    }

    private fun saveClipboard() {
        val array = JSONArray()
        clipboardItems.forEach { item ->
            array.put(JSONObject().put("text", item.text).put("time", item.time).put("pinned", item.pinned))
        }
        clipboardPrefs.edit().putString("items", array.toString()).apply()
    }

    private fun captureClipboard() {
        try {
            val clip = clipboardManager.primaryClip ?: return
            if (clip.itemCount == 0) return
            val copied = clip.getItemAt(0).coerceToText(this)?.toString()?.trim().orEmpty()
            if (copied.isBlank()) return
            addClipboardItem(copied)
        } catch (_: Exception) { }
    }

    private fun addClipboardItem(value: String) {
        val now = System.currentTimeMillis()
        val existing = clipboardItems.firstOrNull { it.text == value }
        val wasPinned = existing?.pinned ?: false
        clipboardItems.removeAll { it.text == value }
        clipboardItems.add(0, ClipboardEntry(value, now, wasPinned))
        while (clipboardItems.size > 8) {
            val oldestUnpinned = clipboardItems.indexOfLast { !it.pinned }
            if (oldestUnpinned >= 0) clipboardItems.removeAt(oldestUnpinned) else break
        }
        saveClipboard()
    }

    private fun showClipboard() {
        val now = System.currentTimeMillis()
        clipboardItems.removeAll { !it.pinned && now - it.time >= expiryMs }
        saveClipboard()
        val p = panel("CLIPBOARD • 24H AUTO-SAVE • PIN TO KEEP")
        if (clipboardItems.isEmpty()) {
            p.addView(TextView(this).apply {
                text = "Copied text will appear here while AI CLICKER keyboard is active."
                textSize = 12f; setTextColor(0xFFD0C8DA.toInt()); gravity = Gravity.CENTER
            }, LinearParams(-1, 52))
            return
        }
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clipboardItems.toList().forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(2), dp(2), dp(2))
            }
            val paste = chip((if (item.pinned) "📌 " else "") + item.text.replace("\n", " "), key, 11f) {
                commit(item.text)
                status.text = "Clipboard item inserted"
            }
            row.addView(paste, LinearLayout.LayoutParams(0, dp(34), 1f))
            row.addView(chip(if (item.pinned) "UNPIN" else "PIN", accent, 10f) {
                val index = clipboardItems.indexOfFirst { it.text == item.text }
                if (index >= 0) clipboardItems[index].pinned = !clipboardItems[index].pinned
                saveClipboard()
                showClipboard()
            }, LinearParams(52, 34))
            row.addView(chip("✕", 0xFF8B3547.toInt(), 13f) {
                clipboardItems.removeAll { it.text == item.text }
                saveClipboard()
                showClipboard()
            }, LinearParams(32, 34))
            list.addView(row)
        }
        scroll.addView(list)
        p.addView(scroll, LinearParams(-1, 0).apply { height = 0; weight = 1f })
        p.addView(chip("CLEAR ALL UNPINNED", 0xFF55445F.toInt(), 11f) {
            clipboardItems.removeAll { !it.pinned }
            saveClipboard()
            showClipboard()
        }, LinearParams(-1, 30))
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopSpaceRepeat()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        stopSpaceRepeat()
        if (::clipboardManager.isInitialized) clipboardManager.removePrimaryClipChangedListener(clipboardListener)
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