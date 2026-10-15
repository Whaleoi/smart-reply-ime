package com.example.smartreplyime.a11y

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务：监听聊天窗口内容变化，提取最后一条消息文本。
 * 只读不写，不自动发送任何内容。需用户在系统设置中手动开启。
 */
class ChatCaptureService : AccessibilityService() {

    companion object {
        @Volatile
        var lastMessage: String? = null
            private set
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return
        val root = rootInActiveWindow ?: return
        try {
            val texts = mutableListOf<String>()
            collectTexts(root, texts, 0)
            // 聊天列表最后一条文本通常就是对方最新消息
            texts.lastOrNull { it.length >= 2 }?.let { lastMessage = it }
        } finally {
            root.recycle()
        }
    }

    override fun onInterrupt() {}

    private fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 40) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTexts(child, out, depth + 1)
            child.recycle()
        }
    }
}
