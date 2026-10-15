package com.example.smartreplyime.a11y

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务：智能识别当前屏幕中出现的文字，提取最可能是"对方最新消息"的文本。
 * - 不限定应用：任何聊天/社交/内容界面都可以
 * - 点击输入法"AI回复"按钮时实时读取当前屏幕（captureNow）
 * - 自动过滤：系统状态栏/通知栏、自己输入框里的草稿、输入法自身界面
 * - 只读不写，不自动发送任何内容。需用户在系统设置中手动开启。
 */
class ChatCaptureService : AccessibilityService() {

    companion object {
        /** 输入法与无障碍服务在同一进程，点"AI回复"时直接调用 */
        @Volatile
        private var instance: ChatCaptureService? = null

        /** 最近一次识别到的屏幕消息（事件驱动更新的缓存） */
        @Volatile
        var lastMessage: String? = null
            private set

        /** 立即读取当前屏幕，返回最可能的"对方最新消息" */
        fun captureNow(): String? = instance?.readScreen()
    }

    /** 这些包名的内容一律忽略（系统 UI、桌面、输入法自己） */
    private val ignorePackages = setOf(
        "com.android.systemui",          // 状态栏/通知栏
        "com.android.launcher",          // 部分桌面
        "com.android.launcher3",
        "com.example.smartreplyime"      // 输入法自己的界面
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignorePackages) return
        // 只有事件确实来自当前前台应用时才更新缓存，防止后台应用刷事件污染
        val root = rootInActiveWindow ?: return
        try {
            if (root.packageName?.toString() != pkg) return
            readScreen()?.let { lastMessage = it }
        } finally {
            root.recycle()
        }
    }

    override fun onInterrupt() {}

    /** 读取当前活动窗口，挑出最像"对方最新消息"的一段文字 */
    private fun readScreen(): String? {
        val root = rootInActiveWindow ?: return null
        try {
            if (root.packageName?.toString() in ignorePackages) return null
            val texts = mutableListOf<Pair<String, Rect>>()
            collectTexts(root, texts, 0)
            if (texts.isEmpty()) return null
            val screenH = resources.displayMetrics.heightPixels
            return pickBest(texts, screenH)
        } finally {
            root.recycle()
        }
    }

    private fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<Pair<String, Rect>>, depth: Int) {
        if (depth > 50) return
        // 跳过输入框：里面是用户自己打的草稿，不是对方消息
        if (node.className?.contains("EditText") == true) return
        val t = node.text?.toString()?.trim().orEmpty()
        if (t.isNotEmpty() && t.length <= 200) {
            val r = Rect()
            node.getBoundsInScreen(r)
            if (!r.isEmpty) out.add(t to r)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTexts(child, out, depth + 1)
            child.recycle()
        }
    }

    /**
     * 挑选最可能的"对方最新消息"：
     * 1. 过滤掉太靠顶部（标题栏/状态栏区域）和太靠底部（导航栏）的文本
     * 2. 在剩余文本里优先取位置最靠下的（聊天界面最新消息通常在最下方）
     * 3. 同样靠下时优先较长的（消息通常比按钮/标签长）
     */
    private fun pickBest(texts: List<Pair<String, Rect>>, screenH: Int): String? {
        val cands = texts.filter { (t, r) ->
            t.length >= 2 &&
                r.centerY() > screenH * 0.12 &&
                r.centerY() < screenH * 0.92
        }
        if (cands.isEmpty()) return null
        return cands
            .sortedWith(
                compareByDescending<Pair<String, Rect>> { it.second.bottom }
                    .thenByDescending { it.first.length }
            )
            .firstOrNull()?.first
    }
}
