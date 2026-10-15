package com.example.smartreplyime.ime

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.smartreplyime.a11y.ChatCaptureService
import com.example.smartreplyime.engine.PinyinEngine
import com.example.smartreplyime.engine.ReplyEngine
import com.example.smartreplyime.engine.RuleStore

/** 键盘按键定义：label=显示文字， smallText=小号字， weight=宽度权重， action=点击动作 */
private class KeySpec(
    val label: String,
    val smallText: Boolean = false,
    val weight: Float = 1f,
    val action: () -> Unit
)

/**
 * 智能回复输入法（简约版）
 * - 中文模式：全拼输入，候选栏出字，数字键/点击选字
 * - 英文模式：直接上屏，本地短语联想
 * - AI回复：基于对方最新消息（无障碍服务抓取）生成建议
 * 键盘为 Canvas 自绘，不依赖系统 Button 渲染，任何 ROM 上表现一致。
 */
class SmartImeService : InputMethodService() {

    // ===== 状态 =====
    private var modeEn = false                  // false=中文拼音, true=英文
    private val pinyinBuf = StringBuilder()     // 中文模式拼音缓冲
    private val buffer = StringBuilder()        // 英文模式已输入缓冲（用于联想）
    private var curWords = listOf<String>()     // 当前整词候选
    private var curChars = listOf<String>()     // 当前单字候选

    // ===== 视图 =====
    private lateinit var composingView: TextView
    private lateinit var candidateRow: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var modeBtn: TextView
    private lateinit var keyboardView: KeyboardView

    // 配色（简约浅色系）
    private val cBg = 0xFFECECF1.toInt()
    private val cKey = 0xFFFFFFFF.toInt()
    private val cKeyText = 0xFF333333.toInt()
    private val cAccent = 0xFF4A7DFF.toInt()
    private val cChip = 0xFFDDE7FF.toInt()

