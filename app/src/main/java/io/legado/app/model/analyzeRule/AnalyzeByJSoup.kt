package io.legado.app.model.analyzeRule

import android.text.TextUtils.join
import androidx.annotation.Keep
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Collector
import org.jsoup.select.Elements
import org.jsoup.select.Evaluator
import org.seimicrawler.xpath.JXNode
import java.util.*

/**
 * Created by GKF on 2018/1/25.
 * 书源规则解析
 */
@Keep
class AnalyzeByJSoup(doc: Any) {
    companion object {
        /**
         * "class", "id", "tag", "text", "children"
         */
        val validKeys = arrayOf("class", "id", "tag", "text", "children")

        fun parse(doc: Any): Element {
            return when (doc) {
                is Element -> doc
                is JXNode -> if (doc.isElement) doc.asElement() else Jsoup.parse(doc.toString())
                else -> Jsoup.parse(doc.toString())
            }
        }

    }

    private var element: Element = parse(doc)

    /**
     * 获取列表
     */
    internal fun getElements(rule: String) = getElements(element, rule)

    /**
     * 合并内容列表,得到内容
     */
    internal fun getString(ruleStr: String) =
        if(ruleStr.isEmpty()) null
        else getStringList(ruleStr).takeIf { it.isNotEmpty() }?.joinToString("\n")

    /**
     * 获取一个字符串
     */
    internal fun getString0(ruleStr: String) = getStringList(ruleStr).let{ if ( it.isEmpty() ) "" else it[0] }

    /**
     * 获取所有内容列表
     */
    internal fun getStringList(ruleStr: String): List<String> {

        val textS = ArrayList<String>()

        if (ruleStr.isEmpty()) return textS

        //拆分规则
        val sourceRule = SourceRule(ruleStr)

        if (sourceRule.elementsRule.isEmpty()) {

            textS.add(element.data() ?: "")

        } else {

            val ruleAnalyzes = RuleAnalyzer(sourceRule.elementsRule)
            val ruleStrS = ruleAnalyzes.splitRule("&&","||" ,"%%")

            val results = ArrayList<List<String>>()
            for (ruleStrX in ruleStrS) {

                val temp: List<String>? =
                    if (sourceRule.isCss) {
                        val lastIndex = ruleStrX.lastIndexOf('@')
                        getResultLast(
                            element.select(ruleStrX.substring(0, lastIndex)),
                            ruleStrX.substring(lastIndex + 1)
                        )
                    } else {
                        getResultList(ruleStrX)
                    }

                if (!temp.isNullOrEmpty()) {

                    results.add(temp) //!temp.isNullOrEmpty()时，results.isNotEmpty()为true

                    if (ruleAnalyzes.elementsType == "||") break

                }
            }
            if (results.size > 0) {
                if ("%%" == ruleAnalyzes.elementsType) {
                    for (i in results[0].indices) {
                        for (temp in results) {
                            if (i < temp.size) {
                                textS.add(temp[i])
                            }
                        }
                    }
                } else {
                    for (temp in results) {
                        textS.addAll(temp)
                    }
                }
            }
        }
        return textS
    }

    /**
     * 获取Elements
     */
    private fun getElements(temp: Element?, rule: String): Elements {

        if (temp == null || rule.isEmpty()) return Elements()

        val elements = Elements()

        val sourceRule = SourceRule(rule)
        val ruleAnalyzes = RuleAnalyzer(sourceRule.elementsRule)
        val ruleStrS = ruleAnalyzes.splitRule("&&","||","%%")

        val elementsList = ArrayList<Elements>()
        if (sourceRule.isCss) {
            for (ruleStr in ruleStrS) {
                val tempS = temp.select(ruleStr)
                elementsList.add(tempS)
                if (tempS.size > 0 && ruleAnalyzes.elementsType == "||") {
                    break
                }
            }
        } else {
            for (ruleStr in ruleStrS) {
                //将原getElementsSingle函数调用的函数的部分代码内联过来，方便简化getElementsSingle函数

                val rsRule = RuleAnalyzer(ruleStr)

                if( rsRule.peek() =='@' || rsRule.peek() < '!' ) rsRule.advance()  // 修剪当前规则之前的"@"或者空白符

                val rs = rsRule.splitRule("@")

                val el = if (rs.size > 1) {
                    val el = Elements()
                    el.add(temp)
                    for (rl in rs) {
                        val es = Elements()
                        for (et in el) {
                            es.addAll(getElements(et, rl))
                        }
                        el.clear()
                        el.addAll(es)
                    }
                    el
                }else getElementsSingle(temp,ruleStr)

                elementsList.add(el)
                if (el.size > 0 && ruleAnalyzes.elementsType == "||") {
                    break
                }
            }
        }
        if (elementsList.size > 0) {
            if ("%%" == ruleAnalyzes.elementsType) {
                for (i in 0 until elementsList[0].size) {
                    for (es in elementsList) {
                        if (i < es.size) {
                            elements.add(es[i])
                        }
                    }
                }
            } else {
                for (es in elementsList) {
                    elements.addAll(es)
                }
            }
        }
        return elements
    }

