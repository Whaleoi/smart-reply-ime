# SmartReplyIME — 智能回复输入法（Android）

纯本地、离线、零 API 的自定义输入法。

## 功能
1. **中文拼音输入**：全拼 → 候选栏出字，整词直接出词（nihao→你好）；数字键或点击选字
2. **智能回复**：无障碍服务抓取对方最新消息 → 规则模板匹配（自定义优先）→ 3 条建议，点击上屏
3. **输入补全**：英文模式随打随联想本地常用短语
4. **自定义模板页**：可视化管理触发关键词与回复，SharedPreferences 持久化，实时生效

## 构建
- Android Studio 打开本目录 → Build APK
- 或 `./gradlew assembleDebug`

## 使用
1. App 内依次点：启用输入法 → 切换输入法 → （可选）开启消息读取
2. 键盘左上角「中/EN」切换中英文；中文模式下数字行是候选快选键 1-9
3. 首页「自定义回复模板」可随时增删改回复规则

## 适配说明
- 键盘全部 weight 布局，自动拉伸适配任意屏宽（手机/平板横竖屏）
- 所有尺寸按屏幕密度换算 dp
- 浅色简约 UI，仅依赖 androidx.core，无其他第三方库

## 扩展
- `engine/PinyinEngine.kt`：CHAR_TABLE / WORD_TABLE 按行追加即可扩充字库词库
- `engine/ReplyEngine.kt`：修改内置模板与联想短语
