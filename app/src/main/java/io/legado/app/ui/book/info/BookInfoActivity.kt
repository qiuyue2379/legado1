package io.legado.app.ui.book.info

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.Theme
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.databinding.ActivityBookInfoBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.model.BookCover
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.association.ImportOnLineBookFileDialog
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.book.changecover.ChangeCoverDialog
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.group.GroupSelectDialog
import io.legado.app.ui.book.info.edit.BookInfoEditActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class BookInfoActivity :
    VMBaseActivity<ActivityBookInfoBinding, BookInfoViewModel>(toolBarTheme = Theme.Dark),
    GroupSelectDialog.CallBack,
    ChangeBookSourceDialog.CallBack,
    ChangeCoverDialog.CallBack {

    private val tocActivityResult = registerForActivityResult(TocActivityResult()) {
        it?.let {
            viewModel.bookData.value?.let { book ->
                launch {
                    withContext(IO) {
                        book.durChapterIndex = it.first
                        book.durChapterPos = it.second
                        appDb.bookDao.update(book)
                    }
                    viewModel.chapterListData.value?.let { chapterList ->
                        binding.tvToc.text =
                            getString(R.string.toc_s, chapterList[book.durChapterIndex].title)
                    }
                    startReadActivity(book)
                }
            }
        } ?: let {
            if (!viewModel.inBookshelf) {
                viewModel.delBook()
            }
        }
    }
    private val readBookResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshData(intent)
        if (it.resultCode == RESULT_OK) {
            viewModel.inBookshelf = true
            upTvBookshelf()
        }
    }
    private val infoEditResult = registerForActivityResult(
        StartActivityContract(BookInfoEditActivity::class.java)
    ) {
        if (it.resultCode == RESULT_OK) {
            viewModel.upEditBook()
        }
    }
    private var tocChanged = false

    override val binding by viewBinding(ActivityBookInfoBinding::inflate)
    override val viewModel by viewModels<BookInfoViewModel>()

    @SuppressLint("PrivateResource")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.transparent()
        binding.arcView.setBgColor(backgroundColor)
        binding.llInfo.setBackgroundColor(backgroundColor)
        binding.scrollView.setBackgroundColor(backgroundColor)
        binding.flAction.setBackgroundColor(bottomBackground)
        binding.tvShelf.setTextColor(getPrimaryTextColor(ColorUtils.isColorLight(bottomBackground)))
        binding.tvToc.text = getString(R.string.toc_s, getString(R.string.loading))
        viewModel.bookData.observe(this) { showBook(it) }
        viewModel.chapterListData.observe(this) { upLoading(false, it) }
        viewModel.initData(intent)
        initViewEvent()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_info, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_can_update)?.isChecked =
            viewModel.bookData.value?.canUpdate ?: true
        menu.findItem(R.id.menu_split_long_chapter)?.isChecked =
            viewModel.bookData.value?.getSplitLongChapter() ?: true
        menu.findItem(R.id.menu_login)?.isVisible =
            !viewModel.bookSource?.loginUrl.isNullOrBlank()
        menu.findItem(R.id.menu_set_source_variable)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_set_book_variable)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_can_update)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_split_long_chapter)?.isVisible =
            viewModel.bookData.value?.isLocalTxt() ?: false
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_edit -> {
                if (viewModel.inBookshelf) {
                    viewModel.bookData.value?.let {
                        infoEditResult.launch {
                            putExtra("bookUrl", it.bookUrl)
                        }
                    }
                } else {
                    toastOnUi(R.string.after_add_bookshelf)
                }
            }
            R.id.menu_share_it -> {
                viewModel.bookData.value?.let {
                    val bookJson = GSON.toJson(it)
                    val shareStr = "${it.bookUrl}#$bookJson"
                    shareWithQr(shareStr, it.name)
                }
            }
            R.id.menu_refresh -> {
                upLoading(true)
                viewModel.bookData.value?.let {
                    if (it.isLocalBook()) {
                        it.tocUrl = ""
                    }
                    viewModel.loadBookInfo(it, false)
                }
            }
            R.id.menu_login -> viewModel.bookSource?.let {
                startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", it.bookSourceUrl)
                }
            }
            R.id.menu_top -> viewModel.topBook()
            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_set_book_variable -> setBookVariable()
            R.id.menu_copy_book_url -> viewModel.bookData.value?.bookUrl?.let {
                sendToClip(it)
            } ?: toastOnUi(R.string.no_book)
            R.id.menu_copy_toc_url -> viewModel.bookData.value?.tocUrl?.let {
                sendToClip(it)
            } ?: toastOnUi(R.string.no_book)
            R.id.menu_can_update -> {
                if (viewModel.inBookshelf) {
                    viewModel.bookData.value?.let {
                        it.canUpdate = !it.canUpdate
                        viewModel.saveBook(it)
                    }
                } else {
                    toastOnUi(R.string.after_add_bookshelf)
                }
            }
            R.id.menu_clear_cache -> viewModel.clearCache()
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_split_long_chapter -> {
                upLoading(true)
                tocChanged = true
                viewModel.bookData.value?.let {
                    it.setSplitLongChapter(!item.isChecked)
                    viewModel.loadBookInfo(it, false)
                }
                item.isChecked = !item.isChecked
                if (!item.isChecked) longToastOnUi(R.string.need_more_time_load_content)
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun showBook(book: Book) = binding.run {
        showCover(book)
        tvName.text = book.name
        tvAuthor.text = getString(R.string.author_show, book.getRealAuthor())
        tvOrigin.text = getString(R.string.origin_show, book.originName)
        tvLasted.text = getString(R.string.lasted_show, book.latestChapterTitle)
        tvIntro.text = book.getDisplayIntro()
        upTvBookshelf()
        val kinds = book.getKindList()
        if (kinds.isEmpty()) {
            lbKind.gone()
        } else {
            lbKind.visible()
            lbKind.setLabels(kinds)
        }
        upGroup(book.group)
    }

    private fun showCover(book: Book) {
        binding.ivCover.load(book.getDisplayCover(), book.name, book.author, false, book.origin)
        BookCover.loadBlur(this, book.getDisplayCover())
            .into(binding.bgBook)
    }

    private fun upLoading(isLoading: Boolean, chapterList: List<BookChapter>? = null) {
        when {
            isLoading -> {
                binding.tvToc.text = getString(R.string.toc_s, getString(R.string.loading))
            }
            chapterList.isNullOrEmpty() -> {
                binding.tvToc.text = if (viewModel.isImportBookOnLine) getString(R.string.click_read_button_load) else getString(R.string.toc_s, getString(R.string.error_load_toc))
            }
            else -> {
                viewModel.bookData.value?.let {
                    if (it.durChapterIndex < chapterList.size) {
                        binding.tvToc.text =
                            getString(R.string.toc_s, chapterList[it.durChapterIndex].title)
                    } else {
                        binding.tvToc.text = getString(R.string.toc_s, chapterList.last().title)
                    }
                }
            }
        }
    }

    private fun upTvBookshelf() {
        if (viewModel.inBookshelf) {
            binding.tvShelf.text = getString(R.string.remove_from_bookshelf)
        } else {
            binding.tvShelf.text = getString(R.string.add_to_shelf)
        }
    }

    private fun upGroup(groupId: Long) {
        viewModel.loadGroup(groupId) {
            if (it.isNullOrEmpty()) {
                binding.tvGroup.text = getString(R.string.group_s, getString(R.string.no_group))
            } else {
                binding.tvGroup.text = getString(R.string.group_s, it)
            }
        }
    }

    private fun initViewEvent() = binding.run {
        ivCover.setOnClickListener {
            viewModel.bookData.value?.let {
                showDialogFragment(
                    ChangeCoverDialog(it.name, it.author)
                )
            } ?: toastOnUi("Book is null")
        }
        ivCover.setOnLongClickListener {
            viewModel.bookData.value?.getDisplayCover()?.let { path ->
                showDialogFragment(PhotoDialog(path))
            }
            true
        }
        tvRead.setOnClickListener {
            viewModel.bookData.value?.let { book ->
                if (viewModel.isImportBookOnLine) {
                    showDialogFragment<ImportOnLineBookFileDialog> {
                        putString("bookUrl", book.bookUrl)
                    }
                } else {
                    readBook(book)
                }
            } ?: toastOnUi("Book is null")
        }
        tvShelf.setOnClickListener {
            if (viewModel.inBookshelf) {
                deleteBook()
            } else {
                viewModel.addToBookshelf {
                    upTvBookshelf()
                }
            }
        }
        tvOrigin.setOnClickListener {
            viewModel.bookData.value?.let {
                startActivity<BookSourceEditActivity> {
                    putExtra("sourceUrl", it.origin)
                }
            } ?: toastOnUi("Book is null")
        }
        tvChangeSource.setOnClickListener {
            viewModel.bookData.value?.let { book ->
                showDialogFragment(ChangeBookSourceDialog(book.name, book.author))
            } ?: toastOnUi("Book is null")
        }
        tvTocView.setOnClickListener {
            if (!viewModel.inBookshelf) {
                viewModel.saveBook(viewModel.bookData.value) {
                    viewModel.saveChapterList {
                        openChapterList()
                    }
                }
            } else {
                openChapterList()
            }
        }
        tvChangeGroup.setOnClickListener {
            viewModel.bookData.value?.let {
                showDialogFragment(
                    GroupSelectDialog(it.group)
                )
            } ?: toastOnUi("Book is null")
        }
        tvAuthor.setOnClickListener {
            startActivity<SearchActivity> {
                putExtra("key", viewModel.bookData.value?.author)
            }
        }
        tvName.setOnClickListener {
            startActivity<SearchActivity> {
                putExtra("key", viewModel.bookData.value?.name)
            }
        }
    }

    private fun setSourceVariable() {
        launch {
            val variable = withContext(IO) { viewModel.bookSource?.getVariable() }
            alert(R.string.set_source_variable) {
                setMessage("源变量可在js中通过source.getVariable()获取")
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.hint = "source variable"
                    editView.setText(variable)
                }
                customView { alertBinding.root }
                okButton {
                    viewModel.bookSource?.setVariable(alertBinding.editView.text?.toString())
                }
                cancelButton()
                neutralButton(R.string.delete) {
                    viewModel.bookSource?.setVariable(null)
                }
            }
        }
    }

    private fun setBookVariable() {
        launch {
            val variable = withContext(IO) { viewModel.bookData.value?.getVariable("custom") }
            alert(R.string.set_source_variable) {
                setMessage("""书籍变量可在js中通过book.getVariable("custom")获取""")
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.hint = "book variable"
                    editView.setText(variable)
                }
                customView { alertBinding.root }
                okButton {
                    viewModel.bookData.value?.let { book ->
                        book.putVariable("custom", alertBinding.editView.text?.toString())
                        viewModel.saveBook(book)
                    }
                }
                cancelButton()
                neutralButton(R.string.delete) {
                    viewModel.bookData.value?.let { book ->
                        book.putVariable("custom", null)
                        viewModel.saveBook(book)
                    }
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun deleteBook() {
        viewModel.bookData.value?.let {
            if (it.isLocalBook()) {
                alert(
                    titleResource = R.string.sure,
                    messageResource = R.string.sure_del
                ) {
                    val checkBox = CheckBox(this@BookInfoActivity).apply {
                        setText(R.string.delete_book_file)
                    }
                    val view = LinearLayout(this@BookInfoActivity).apply {
                        setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
                        addView(checkBox)
                    }
                    customView { view }
                    positiveButton(R.string.yes) {
                        viewModel.delBook(checkBox.isChecked) {
                            finish()
                        }
                    }
                    negativeButton(R.string.no)
                }
            } else {
                viewModel.delBook {
                    upTvBookshelf()
                }
            }
        }
    }

    private fun openChapterList() {
        if (viewModel.chapterListData.value.isNullOrEmpty()) {
            toastOnUi(R.string.chapter_list_empty)
            return
        }
        viewModel.bookData.value?.let {
            tocActivityResult.launch(it.bookUrl)
        }
    }

    private fun readBook(book: Book) {
        if (!viewModel.inBookshelf) {
            viewModel.saveBook(book) {
                viewModel.saveChapterList {
                    startReadActivity(book)
                }
            }
        } else {
            viewModel.saveBook(book) {
                startReadActivity(book)
            }
        }
    }

    private fun startReadActivity(book: Book) {
        when (book.type) {
            BookType.audio -> readBookResult.launch(
                Intent(this, AudioPlayActivity::class.java)
                    .putExtra("bookUrl", book.bookUrl)
                    .putExtra("inBookshelf", viewModel.inBookshelf)
            )
            else -> readBookResult.launch(
                Intent(this, ReadBookActivity::class.java)
                    .putExtra("bookUrl", book.bookUrl)
                    .putExtra("inBookshelf", viewModel.inBookshelf)
                    .putExtra("tocChanged", tocChanged)
            )
        }
        tocChanged = false
    }

    override val oldBook: Book?
        get() = viewModel.bookData.value

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        viewModel.changeTo(source, book, toc)
    }

    override fun coverChangeTo(coverUrl: String) {
        viewModel.bookData.value?.let { book ->
            book.customCoverUrl = coverUrl
            viewModel.saveBook(book)
            showCover(book)
        }
    }

    override fun upGroup(requestCode: Int, groupId: Long) {
        upGroup(groupId)
        viewModel.bookData.value?.let { book ->
            book.group = groupId
            if (viewModel.inBookshelf) {
                viewModel.saveBook(book)
            } else if (groupId > 0) {
                viewModel.saveBook(book)
                viewModel.inBookshelf = true
                upTvBookshelf()
            }
        }
    }

    override fun observeLiveBus() {
        observeEvent<String>(EventBus.BOOK_URL_CHANGED) {
            viewModel.changeToLocalBook(it)
        }
    }
}