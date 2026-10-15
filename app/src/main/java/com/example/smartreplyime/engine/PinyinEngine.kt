package com.example.smartreplyime.engine

/**
 * 本地拼音引擎：纯离线，零依赖。
 * - 音节表：常见音节 -> 高频汉字
 * - 整词表：全拼 -> 常用词（多字直接出词）
 * 贪心最长匹配分词，满足日常聊天输入。
 */
object PinyinEngine {

    private const val CHAR_TABLE = """
a=阿啊呵
ai=爱哀挨唉埃
an=安岸暗按案
ang=昂
ao=奥傲熬凹
ba=八把吧爸拔罢
bai=白百摆败拜
ban=半办班般板伴
bang=帮棒傍
bao=包保报宝抱爆
bei=被北杯背悲辈
ben=本笨
beng=崩
bi=比笔必闭彼币碧逼鼻
bian=边变便遍编
biao=表标
bie=别
bin=宾
bing=并病兵冰
bo=波播伯博勃
bu=不步部布补捕
ca=擦
cai=才彩财菜采
can=参餐残惨
cang=藏苍
cao=草操
ce=策侧测
cen=参
ceng=层曾
cha=查茶差插
chai=拆
chan=产颤缠
chang=长常场唱厂
chao=超吵朝潮
che=车彻
chen=沉陈晨尘
cheng=成城程承诚
chi=吃持尺迟赤
chong=充冲重虫
chou=抽愁丑
chu=出初处除楚
chuan=传穿船川
chuang=床窗创
chui=吹垂
chun=春纯唇
chuo=戳
ci=此次词刺慈
cong=从聪葱
cou=凑
cu=粗促醋
cui=催脆
cun=存村
cuo=错
da=大答打达
dai=带代待袋戴
dan=但单蛋淡
dang=当党
dao=到道刀导岛
de=的得德
deng=等灯登
di=的地第低底帝弟
dian=点电店垫
diao=掉钓吊
die=爹跌叠
ding=定顶丁
diu=丢
dong=动东懂冬
dou=都斗豆
du=读度独毒
duan=短断段
dui=对堆
dun=顿盾
duo=多朵夺
e=恶饿鹅额
en=恩
er=儿而二耳
fa=发法罚
fan=反饭翻烦
fang=放方房防
fei=非飞费废
fen=分份粉奋
feng=风封丰峰
fo=佛
fou=否
fu=夫服福复付父
ga=嘎
gai=改该盖
gan=干感敢赶
gang=刚钢
gao=高告
ge=个各哥歌
gei=给
gen=跟根
geng=更
gong=工公共功
gou=够狗购
gu=古故股骨顾
gua=挂瓜
guai=怪
guan=关管观惯
guang=光广
gui=归贵鬼
gun=滚
guo=国过果
ha=哈
hai=还海害
han=汉含喊寒
hang=行航
hao=好号
he=和何合河喝
hei=黑
hen=很恨
heng=横
hong=红轰
hou=后厚候
hu=虎户忽
hua=话花化
huai=坏怀
huan=欢换
huang=黄慌
hui=回会灰
hun=混婚
huo=或火活
ji=几机记己即季基
jia=家加价假
jian=见件建间
jiang=将江讲
jiao=叫教交角
jie=接结节姐
jin=进今近金
jing=经京静景
jiu=就九酒旧
ju=句据具居
juan=卷
jue=觉决
jun=军君
ka=卡
kai=开
kan=看
kang=抗
kao=靠
ke=可科刻
ken=肯
kong=空
kou=口
ku=苦
kua=夸跨
kuai=快块
kuan=款
kuang=况
kui=亏
kun=困
kuo=括
la=拉啦
lai=来
lan=兰蓝
lang=浪
lao=老劳
le=了乐
lei=类累
leng=冷
li=里理力利立李
lia=俩
lian=连脸练
liang=两亮量
liao=了料
lie=列
lin=林临
ling=另领令
liu=六留流
long=龙
lou=楼
lu=路录陆
lv=绿旅
luan=乱
lve=略
lun=论轮
luo=落
ma=吗妈马骂
mai=买卖
man=满慢
mang=忙
mao=毛
me=么
mei=没美每
men=们门
meng=梦
mi=米密
mian=面免
miao=妙苗
mie=灭
min=民
ming=名明
mo=莫默
mou=某
mu=母木目
na=那拿
nai=奶
nan=难男
nao=闹
ne=呢
nei=内
neng=能
ni=你尼
nian=年念
niang=娘
niao=鸟
nin=您
ning=宁
niu=牛
nong=农
nu=怒
nv=女
nuan=暖
nuo=诺
o=哦
ou=偶
pa=怕
pai=排牌
pan=判
pang=旁
pao=跑
pei=配陪
pen=盆
peng=朋
pi=批皮
pian=片
piao=票
pin=品拼
ping=平
po=破婆
pu=普铺
qi=起其七期
qian=前钱千
qiang=强
qiao=桥巧
qie=且
qin=亲
qing=请情
qiong=穷
qiu=求
qu=去区取
quan=全权
que=却确
qun=群
ran=然
rang=让
rao=绕
re=热
ren=人任
reng=仍
ri=日
rong=容
rou=肉
ru=如入
ruan=软
rui=瑞
run=润
ruo=若
sa=洒
sai=赛
san=三
sang=桑
sao=扫
se=色
sen=森
sha=杀
shai=晒
shan=山
shang=上商
shao=少
she=社舍
shei=谁
shen=什身
sheng=生省
shi=是十事时
shou=手受
shu=书数
shua=刷
shuai=衰
shuan=栓
shuang=双
shui=水
shun=顺
shuo=说
si=四思死
song=送
sou=搜
su=速素
suan=算
sui=岁随
sun=损
suo=所
ta=他她它
tai=太
tan=谈
tang=唐
tao=讨
te=特
teng=腾
ti=题体提
tian=天
tiao=条
tie=铁
ting=听
tong=同
tou=头
tu=图土
tuan=团
tui=推
tun=吞
tuo=拖
wa=瓦
wai=外
wan=万完
wang=王往网
wei=为未位
wen=问文
weng=翁
wo=我
wu=五无物
xi=西系喜
xia=下夏
xian=先现线
xiang=想向
xiao=小笑
xie=写谢
xin=新心
xing=行性
xiong=兄
xiu=休
xu=许需
xuan=选
xue=学
xun=寻
ya=呀压
yan=言眼
yang=样
yao=要
ye=也
yi=一以
yin=因音
ying=应英
yong=用
you=有又
yu=于与
yuan=元远
yue=月
yun=云
za=杂
zai=在再
zan=咱
zang=藏
zao=早
ze=则
zei=贼
zen=怎
zeng=增
zha=炸
zhai=摘
zhan=站
zhang=张
zhao=找
zhe=这
zhen=真
zheng=正
zhi=知之
zhong=中重
zhou=周
zhu=主住
zhua=抓
zhuan=转
zhuang=装
zhui=追
zhun=准
zhuo=着
zi=字自
zong=总
zou=走
zu=足
zuan=钻
zui=最
zun=尊
zuo=作
"""