    /**
     * 1.支持阅读原有写法，':'分隔索引，!或.表示筛选方式，索引可为负数
     *
     * 例如 tag.div.-1:10:2 或 tag.div!0:3
     *
     * 2. 支持与jsonPath类似的[]索引写法
     *
     * 格式形如 [it,it，。。。] 或 [!it,it，。。。] 其中[!开头表示筛选方式为排除，it为单个索引或区间。
     *
     * 区间格式为 start:end 或 start:end:step，其中start为0可省略，end为-1可省略。
     *
     * 索引，区间两端及间隔都支持负数
     *
     * 例如 tag.div[-1, 3:-2:-10, 2]
     *
     * 特殊用法 tag.div[-1:0] 可在任意地方让列表反向
     *
     * */

    fun findIndexSet( rule:String ): IndexSet {

        val indexSet = IndexSet()
        val rus = rule.trim{ it <= ' '}

        var len = rus.length
        var curInt: Int? //当前数字
        var curMinus = false //当前数字是否为负
        val curList = mutableListOf<Int?>() //当前数字区间
        var l = "" //暂存数字字符串

        val head = rus[rus.length-1] == ']' //是否为常规索引写法

        if(head){ //常规索引写法[index...]

            len-- //跳过尾部']'

            while (len-- > 0) { //逆向遍历,至少有一位前置字符,如 [

                var rl = rus[len]
                if (rl == ' ') continue //跳过空格

                if (rl in '0'..'9') l += rl //将数值累接入临时字串中，遇到分界符才取出
                else if (rl == '-') curMinus = true
                else {

                    curInt = if (l.isEmpty()) null else if (curMinus) -l.toInt() else l.toInt() //当前数字

                    when (rl) {

                        ':' -> curList.add(curInt) //区间右端或区间间隔

                        else -> {

                            //为保证查找顺序，区间和单个索引都添加到同一集合
                            if(curList.isEmpty())indexSet.indexs.add(curInt!!)
                            else{

                                //列表最后压入的是区间右端，若列表有两位则最先压入的是间隔
                                indexSet.indexs.add( Triple(curInt, curList.last(), if(curList.size == 2) curList.first() else 1) )

                                curList.clear() //重置临时列表，避免影响到下个区间的处理

                            }

                            if(rl == '!'){
                                indexSet.split='!'
                                do{ rl = rus[--len] } while (len > 0 && rl == ' ')//跳过所有空格
                            }

                            if(rl == '[') return indexSet.apply {
                                beforeRule = rus.substring(0, len)
                            } //遇到索引边界，返回结果

                            if(rl != ',') break //非索引结构，跳出

                        }
                    }

                    l = "" //清空
                    curMinus = false //重置
                }
            }
        } else while (len --> 1) { //阅读原本写法，逆向遍历,至少两位前置字符,如 p.

            val rl = rus[len]
            if (rl == ' ') continue //跳过空格

            if (rl in '0'..'9') l += rl //将数值累接入临时字串中，遇到分界符才取出
            else if (rl == '-') curMinus = true
            else {

                if(rl == '!'  || rl == '.' || rl == ':') { //分隔符或起始符

                    indexSet.indexDefault.add(if (curMinus) -l.toInt() else l.toInt()) // 当前数字追加到列表

                    if (rl != ':') return indexSet.apply { //rl == '!'  || rl == '.'
                        split = rl
                        beforeRule = rus.substring(0, len)
                    }

                }else break //非索引结构，跳出循环

                l = "" //清空
                curMinus = false //重置
            }

        }

        return indexSet.apply{
            split = ' '
            beforeRule = rus } //非索引格式
    }

    /**
     * 获取Elements按照一个规则
     */
    private fun getElementsSingle(temp: Element, rule: String): Elements {

        var elements = Elements()

        val fi = findIndexSet(rule) //执行索引列表处理器

        val (filterType,ruleStr) = fi //获取操作类型及非索引部分的规则字串

//        val rulePc = rulePcx[0].trim { it <= ' ' }.split(">")
//        jsoup中，当前节点是参与选择的，tag.div 与 tag.div@tag.div 结果相同
//        此处">"效果和“@”完全相同，且容易让人误解成选择子节点，实际并不是。以后不允许这种无意义的写法

        val rules = ruleStr.split(".")

        elements.addAll(
            when (rules[0]) {
                "children" -> temp.children()
                "class" -> temp.getElementsByClass(rules[1])
                "tag" -> temp.getElementsByTag(rules[1])
                "id" -> Collector.collect(Evaluator.Id(rules[1]), temp)
                "text" -> temp.getElementsContainingOwnText(rules[1])
                else -> temp.select(ruleStr)
            } )

        val indexSet = fi.getIndexs(elements.size) //传入元素数量，处理负数索引及索引越界问题，生成可用索引集合。

        if(filterType == '!'){ //排除

            for (pcInt in indexSet) elements[pcInt] = null

            elements.removeAll(listOf(null)) //测试过，这样就行

        }else if(filterType == '.'){ //选择

            val es = Elements()

            for (pcInt in indexSet) es.add(elements[pcInt])

            elements = es

        }

        return elements
    }

