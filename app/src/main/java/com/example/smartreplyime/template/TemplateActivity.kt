package com.example.smartreplyime.template

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.example.smartreplyime.engine.ReplyEngine
import com.example.smartreplyime.engine.RuleStore

/**
 * 自定义回复模板管理页（简约）
 * - 点列表项：编辑 / 删除
 * - 底部按钮：新增
 * 数据存 SharedPreferences，输入法实时生效。
 */
class TemplateActivity : Activity() {

    private lateinit var listView: ListView
    private val items = mutableListOf<ReplyEngine.Rule>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RuleStore.load(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }

        root.addView(TextView(this).apply {
            text = "自定义回复模板"
            textSize = 20f
        })

        root.addView(TextView(this).apply {
            text = "匹配优先级高于内置模板 · 点击条目编辑或删除"
            textSize = 13f
            setTextColor(0xFF888888.toInt())
            setPadding(0, dp(6), 0, dp(10))
        })

        listView = ListView(this)
        root.addView(listView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(Button(this).apply {
            text = "＋ 新增模板"
            setOnClickListener { showEditDialog(-1) }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(root)
        refresh()
        listView.setOnItemClickListener { _, _, position, _ -> showEditDialog(position) }
    }

    private fun refresh() {
        items.clear()
        items.addAll(RuleStore.rules)
        listView.adapter = android.widget.ArrayAdapter(
            this, android.R.layout.simple_list_item_1,
            items.map { "【${it.keywords.joinToString(" / ")}】→ ${it.replies.joinToString("；")}" }
        )
    }

    private fun showEditDialog(position: Int) {
        val editing = position >= 0
        val rule = if (editing) items[position] else null

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val kwInput = EditText(this).apply {
            hint = "触发关键词，多个用逗号分隔，如：在吗，在？"
            setText(rule?.keywords?.joinToString("，"))
        }
        val repInput = EditText(this).apply {
            hint = "回复内容，多条用 | 分隔"
            setText(rule?.replies?.joinToString(" | "))
            minLines = 2
            gravity = Gravity.TOP
        }
        container.addView(kwInput)
        container.addView(repInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (editing) "编辑模板" else "新增模板")
            .setView(container)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .apply {
                if (editing) setNeutralButton("删除") { _, _ ->
                    val newList = items.toMutableList().also { it.removeAt(position) }
                    RuleStore.save(applicationContext, newList)
                    refresh()
                }
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val kws = kwInput.text.toString().split("，", ",", "、")
                    .map { it.trim() }.filter { it.isNotEmpty() }
                val reps = repInput.text.toString().split("|", "｜")
                    .map { it.trim() }.filter { it.isNotEmpty() }
                if (kws.isEmpty() || reps.isEmpty()) {
                    if (kws.isEmpty()) kwInput.error = "关键词不能为空"
                    if (reps.isEmpty()) repInput.error = "回复不能为空"
                    return@setOnClickListener
                }
                val newList = items.toMutableList()
                if (editing) newList[position] = ReplyEngine.Rule(kws, reps)
                else newList.add(ReplyEngine.Rule(kws, reps))
                RuleStore.save(applicationContext, newList)
                refresh()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
