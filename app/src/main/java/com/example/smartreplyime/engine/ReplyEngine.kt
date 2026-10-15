package com.example.smartreplyime.engine

/**
 * 本地规则引擎：纯离线模板匹配。
 * - 自定义模板由 RuleStore 持久化（优先匹配）
 * - 内置 17 类场景模板兜底
 * - 兜底回复池 30 条轮换输出，不会翻来覆去那几句
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
        Rule(listOf("哈哈", "笑死", "太逗了", "666"), listOf("哈哈哈", "笑死我了", "那必须的")),
        Rule(listOf("为什么", "怎么办", "怎么", "怎样", "如何"), listOf("我看看啊", "这个说来话长，晚点细说？", "我想想，稍后告诉你")),
        Rule(listOf("好的", "行", "可以", "ok", "收到", "嗯"), listOf("好的👌", "行，那就这样", "收到～")),
        Rule(listOf("想你了"), listOf("我也想你了", "哈哈，真的假的", "才分开多久呀")),
        Rule(listOf("累", "辛苦", "加班", "烦", "压力"), listOf("辛苦啦，抱抱你", "注意休息呀", "别太拼，身体要紧")),
        Rule(listOf("有空吗", "方便吗", "什么时候"), listOf("方便的，你说时间", "这周末有空", "你定，我配合你时间")),
        Rule(listOf("？", "?"), listOf("是的", "我觉得可以", "你猜哈哈")),
        Rule(listOf("发货", "快递", "物流", "到哪里"), listOf("我帮你查一下，稍等", "已经在路上了，快了", "明天应该能到")),
    )

    /** 兜底回复池：模板都匹配不上时从这里轮换取 */
    private val fallback = listOf(
        "好的，我知道了", "嗯嗯，好的", "哈哈，可以可以", "这样啊，然后呢？",
        "真的吗？展开讲讲", "收到，我看一下回复你", "没问题，交给我", "稍等哈，稍后回你",
        "这事我得想想，晚点说", "你说得对，我也这么觉得", "厉害了，怎么做到的", "稳住，问题不大",
        "okk，那就这样定", "好的，明天见", "有空细聊，我先去忙了", "理解理解，辛苦了",
        "妥，听你的", "这个可以有", "哈哈笑死", "真的假的？别骗我",
        "行吧，那就这样", "我懂了，你继续", "有点东西啊", "羡慕了",
        "好家伙", "收到收到", "好嘞", "懂了懂了", "OK，没问题"
    )

    /** 轮换池：洗乱后每次发 3 条，用完一整轮再重新洗 */
    @Volatile
    private var pool: List<String> = emptyList()

    @Volatile
    private var poolIndex = 0

    private fun nextFallback(): List<String> {
        if (poolIndex >= pool.size) {
            pool = fallback.shuffled()
            poolIndex = 0
        }
        val end = minOf(poolIndex + 3, pool.size)
        val out = pool.subList(poolIndex, end)
        poolIndex = end
        return out
    }

    /** 根据对方最新消息生成 3 条回复建议（自定义模板优先，不足 3 条用兜底补齐） */
    fun smartReply(message: String): List<String> {
        val msg = message.trim()
        if (msg.isEmpty()) return nextFallback()

        val lower = msg.lowercase()
        val customReplies = RuleStore.rules
            .filter { r -> r.keywords.any { lower.contains(it.lowercase()) } }
            .flatMap { it.replies }
            .distinct()
        if (customReplies.isNotEmpty()) {
            if (customReplies.size >= 3) return customReplies.take(3)
            return (customReplies + nextFallback()).distinct().take(3)
        }

        val matched = builtinRules
            .filter { r -> r.keywords.any { lower.contains(it.lowercase()) } }
            .flatMap { it.replies }
            .distinct()
        if (matched.size >= 3) return matched.take(3)
        if (matched.isNotEmpty()) return (matched + nextFallback()).distinct().take(3)

        return nextFallback()
    }

    /** 短语补全 */
    fun autocomplete(current: String): List<String> {
        if (current.length < 2) return emptyList()
        return phrases.filter { it.startsWith(current) || it.contains(current) }.take(3)
    }

    private val phrases = listOf(
        "好的，我知道了", "收到，我马上处理", "不好意思，刚刚在忙",
        "好的，稍等哈", "没问题，包在我身上", "谢谢，辛苦你了",
        "改天一起聚聚", "有时间再聊", "我先去忙了，回聊",
        "这个价格可以商量", "你看这样可以吗？", "麻烦你发我一下",
        "好的，明天见", "周末有空吗？出来玩", "最近怎么样？",
        "有空一起吃个饭", "你说的对", "我也这么觉得",
        "这事不急，慢慢来", "放心吧，交给我"
    )
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