    /**
     * 获取内容列表
     */
    private fun getResultList(ruleStr: String): List<String>? {

        if (ruleStr.isEmpty()) return null

        var elements = Elements()

        elements.add(element)

        val rule = RuleAnalyzer(ruleStr) //创建解析

        while( rule.peek() =='@' || rule.peek() < '!' ) rule.advance()  // 修剪当前规则之前的"@"或者空白符

        val rules = rule.splitRule("@") // 切割成列表

        val last = rules.size - 1
        for (i in 0 until last) {
            val es = Elements()
            for (elt in elements) {
                es.addAll(getElementsSingle(elt, rules[i]))
            }
            elements.clear()
            elements = es
        }
        return if (elements.isEmpty()) null else getResultLast(elements, rules[last])
    }

    /**
     * 根据最后一个规则获取内容
     */
    private fun getResultLast(elements: Elements, lastRule: String): List<String> {
        val textS = ArrayList<String>()
        try {
            when (lastRule) {
                "text" -> for (element in elements) {
                    textS.add(element.text())
                }
                "textNodes" -> for (element in elements) {
                    val tn = arrayListOf<String>()
                    val contentEs = element.textNodes()
                    for (item in contentEs) {
                        val temp = item.text().trim { it <= ' ' }
                        if (temp.isNotEmpty()) {
                            tn.add(temp)
                        }
                    }
                    textS.add(join("\n", tn))
                }
                "ownText" -> for (element in elements) {
                    textS.add(element.ownText())
                }
                "html" -> {
                    elements.select("script").remove()
                    elements.select("style").remove()
                    val html = elements.outerHtml()
                    textS.add(html)
                }
                "all" -> textS.add(elements.outerHtml())
                else -> for (element in elements) {

                    val url = element.attr(lastRule)

                    if(url.isEmpty() || textS.contains(url)) break

                    textS.add(url)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return textS
    }

    data class IndexSet(var split:Char = '.',
                        var beforeRule:String = "",
                        val indexDefault:MutableList<Int> = mutableListOf(),
                        val indexs:MutableList<Any> = mutableListOf()){

        fun getIndexs(len:Int): MutableSet<Int> {

            val indexSet = mutableSetOf<Int>()

            val lastIndexs = (indexDefault.size - 1).takeIf { it !=-1 } ?: indexs.size -1


            if(indexs.isEmpty())for (ix in lastIndexs downTo 0 ){ //indexs为空，表明是非[]式索引，集合是逆向遍历插入的，所以这里也逆向遍历，好还原顺序

                val it = indexDefault[ix]
                if(it in 0 until len) indexSet.add(it) //将正数不越界的索引添加到集合
                else if(it < 0 && len >= -it) indexSet.add(it + len) //将负数不越界的索引添加到集合

            }else for (ix in lastIndexs downTo 0 ){ //indexs不空，表明是[]式索引，集合是逆向遍历插入的，所以这里也逆向遍历，好还原顺序

                if(indexs[ix] is Triple<*, *, *>){ //区间

                    val (startx, endx, stepx) = indexs[ix] as Triple<Int?, Int?, Int> //还原储存时的类型

                    val start = if (startx == null)  0 //左端省略表示0
                    else if (startx >= 0) if (startx < len) startx else len - 1 //右端越界，设置为最大索引
                    else if (-startx <= len) len + startx /* 将负索引转正 */ else 0 //左端越界，设置为最小索引

                    val end = if (endx == null)  len - 1 //右端省略表示 len - 1
                    else if (endx >= 0) if (endx < len) endx else len - 1 //右端越界，设置为最大索引
                    else if (-endx <= len) len + endx /* 将负索引转正 */ else 0 //左端越界，设置为最小索引

                    if (start == end || stepx >= len) { //两端相同，区间里只有一个数。或间隔过大，区间实际上仅有首位

                        indexSet.add(start)
                        continue

                    }

                    val step = if (stepx > 0) stepx else if (-stepx < len) stepx + len else 1 //最小正数间隔为1

                    //将区间展开到集合中,允许列表反向。
                    indexSet.addAll(if (end > start) start..end step step else start downTo end step step)

                }else{//单个索引

                    val it = indexs[ix] as Int //还原储存时的类型

                    if(it in 0 until len) indexSet.add(it) //将正数不越界的索引添加到集合
                    else if(it < 0 && len >= -it) indexSet.add(it + len) //将负数不越界的索引添加到集合

                }

            }

            return indexSet

        }

    }


    internal inner class SourceRule(ruleStr: String) {
        var isCss = false
        var elementsRule: String = if (ruleStr.startsWith("@CSS:", true)) {
            isCss = true
            ruleStr.substring(5).trim { it <= ' ' }
        } else {
            ruleStr
        }
    }

}