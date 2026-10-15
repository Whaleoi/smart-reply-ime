package com.example.smartreplyime.ime

import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.smartreplyime.a11y.ChatCaptureService
import com.example.smartreplyime.engine.PinyinEngine
import com.example.smartreplyime.engine.ReplyEngine
import com.example.smartreplyime.engine.RuleStore

/**
 * 智能回复输入法（简约版）
 * - 中文模式：全拼输入，候选栏出字，数字键/点击选字
 * - 英文模式：直接上屏，本地短语联想
 * - AI回复：基于对方最新消息（无障碍服务抓取）生成建议
 * 适配：键盘行全部 weight 布局，自动拉伸适配任意屏宽；尺寸按屏幕密度换算 dp
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
    private lateinit var modeBtn: Button

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

        modeBtn = Button(this).apply {
            text = "中"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(cAccent)
            setOnClickListener { toggleMode() }
        }
        topBar.addView(modeBtn, LinearLayout.LayoutParams(dp(52), dp(38)))

        val aiBtn = Button(this).apply {
            text = "AI回复"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(cAccent)
            setOnClickListener { showSmartReplies() }
        }
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

        // ===== 键盘（weight 布局，自动适配屏宽） =====
        fun row(vararg keys: Pair<String, () -> Unit>) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(3), 0, dp(3), 0)
            }
            root.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            keys.forEach { (label, action) ->
                val b = Button(this).apply {
                    text = label
                    textSize = if (label.length == 1) 16f else 13f
                    setTextColor(cKeyText)
                    setBackgroundColor(cKey)
                    setOnClickListener { action() }
                }
                val lp = LinearLayout.LayoutParams(0, dp(46), 1f)
                lp.setMargins(dp(2), dp(2), dp(2), dp(2))
                line.addView(b, lp)
            }
        }

        // 数字行（中文模式下作为候选快选键 1-9，0 保留）
        row(*(0..9).map { i ->
            i.toString() to { if (modeEn || i == 0) typeEn(i.toString()) else pickNumber(i) }
        }.toTypedArray())

        // 字母行
        fun letterRow(s: String) = row(*s.map { c ->
            c.toString() to { if (modeEn) typeEn(c.toString()) else typePinyin(c.toString()) }
        }.toTypedArray())

        letterRow("qwertyuiop")
        letterRow("asdfghjkl")
        letterRow("zxcvbnm")

        // 功能行
        row(
            "退格" to { backspace() },
            "空格" to { spaceKey() },
            "，" to { punctKey("，") },
            "。" to { punctKey("。") },
            "？" to { punctKey("？") },
            "发送" to { send() }
        )

        return root
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
        val msg = ChatCaptureService.lastMessage
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
}
