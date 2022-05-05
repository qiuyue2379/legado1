package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.core.view.size
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Status
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.BookHelp
import io.legado.app.help.IntentData
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.AppWebDav
import io.legado.app.help.storage.Backup
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.receiver.TimeBatteryReceiver
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.book.bookmark.BookmarkDialog
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.changesource.ChangeChapterSourceDialog
import io.legado.app.ui.book.read.config.*
import io.legado.app.ui.book.read.config.BgTextConfigDialog.Companion.BG_COLOR
import io.legado.app.ui.book.read.config.BgTextConfigDialog.Companion.TEXT_COLOR
import io.legado.app.ui.book.read.config.TipConfigDialog.Companion.TIP_COLOR
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.book.read.page.provider.TextPageFactory
import io.legado.app.ui.book.searchContent.SearchContentActivity
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.browser.WebViewActivity
import io.legado.app.ui.dict.DictDialog
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.ui.replace.edit.ReplaceEditActivity
import io.legado.app.ui.widget.PopupAction
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ReadBookActivity : BaseReadBookActivity(),
    View.OnTouchListener,
    ReadView.CallBack,
    TextActionMenu.CallBack,
    ContentTextView.CallBack,
    PopupMenu.OnMenuItemClickListener,
    ReadMenu.CallBack,
    SearchMenu.CallBack,
    ReadAloudDialog.CallBack,
    ChangeBookSourceDialog.CallBack,
    ChangeChapterSourceDialog.CallBack,
    ReadBook.CallBack,
    AutoReadDialog.CallBack,
    TocRegexDialog.CallBack,
    ColorPickerDialogListener {

    private val tocActivity =
        registerForActivityResult(TocActivityResult()) {
            it?.let {
                viewModel.openChapter(it.first, it.second)
            }
        }
    private val sourceEditActivity =
        registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                viewModel.upBookSource {
                    upMenuView()
                }
            }
        }
    private val replaceActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            it ?: return@registerForActivityResult
            if (it.resultCode == RESULT_OK) {
                viewModel.replaceRuleChanged()
            }
        }
    private val searchContentActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            it ?: return@registerForActivityResult
            it.data?.let { data ->
                val key = data.getLongExtra("key", System.currentTimeMillis())
                val searchResult = IntentData.get<SearchResult>("searchResult$key")
                val searchResultList = IntentData.get<List<SearchResult>>("searchResultList$key")
                if (searchResult != null && searchResultList != null) {
                    viewModel.searchContentQuery = searchResult.query
                    binding.searchMenu.upSearchResultList(searchResultList)
                    isShowingSearchResult = true
                    binding.searchMenu.updateSearchResultIndex(searchResultList.indexOf(searchResult))
                    binding.searchMenu.selectedSearchResult?.let { currentResult ->
                        skipToSearch(currentResult)
                        showActionMenu()
                    }
                }
            }
        }
    private var menu: Menu? = null
    private var changeSourceMenu: PopupMenu? = null
    private var refreshMenu: PopupMenu? = null
    private var autoPageJob: Job? = null
    private var backupJob: Job? = null
    private var keepScreenJon: Job? = null
    val textActionMenu: TextActionMenu by lazy {
        TextActionMenu(this, this)
    }
    private val popupAction: PopupAction by lazy {
        PopupAction(this)
    }
    override val isInitFinish: Boolean get() = viewModel.isInitFinish
    override val isScroll: Boolean get() = binding.readView.isScroll
    override var autoPageProgress = 0
    override var isAutoPage = false
    override var isShowingSearchResult = false
    override var isSelectingSearchResult = false
        set(value) {
            field = value && isShowingSearchResult
        }
    private val timeBatteryReceiver = TimeBatteryReceiver()
    private var screenTimeOut: Long = 0
    private var loadStates: Boolean = false
    override val pageFactory: TextPageFactory get() = binding.readView.pageFactory
    override val headerHeight: Int get() = binding.readView.curPage.headerHeight
    private val menuLayoutIsVisible get() = bottomDialog > 0 || binding.readMenu.isVisible

    @SuppressLint("ClickableViewAccessibility")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        binding.cursorLeft.setColorFilter(accentColor)
        binding.cursorRight.setColorFilter(accentColor)
        binding.cursorLeft.setOnTouchListener(this)
        binding.cursorRight.setOnTouchListener(this)
        upScreenTimeOut()
        ReadBook.callBack = this
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        viewModel.initData(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        upSystemUiVisibility()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.readView.upStatusBar()
    }

    override fun onResume() {
        super.onResume()
        ReadBook.readStartTime = System.currentTimeMillis()
        upSystemUiVisibility()
        registerReceiver(timeBatteryReceiver, timeBatteryReceiver.filter)
        binding.readView.upTime()
    }

    override fun onPause() {
        super.onPause()
        autoPageStop()
        backupJob?.cancel()
        ReadBook.saveRead()
        unregisterReceiver(timeBatteryReceiver)
        upSystemUiVisibility()
        if (!BuildConfig.DEBUG) {
            ReadBook.uploadProgress()
            Backup.autoBack(this)
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_read, menu)
        menu.iconItemOnLongClick(R.id.menu_change_source) {
            val changeSourceMenu = changeSourceMenu ?: PopupMenu(this, it).apply {
                inflate(R.menu.book_read_change_source)
                this.menu.applyOpenTint(this@ReadBookActivity)
                setOnMenuItemClickListener(this@ReadBookActivity)
                changeSourceMenu = this
            }
            changeSourceMenu.show()
        }
        menu.iconItemOnLongClick(R.id.menu_refresh) {
            val refreshMenu = refreshMenu ?: PopupMenu(this, it).apply {
                inflate(R.menu.book_read_refresh)
                this.menu.applyOpenTint(this@ReadBookActivity)
                setOnMenuItemClickListener(this@ReadBookActivity)
                refreshMenu = this
            }
            refreshMenu.show()
        }
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        this.menu = menu
        upMenu()
        return super.onPrepareOptionsMenu(menu)
    }

    /**
     * 更新菜单
     */
    private fun upMenu() {
        val menu = menu
        val book = ReadBook.book
        if (menu != null && book != null) {
            val onLine = !book.isLocalBook()
            for (i in 0 until menu.size) {
                val item = menu[i]
                when (item.groupId) {
                    R.id.menu_group_on_line -> item.isVisible = onLine
                    R.id.menu_group_local -> item.isVisible = !onLine
                    R.id.menu_group_text -> item.isVisible = book.isLocalTxt()
                    else -> when (item.itemId) {
                        R.id.menu_enable_replace -> item.isChecked = book.getUseReplaceRule()
                        R.id.menu_re_segment -> item.isChecked = book.getReSegment()
                        R.id.menu_reverse_content -> item.isVisible = onLine
                    }
                }
            }
            menu.findItem(R.id.menu_get_progress)?.isVisible = AppWebDav.isOk
        }
    }

    /**
     * 菜单
     */
    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_change_source,
            R.id.menu_book_change_source -> {
                binding.readMenu.runMenuOut()
                ReadBook.book?.let {
                    showDialogFragment(ChangeBookSourceDialog(it.name, it.author))
                }
            }
            R.id.menu_chapter_change_source -> launch {
                val book = ReadBook.book ?: return@launch
                val chapter =
                    appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                        ?: return@launch
                binding.readMenu.runMenuOut()
                showDialogFragment(
                    ChangeChapterSourceDialog(book.name, book.author, chapter.index, chapter.title)
                )
            }
            R.id.menu_refresh,
            R.id.menu_refresh_dur -> {
                if (ReadBook.bookSource == null) {
                    upContent()
                } else {
                    ReadBook.book?.let {
                        ReadBook.curTextChapter = null
                        binding.readView.upContent()
                        viewModel.refreshContentDur(it)
                    }
                }
            }
            R.id.menu_refresh_after -> {
                if (ReadBook.bookSource == null) {
                    upContent()
                } else {
                    ReadBook.book?.let {
                        ReadBook.clearTextChapter()
                        binding.readView.upContent()
                        viewModel.refreshContentAfter(it)
                    }
                }
            }
            R.id.menu_refresh_all -> {
                if (ReadBook.bookSource == null) {
                    upContent()
                } else {
                    ReadBook.book?.let {
                        ReadBook.clearTextChapter()
                        binding.readView.upContent()
                        viewModel.refreshContentAll(it)
                    }
                }
            }
            R.id.menu_download -> showDownloadDialog()
            R.id.menu_add_bookmark -> {
                val book = ReadBook.book
                val page = ReadBook.curTextChapter?.getPage(ReadBook.durPageIndex)
                if (book != null && page != null) {
                    val bookmark = book.createBookMark().apply {
                        chapterIndex = ReadBook.durChapterIndex
                        chapterPos = ReadBook.durChapterPos
                        chapterName = page.title
                        bookText = page.text.trim()
                    }
                    showDialogFragment(BookmarkDialog(bookmark))
                }
            }
            R.id.menu_edit_content -> showDialogFragment(ContentEditDialog())
            R.id.menu_update_toc -> ReadBook.book?.let {
                if (it.isEpub()) {
                    BookHelp.clearCache(it)
                }
                loadChapterList(it)
            }
            R.id.menu_enable_replace -> ReadBook.book?.let {
                it.setUseReplaceRule(!it.getUseReplaceRule())
                ReadBook.saveRead()
                menu?.findItem(R.id.menu_enable_replace)?.isChecked = it.getUseReplaceRule()
                viewModel.replaceRuleChanged()
            }
            R.id.menu_re_segment -> ReadBook.book?.let {
                it.setReSegment(!it.getReSegment())
                menu?.findItem(R.id.menu_re_segment)?.isChecked = it.getReSegment()
                ReadBook.loadContent(false)
            }
            R.id.menu_page_anim -> showPageAnimConfig {
                binding.readView.upPageAnim()
                ReadBook.loadContent(false)
            }
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_toc_regex -> showDialogFragment(
                TocRegexDialog(ReadBook.book?.tocUrl)
            )
            R.id.menu_reverse_content -> ReadBook.book?.let {
                viewModel.reverseContent(it)
            }
            R.id.menu_set_charset -> showCharsetConfig()
            R.id.menu_image_style -> {
                val imgStyles =
                    arrayListOf(Book.imgStyleDefault, Book.imgStyleFull, Book.imgStyleText)
                selector(
                    R.string.image_style,
                    imgStyles
                ) { _, index ->
                    ReadBook.book?.setImageStyle(imgStyles[index])
                    ReadBook.loadContent(false)
                }
            }
            R.id.menu_get_progress -> ReadBook.book?.let {
                viewModel.syncBookProgress(it) { progress ->
                    sureSyncProgress(progress)
                }
            }
            R.id.menu_help -> showReadMenuHelp()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return onCompatOptionsItemSelected(item)
    }

    /**
     * 按键拦截,显示菜单
     */
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        val keyCode = event?.keyCode
        val action = event?.action
        val isDown = action == 0

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (isDown && !binding.readMenu.cnaShowMenu) {
                binding.readMenu.runMenuIn()
                return true
            }
            if (!isDown && !binding.readMenu.cnaShowMenu) {
                binding.readMenu.cnaShowMenu = true
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * 按键事件
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (menuLayoutIsVisible) {
            return super.onKeyDown(keyCode, event)
        }
        when {
            isPrevKey(keyCode) -> {
                if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                    binding.readView.pageDelegate?.keyTurnPage(PageDirection.PREV)
                    return true
                }
            }
            isNextKey(keyCode) -> {
                if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                    binding.readView.pageDelegate?.keyTurnPage(PageDirection.NEXT)
                    return true
                }
            }
            keyCode == KeyEvent.KEYCODE_VOLUME_UP -> {
                if (volumeKeyPage(PageDirection.PREV)) {
                    return true
                }
            }
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (volumeKeyPage(PageDirection.NEXT)) {
                    return true
                }
            }
            keyCode == KeyEvent.KEYCODE_PAGE_UP -> {
                binding.readView.pageDelegate?.keyTurnPage(PageDirection.PREV)
                return true
            }
            keyCode == KeyEvent.KEYCODE_PAGE_DOWN -> {
                binding.readView.pageDelegate?.keyTurnPage(PageDirection.NEXT)
                return true
            }
            keyCode == KeyEvent.KEYCODE_SPACE -> {
                binding.readView.pageDelegate?.keyTurnPage(PageDirection.NEXT)
                return true
            }
            keyCode == KeyEvent.KEYCODE_BACK -> {
                if (isShowingSearchResult) {
                    exitSearchMenu()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * 长按事件
     */
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
        }
        return super.onKeyLongPress(keyCode, event)
    }

    /**
     * 松开按键事件
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (volumeKeyPage(PageDirection.NONE)) {
                    return true
                }
            }
            KeyEvent.KEYCODE_BACK -> {
                event?.let {
                    if ((event.flags and KeyEvent.FLAG_CANCELED_LONG_PRESS == 0)
                        && event.isTracking
                        && !event.isCanceled
                    ) {
                        if (BaseReadAloudService.isPlay()) {
                            ReadAloud.pause(this)
                            toastOnUi(R.string.read_aloud_pause)
                            return true
                        }
                        if (isAutoPage) {
                            autoPageStop()
                            return true
                        }
                        if (getPrefBoolean("disableReturnKey")) {
                            if (menuLayoutIsVisible) {
                                finish()
                            }
                            return true
                        }
                    }
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * view触摸,文字选择
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean = binding.run {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> textActionMenu.dismiss()
            MotionEvent.ACTION_MOVE -> {
                when (v.id) {
                    R.id.cursor_left -> readView.curPage.selectStartMove(
                        event.rawX + cursorLeft.width,
                        event.rawY - cursorLeft.height
                    )
                    R.id.cursor_right -> readView.curPage.selectEndMove(
                        event.rawX - cursorRight.width,
                        event.rawY - cursorRight.height
                    )
                }
            }
            MotionEvent.ACTION_UP -> showTextActionMenu()
        }
        return true
    }

    /**
     * 更新文字选择开始位置
     */
    override fun upSelectedStart(x: Float, y: Float, top: Float) = binding.run {
        cursorLeft.x = x - cursorLeft.width
        cursorLeft.y = y
        cursorLeft.visible(true)
        textMenuPosition.x = x
        textMenuPosition.y = top
    }

    /**
     * 更新文字选择结束位置
     */
    override fun upSelectedEnd(x: Float, y: Float) = binding.run {
        cursorRight.x = x
        cursorRight.y = y
        cursorRight.visible(true)
    }

    /**
     * 取消文字选择
     */
    override fun onCancelSelect() = binding.run {
        cursorLeft.invisible()
        cursorRight.invisible()
        textActionMenu.dismiss()
    }

    /**
     * 显示文本操作菜单
     */
    override fun showTextActionMenu() {
        val navigationBarHeight =
            if (!ReadBookConfig.hideNavigationBar && navigationBarGravity == Gravity.BOTTOM)
                navigationBarHeight else 0
        textActionMenu.show(
            binding.textMenuPosition,
            binding.root.height + navigationBarHeight,
            binding.textMenuPosition.x.toInt(),
            binding.textMenuPosition.y.toInt(),
            binding.cursorLeft.y.toInt() + binding.cursorLeft.height,
            binding.cursorRight.x.toInt(),
            binding.cursorRight.y.toInt() + binding.cursorRight.height
        )
    }

    /**
     * 当前选择的文本
     */
    override val selectedText: String get() = binding.readView.curPage.selectedText

    /**
     * 文本选择菜单操作
     */
    override fun onMenuItemSelected(itemId: Int): Boolean {
        when (itemId) {
            R.id.menu_bookmark -> binding.readView.curPage.let {
                val bookmark = it.createBookmark()
                if (bookmark == null) {
                    toastOnUi(R.string.create_bookmark_error)
                } else {
                    showDialogFragment(BookmarkDialog(bookmark))
                }
                return true
            }
            R.id.menu_replace -> {
                val scopes = arrayListOf<String>()
                ReadBook.book?.name?.let {
                    scopes.add(it)
                }
                ReadBook.bookSource?.bookSourceUrl?.let {
                    scopes.add(it)
                }
                replaceActivity.launch(
                    ReplaceEditActivity.startIntent(
                        this,
                        pattern = selectedText,
                        scope = scopes.joinToString(";")
                    )
                )
                return true
            }
            R.id.menu_search_content -> {
                viewModel.searchContentQuery = selectedText
                openSearchActivity(selectedText)
                return true
            }
            R.id.menu_dict -> {
                showDialogFragment(DictDialog(selectedText))
                return true
            }
        }
        return false
    }

    /**
     * 文本选择菜单操作完成
     */
    override fun onMenuActionFinally() = binding.run {
        textActionMenu.dismiss()
        readView.curPage.cancelSelect()
        readView.isTextSelected = false
    }

    /**
     * 音量键翻页
     */
    private fun volumeKeyPage(direction: PageDirection): Boolean {
        if (!binding.readMenu.isVisible) {
            if (getPrefBoolean("volumeKeyPage", true)) {
                if (getPrefBoolean("volumeKeyPageOnPlay")
                    || BaseReadAloudService.pause
                ) {
                    binding.readView.pageDelegate?.isCancel = false
                    binding.readView.pageDelegate?.keyTurnPage(direction)
                    return true
                }
            }
        }
        return false
    }

    override fun upMenuView() {
        launch {
            upMenu()
            binding.readMenu.upBookView()
        }
    }

    override fun loadChapterList(book: Book) {
        ReadBook.upMsg(getString(R.string.toc_updateing))
        viewModel.loadChapterList(book)
    }

    /**
     * 内容加载完成
     */
    override fun contentLoadFinish() {
        if (intent.getBooleanExtra("readAloud", false)) {
            intent.removeExtra("readAloud")
            ReadBook.readAloud()
        }
        loadStates = true
    }

    /**
     * 更新内容
     */
    override fun upContent(
        relativePosition: Int,
        resetPageOffset: Boolean,
        success: (() -> Unit)?
    ) {
        launch {
            autoPageProgress = 0
            binding.readView.upContent(relativePosition, resetPageOffset)
            binding.readMenu.setSeekPage(ReadBook.durPageIndex)
            loadStates = false
            success?.invoke()
        }
    }

    override fun upPageAnim() {
        launch {
            binding.readView.upPageAnim()
        }
    }

    /**
     * 页面改变
     */
    override fun pageChanged() {
        launch {
            autoPageProgress = 0
            binding.readMenu.setSeekPage(ReadBook.durPageIndex)
            startBackupJob()
        }
    }

    /**
     * 显示菜单
     */
    override fun showMenuBar() {
        binding.readMenu.runMenuIn()
    }

    override val oldBook: Book?
        get() = ReadBook.book

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        if (book.type != BookType.audio) {
            viewModel.changeTo(source, book, toc)
        } else {
            ReadAloud.stop(this)
            launch {
                ReadBook.book?.changeTo(book, toc)
                appDb.bookDao.insert(book)
            }
            startActivity<AudioPlayActivity> {
                putExtra("bookUrl", book.bookUrl)
            }
            finish()
        }
    }

    override fun replaceContent(content: String) {
        ReadBook.book?.let {
            viewModel.saveContent(it, content)
        }
    }

    override fun showActionMenu() {
        when {
            BaseReadAloudService.isRun -> showReadAloudDialog()
            isAutoPage -> showDialogFragment<AutoReadDialog>()
            isShowingSearchResult -> binding.searchMenu.runMenuIn()
            else -> binding.readMenu.runMenuIn()
        }
    }

    override fun showReadMenuHelp() {
        val text = String(assets.open("help/readMenuHelp.md").readBytes())
        showDialogFragment(TextDialog(text, TextDialog.Mode.MD))
    }

    /**
     * 显示朗读菜单
     */
    override fun showReadAloudDialog() {
        showDialogFragment<ReadAloudDialog>()
    }

    /**
     * 自动翻页
     */
    override fun autoPage() {
        ReadAloud.stop(this)
        if (isAutoPage) {
            autoPageStop()
        } else {
            isAutoPage = true
            autoPagePlus()
            binding.readMenu.setAutoPage(true)
            screenTimeOut = -1L
            screenOffTimerStart()
        }
    }

    override fun autoPageStop() {
        if (isAutoPage) {
            isAutoPage = false
            autoPageJob?.cancel()
            binding.readView.invalidate()
            binding.readMenu.setAutoPage(false)
            upScreenTimeOut()
        }
    }

    private fun autoPagePlus() {
        autoPageJob?.cancel()
        autoPageJob = launch {
            while (isActive) {
                var delayMillis = ReadBookConfig.autoReadSpeed * 1000L / binding.readView.height
                var scrollOffset = 1
                if (delayMillis < 20) {
                    var delayInt = delayMillis.toInt()
                    if (delayInt == 0) delayInt = 1
                    scrollOffset = 20 / delayInt
                    delayMillis = 20
                }
                delay(delayMillis)
                if (!menuLayoutIsVisible) {
                    if (binding.readView.isScroll) {
                        binding.readView.curPage.scroll(-scrollOffset)
                    } else {
                        autoPageProgress += scrollOffset
                        if (autoPageProgress >= binding.readView.height) {
                            autoPageProgress = 0
                            if (!binding.readView.fillPage(PageDirection.NEXT)) {
                                autoPageStop()
                            }
                        } else {
                            binding.readView.invalidate()
                        }
                    }
                }
            }
        }
    }

    override fun openSourceEditActivity() {
        ReadBook.bookSource?.let {
            sourceEditActivity.launch {
                putExtra("sourceUrl", it.bookSourceUrl)
            }
        }
    }

    /**
     * 替换
     */
    override fun openReplaceRule() {
        replaceActivity.launch(Intent(this, ReplaceRuleActivity::class.java))
    }

    /**
     * 打开目录
     */
    override fun openChapterList() {
        ReadBook.book?.let {
            tocActivity.launch(it.bookUrl)
        }
    }

    /**
     * 打开搜索界面
     */
    override fun openSearchActivity(searchWord: String?) {
        ReadBook.book?.let {
            searchContentActivity.launch(Intent(this, SearchContentActivity::class.java).apply {
                putExtra("bookUrl", it.bookUrl)
                putExtra("searchWord", searchWord ?: viewModel.searchContentQuery)
            })
        }
    }

    /**
     * 禁用书源
     */
    override fun disableSource() {
        viewModel.disableSource()
    }

    /**
     * 显示阅读样式配置
     */
    override fun showReadStyle() {
        showDialogFragment<ReadStyleDialog>()
    }

    /**
     * 显示更多设置
     */
    override fun showMoreSetting() {
        showDialogFragment<MoreConfigDialog>()
    }

    override fun showSearchSetting() {
        showDialogFragment<MoreConfigDialog>()
    }

    /**
     * 更新状态栏,导航栏
     */
    override fun upSystemUiVisibility() {
        upSystemUiVisibility(isInMultiWindow, !binding.readMenu.isVisible)
        upNavigationBarColor()
    }

    override fun exitSearchMenu() {
        if (isShowingSearchResult) {
            isShowingSearchResult = false
            binding.searchMenu.invalidate()
            binding.searchMenu.invisible()
        }
    }

    override fun showLogin() {
        ReadBook.bookSource?.let {
            startActivity<SourceLoginActivity> {
                putExtra("type", "bookSource")
                putExtra("key", it.bookSourceUrl)
            }
        }
    }

    override fun payAction() {
        Coroutine.async(this) {
            val book = ReadBook.book ?: throw NoStackTraceException("no book")
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: throw NoStackTraceException("no chapter")
            val source = ReadBook.bookSource ?: throw NoStackTraceException("no book source")
            val payAction = source.getContentRule().payAction
            if (payAction.isNullOrEmpty()) {
                throw NoStackTraceException("no pay action")
            }
            JsUtils.evalJs(payAction) {
                it["book"] = book
                it["chapter"] = chapter
            }
        }.onSuccess {
            startActivity<WebViewActivity> {
                putExtra("title", getString(R.string.chapter_pay))
                putExtra("url", it)
                IntentData.put(it, ReadBook.bookSource?.getHeaderMap(true))
            }
        }.onError {
            toastOnUi(it.localizedMessage)
        }
    }

    /**
     * 朗读按钮
     */
    override fun onClickReadAloud() {
        autoPageStop()
        when {
            !BaseReadAloudService.isRun -> {
                ReadAloud.upReadAloudClass()
                ReadBook.readAloud()
            }
            BaseReadAloudService.pause -> ReadAloud.resume(this)
            else -> ReadAloud.pause(this)
        }
    }

    /**
     * 长按图片
     */
    @SuppressLint("RtlHardcoded")
    override fun onImageLongPress(x: Float, y: Float, src: String) {
        popupAction.setItems(
            listOf(
                SelectItem(getString(R.string.show), "show"),
                SelectItem(getString(R.string.refresh), "refresh")
            )
        )
        popupAction.onActionClick = {
            when (it) {
                "show" -> showDialogFragment(PhotoDialog(src))
                "refresh" -> viewModel.refreshImage(src)
            }
            popupAction.dismiss()
        }
        val navigationBarHeight =
            if (!ReadBookConfig.hideNavigationBar && navigationBarGravity == Gravity.BOTTOM)
                navigationBarHeight else 0
        popupAction.showAtLocation(
            binding.readView, Gravity.BOTTOM or Gravity.LEFT, x.toInt(),
            binding.root.height + navigationBarHeight - y.toInt()
        )
    }

    /**
     * colorSelectDialog
     */
    override fun onColorSelected(dialogId: Int, color: Int) = ReadBookConfig.durConfig.run {
        when (dialogId) {
            TEXT_COLOR -> {
                setCurTextColor(color)
                postEvent(EventBus.UP_CONFIG, false)
            }
            BG_COLOR -> {
                setCurBg(0, "#${color.hexString}")
                ReadBookConfig.upBg()
                postEvent(EventBus.UP_CONFIG, false)
            }
            TIP_COLOR -> {
                ReadTipConfig.tipColor = color
                postEvent(EventBus.TIP_COLOR, "")
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
    }

    /**
     * colorSelectDialog
     */
    override fun onDialogDismissed(dialogId: Int) = Unit

    override fun onTocRegexDialogResult(tocRegex: String) {
        ReadBook.book?.let {
            it.tocUrl = tocRegex
            loadChapterList(it)
        }
    }

    private fun sureSyncProgress(progress: BookProgress) {
        alert(R.string.get_book_progress) {
            setMessage(R.string.current_progress_exceeds_cloud)
            okButton {
                ReadBook.setProgress(progress)
            }
            noButton()
        }
    }

    override fun navigateToSearch(searchResult: SearchResult) {
        skipToSearch(searchResult)
    }

    private fun skipToSearch(searchResult: SearchResult) {
        val previousResult = binding.searchMenu.previousSearchResult

        fun jumpToPosition() {
            ReadBook.curTextChapter?.let {
                binding.searchMenu.updateSearchInfo()
                val positions = viewModel.searchResultPositions(it, searchResult)
                ReadBook.skipToPage(positions[0]) {
                    launch {
                        isSelectingSearchResult = true
                        binding.readView.curPage.selectStartMoveIndex(0, positions[1], positions[2])
                        when (positions[3]) {
                            0 -> binding.readView.curPage.selectEndMoveIndex(
                                0,
                                positions[1],
                                positions[2] + viewModel.searchContentQuery.length - 1
                            )
                            1 -> binding.readView.curPage.selectEndMoveIndex(
                                0, positions[1] + 1, positions[4]
                            )
                            //consider change page, jump to scroll position
                            -1 -> binding.readView.curPage.selectEndMoveIndex(1, 0, positions[4])
                        }
                        binding.readView.isTextSelected = true
                        isSelectingSearchResult = false
                    }
                }
            }
        }

        if (searchResult.chapterIndex != previousResult?.chapterIndex) {
            viewModel.openChapter(searchResult.chapterIndex) {
                jumpToPosition()
            }
        } else {
            jumpToPosition()
        }
    }

    private fun startBackupJob() {
        backupJob?.cancel()
        backupJob = launch {
            delay(120000)
            ReadBook.book?.let {
                AppWebDav.uploadBookProgress(it)
                Backup.autoBack(this@ReadBookActivity)
            }
        }
    }

    override fun finish() {
        ReadBook.book?.let {
            if (!ReadBook.inBookshelf) {
                alert(title = getString(R.string.add_to_shelf)) {
                    setMessage(getString(R.string.check_add_bookshelf, it.name))
                    okButton {
                        ReadBook.inBookshelf = true
                        setResult(Activity.RESULT_OK)
                    }
                    noButton { viewModel.removeFromBookshelf { super.finish() } }
                }
            } else {
                super.finish()
            }
        } ?: super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        textActionMenu.dismiss()
        popupAction.dismiss()
        binding.readView.onDestroy()
        ReadBook.msg = null
        ReadBook.callBack = null
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
    }

    override fun observeLiveBus() = binding.run {
        super.observeLiveBus()
        observeEvent<String>(EventBus.TIME_CHANGED) { readView.upTime() }
        observeEvent<Int>(EventBus.BATTERY_CHANGED) { readView.upBattery(it) }
        observeEvent<BookChapter>(EventBus.OPEN_CHAPTER) {
            viewModel.openChapter(it.index, ReadBook.durChapterPos)
            readView.upContent()
        }
        observeEvent<Boolean>(EventBus.MEDIA_BUTTON) {
            if (it) {
                onClickReadAloud()
            } else {
                ReadBook.readAloud(!BaseReadAloudService.pause)
            }
        }
        observeEvent<Boolean>(EventBus.UP_CONFIG) {
            upSystemUiVisibility()
            readView.upBg()
            readView.upStyle()
            readView.upBgAlpha()
            if (it) {
                ReadBook.loadContent(resetPageOffset = false)
            } else {
                readView.upContent(resetPageOffset = false)
            }
        }
        observeEvent<Int>(EventBus.ALOUD_STATE) {
            if (it == Status.STOP || it == Status.PAUSE) {
                ReadBook.curTextChapter?.let { textChapter ->
                    val page = textChapter.getPageByReadPos(ReadBook.durChapterPos)
                    if (page != null) {
                        page.removePageAloudSpan()
                        readView.upContent(resetPageOffset = false)
                    }
                }
            }
        }
        observeEventSticky<Int>(EventBus.TTS_PROGRESS) { chapterStart ->
            launch(IO) {
                if (BaseReadAloudService.isPlay()) {
                    ReadBook.curTextChapter?.let { textChapter ->
                        val pageIndex = ReadBook.durPageIndex
                        val aloudSpanStart = chapterStart - textChapter.getReadLength(pageIndex)
                        textChapter.getPage(pageIndex)
                            ?.upPageAloudSpan(aloudSpanStart)
                        upContent()
                    }
                }
            }
        }
        observeEvent<Boolean>(PreferKey.keepLight) {
            upScreenTimeOut()
        }
        observeEvent<Boolean>(PreferKey.textSelectAble) {
            readView.curPage.upSelectAble(it)
        }
        observeEvent<String>(PreferKey.showBrightnessView) {
            readMenu.upBrightnessState()
        }
    }

    private fun upScreenTimeOut() {
        val keepLightPrefer = getPrefString(PreferKey.keepLight)?.toInt() ?: 0
        screenTimeOut = keepLightPrefer * 1000L
        screenOffTimerStart()
    }

    /**
     * 重置黑屏时间
     */
    override fun screenOffTimerStart() {
        keepScreenJon?.cancel()
        keepScreenJon = launch {
            if (screenTimeOut < 0) {
                keepScreenOn(true)
                return@launch
            }
            val t = screenTimeOut - sysScreenOffTime
            if (t > 0) {
                keepScreenOn(true)
                delay(screenTimeOut)
                keepScreenOn(false)
            } else {
                keepScreenOn(false)
            }
        }
    }
}