package com.example.smartreplyime.engine

/**
 * 本地规则引擎：纯离线模板匹配。
 * - 自定义模板由 RuleStore 持久化（优先匹配）
 * - 内置 17 类场景模板兜底
 */
object ReplyEngine {

    data class Rule(val keywords: List<String>, val replies: List<String>)

    private val builtinRules = listOf(
        Rule(listOf("在吗", "在不在", "在？", "在?"), listOf("在的，怎么啦？", "在呢，你说", "我在，什么事？")),
        Rule(listOf("你好", "您好", "hi", "hello", "嗨", "早上好", "晚上好"), listOf("你好呀～", "嗨，你好", "你好，很高兴见到你")),
        Rule(listOf("谢谢", "感谢", "thx", "thanks"), listOf("不客气～", "客气啥", "应该的")),
        Rule(listOf("对不起", "抱歉", "不好意思"), listOf("没关系～", "没事没事", "别放在心上")),
        Rule(listOf("再见", "拜拜", "晚安", "先走了"), listOf("拜拜，回聊～", "晚安，好梦", "再见啦")),
        Rule(listOf("多少钱", "价格", "报价", "怎么卖", "优惠"), listOf("具体要看情况，我私聊你细节？", "价格好商量，咱们详聊？", "我发你一份详细说明？")),
        Rule(listOf("在干嘛", "干什么呢", "忙啥"), listOf("刚忙完，怎么啦？", "没干嘛，你呢？", "在休息哈哈，有事吗？")),
        Rule(listOf("吃了吗", "吃饭没", "吃了没"), listOf("吃啦，你呢？", "还没呢，正准备去", "吃过了，你呢？")),
        Rule(listOf("帮我", "能不能", "可不可以", "拜托"), listOf("好的，没问题", "我看看啊，稍后回复你", "可以呀，发过来吧")),
        Rule(listOf("哈哈", "笑死", "太逗了"), listOf("哈哈哈", "笑死我了", "😂😂")),
        Rule(listOf("为什么", "怎么办", "怎么", "怎样", "如何"), listOf("我看看啊", "这个说来话长，晚点细说？", "我想想，稍后告诉你")),
        Rule(listOf("好的", "行", "可以", "ok", "收到", "嗯"), listOf("好的👌", "行，那就这样", "收到～")),
        Rule(listOf("想你了"), listOf("我也想你了", "哈哈，真的假的", "才分开多久呀")),
        Rule(listOf("累", "辛苦", "加班", "烦", "压力"), listOf("辛苦啦，抱抱你", "注意休息呀", "别太拼，身体要紧")),
        Rule(listOf("有空吗", "方便吗", "什么时候"), listOf("方便的，你说时间", "这周末有空", "你定，我配合你时间")),
        Rule(listOf("？", "?"), listOf("是的", "我觉得可以", "你猜哈哈")),
        Rule(listOf("发货", "快递", "物流", "到哪里"), listOf("我帮你查一下，稍等", "已经在路上了，快了", "明天应该能到")),
    )

    private val fallback = listOf(
        "好的，我知道了", "嗯嗯，好的", "哈哈，好的",
        "收到，我看一下回复你", "这样啊，然后呢？", "真的吗？展开讲讲"
    )

    private val phrases = listOf(
        "好的，我知道了", "收到，我马上处理", "不好意思，刚刚在忙",
        "好的，稍等哈", "没问题，包在我身上", "谢谢，辛苦你了",
        "改天一起聚聚", "有时间再聊", "我先去忙了，回聊",
        "这个价格可以商量", "你看这样可以吗？", "麻烦你发我一下",
        "好的，明天见", "周末有空吗？出来玩", "最近怎么样？",
        "有空一起吃个饭", "你说的对", "我也这么觉得",
        "这事不急，慢慢来", "放心吧，交给我"
    )

    /** 根据对方最新消息生成 3 条回复建议（自定义模板优先） */
    fun smartReply(message: String): List<String> {
        val msg = message.trim()
        if (msg.isEmpty()) return emptyList()
        val lower = msg.lowercase()
        val custom = RuleStore.rules.filter { r -> r.keywords.any { lower.contains(it.lowercase()) } }
        if (custom.isNotEmpty()) return custom.flatMap { it.replies }.distinct().take(3)

        val matched = builtinRules.filter { r -> r.keywords.any { lower.contains(it.lowercase()) } }
        val result = matched.flatMap { it.replies }.distinct().take(3)
        return if (result.isNotEmpty()) result else fallback.shuffled().take(3)
    }

    /** 短语补全 */
    fun autocomplete(current: String): List<String> {
        if (current.length < 2) return emptyList()
        return phrases.filter { it.startsWith(current) || it.contains(current) }.take(3)
    }
}

/** 自定义模板持久化（SharedPreferences + JSON） */
object RuleStore {

    private const val PREFS = "smart_reply_prefs"
    private const val KEY = "custom_rules_v1"

    @Volatile
    var rules: List<ReplyEngine.Rule> = emptyList()
        private set

    fun load(ctx: android.content.Context) {
        val sp = ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val json = sp.getString(KEY, "[]") ?: "[]"
        val parsed = mutableListOf<ReplyEngine.Rule>()
        try {
            val arr = org.json.JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val kws = obj.getJSONArray("k").let { a -> (0 until a.length()).map { a.getString(it) } }
                val reps = obj.getJSONArray("r").let { a -> (0 until a.length()).map { a.getString(it) } }
                if (kws.isNotEmpty() && reps.isNotEmpty()) parsed.add(ReplyEngine.Rule(kws, reps))
            }
        } catch (_: Exception) { /* 忽略损坏数据 */ }
        rules = parsed
    }

    fun save(ctx: android.content.Context, newRules: List<ReplyEngine.Rule>) {
        val arr = org.json.JSONArray()
        newRules.forEach { r ->
            val obj = org.json.JSONObject()
            obj.put("k", org.json.JSONArray(r.keywords))
            obj.put("r", org.json.JSONArray(r.replies))
            arr.put(obj)
        }
        ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
        rules = newRules
    }
}