    override fun onCreate() {
        super.onCreate()
        RuleStore.load(applicationContext)
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cBg)
        }

        // ===== 顶栏：模式 + AI回复 + 状态 =====
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(2))
        }

        modeBtn = chip("中") { toggleMode() }
        topBar.addView(modeBtn, LinearLayout.LayoutParams(dp(52), dp(38)))

        val aiBtn = chip("AI回复") { showSmartReplies() }
        val aiLp = LinearLayout.LayoutParams(dp(88), dp(38))
        aiLp.marginStart = dp(6)
        topBar.addView(aiBtn, aiLp)

        statusView = TextView(this).apply {
            text = "  点击候选上屏"
            textSize = 12f
            setTextColor(0xFF888888.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(statusView, LinearLayout.LayoutParams(0, dp(38), 1f))
        root.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ===== 拼音组合显示 =====
        composingView = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF555555.toInt())
            setPadding(dp(12), dp(2), dp(12), dp(2))
            visibility = View.GONE
        }
        root.addView(composingView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ===== 候选栏 =====
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        candidateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), 0, dp(4), dp(2))
        }
        scroll.addView(candidateRow)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ===== 自绘键盘 =====
        keyboardView = KeyboardView(this).apply {
            addRow(*(0..9).map { i ->
                KeySpec(i.toString()) { if (modeEn || i == 0) typeEn(i.toString()) else pickNumber(i) }
            }.toTypedArray())

            fun letters(s: String) = addRow(*s.map { c ->
                KeySpec(c.toString()) { if (modeEn) typeEn(c.toString()) else typePinyin(c.toString()) }
            }.toTypedArray())

            letters("qwertyuiop")
            letters("asdfghjkl")
            letters("zxcvbnm")

            addRow(
                KeySpec("退格", smallText = true) { backspace() },
                KeySpec("空格", smallText = true) { spaceKey() },
                KeySpec("，") { punctKey("，") },
                KeySpec("。") { punctKey("。") },
                KeySpec("？") { punctKey("？") },
                KeySpec("发送", smallText = true) { send() }
            )
        }
        root.addView(keyboardView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        return root
    }

    private fun chip(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(cAccent)
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        pinyinBuf.clear()
        buffer.clear()
        curWords = emptyList(); curChars = emptyList()
        updateComposing(); showCandidates(emptyList())
        statusView.text = "  点击候选上屏"
    }

    // ========== 模式 ==========

    private fun toggleMode() {
        if (!modeEn && pinyinBuf.isNotEmpty()) {
            currentInputConnection?.commitText(pinyinBuf.toString(), 1)
            pinyinBuf.clear()
        }
        modeEn = !modeEn
        modeBtn.text = if (modeEn) "EN" else "中"
        buffer.clear()
        updateComposing(); showCandidates(emptyList())
    }

    // ========== 中文拼音 ==========

    private fun typePinyin(s: String) {
        pinyinBuf.append(s)
        updateComposing(); updatePinyinCandidates()
    }

    private fun updatePinyinCandidates() {
        val input = pinyinBuf.toString()
        curWords = PinyinEngine.wordCandidates(input)
        val syl = PinyinEngine.lastSyllable(input)
        curChars = syl?.let { PinyinEngine.charCandidates(it) } ?: emptyList()

        val all = mutableListOf<String>()
        curWords.take(3).forEach { all.add("「$it」") }
        curChars.take(9 - all.size).forEach { all.add(it) }
        showCandidates(all) { idx -> pickCandidate(idx) }
    }

    private fun pickNumber(n: Int) = pickCandidate(n - 1)

    private fun pickCandidate(index: Int) {
        val ic = currentInputConnection ?: return
        if (index < curWords.size) {
            ic.commitText(curWords[index], 1)
            pinyinBuf.clear()
        } else {
            val ci = index - curWords.size
            if (ci !in curChars.indices) return
            val syl = PinyinEngine.lastSyllable(pinyinBuf.toString()) ?: return
            pinyinBuf.delete(pinyinBuf.length - syl.length, pinyinBuf.length)
            ic.commitText(curChars[ci], 1)
        }
        updateComposing()
        if (pinyinBuf.isEmpty()) {
            curWords = emptyList(); curChars = emptyList(); showCandidates(emptyList())
        } else updatePinyinCandidates()
    }

    private fun spaceKey() {
        if (pinyinBuf.isEmpty()) {
            if (modeEn) typeEn(" ") else currentInputConnection?.commitText(" ", 1)
            return
        }
        when {
            curWords.isNotEmpty() -> pickCandidate(0)
            curChars.isNotEmpty() -> pickCandidate(curWords.size)
            else -> {
                currentInputConnection?.commitText(pinyinBuf.toString(), 1)
                pinyinBuf.clear()
                updateComposing(); showCandidates(emptyList())
            }
        }
    }

    private fun punctKey(s: String) {
        if (pinyinBuf.isNotEmpty()) spaceKey()
        currentInputConnection?.commitText(s, 1)
    }

    private fun updateComposing() {
        if (pinyinBuf.isEmpty()) {
            composingView.visibility = View.GONE
        } else {
            composingView.visibility = View.VISIBLE
            composingView.text = PinyinEngine.segment(pinyinBuf.toString()).joinToString(" ")
        }
    }

    // ========== 英文 ==========

    private fun typeEn(s: String) {
        buffer.append(s)
        currentInputConnection?.commitText(s, 1)
        val suggestions = ReplyEngine.autocomplete(buffer.toString())
        showCandidates(suggestions.map { "联想：$it" }) { idx ->
            val cur = buffer.toString()
            val ic = currentInputConnection ?: return@showCandidates
            ic.deleteSurroundingText(cur.length, 0)
            buffer.clear()
            ic.commitText(suggestions[idx], 1)
            showCandidates(emptyList())
        }
    }

    // ========== 通用 ==========

    private fun backspace() {
        if (pinyinBuf.isNotEmpty()) {
            pinyinBuf.deleteCharAt(pinyinBuf.length - 1)
            updateComposing()
            if (pinyinBuf.isEmpty()) showCandidates(emptyList()) else updatePinyinCandidates()
            return
        }
        if (modeEn && buffer.isNotEmpty()) buffer.deleteCharAt(buffer.length - 1)
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (selected.isNullOrEmpty()) ic.deleteSurroundingText(1, 0) else ic.commitText("", 1)
        if (modeEn && buffer.length < 2) showCandidates(emptyList())
    }

    private fun send() {
        if (pinyinBuf.isNotEmpty()) spaceKey()
        currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_SEND) ?: sendDownUpKeyEvents(66)
    }

    private fun showSmartReplies() {
        if (pinyinBuf.isNotEmpty()) spaceKey()
        val msg = ChatCaptureService.captureNow() ?: ChatCaptureService.lastMessage
        statusView.text = if (msg != null) "  对方: ${msg.take(16)}" else "  未开启消息读取，使用通用建议"
        val suggestions = ReplyEngine.smartReply(msg ?: "")
        showCandidates(suggestions) { idx ->
            currentInputConnection?.commitText(suggestions[idx], 1)
            buffer.clear()
            statusView.text = "  已上屏"
        }
    }

    private fun showCandidates(list: List<String>, onPick: ((Int) -> Unit)? = null) {
        candidateRow.removeAllViews()
        list.forEachIndexed { i, text ->
            val tv = TextView(this).apply {
                this.text = " ${i + 1}. $text "
                textSize = 15f
                setTextColor(0xFF222222.toInt())
                setBackgroundColor(cChip)
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(8), dp(10), dp(8))
                onPick?.let { cb -> setOnClickListener { cb(i) } }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(3), dp(2), dp(3), dp(2))
            candidateRow.addView(tv, lp)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ========== 自绘键盘 ==========

    private inner class KeyboardView(ctx: android.content.Context) : View(ctx) {

        private val rows = mutableListOf<List<KeySpec>>()

        private var pressedRow = -1
        private var pressedCol = -1

        private val bgPaint = Paint().apply { color = cBg }
        private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cKey }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cKeyText
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }

        private val keyH get() = dp(46)
        private val gap get() = dp(4)
        private val padSide get() = dp(3)

        fun addRow(vararg keys: KeySpec) {
            rows.add(keys.toList())
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val h = gap + rows.size * (keyH + gap)
            setMeasuredDimension(w, h)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            var top = gap.toFloat()
            for (r in rows.indices) {
                val row = rows[r]
                val unit = unitWidth(row)
                var left = padSide.toFloat()
                for (c in row.indices) {
                    val key = row[c]
                    val kw = unit * key.weight
                    val rect = RectF(left, top, left + kw, top + keyH)
                    val pressedNow = (r == pressedRow && c == pressedCol)
                    keyPaint.color = if (pressedNow) cChip else cKey
                    canvas.drawRoundRect(rect, dp(6).toFloat(), dp(6).toFloat(), keyPaint)
                    textPaint.textSize = (if (key.smallText) dp(14) else dp(19)).toFloat()
                    val fm = textPaint.fontMetrics
                    val baseline = rect.centerY() - (fm.ascent + fm.descent) / 2f
                    canvas.drawText(key.label, rect.centerX(), baseline, textPaint)
                    left += kw + gap
                }
                top += keyH + gap
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val hit = locate(event.x, event.y) ?: return true
                    setPressed(hit.first, hit.second)
                }
                MotionEvent.ACTION_MOVE -> {
                    val hit = locate(event.x, event.y)
                    if (hit == null) setPressed(-1, -1)
                    else if (hit.first != pressedRow || hit.second != pressedCol) setPressed(hit.first, hit.second)
                }
                MotionEvent.ACTION_UP -> {
                    val hit = locate(event.x, event.y)
                    setPressed(-1, -1)
                    if (hit != null) rows[hit.first][hit.second].action()
                    performClick()
                }
                MotionEvent.ACTION_CANCEL -> setPressed(-1, -1)
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun setPressed(r: Int, c: Int) {
            if (r != pressedRow || c != pressedCol) {
                pressedRow = r; pressedCol = c
                invalidate()
            }
        }

        private fun unitWidth(row: List<KeySpec>): Float {
            val total = width - padSide * 2 - gap * (row.size - 1)
            return total / row.sumOf { it.weight.toDouble() }.toFloat()
        }

        private fun locate(x: Float, y: Float): Pair<Int, Int>? {
            val r = ((y - gap) / (keyH + gap)).toInt()
            if (r < 0 || r >= rows.size) return null
            if (y > gap + r * (keyH + gap) + keyH) return null
            val row = rows[r]
            val unit = unitWidth(row)
            var left = padSide.toFloat()
            for (c in row.indices) {
                val kw = unit * row[c].weight
                if (x >= left && x <= left + kw) return r to c
                left += kw + gap
            }
            return null
        }
    }
}
