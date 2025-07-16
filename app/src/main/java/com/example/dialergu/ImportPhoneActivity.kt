package com.example.dialergu

import com.example.dialergu.BuildConfig
import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import android.content.ContentValues
import android.graphics.ImageDecoder // Add this import
import android.os.Build // Add this import

// PhoneNumberEntry 数据模型定义，确保其为 Parcelable
// 已经包含在 MainActivity.kt 中，这里不再重复定义，但确保在项目中可见
// @Parcelize
// data class PhoneNumberEntry(val number: String, var status: String, val name: String) : Parcelable


class ImportPhoneActivity : AppCompatActivity() {

    private lateinit var buttonConfirmImport: Button // 导入确认按钮
    private lateinit var buttonImportClipboard: Button // 从剪贴板导入按钮
    private lateinit var buttonImportFile: Button // 从文件导入按钮
    private lateinit var buttonImportOcr: Button // 拍照识别按钮
    private lateinit var buttonImportPhotoOcr: Button // 照片识别按钮
    private lateinit var importStatusTextView: TextView // 导入状态文本视图
    private lateinit var imageViewCapturedPhoto: ImageView // 显示捕获照片的 ImageView
    private lateinit var textViewRecognizedText: TextView // 显示识别文本的 TextView

    // 存储新解析的电话号码列表
    private var newPhoneList: MutableList<PhoneNumberEntry> = mutableListOf()

    private val FILE_SELECT_REQUEST_CODE = 1001 // 文件选择请求码
    private val RESULT_KEY_PHONE_LIST = "new_phone_list" // 返回结果的键

    // 用于存储捕获图像的 Uri
    private var currentImageUri: Uri? = null

    // DashScope API 配置

