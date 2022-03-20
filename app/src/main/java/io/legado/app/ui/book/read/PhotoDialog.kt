package io.legado.app.ui.book.read

import android.os.Bundle
import android.view.View
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogPhotoViewBinding
import io.legado.app.model.BookCover
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.provider.ImageProvider
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 显示图片
 */
class PhotoDialog() : BaseDialogFragment(R.layout.dialog_photo_view) {

    constructor(chapterIndex: Int, src: String) : this() {
        arguments = Bundle().apply {
            putInt("chapterIndex", chapterIndex)
            putString("src", src)
        }
    }

    constructor(path: String) : this() {
        arguments = Bundle().apply {
            putString("path", path)
        }
    }

    private val binding by viewBinding(DialogPhotoViewBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(1f, 1f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        arguments?.let {
            val path = it.getString("path")
            if (path.isNullOrEmpty()) {
                val chapterIndex = it.getInt("chapterIndex")
                val src = it.getString("src")
                ReadBook.book?.let { book ->
                    src?.let {
                        execute {
                            ImageProvider.getImage(book, chapterIndex, src, ReadBook.bookSource)
                        }.onSuccess { bitmap ->
                            if (bitmap != null) {
                                binding.photoView.setImageBitmap(bitmap)
                            }
                        }
                    }
                }
            } else {
                BookCover.load(requireContext(), path = path)
                    .into(binding.photoView)
            }
        }

    }

}
