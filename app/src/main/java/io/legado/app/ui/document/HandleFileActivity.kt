package io.legado.app.ui.document

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.Theme
import io.legado.app.databinding.ActivityTranslucenceBinding
import io.legado.app.help.IntentData
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.utils.getJsonArray
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.io.File

class HandleFileActivity :
    VMBaseActivity<ActivityTranslucenceBinding, HandleFileViewModel>(
        theme = Theme.Transparent
    ), FilePickerDialog.CallBack {

    override val binding by viewBinding(ActivityTranslucenceBinding::inflate)
    override val viewModel by viewModels<HandleFileViewModel>()
    private var mode = 0

    private val selectDocTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: let {
                finish()
                return@registerForActivityResult
            }
            if (uri.isContentScheme()) {
                val modeFlags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, modeFlags)
            }
            onResult(Intent().setData(uri))
        }

    private val selectDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        it ?: return@registerForActivityResult
        onResult(Intent().setData(it))
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        mode = intent.getIntExtra("mode", 0)
        viewModel.errorLiveData.observe(this) {
            toastOnUi(it)
            finish()
        }
        val allowExtensions = intent.getStringArrayExtra("allowExtensions")
        val selectList: ArrayList<SelectItem<Int>> = when (mode) {
            HandleFileContract.DIR -> getDirActions()
            HandleFileContract.FILE -> getFileActions()
            HandleFileContract.EXPORT -> arrayListOf(
                SelectItem(getString(R.string.upload_url), 111)
            ).apply {
                addAll(getDirActions())
            }
            else -> arrayListOf()
        }
        intent.getJsonArray<SelectItem<Int>>("otherActions")?.let {
            selectList.addAll(it)
        }
        val title = intent.getStringExtra("title") ?: let {
            when (mode) {
                HandleFileContract.EXPORT -> return@let getString(R.string.export)
                HandleFileContract.DIR -> return@let getString(R.string.select_folder)
                else -> return@let getString(R.string.select_file)
            }
        }
        alert(title) {
            items(selectList) { _, item, _ ->
                when (item.value) {
                    HandleFileContract.DIR -> selectDocTree.launch(null)
                    HandleFileContract.FILE -> selectDoc.launch(typesOfExtensions(allowExtensions))
                    10 -> checkPermissions {
                        FilePickerDialog.show(
                            supportFragmentManager,
                            mode = HandleFileContract.DIR
                        )
                    }
                    11 -> checkPermissions {
                        FilePickerDialog.show(
                            supportFragmentManager,
                            mode = HandleFileContract.FILE,
                            allowExtensions = allowExtensions
                        )
                    }
                    111 -> getFileData()?.let {
                        viewModel.upload(it.first, it.second, it.third) { url ->
                            val uri = Uri.parse(url)
                            setResult(RESULT_OK, Intent().setData(uri))
                            finish()
                        }
                    }
                    else -> {
                        val path = item.title
                        val uri = if (path.isContentScheme()) {
                            Uri.parse(path)
                        } else {
                            Uri.fromFile(File(path))
                        }
                        onResult(Intent().setData(uri))
                    }
                }
            }
            onCancelled {
                finish()
            }
        }.show()
    }

    private fun getFileData(): Triple<String, ByteArray, String>? {
        val fileName = intent.getStringExtra("fileName")
        val file = intent.getStringExtra("fileKey")?.let {
            IntentData.getData<ByteArray>(it)
        }
        val contentType = intent.getStringExtra("contentType")
        if (fileName != null && file != null && contentType != null) {
            return Triple(fileName, file, contentType)
        }
        return null
    }

    private fun getDirActions(): ArrayList<SelectItem<Int>> {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            arrayListOf(
                SelectItem(getString(R.string.sys_folder_picker), HandleFileContract.DIR),
                SelectItem(getString(R.string.app_folder_picker), 10)
            )
        } else {
            arrayListOf(SelectItem(getString(R.string.sys_folder_picker), HandleFileContract.DIR))
        }
    }

    private fun getFileActions(): ArrayList<SelectItem<Int>> {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            arrayListOf(
                SelectItem(getString(R.string.sys_file_picker), HandleFileContract.FILE),
                SelectItem(getString(R.string.app_file_picker), 11)
            )
        } else {
            arrayListOf(SelectItem(getString(R.string.sys_file_picker), HandleFileContract.FILE))
        }
    }

    private fun checkPermissions(success: (() -> Unit)? = null) {
        PermissionsCompat.Builder(this)
            .addPermissions(*Permissions.Group.STORAGE)
            .rationale(R.string.tip_perm_request_storage)
            .onGranted {
                success?.invoke()
            }
            .request()
    }

    private fun typesOfExtensions(allowExtensions: Array<String>?): Array<String> {
        val types = hashSetOf<String>()
        if (allowExtensions.isNullOrEmpty()) {
            types.add("*/*")
        } else {
            allowExtensions.forEach {
                when (it) {
                    "*" -> types.add("*/*")
                    "txt", "xml" -> types.add("text/*")
                    else -> {
                        val mime = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(it)
                            ?: "application/octet-stream"
                        types.add(mime)
                    }
                }
            }
        }
        return types.toTypedArray()
    }

    override fun onResult(data: Intent) {
        val uri = data.data
        uri ?: let {
            finish()
            return
        }
        if (mode == HandleFileContract.EXPORT) {
            getFileData()?.let { fileData ->
                viewModel.saveToLocal(uri, fileData.first, fileData.second) { savedUri ->
                    setResult(RESULT_OK, Intent().setData(savedUri))
                    finish()
                }
            }
        } else {
            setResult(RESULT_OK, data)
            finish()
        }
    }

}