    //private val apiKey = "sk-e22a88c692f6426cb38c5f8dfb5a60a4" // 请替换为您的实际 API Key
    private val apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    private val apiKey = BuildConfig.DASH_SCOPE_API_KEY

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // 相机权限请求启动器
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
        }
    }

    // 读取存储权限请求启动器
    private val requestReadStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openImagePicker()
        } else {
            Toast.makeText(this, "需要读取存储权限才能选择图片", Toast.LENGTH_SHORT).show()
        }
    }

    // 拍照结果启动器
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // 从指定的 Uri 加载图像，而不是从 Intent 的数据中加载
            currentImageUri?.let { uri ->
                try {
                    val imageBitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    }
                    imageBitmap?.let {
                        imageViewCapturedPhoto.setImageBitmap(it) // 显示捕获的图像
                        textViewRecognizedText.text = "正在识别文字..." // 立即更新 UI 显示识别状态
                        recognizeText(it)
                    } ?: run {
                        Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("OCR", "加载图片失败", e)
                    Toast.makeText(this, "加载图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Toast.makeText(this, "图片URI为空，无法加载图片", Toast.LENGTH_SHORT).show()
            }
        } else {
            // 如果拍照取消或失败，清除 URI
            currentImageUri = null
            Toast.makeText(this, "拍照已取消", Toast.LENGTH_SHORT).show()
            textViewRecognizedText.text = "识别文本：拍照已取消" // 更新 UI
        }
    }

    // 选择图片结果启动器
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            imageUri?.let { uri ->
                try {
                    val imageBitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    }
                    imageBitmap?.let {
                        imageViewCapturedPhoto.setImageBitmap(it) // 显示选择的图像
                        textViewRecognizedText.text = "正在识别文字..." // 立即更新 UI 显示识别状态
                        recognizeText(it)
                    }
                } catch (e: Exception) {
                    Log.e("OCR", "加载图片失败", e)
                    Toast.makeText(this, "加载图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "选择图片已取消", Toast.LENGTH_SHORT).show()
            textViewRecognizedText.text = "识别文本：选择图片已取消" // 更新 UI
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_phone)

        buttonConfirmImport = findViewById(R.id.buttonConfirmImport) // 初始化导入确认按钮
        buttonImportClipboard = findViewById(R.id.buttonImportClipboard)
        buttonImportFile = findViewById(R.id.buttonImportFile)
        buttonImportOcr = findViewById(R.id.buttonImportOcr)
        buttonImportPhotoOcr = findViewById(R.id.buttonImportPhotoOcr)
        importStatusTextView = findViewById(R.id.importStatusTextView)
        imageViewCapturedPhoto = findViewById(R.id.imageViewCapturedPhoto) // 初始化 ImageView
        textViewRecognizedText = findViewById(R.id.textViewRecognizedText) // 初始化 TextView

        // 为导入确认按钮设置点击监听器
        buttonConfirmImport.setOnClickListener {
            confirmImport()
        }

        buttonImportClipboard.setOnClickListener {
            importFromClipboard()
        }

        buttonImportFile.setOnClickListener {
            openFilePicker()
        }

        // 修改“拍照识别”按钮的点击事件以检查相机权限
        buttonImportOcr.setOnClickListener {
            checkCameraPermission()
        }

        // 修改“照片识片”按钮的点击事件以检查读取存储权限
        buttonImportPhotoOcr.setOnClickListener {
            checkReadStoragePermission()
        }
    }

    /**
     * 从剪贴板导入电话号码
     */
    private fun importFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip()) {
            val clipData: ClipData? = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val clipboardText = clipData.getItemAt(0).text?.toString()
                if (!clipboardText.isNullOrEmpty()) {
                    processImportedData(clipboardText)
                    importStatusTextView.text = "导入状态：从剪贴板导入成功！"
                } else {
                    Toast.makeText(this, "剪贴板为空或不包含文本", Toast.LENGTH_SHORT).show()
                    importStatusTextView.text = "导入状态：剪贴板为空或不包含文本。"
                }
            } else {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
                importStatusTextView.text = "导入状态：剪贴板为空。"
            }
        } else {
            Toast.makeText(this, "剪贴板无内容", Toast.LENGTH_SHORT).show()
            importStatusTextView.text = "导入状态：剪贴板无内容。"
        }
    }

    /**
     * 打开文件选择器以选择 TXT 文件
     */
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain" // 只允许选择文本文件
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, FILE_SELECT_REQUEST_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_SELECT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                try {
                    val content = readTextFromUri(uri)
                    processImportedData(content)
                    importStatusTextView.text = "导入状态：从文件导入成功！"
                } catch (e: Exception) {
                    Log.e("ImportPhoneActivity", "读取文件失败", e)
                    Toast.makeText(this, "读取文件失败: ${e.message}", Toast.LENGTH_LONG).show()
                    importStatusTextView.text = "导入状态：读取文件失败。"
                }
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            importStatusTextView.text = "导入状态：文件选择已取消。"
        }
    }

    /**
     * 从给定的 URI 读取文本内容
     */
    private fun readTextFromUri(uri: Uri): String {
        val stringBuilder = StringBuilder()
        contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line).append("\n")
                }
            }
        }
        return stringBuilder.toString()
    }

    /**
     * 处理导入的文本数据，解析成 PhoneNumberEntry 列表并显示在文本区域
     * 修改版本：每行是一个联系人记录，通过常见分隔符分成电话号码、状态、姓名
     */
    private fun processImportedData(data: String) {
        // 清空旧数据，准备存储新解析的数据
        newPhoneList.clear()

        // 按行分割数据，每行是一个联系人记录
        val lines = data.split("\n", "\r\n", "\r").filter { it.isNotBlank() }

        Log.d("ImportPhoneActivity", "Parsing data, total ${lines.size} lines")

        lines.forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty()) {
                val entry = parseContactLine(trimmedLine)
                if (entry != null) {
                    newPhoneList.add(entry)
                    Log.d("ImportPhoneActivity", "Parsed successfully: ${entry.name} - ${entry.number} - ${entry.status}")
                } else {
                    Log.w("ImportPhoneActivity", "Failed to parse line: $trimmedLine")
                }
            }
        }

        if (newPhoneList.isNotEmpty()) {
            // 格式化 newPhoneList 内容并显示在 textViewRecognizedText 中
            val formattedText = StringBuilder()
            newPhoneList.forEachIndexed { index, entry ->
//                formattedText.append("姓名: ${entry.name}\n")
//                formattedText.append("电话: ${entry.number}\n")
//                formattedText.append("状态: ${entry.status}\n")
                formattedText.append(" ${entry.name},${entry.number},${entry.status}")
                if (index < newPhoneList.size - 1) {
                    formattedText.append("\n") // 每条记录之间添加空行
                }
            }
            textViewRecognizedText.text = formattedText.toString()
            importStatusTextView.text = "导入状态：成功解析 ${newPhoneList.size} 条记录并显示。"
            Toast.makeText(this, "成功解析 ${newPhoneList.size} 条记录", Toast.LENGTH_SHORT).show()

        } else {
            Toast.makeText(this, "未解析到有效电话号码记录", Toast.LENGTH_SHORT).show()
            importStatusTextView.text = "导入状态：未解析到有效电话号码记录。"
            textViewRecognizedText.text = "识别文本：未找到有效记录。" // 清空或显示提示
        }
    }

    /**
     * 解析单行联系人数据
     * 支持的格式：
     * 1. 姓名,电话号码,状态
     * 2. 电话号码,姓名,状态 (与 MainActivity 示例数据顺序一致)
     * 3. 电话号码,姓名 (状态默认为"未拨出")
     * 4. 姓名,电话号码 (状态默认为"未拨出")
     * 5. 仅电话号码 (姓名为空，状态默认为"未拨出")
     * 6. 电话号码 状态 姓名 (空格分隔)
     * 7. 姓名 电话号码 状态 (空格分隔)
     * 8. 电话号码|状态|姓名 (竖线分隔)
     * 9. 电话号码\t状态\t姓名 (制表符分隔)
     * 10. 电话号码;状态;姓名 (分号分隔)
     * 11. 电话号码-状态-姓名 (连字符分隔)
     */
    private fun parseContactLine(line: String): PhoneNumberEntry? {
        // 定义常见的分隔符，按优先级排序
        val separators = arrayOf(",", "，", "|", "\t", ";", "-")

        // 尝试使用不同的分隔符进行分割
        for (separator in separators) {
            if (line.contains(separator)) {
                val parts = line.split(separator).map { it.trim() }.filter { it.isNotEmpty() }

                when (parts.size) {
                    1 -> {
                        // 只有一个部分，尝试作为电话号码
                        val number = parts[0]
                        if (isValidPhoneNumber(number)) {
                            return PhoneNumberEntry(number, "未拨出", "")
                        }
                    }
                    2 -> {
                        // 两个部分：可能是 电话号码,姓名 或 姓名,电话号码
                        val first = parts[0]
                        val second = parts[1]

                        if (isValidPhoneNumber(first)) {
                            // 第一个字段是电话号码，第二个字段是姓名
                            return PhoneNumberEntry(first, "未拨出", second)
                        } else if (isValidPhoneNumber(second)) {
                            // 第二个字段是电话号码，第一个字段是姓名
                            return PhoneNumberEntry(second, "未拨出", first)
                        }
                    }
                    3 -> {
                        // 三个部分：尝试 姓名,电话号码,状态 或 电话号码,姓名,状态
                        val p1 = parts[0]
                        val p2 = parts[1]
                        val p3 = parts[2]

                        // 优先尝试 姓名,电话号码,状态
                        if (isValidPhoneNumber(p2)) { // p2 是电话号码
                            return PhoneNumberEntry(p2, p3, p1) // name, number, status
                        } else if (isValidPhoneNumber(p1)) { // p1 是电话号码
                            return PhoneNumberEntry(p1, p3, p2) // number, name, status
                        }
                    }
                    else -> {
                        // 超过 3 个部分，尝试提取前三个作为 姓名,电话号码,状态
                        val p1 = parts[0]
                        val p2 = parts[1]
                        val p3 = parts[2]
                        val remainingParts = parts.drop(3).joinToString(" ") // 将剩余部分作为姓名的一部分

                        if (isValidPhoneNumber(p2)) { // p2 是电话号码
                            return PhoneNumberEntry(p2, p3, p1 + " " + remainingParts)
                        } else if (isValidPhoneNumber(p1)) { // p1 是电话号码
                            return PhoneNumberEntry(p1, p3, p2 + " " + remainingParts)
                        }
                    }
                }
            }
        }

        // 如果没有找到分隔符，或者分隔符解析失败，尝试用空格分割
        val spaceParts = line.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
        when (spaceParts.size) {
            1 -> {
                val number = spaceParts[0]
                if (isValidPhoneNumber(number)) {
                    return PhoneNumberEntry(number, "未拨出", "")
                }
            }
            2 -> {
                val first = spaceParts[0]
                val second = spaceParts[1]
                if (isValidPhoneNumber(first)) {
                    return PhoneNumberEntry(first, "未拨出", second)
                } else if (isValidPhoneNumber(second)) {
                    return PhoneNumberEntry(second, "未拨出", first)
                }
            }
            3 -> {
                val p1 = spaceParts[0]
                val p2 = spaceParts[1]
                val p3 = spaceParts[2]

                if (isValidPhoneNumber(p2)) { // p2 是电话号码
                    return PhoneNumberEntry(p2, p3, p1)
                } else if (isValidPhoneNumber(p1)) { // p1 是电话号码
                    return PhoneNumberEntry(p1, p3, p2)
                }
            }
            else -> {
                // 超过 3 个部分，尝试提取前三个作为 姓名,电话号码,状态 (或 电话号码,姓名,状态)
                val p1 = spaceParts[0]
                val p2 = spaceParts[1]
                val p3 = spaceParts[2]
                val remainingParts = spaceParts.drop(3).joinToString(" ")

                if (isValidPhoneNumber(p2)) {
                    return PhoneNumberEntry(p2, p3, p1 + " " + remainingParts)
                } else if (isValidPhoneNumber(p1)) {
                    return PhoneNumberEntry(p1, p3, p2 + " " + remainingParts)
                }
            }
        }

        // 如果所有尝试都失败，返回 null
        return null
    }


    /**
     * 简单的电话号码验证
     * 支持手机号码、座机号码、带区号的号码等
     */
    private fun isValidPhoneNumber(number: String): Boolean {
        // 移除所有非数字和非特殊字符（+、-、(、)）
        val cleanNumber = number.replace(Regex("[^0-9+\\-()]"), "")

        // 基本长度检查
        if (cleanNumber.length < 7 || cleanNumber.length > 20) {
            return false
        }

        // 检查是否包含数字
        if (!cleanNumber.contains(Regex("\\d"))) {
            return false
        }

        // 简单的格式检查
        return cleanNumber.matches(Regex("^[+]?[0-9\\-()]+$"))
    }

    /**
     * 判断一个字段是否是状态字段
     */
    private fun isStatusField(field: String): Boolean {
        val statusKeywords = arrayOf(
            "未拨出", "已拨出", "已尝试拨出", "拨号失败", "已完成", "忙音", "无人接听", "关机", "空号"
        )
        return statusKeywords.any { field.contains(it) }
    }

    // 以下是集成“拍照识别”和“照片识片”功能的代码

    /**
     * 检查相机权限并打开相机
     */
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /**
     * 检查读取存储权限并打开图片选择器
     */
    private fun checkReadStoragePermission() {
        when {
            // 对于 Android 10 (API 29) 及以上版本，不再需要 READ_EXTERNAL_STORAGE 权限来访问自己的媒体文件
            // 而是直接通过 MediaStore API 写入，所以这里只检查 Android 9 (API 28) 及以下版本
            // 对于 READ_MEDIA_IMAGES 权限，仅在 Android 13 (API 33) 及以上版本需要
            ContextCompat.checkSelfPermission(
                this,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
            ) == PackageManager.PERMISSION_GRANTED -> {
                openImagePicker()
            }
            else -> {
                requestReadStoragePermissionLauncher.launch(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_IMAGES
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                )
            }
        }
    }

    /**
     * 打开相机应用程序拍照
     */
    private fun openCamera() {
        val filename = "IMG_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
        }

        currentImageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        currentImageUri?.let { uri ->
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
            }
            takePictureLauncher.launch(takePictureIntent)
        } ?: run {
            Toast.makeText(this, "无法创建图片文件URI", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 打开图片选择器选择图片
     */
    private fun openImagePicker() {
        val pickImageIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(pickImageIntent)
    }

    /**
     * 从给定的位图识别文本
     */
    private fun recognizeText(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("OCR", "Starting Bitmap to Base64 conversion...")
                val startTimeBase64 = System.currentTimeMillis()
                val base64Image = bitmapToBase64(bitmap)
                val endTimeBase64 = System.currentTimeMillis()
                Log.d("OCR", "Bitmap to Base64 conversion completed, time taken: ${endTimeBase64 - startTimeBase64} ms")

                Log.d("OCR", "Starting DashScope API call...")
                val startTimeApi = System.currentTimeMillis()
                val response = callDashScopeAPI(base64Image)
                val endTimeApi = System.currentTimeMillis()
                Log.d("OCR", "DashScope API call completed, time taken: ${endTimeApi - startTimeApi} ms")

                withContext(Dispatchers.Main) {
                    textViewRecognizedText.text = response
                    Log.d("OCR", "UI update completed.")
                }
            } catch (e: Exception) {
                Log.e("OCR", "Recognition failed", e)
                withContext(Dispatchers.Main) {
                    textViewRecognizedText.text = "识别失败: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    Log.d("OCR", "Recognition process finished.")
                }
            }
        }
    }

    /**
     * 将位图转换为 Base64 字符串
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos) // 降低图像质量以减小 API 请求体大小
        val imageBytes = baos.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    }

    /**
     * 调用 DashScope API 进行文本识别
     */
    private fun callDashScopeAPI(base64Image: String): String {
        val jsonBody = JSONObject().apply {
            put("model", "qwen-vl-ocr-latest") // 使用官方推荐的 OCR 模型
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$base64Image")
                            })
                            // 添加像素阈值参数，参考官方示例
                            put("min_pixels", 28 * 28 * 4)
                            put("max_pixels", 28 * 28 * 8192)
                        })
                        put(JSONObject().apply {
                            put("type", "text")
                            // 恢复到原始的 text 提示
                            put("text", "请提取图像中的单位、电话、联系人，形成通讯录条目。")
                        })
                    })
                })
            })
        }
        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(apiUrl)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        val response = client.newCall(request).execute() // 这是一个同步调用
        val responseBody = response.body?.string()

        Log.d("OCR", "API Response Status Code: ${response.code}")
        Log.d("OCR", "API Response Body: $responseBody")

        return if (response.isSuccessful && responseBody != null) {
            parseDashScopeResponse(responseBody)
        } else {
            "API调用失败: ${response.code} - ${response.message}\n响应体: $responseBody"
        }
    }

    /**
     * 解析 DashScope API 响应
     */
    private fun parseDashScopeResponse(responseBody: String): String {
        return try {
            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.getJSONArray("choices")
            if (choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                message.getString("content")
            } else {
                "未检测到文字"
            }
        } catch (e: Exception) {
            Log.e("OCR", "Failed to parse response", e)
            "解析响应失败: ${e.message}\n原始响应: $responseBody"
        }
    }

    /**
     * 处理“确认导入”按钮点击事件
     */
    private fun confirmImport() {
        // 重新处理 textViewRecognizedText.text 中的数据，以确保 newPhoneList 包含最新的解析结果
        processImportedData(textViewRecognizedText.text.toString())

        val resultIntent = Intent().apply {
            putParcelableArrayListExtra(RESULT_KEY_PHONE_LIST, ArrayList(newPhoneList))
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
