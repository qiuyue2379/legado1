package io.legado.app.ui.book.info.edit

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.documentfile.provider.DocumentFile
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.Permissions
import io.legado.app.data.entities.Book
import io.legado.app.databinding.ActivityBookInfoEditBinding
import io.legado.app.ui.book.changecover.ChangeCoverDialog
import io.legado.app.utils.*
import java.io.File

class BookInfoEditActivity :
    VMBaseActivity<ActivityBookInfoEditBinding, BookInfoEditViewModel>(),
    ChangeCoverDialog.CallBack {

    override val viewModel: BookInfoEditViewModel
            by viewModels()

    override fun getViewBinding(): ActivityBookInfoEditBinding {
        return ActivityBookInfoEditBinding.inflate(layoutInflater)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        viewModel.bookData.observe(this, { upView(it) })
        if (viewModel.bookData.value == null) {
            intent.getStringExtra("bookUrl")?.let {
                viewModel.loadBook(it)
            }
        }
        initEvent()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_info_edit, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> saveData()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initEvent() = with(binding) {
        tvChangeCover.setOnClickListener {
            viewModel.bookData.value?.let {
                ChangeCoverDialog.show(supportFragmentManager, it.name, it.author)
            }
        }
        tvSelectCover.setOnClickListener {
            selectImage()
        }
        tvRefreshCover.setOnClickListener {
            viewModel.book?.customCoverUrl = tieCoverUrl.text?.toString()
            upCover()
        }
    }

    private fun upView(book: Book) = with(binding) {
        tieBookName.setText(book.name)
        tieBookAuthor.setText(book.author)
        tieCoverUrl.setText(book.getDisplayCover())
        tieBookIntro.setText(book.getDisplayIntro())
        upCover()
    }

    private fun upCover() {
        viewModel.book.let {
            binding.ivCover.load(it?.getDisplayCover(), it?.name, it?.author)
        }
    }

    private fun saveData() = with(binding) {
        viewModel.book?.let { book ->
            book.name = tieBookName.text?.toString() ?: ""
            book.author = tieBookAuthor.text?.toString() ?: ""
            val customCoverUrl = tieCoverUrl.text?.toString()
            book.customCoverUrl = if (customCoverUrl == book.coverUrl) null else customCoverUrl
            book.customIntro = tieBookIntro.text?.toString()
            viewModel.saveBook(book) {
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
    }

    private fun selectImage() {
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            coverChangeTo(it)
        }.launch("image/*")
    }

    override fun coverChangeTo(coverUrl: String) {
        viewModel.book?.customCoverUrl = coverUrl
        binding.tieCoverUrl.setText(coverUrl)
        upCover()
    }

    private fun coverChangeTo(uri: Uri) {
        if (uri.isContentScheme()) {
            val doc = DocumentFile.fromSingleUri(this, uri)
            doc?.name?.let {
                var file = this.externalFilesDir
                file = FileUtils.createFileIfNotExist(file, "covers", it)
                kotlin.runCatching {
                    DocumentUtils.readBytes(this, doc.uri)
                }.getOrNull()?.let { byteArray ->
                    file.writeBytes(byteArray)
                    coverChangeTo(file.absolutePath)
                } ?: toastOnUi("获取文件出错")
            }
        } else {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                RealPathUtil.getPath(this, uri)?.let { path ->
                    val imgFile = File(path)
                    if (imgFile.exists()) {
                        var file = this.externalFilesDir
                        file = FileUtils.createFileIfNotExist(file, "covers", imgFile.name)
                        file.writeBytes(imgFile.readBytes())
                        coverChangeTo(file.absolutePath)
                    }
                }
            }.launch(Permissions.Group.STORAGE)
        }
    }

}