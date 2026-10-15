package com.example.smartreplyime.a11y

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务：智能识别当前屏幕中出现的文字，提取最可能是"对方最新消息"的文本。
 * - 不限定应用：任何聊天/社交/内容界面都可以
 * - 点击输入法"AI回复"按钮时实时读取当前屏幕（captureNow）
 * - 定位输入框位置，只取输入框上方的消息区文字
 * - 优先取屏幕左侧文本（对方消息靠左，自己发的靠右）
 * - 过滤时间戳/角标数字等干扰文本，排除系统栏与输入法自身界面
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

    /** 时间戳样式文本，如 18:27 / 9:05 */
    private val timeRegex = Regex("""^\d{1,2}:\d{2}$""")

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

    private class Scan {
        val texts = mutableListOf<Pair<String, Rect>>()
        var inputTop = -1   // 屏幕最下方输入框的上边缘
    }

    /** 读取当前活动窗口，挑出最像"对方最新消息"的一段文字 */
    private fun readScreen(): String? {
        val root = rootInActiveWindow ?: return null
        try {
            if (root.packageName?.toString() in ignorePackages) return null
            val scan = Scan()
            collectTexts(root, scan, 0)
            if (scan.texts.isEmpty()) return null
            val dm = resources.displayMetrics
            return pickBest(scan, dm.widthPixels, dm.heightPixels)
        } finally {
            root.recycle()
        }
    }

    private fun collectTexts(node: AccessibilityNodeInfo, scan: Scan, depth: Int) {
        if (depth > 50) return
        val r = Rect()
        node.getBoundsInScreen(r)
        if (node.isEditable || node.className?.contains("EditText") == true) {
            // 记录输入框位置，但不把输入框里的文字（用户草稿）当消息
            if (!r.isEmpty && r.height() > 0 && r.top > scan.inputTop) scan.inputTop = r.top
        } else {
            val t = node.text?.toString()?.trim().orEmpty()
            if (t.isNotEmpty() && t.length <= 200 && !r.isEmpty) scan.texts.add(t to r)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTexts(child, scan, depth + 1)
            child.recycle()
        }
    }

    /**
     * 挑选最可能的"对方最新消息"：
     * 1. 只看输入框上方的区域（聊天消息区），过滤时间戳/角标数字
     * 2. 对方消息通常在屏幕左侧（自己发的在右侧），优先取靠左的
     * 3. 同样靠左时取最靠近输入框的（最新消息）
     */
    private fun pickBest(scan: Scan, screenW: Int, screenH: Int): String? {
        val anchor = if (scan.inputTop > 0) scan.inputTop else (screenH * 0.92).toInt()
        val cands = scan.texts.filter { (t, r) ->
            t.length >= 2 &&
                !(timeRegex.matches(t)) &&
                !(t.all { it.isDigit() } && t.length <= 2) &&
                r.centerY() > screenH * 0.12 &&
                r.bottom <= anchor + screenH * 0.02
        }
        if (cands.isEmpty()) return null
        return cands
            .sortedWith(
                compareBy(
                    { it.second.centerX() > screenW * 0.55 },   // 右侧（自己发的）排后面
                    { -it.second.bottom }                        // 越靠下越新
                )
            )
            .firstOrNull()?.first
    }
}
