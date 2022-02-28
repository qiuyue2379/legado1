package io.legado.app.help


object RuleComplete {

    // 补全时忽略匹配规则
    private val completeIgnore =
        Regex(
            """\\n|##|@js:|<js>|@Json:|\$\.|(attr|text|ownText|textNodes|href|content|html|alt|all|value|src)(\(\)|##.*)?\s*$""",
            RegexOption.MULTILINE
        )

    // 补全时忽略匹配的规则(判断列表项和详情页预处理规则生效)
    private val completeIgnorePreRule = Regex("""^:|##|@js:|<js>|@Json:|\$\.""")

    // 匹配从图片获取信息的规则
    private val imgComplete = Regex(
        """(?<=(tag\.|[+/@~>| &]))img[@/]text(\(\))?$|^img[@/]text(\(\))?$""",
        RegexOption.IGNORE_CASE
    )


    /**
     * 对简单规则进行补全，简化部分书源规则的编写
     * 该方法仅对对JSOUP/XPath/CSS规则生效
     * @author 希弥
     * @return 补全后的规则 或 原规则
     * @param rule 需要补全的规则
     * @param preRule 预处理规则
     *  用于分析详情页预处理规则
     * @param type 补全结果的类型，可选的值有:
     *  1 文字(默认)
     *  2 链接
     *  3 图片
     */
    fun autoComplete(
        rule: String?,
        preRule: String? = null,
        type: Int = 1
    ): String? {
        if (rule.isNullOrEmpty() || rule.contains(completeIgnore)
            || preRule?.contains(completeIgnorePreRule) == true
        ) {
            return rule
        }
        val textRule: String
        val linkRule: String
        val imgRule: String
        val imgText: String
        if (rule.contains(Regex("/[^@]+$"))) {
            textRule = "/text()"
            linkRule = "/@href"
            imgRule = "/@src"
            imgText = "img/@alt"
        } else {
            textRule = "@text"
            linkRule = "@href"
            imgRule = "@src"
            imgText = "img@alt"
        }
        var ret: String = rule
        when (type) {
            1 -> ret = rule.replace(Regex("$"), textRule).replace(imgComplete, imgText)
            2 -> ret = rule.replace(Regex("$"), linkRule)
            3 -> ret = rule.replace(Regex("$"), imgRule)
        }
        return ret
    }


}