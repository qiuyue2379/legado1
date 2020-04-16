package io.legado.app.ui.book.read.page

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.ReadBookConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.book.read.page.entities.TextChar
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.utils.activity
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getPrefBoolean


class ContentTextView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var selectAble = context.getPrefBoolean(PreferKey.textSelectAble)
    var upView: ((TextPage) -> Unit)? = null
    private val selectedPaint by lazy {
        Paint().apply {
            color = context.getCompatColor(R.color.btn_bg_press_2)
            style = Paint.Style.FILL
        }
    }
    private var callBack: CallBack
    private val visibleRect = RectF()
    private val selectStart = arrayOf(0, 0, 0)
    private val selectEnd = arrayOf(0, 0, 0)
    private var textPage: TextPage = TextPage()

    //滚动参数
    private val pageFactory: TextPageFactory get() = callBack.pageFactory
    private val maxScrollOffset = 100f
    private var pageOffset = 0f

    init {
        callBack = activity as CallBack
        contentDescription = textPage.text
    }

    fun setContent(textPage: TextPage) {
        this.textPage = textPage
        contentDescription = textPage.text
        invalidate()
    }

    fun upVisibleRect() {
        visibleRect.set(
            ChapterProvider.paddingLeft.toFloat(),
            ChapterProvider.paddingTop.toFloat(),
            ChapterProvider.visibleRight.toFloat(),
            ChapterProvider.visibleBottom.toFloat()
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ChapterProvider.viewWidth = w
        ChapterProvider.viewHeight = h
        ChapterProvider.upViewSize()
        upVisibleRect()
        textPage.format()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.clipRect(visibleRect)
        drawPage(canvas)
    }

    /**
     * 绘制页面
     */
    private fun drawPage(canvas: Canvas) {
        var relativeOffset = relativeOffset(0)
        textPage.textLines.forEach { textLine ->
            val lineTop = textLine.lineTop + relativeOffset
            val lineBase = textLine.lineBase + relativeOffset
            val lineBottom = textLine.lineBottom + relativeOffset
            drawChars(
                canvas,
                textLine.textChars,
                lineTop,
                lineBase,
                lineBottom,
                textLine.isTitle,
                textLine.isReadAloud
            )
        }
        if (!ReadBookConfig.isScroll) return
        //滚动翻页
        if (!pageFactory.hasNext()) return
        val nextPage = relativePage(1)
        relativeOffset = relativeOffset(1)
        nextPage.textLines.forEach { textLine ->
            val lineTop = textLine.lineTop + relativeOffset
            val lineBase = textLine.lineBase + relativeOffset
            val lineBottom = textLine.lineBottom + relativeOffset
            drawChars(
                canvas,
                textLine.textChars,
                lineTop,
                lineBase,
                lineBottom,
                textLine.isTitle,
                textLine.isReadAloud
            )
        }
        if (!pageFactory.hasNextPlus()) return
        relativeOffset = relativeOffset(2)
        if (relativeOffset < ChapterProvider.visibleHeight) {
            relativePage(2).textLines.forEach { textLine ->
                val lineTop = textLine.lineTop + relativeOffset
                val lineBase = textLine.lineBase + relativeOffset
                val lineBottom = textLine.lineBottom + relativeOffset
                drawChars(
                    canvas,
                    textLine.textChars,
                    lineTop,
                    lineBase,
                    lineBottom,
                    textLine.isTitle,
                    textLine.isReadAloud
                )
            }
        }
    }

    /**
     * 绘制文字
     */
    private fun drawChars(
        canvas: Canvas,
        textChars: List<TextChar>,
        lineTop: Float,
        lineBase: Float,
        lineBottom: Float,
        isTitle: Boolean,
        isReadAloud: Boolean
    ) {
        val textPaint = if (isTitle) ChapterProvider.titlePaint else ChapterProvider.contentPaint
        textPaint.color =
            if (isReadAloud) context.accentColor else ReadBookConfig.durConfig.textColor()
        textChars.forEach {
            canvas.drawText(it.charData, it.start, lineBase, textPaint)
            if (it.selected) {
                canvas.drawRect(it.start, lineTop, it.end, lineBottom, selectedPaint)
            }
        }
    }

    /**
     * 滚动事件
     */
    fun onScroll(mOffset: Float) {
        if (mOffset == 0f) return
        var offset = mOffset
        if (offset > maxScrollOffset) {
            offset = maxScrollOffset
        } else if (offset < -maxScrollOffset) {
            offset = -maxScrollOffset
        }

        pageOffset += offset
        if (!pageFactory.hasPrev() && pageOffset > 0) {
            pageOffset = 0f
        } else if (!pageFactory.hasNext() && pageOffset < 0) {
            pageOffset = 0f
        } else if (pageOffset > 0) {
            pageFactory.moveToPrev(false)
            textPage = pageFactory.currentPage
            pageOffset -= textPage.height
            upView?.invoke(textPage)
        } else if (pageOffset < -textPage.height) {
            pageOffset += textPage.height
            pageFactory.moveToNext(false)
            textPage = pageFactory.currentPage
            upView?.invoke(textPage)
        }
        invalidate()
    }

    fun resetPageOffset() {
        pageOffset = 0f
    }

    /**
     * 选择初始文字
     */
    fun selectText(
        x: Float,
        y: Float,
        select: (relativePage: Int, lineIndex: Int, charIndex: Int) -> Unit
    ) {
        if (!selectAble) return
        if (!visibleRect.contains(x, y)) return
        var relativeOffset = relativeOffset(0)
        for ((lineIndex, textLine) in textPage.textLines.withIndex()) {
            if (y > textLine.lineTop + relativeOffset && y < textLine.lineBottom + relativeOffset) {
                for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                    if (x > textChar.start && x < textChar.end) {
                        textChar.selected = true
                        invalidate()
                        selectStart[0] = 0
                        selectStart[1] = lineIndex
                        selectStart[2] = charIndex
                        selectEnd[0] = 0
                        selectEnd[1] = lineIndex
                        selectEnd[2] = charIndex
                        upSelectedStart(
                            textChar.start,
                            textLine.lineBottom + relativeOffset,
                            textLine.lineTop + relativeOffset
                        )
                        upSelectedEnd(textChar.end, textLine.lineBottom + relativeOffset)
                        select(0, lineIndex, charIndex)
                        return
                    }
                }
                return
            }
        }
        if (!ReadBookConfig.isScroll) return
        //滚动翻页
        relativeOffset = relativeOffset(1)
        if (relativeOffset >= ChapterProvider.visibleHeight) return
        val nextPage = relativePage(1)
        for ((lineIndex, textLine) in nextPage.textLines.withIndex()) {
            if (y > textLine.lineTop + relativeOffset && y < textLine.lineBottom + relativeOffset) {
                for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                    if (x > textChar.start && x < textChar.end) {
                        textChar.selected = true
                        invalidate()
                        selectStart[0] = 1
                        selectStart[1] = lineIndex
                        selectStart[2] = charIndex
                        selectEnd[0] = 1
                        selectEnd[1] = lineIndex
                        selectEnd[2] = charIndex
                        upSelectedStart(
                            textChar.start,
                            textLine.lineBottom + relativeOffset,
                            textLine.lineTop + relativeOffset
                        )
                        upSelectedEnd(textChar.end, textLine.lineBottom + relativeOffset)
                        select(1, lineIndex, charIndex)
                        return
                    }
                }
                return
            }
        }
        relativeOffset = relativeOffset(2)
        if (relativeOffset >= ChapterProvider.visibleHeight) return
        for ((lineIndex, textLine) in relativePage(2).textLines.withIndex()) {
            if (y > textLine.lineTop + relativeOffset && y < textLine.lineBottom + relativeOffset) {
                for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                    if (x > textChar.start && x < textChar.end) {
                        textChar.selected = true
                        invalidate()
                        selectStart[0] = 2
                        selectStart[1] = lineIndex
                        selectStart[2] = charIndex
                        selectEnd[0] = 2
                        selectEnd[1] = lineIndex
                        selectEnd[2] = charIndex
                        upSelectedStart(
                            textChar.start,
                            textLine.lineBottom + relativeOffset,
                            textLine.lineTop + relativeOffset
                        )
                        upSelectedEnd(textChar.end, textLine.lineBottom + relativeOffset)
                        select(2, lineIndex, charIndex)
                        return
                    }
                }
                return
            }
        }
    }

    /**
     * 开始选择符移动
     */
    fun selectStartMove(x: Float, y: Float) {
        if (!visibleRect.contains(x, y)) return
        var relativeOffset = relativeOffset(0)
        for ((lineIndex, textLine) in textPage.textLines.withIndex()) {
            if (y > textLine.lineTop + relativeOffset && y < textLine.lineBottom + relativeOffset) {
                for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                    if (x > textChar.start && x < textChar.end) {
                        if (selectStart[0] != 0 || selectStart[1] != lineIndex || selectStart[2] != charIndex) {
                            if (selectToInt(0, lineIndex, charIndex) > selectToInt(selectEnd)) {
                                return
                            }
                            selectStart[0] = 0
                            selectStart[1] = lineIndex
                            selectStart[2] = charIndex
                            upSelectedStart(
                                textChar.start,
                                textLine.lineBottom + relativeOffset,
                                textLine.lineTop + relativeOffset
                            )
                            upSelectChars()
                        }
                        return
                    }
                }
                return
            }
        }
        if (!ReadBookConfig.isScroll) return
        //滚动翻页
        relativeOffset = relativeOffset(1)
        if (relativeOffset >= ChapterProvider.visibleHeight) return
        for ((lineIndex, textLine) in relativePage(1).textLines.withIndex()) {
            if (y > textLine.lineTop + relativeOffset && y < textLine.lineBottom + relativeOffset) {
                for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                    if (x > textChar.start && x < textChar.end) {
                        if (selectStart[0] != 1 || selectStart[1] != lineIndex || selectStart[2] != charIndex) {
                            if (selectToInt(1, lineIndex, charIndex) > selectToInt(selectEnd)) {
                                return
                            }
                            selectStart[0] = 1
                            selectStart[1] = lineIndex
                            selectStart[2] = charIndex
                            upSelectedStart(
                                textChar.start,
                                textLine.lineBottom + relativeOffset,
                                textLine.lineTop + relativeOffset
                            )
                            upSelectChars()
                        }
                        return
                    }
                }
                return
            }
        }
        relativeOffset = relativeOffset(2)
        if (relativeOffset >= ChapterProvider.visibleHeight) return
        for ((lineIndex, textLine) in relativePage(2).textLines.withIndex()) {
            if (y > textLine.lineTop + relativeOffset && y < textLine.lineBottom + relativeOffset) {
                for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                    if (x > textChar.start && x < textChar.end) {
                        if (selectStart[0] != 2 || selectStart[1] != lineIndex || selectStart[2] != charIndex) {
                            if (selectToInt(2, lineIndex, charIndex) > selectToInt(selectEnd)) {
                                return
                            }
                            selectStart[0] = 2
                            selectStart[1] = lineIndex
                            selectStart[2] = charIndex
                            upSelectedStart(
                                textChar.start,
                                textLine.lineBottom + relativeOffset,
                                textLine.lineTop + relativeOffset
                            )
                            upSelectChars()
                        }
                        return
                    }
                }
                return
            }
        }
    }

    /**
     * 结束选择符移动
     */
    fun selectEndMove(x: Float, y: Float) {
        if (!visibleRect.contains(x, y)) return
        var relativeOffset = relativeOffset(0)
        for ((lineIndex, textLine) in textPage.textLines.withIndex()) {
            if (y > textLine.lineTop + relativeOffset && y < textLine.lineBottom + relativeOffset) {
                for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                    if (x > textChar.start && x < textChar.end) {
                        if (selectEnd[0] != 0 || selectEnd[1] != lineIndex || selectEnd[2] != charIndex) {
                            if (selectToInt(0, lineIndex, charIndex) < selectToInt(selectStart)) {
                                return
                            }
                            selectEnd[0] = 0
                            selectEnd[1] = lineIndex
                            selectEnd[2] = charIndex
                            upSelectedEnd(textChar.end, textLine.lineBottom + relativeOffset)
                            upSelectChars()
                        }
                        return
                    }
                }
                return
            }
        }
        if (!ReadBookConfig.isScroll) return
        //滚动翻页
        relativeOffset = relativeOffset(1)
        if (relativeOffset >= ChapterProvider.visibleHeight) return
        for ((lineIndex, textLine) in relativePage(1).textLines.withIndex()) {
            if (y > textLine.lineTop + relativeOffset && y < textLine.lineBottom + relativeOffset) {
                for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                    if (x > textChar.start && x < textChar.end) {
                        if (selectEnd[0] != 1 || selectEnd[1] != lineIndex || selectEnd[2] != charIndex) {
                            if (selectToInt(1, lineIndex, charIndex) < selectToInt(selectStart)) {
                                return
                            }
                            selectEnd[0] = 1
                            selectEnd[1] = lineIndex
                            selectEnd[2] = charIndex
                            upSelectedEnd(textChar.end, textLine.lineBottom + relativeOffset)
                            upSelectChars()
                        }
                        return
                    }
                }
                return
            }
        }
        relativeOffset = relativeOffset(2)
        if (relativeOffset >= ChapterProvider.visibleHeight) return
        for ((lineIndex, textLine) in relativePage(2).textLines.withIndex()) {
            if (y > textLine.lineTop + relativeOffset && y < textLine.lineBottom + relativeOffset) {
                for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                    if (x > textChar.start && x < textChar.end) {
                        if (selectEnd[0] != 2 || selectEnd[1] != lineIndex || selectEnd[2] != charIndex) {
                            if (selectToInt(2, lineIndex, charIndex) < selectToInt(selectStart)) {
                                return
                            }
                            selectEnd[0] = 2
                            selectEnd[1] = lineIndex
                            selectEnd[2] = charIndex
                            upSelectedEnd(textChar.end, textLine.lineBottom + relativeOffset)
                            upSelectChars()
                        }
                        return
                    }
                }
                return
            }
        }
    }

    /**
     * 选择开始文字
     */
    fun selectStartMoveIndex(relativePage: Int, lineIndex: Int, charIndex: Int) {
        selectStart[0] = relativePage
        selectStart[1] = lineIndex
        selectStart[2] = charIndex
        val textLine = relativePage(relativePage).textLines[lineIndex]
        val textChar = textLine.textChars[charIndex]
        upSelectedStart(
            textChar.start,
            textLine.lineBottom + relativeOffset(relativePage),
            textLine.lineTop
        )
        upSelectChars()
    }

    /**
     * 选择结束文字
     */
    fun selectEndMoveIndex(relativePage: Int, lineIndex: Int, charIndex: Int) {
        selectEnd[0] = relativePage
        selectEnd[1] = lineIndex
        selectEnd[2] = charIndex
        val textLine = relativePage(relativePage).textLines[lineIndex]
        val textChar = textLine.textChars[charIndex]
        upSelectedEnd(textChar.end, textLine.lineBottom + relativeOffset(relativePage))
        upSelectChars()
    }

    private fun upSelectChars() {
        val last = if (ReadBookConfig.isScroll) 2 else 0
        for (relativePos in 0..last) {
            for ((lineIndex, textLine) in relativePage(relativePos).textLines.withIndex()) {
                for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                    textChar.selected =
                        if (relativePos == selectStart[0]
                            && relativePos == selectEnd[0]
                            && lineIndex == selectStart[1]
                            && lineIndex == selectEnd[1]
                        ) {
                            charIndex in selectStart[2]..selectEnd[2]
                        } else if (relativePos == selectStart[0] && lineIndex == selectStart[1]) {
                            charIndex >= selectStart[2]
                        } else if (relativePos == selectEnd[0] && lineIndex == selectEnd[1]) {
                            charIndex <= selectEnd[2]
                        } else if (relativePos == selectStart[0] && relativePos == selectEnd[0]) {
                            lineIndex in (selectStart[1] + 1) until selectEnd[1]
                        } else if (relativePos == selectStart[0]) {
                            lineIndex > selectStart[1]
                        } else if (relativePos == selectEnd[0]) {
                            lineIndex < selectEnd[1]
                        } else {
                            relativePos in selectStart[0] + 1 until selectEnd[0]
                        }
                }
            }
        }
        invalidate()
    }

    private fun upSelectedStart(x: Float, y: Float, top: Float) = callBack.apply {
        upSelectedStart(x, y + headerHeight, top + headerHeight)
    }

    private fun upSelectedEnd(x: Float, y: Float) = callBack.apply {
        upSelectedEnd(x, y + headerHeight)
    }

    fun cancelSelect() {
        val last = if (ReadBookConfig.isScroll) 2 else 0
        for (relativePos in 0..last) {
            relativePage(relativePos).textLines.forEach { textLine ->
                textLine.textChars.forEach {
                    it.selected = false
                }
            }
        }
        invalidate()
        callBack.onCancelSelect()
    }

    val selectedText: String
        get() {
            val stringBuilder = StringBuilder()
            for (relativePos in selectStart[0]..selectEnd[0]) {
                val textPage = relativePage(relativePos)
                if (relativePos == selectStart[0] && relativePos == selectEnd[0]) {
                    for (lineIndex in selectStart[1]..selectEnd[1]) {
                        if (lineIndex == selectStart[1] && lineIndex == selectEnd[1]) {
                            stringBuilder.append(
                                textPage.textLines[lineIndex].text.substring(
                                    selectStart[2],
                                    selectEnd[2] + 1
                                )
                            )
                        } else if (lineIndex == selectStart[1]) {
                            stringBuilder.append(
                                textPage.textLines[lineIndex].text.substring(
                                    selectStart[2]
                                )
                            )
                        } else if (lineIndex == selectEnd[1]) {
                            stringBuilder.append(
                                textPage.textLines[lineIndex].text.substring(0, selectEnd[2] + 1)
                            )
                        } else {
                            stringBuilder.append(textPage.textLines[lineIndex].text)
                        }
                    }
                } else if (relativePos == selectStart[0]) {
                    for (lineIndex in selectStart[1] until relativePage(relativePos).textLines.size) {
                        if (lineIndex == selectStart[1]) {
                            stringBuilder.append(
                                textPage.textLines[lineIndex].text.substring(
                                    selectStart[2]
                                )
                            )
                        } else {
                            stringBuilder.append(textPage.textLines[lineIndex].text)
                        }
                    }
                } else if (relativePos == selectEnd[0]) {
                    for (lineIndex in 0..selectEnd[1]) {
                        if (lineIndex == selectEnd[1]) {
                            stringBuilder.append(
                                textPage.textLines[lineIndex].text.substring(0, selectEnd[2] + 1)
                            )
                        } else {
                            stringBuilder.append(textPage.textLines[lineIndex].text)
                        }
                    }
                } else if (relativePos in selectStart[0] + 1 until selectEnd[0]) {
                    for (lineIndex in selectStart[1]..selectEnd[1]) {
                        stringBuilder.append(textPage.textLines[lineIndex].text)
                    }
                }
            }
            return stringBuilder.toString()
        }

    private fun selectToInt(page: Int, line: Int, char: Int): Int {
        return page * 1000000 + line * 100000 + char
    }

    private fun selectToInt(select: Array<Int>): Int {
        return select[0] * 1000000 + select[1] * 100000 + select[2]
    }

    private fun relativeOffset(relativePos: Int): Float {
        return when (relativePos) {
            0 -> pageOffset
            1 -> pageOffset + textPage.height
            else -> pageOffset + textPage.height + pageFactory.nextPage.height
        }
    }

    private fun relativePage(relativePos: Int): TextPage {
        return when (relativePos) {
            0 -> textPage
            1 -> pageFactory.nextPage
            else -> pageFactory.nextPagePlus
        }
    }

    interface CallBack {
        fun upSelectedStart(x: Float, y: Float, top: Float)
        fun upSelectedEnd(x: Float, y: Float)
        fun onCancelSelect()
        val headerHeight: Int
        val pageFactory: TextPageFactory
    }
}
