package com.example.dialergu

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.SharedPreferences // 导入 SharedPreferences

// 数据模型：表示一个电话号码及其拨号状态
// 确保 PhoneNumberEntry 已经实现了 Parcelable 接口
 import android.os.Parcelable
 import kotlinx.parcelize.Parcelize
 @Parcelize
 data class PhoneNumberEntry(val number: String, var status: String, val name: String) : Parcelable


class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PhoneNumberAdapter
    private lateinit var buttonImportPhone: Button
    private lateinit var buttonHelp: Button

    // SharedPreferences 的文件名和键
    private val PREFS_NAME = "DialerGuPrefs"
    private val IMPORT_PHONE_REQUEST_CODE = 1002 // 用于启动 ImportPhoneActivity 的请求码
    private val RESULT_KEY_PHONE_LIST = "new_phone_list" // 与 ImportPhoneActivity 中定义的键一致

    // 示例电话号码列表
    private val phoneNumbers = mutableListOf(
        PhoneNumberEntry("13800138000", "未拨出", "Smith"),
        PhoneNumberEntry("010-12309", "未拨出", "国家反诈中心")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerViewPhoneNumbers)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 在设置适配器之前加载保存的状态
        loadPhoneStatuses()

        adapter = PhoneNumberAdapter(phoneNumbers) { position ->
            dialPhoneNumber(position)
        }
        recyclerView.adapter = adapter

        buttonImportPhone = findViewById(R.id.buttonImportPhone)
        buttonHelp = findViewById(R.id.buttonHelp)

        buttonImportPhone.setOnClickListener {
            // 启动 ImportPhoneActivity 并期望返回结果
            val intent = Intent(this, ImportPhoneActivity::class.java)
            startActivityForResult(intent, IMPORT_PHONE_REQUEST_CODE)
            Log.d("MainActivity", "点击了导入电话按钮，跳转到导入电话页面")
        }

        buttonHelp.setOnClickListener {
            val intent = Intent(this, HelpActivity::class.java)
            startActivity(intent)
            Log.d("MainActivity", "点击了使用帮助按钮，跳转到使用帮助页面")
        }
    }

    /**
     * 处理从其他 Activity 返回的结果
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPORT_PHONE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // 从 Intent 中获取返回的电话列表
            val newImportedList: ArrayList<PhoneNumberEntry>? =
                data?.getParcelableArrayListExtra(RESULT_KEY_PHONE_LIST)

            if (newImportedList != null && newImportedList.isNotEmpty()) {
                // 清空原有列表并添加新导入的记录
                phoneNumbers.clear()
                phoneNumbers.addAll(newImportedList)
                adapter.notifyDataSetChanged() // 通知适配器数据已更改
                savePhoneStatuses() // 保存新导入的电话状态

                Toast.makeText(this, "电话号码已成功导入！", Toast.LENGTH_SHORT).show()
                Log.d("MainActivity", "电话号码已成功导入，共 ${newImportedList.size} 条记录。")
            } else {
                Toast.makeText(this, "未导入任何有效电话号码。", Toast.LENGTH_SHORT).show()
                Log.d("MainActivity", "未导入任何有效电话号码。")
            }
        } else if (requestCode == IMPORT_PHONE_REQUEST_CODE && resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(this, "电话导入已取消。", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "电话导入已取消。")
        }
    }


    /**
     * 保存电话号码的状态到 SharedPreferences
     */
    private fun savePhoneStatuses() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val editor = prefs.edit()
        // 先清空所有旧的状态，因为列表可能已经完全更新
        editor.clear()
        phoneNumbers.forEach { entry ->
            // 使用电话号码作为键，保存其状态
            editor.putString(entry.number, entry.status)
        }
        editor.apply() // 异步保存更改
        Log.d("MainActivity", "电话状态已保存到 SharedPreferences")
    }

    /**
     * 从 SharedPreferences 加载电话号码的状态
     */
    private fun loadPhoneStatuses() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        phoneNumbers.forEach { entry ->
            // 尝试从 SharedPreferences 获取保存的状态，如果不存在则使用默认值（null）
            val savedStatus = prefs.getString(entry.number, null)
            if (savedStatus != null) {
                // 如果找到了保存的状态，则更新当前 PhoneNumberEntry 的状态
                entry.status = savedStatus
            }
        }
        // 通知适配器数据已更改，以便刷新 UI
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
        Log.d("MainActivity", "电话状态已从 SharedPreferences 加载")
    }

    // 拨打电话号码的函数 - 多种方式尝试
    private fun dialPhoneNumber(position: Int) {
        val entry = phoneNumbers[position]
        val phoneNumber = entry.number

        Log.d("DialerGu", "开始拨号: $phoneNumber")

        // 尝试方法1：标准的ACTION_DIAL
        if (tryDialMethod1(phoneNumber)) {
            entry.status = "已尝试拨出"
            adapter.notifyItemChanged(position)
            Toast.makeText(this, "正在打开拨号界面...", Toast.LENGTH_SHORT).show()
            savePhoneStatuses() // 状态改变后立即保存
            return
        }

        // 尝试方法2：使用ACTION_VIEW
        if (tryDialMethod2(phoneNumber)) {
            entry.status = "已尝试拨出"
            adapter.notifyItemChanged(position)
            Toast.makeText(this, "正在打开拨号界面...", Toast.LENGTH_SHORT).show()
            savePhoneStatuses() // 状态改变后立即保存
            return
        }

        // 尝试方法3：强制启动，不检查resolveActivity
        if (tryDialMethod3(phoneNumber)) {
            entry.status = "已尝试拨出"
            adapter.notifyItemChanged(position)
            Toast.makeText(this, "正在打开拨号界面...", Toast.LENGTH_SHORT).show()
            savePhoneStatuses() // 状态改变后立即保存
            return
        }

        // 所有方法都失败
        Log.e("DialerGu", "所有拨号方法都失败了")
        entry.status = "拨号失败"
        adapter.notifyItemChanged(position)
        Toast.makeText(this, "无法找到拨号应用", Toast.LENGTH_LONG).show()
        savePhoneStatuses() // 状态改变后立即保存
    }

    // 方法1：标准的ACTION_DIAL
    private fun tryDialMethod1(phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                Log.d("DialerGu", "方法1成功")
                true
            } else {
                Log.d("DialerGu", "方法1失败：无法解析Activity")
                false
            }
        } catch (e: Exception) {
            Log.e("DialerGu", "方法1异常", e)
            false
        }
    }

    // 方法2：使用ACTION_VIEW
    private fun tryDialMethod2(phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tel:$phoneNumber"))
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                Log.d("DialerGu", "方法2成功")
                true
            } else {
                Log.d("DialerGu", "方法2失败：无法解析Activity")
                false
            }
        } catch (e: Exception) {
            Log.e("DialerGu", "方法2异常", e)
            false
        }
    }

    // 方法3：强制启动，不检查resolveActivity
    private fun tryDialMethod3(phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            startActivity(intent)
            Log.d("DialerGu", "方法3成功")
            true
        } catch (e: Exception) {
            Log.e("DialerGu", "方法3异常", e)
            false
        }
    }

    // RecyclerView 的适配器
    class PhoneNumberAdapter(
        private val dataSet: MutableList<PhoneNumberEntry>,
        private val onDialClickListener: (Int) -> Unit
    ) : RecyclerView.Adapter<PhoneNumberAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textViewPhoneNumber: TextView = view.findViewById(R.id.textViewPhoneNumber)
            val buttonDial: Button = view.findViewById(R.id.buttonDial)
            val textViewStatus: TextView = view.findViewById(R.id.textViewStatus)
            val textViewName: TextView = view.findViewById(R.id.textViewName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_phone_number, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = dataSet[position]
            holder.textViewName.text = entry.name
            holder.textViewPhoneNumber.text = entry.number
            holder.textViewStatus.text = entry.status

            holder.buttonDial.setOnClickListener {
                onDialClickListener(position)
            }
        }

        override fun getItemCount(): Int {
            return dataSet.size
        }
    }
}
