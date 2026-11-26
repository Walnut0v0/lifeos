package io.lifeos.focuslab

enum class AppCategory(val title: String) {
    STUDY("学习教育"), RESEARCH("科研工具"), PRODUCTIVITY("办公效率"), SOCIAL("通信社交"),
    MEDIA("影音娱乐"), GAME("游戏"), SHOPPING("购物消费"), LIFE("生活服务"),
    SYSTEM("系统工具"), FINANCE("金融支付"), TRAVEL("出行导航"), UNKNOWN("待分类")
}
enum class ClassificationSource { USER, PACKAGE, ANDROID, KEYWORD, UNKNOWN }
enum class ClassificationConfidence { HIGH, MEDIUM, LOW, NONE }
data class Classification(val category: AppCategory, val source: ClassificationSource, val confidence: ClassificationConfidence) {
    val explanation: String get() = when (source) {
        ClassificationSource.USER -> "手动设置"
        ClassificationSource.PACKAGE -> "常见应用规则"
        ClassificationSource.ANDROID -> "应用声明的分类"
        ClassificationSource.KEYWORD -> "名称推测，建议确认"
        ClassificationSource.UNKNOWN -> "需要你确认"
    }
}
object AppClassifier {
    private val exact = mapOf(
        "com.tencent.mm" to AppCategory.SOCIAL,
        "com.tencent.mobileqq" to AppCategory.SOCIAL,
        "org.telegram.messenger" to AppCategory.SOCIAL,
        "com.whatsapp" to AppCategory.SOCIAL,
        "tv.danmaku.bili" to AppCategory.MEDIA,
        "com.ss.android.ugc.aweme" to AppCategory.MEDIA,
        "com.smile.gifmaker" to AppCategory.MEDIA,
        "com.google.android.youtube" to AppCategory.MEDIA,
        "com.spotify.music" to AppCategory.MEDIA,
        "com.taobao.taobao" to AppCategory.SHOPPING,
        "com.jingdong.app.mall" to AppCategory.SHOPPING,
        "com.xunmeng.pinduoduo" to AppCategory.SHOPPING,
        "com.eg.android.AlipayGphone" to AppCategory.FINANCE,
        "com.ichi2.anki" to AppCategory.STUDY,
        "com.duolingo" to AppCategory.STUDY,
        "com.termux" to AppCategory.RESEARCH,
        "com.microsoft.office.word" to AppCategory.PRODUCTIVITY,
        "com.microsoft.office.excel" to AppCategory.PRODUCTIVITY,
        "com.microsoft.office.onenote" to AppCategory.PRODUCTIVITY
    )
    private val keywords = linkedMapOf(
        AppCategory.STUDY to listOf("学习", "课程", "背单词", "词典", "anki", "flashcard", "study"),
        AppCategory.RESEARCH to listOf("科研", "论文", "arxiv", "scholar", "matlab", "python", "research"),
        AppCategory.PRODUCTIVITY to listOf("办公", "笔记", "日历", "待办", "邮件", "文档", "office", "calendar", "notes", "todo"),
        AppCategory.SOCIAL to listOf("微信", "聊天", "通讯", "telegram", "whatsapp", "signal"),
        AppCategory.MEDIA to listOf("视频", "音乐", "直播", "影视", "影音", "抖音", "快手", "哔哩", "video", "music", "youtube", "spotify"),
        AppCategory.GAME to listOf("游戏", "手游", "game", "games", "gaming"),
        AppCategory.SHOPPING to listOf("购物", "淘宝", "京东", "拼多多", "商城", "shopping", "shop"),
        AppCategory.FINANCE to listOf("银行", "支付", "钱包", "金融", "支付宝", "云闪付", "bank", "wallet", "pay", "finance"),
        AppCategory.TRAVEL to listOf("地图", "出行", "打车", "导航", "铁路", "公交", "地铁", "maps", "navigation", "travel", "taxi", "rail"),
        AppCategory.LIFE to listOf("生活", "外卖", "天气", "美团", "饿了么", "weather", "food", "delivery"),
        AppCategory.SYSTEM to listOf("设置", "文件", "计算器", "时钟", "相机", "浏览器", "系统", "工具", "calculator", "clock", "camera", "files", "launcher", "browser")
    )
    fun classify(packageName: String, name: String, androidCategory: Int, userCategory: AppCategory? = null): Classification {
        userCategory?.let { return Classification(it, ClassificationSource.USER, ClassificationConfidence.HIGH) }
        exact[packageName]?.let { return Classification(it, ClassificationSource.PACKAGE, ClassificationConfidence.HIGH) }
        val platform = when (androidCategory) {
            0 -> AppCategory.GAME
            1, 2, 3, 5 -> AppCategory.MEDIA
            4 -> AppCategory.SOCIAL
            6 -> AppCategory.TRAVEL
            7 -> AppCategory.PRODUCTIVITY
            8 -> AppCategory.SYSTEM
            else -> null
        }
        platform?.let { return Classification(it, ClassificationSource.ANDROID, ClassificationConfidence.MEDIUM) }
        val text = "$name $packageName".lowercase(java.util.Locale.ROOT)
        keywords.forEach { (category, words) ->
            if (words.any { word ->
                if (word.any { it.code > 127 }) text.contains(word)
                else Regex("(^|[^a-z])${Regex.escape(word)}([^a-z]|$)").containsMatchIn(text)
            }) return Classification(category, ClassificationSource.KEYWORD, ClassificationConfidence.LOW)
        }
        return Classification(AppCategory.UNKNOWN, ClassificationSource.UNKNOWN, ClassificationConfidence.NONE)
    }
}

object CategoryAccessPolicy {
    fun allows(protected: Boolean, appOverride: Boolean?, categoryAllowed: Boolean): Boolean =
        protected || (appOverride ?: categoryAllowed)
}
