package io.legado.app.model.analyzeRule

import androidx.annotation.Keep
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.ReadContext
import java.util.*

@Suppress("RegExpRedundantEscape")
@Keep
class AnalyzeByJSonPath(json: Any) {

    companion object {

        fun parse(json: Any): ReadContext {
            return when (json) {
                is ReadContext -> json
                is String -> JsonPath.parse(json) //JsonPath.parse<String>(json)
                else -> JsonPath.parse(json) //JsonPath.parse<Any>(json)
            }
        }
    }

    private var ctx: ReadContext = parse(json)

    /**
     * 改进解析方法
     * 解决阅读”&&“、”||“与jsonPath支持的”&&“、”||“之间的冲突
     * 解决{$.rule}形式规则可能匹配错误的问题，旧规则用正则解析内容含‘}’的json文本时，用规则中的字段去匹配这种内容会匹配错误.现改用平衡嵌套方法解决这个问题
     * */
    fun getString(rule: String): String? {
        if (rule.isEmpty()) return null
        var result: String
        val ruleAnalyzes = RuleAnalyzer(rule)
        val rules = ruleAnalyzes.splitRule("&&","||")

        if (rules.size == 1) {

            ruleAnalyzes.reSetPos() //将pos重置为0，复用解析器

            result = ruleAnalyzes.innerRule("{$."){ getString(it) } //替换所有{$.rule...}

            if (result.isEmpty()) { //st为空，表明无成功替换的内嵌规则

                try {

                    val ob = ctx.read<Any>(rule)

                    result =(if (ob is List<*>) {

                        val builder = StringBuilder()
                        for (o in ob) {
                            builder.append(o).append("\n")
                        }

                        builder.deleteCharAt(builder.lastIndex) //删除末尾赘余换行

                        builder

                    } else ob).toString()

                } catch (ignored: Exception) {
                }

            }

            return result

        } else {
            val textList = arrayListOf<String>()
            for (rl in rules) {
                val temp = getString(rl)
                if (!temp.isNullOrEmpty()) {
                    textList.add(temp)
                    if (ruleAnalyzes.elementsType == "||") {
                        break
                    }
                }
            }
            return textList.joinToString("\n")
        }
    }

    internal fun getStringList(rule: String): List<String> {
        val result = ArrayList<String>()
        if (rule.isEmpty()) return result
        val ruleAnalyzes = RuleAnalyzer(rule)
        val rules = ruleAnalyzes.splitRule("&&","||","%%")

        if (rules.size == 1) {

            ruleAnalyzes.reSetPos() //将pos重置为0，复用解析器

            val st = ruleAnalyzes.innerRule("{$."){ getString(it) } //替换所有{$.rule...}

            if (st.isEmpty()) { //st为空，表明无成功替换的内嵌规则

                try {

                    val obj = ctx.read<Any>(rule) //kotlin的Any型返回值不包含null ，删除赘余 ?: return result

                    if (obj is List<*>) {

                        for (o in obj) result.add(o.toString())

                    } else result.add(obj.toString())

                } catch (ignored: Exception) {
                }

            }else result.add(st)

            return result

        } else {
            val results = ArrayList<List<String>>()
            for (rl in rules) {
                val temp = getStringList(rl)
                if (temp.isNotEmpty()) {
                    results.add(temp)
                    if (temp.isNotEmpty() && ruleAnalyzes.elementsType == "||") {
                        break
                    }
                }
            }
            if (results.size > 0) {
                if ("%%" == ruleAnalyzes.elementsType) {
                    for (i in results[0].indices) {
                        for (temp in results) {
                            if (i < temp.size) {
                                result.add(temp[i])
                            }
                        }
                    }
                } else {
                    for (temp in results) {
                        result.addAll(temp)
                    }
                }
            }
            return result
        }
    }

    internal fun getObject(rule: String): Any {
        return ctx.read(rule)
    }

    internal fun getList(rule: String): ArrayList<Any>? {
        val result = ArrayList<Any>()
        if (rule.isEmpty()) return result
        val ruleAnalyzes = RuleAnalyzer(rule)
        val rules = ruleAnalyzes.splitRule("&&","||","%%")
        if (rules.size == 1) {
            ctx.let {
                try {
                    return it.read<ArrayList<Any>>(rules[0])
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return null
        } else {
            val results = ArrayList<ArrayList<*>>()
            for (rl in rules) {
                val temp = getList(rl)
                if (temp != null && temp.isNotEmpty()) {
                    results.add(temp)
                    if (temp.isNotEmpty() && ruleAnalyzes.elementsType == "||") {
                        break
                    }
                }
            }
            if (results.size > 0) {
                if ("%%" == ruleAnalyzes.elementsType) {
                    for (i in 0 until results[0].size) {
                        for (temp in results) {
                            if (i < temp.size) {
                                temp[i]?.let { result.add(it) }
                            }
                        }
                    }
                } else {
                    for (temp in results) {
                        result.addAll(temp)
                    }
                }
            }
        }
        return result
    }

}