    private const val WORD_TABLE = """
nihao=你好，您好
women=我们
nimen=你们
tamen=他们
shide=是的
bukeqi=不客气
xiexie=谢谢
zaoshanghao=早上好
wanshanghao=晚上好
wanan=晚安
zaijian=再见
buzai=不在
duoshaoqian=多少钱
jiage=价格
weishenme=为什么
zenme=怎么
zenyang=怎样
meiguanxi=没关系
buhaoyisi=不好意思
xiangnile=想你了
chilema=吃了吗
zaiganma=在干嘛
xianzai=现在
yihou=以后
yiqian=以前
jintian=今天
mingtian=明天
zuotian=昨天
gongzuo=工作
xuexi=学习
shijian=时间
meiyou=没有
haishi=还是
danshi=但是
suoyi=所以
yinwei=因为
zhende=真的
keyi=可以
buxing=不行
mashang=马上
shaodeng=稍等
chifan=吃饭
zouba=走吧
haode=好的
kuailai=快来
dengni=等你
"""

    private val charDict: Map<String, List<String>>
    private val wordDict: Map<String, List<String>>
    private val syllablesDesc: List<String>

    init {
        val chars = mutableMapOf<String, List<String>>()
        val words = mutableMapOf<String, List<String>>()
        for (line in CHAR_TABLE.trim().split("\n")) {
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) chars[parts[0].trim()] = parts[1].trim().map { it.toString() }
        }
        for (line in WORD_TABLE.trim().split("\n")) {
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) words[parts[0].trim()] =
                parts[1].trim().split("，", ",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        charDict = chars
        wordDict = words
        syllablesDesc = chars.keys.sortedByDescending { it.length }
    }

    /** 贪心分词；无法匹配的部分作为未知片段返回 */
    fun segment(input: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < input.length) {
            var matched: String? = null
            for (s in syllablesDesc) {
                if (input.startsWith(s, i)) { matched = s; break }
            }
            if (matched == null) {
                out.add(input.substring(i))
                break
            }
            out.add(matched)
            i += matched.length
        }
        return out
    }

    /** 输入末尾的合法音节（无则返回 null） */
    fun lastSyllable(input: String): String? {
        val last = segment(input).lastOrNull() ?: return null
        return if (charDict.containsKey(last)) last else null
    }

    /** 整词候选（全拼完全匹配） */
    fun wordCandidates(input: String): List<String> =
        wordDict[input.trim()] ?: emptyList()

    /** 某音节的高频字候选 */
    fun charCandidates(syllable: String): List<String> =
        charDict[syllable] ?: emptyList()
}
