package com.example.contacts

import android.annotation.SuppressLint
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.libcontacts.SmsPlugin
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private var smsObserver: ContentObserver? = null
    private var previousSmsIds: Set<String> = emptySet()
    private val smsUri: Uri = Uri.parse("content://sms")
    private val callLogObserver by lazy {
        object : ContentObserver(Handler(mainLooper)) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                uri?.let {
                    Log.d("CallLog", "变化记录URI: $it")
                    readSingleCallLog(it) // 只读取变化项
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 注册内容观察者
//        contentResolver.registerContentObserver(
//            CallLog.Calls.CONTENT_URI,
//            true, // 监听所有后代URI
//            callLogObserver
//        )

        findViewById<View>(R.id.btn_call_log).setOnClickListener {
            // 读取通话记录
            println("点击了按钮")
            SmsPlugin().handleHook(this)
        }
        smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                uri?.let {
                    Log.d("SMSObserver", "短信变更URI: $it")
                    // 检查URI格式是否为content://sms/数字
                    if (it.toString().matches(Regex("content://sms/\\d+"))) {
                        handleSmsChange(this@MainActivity, it)
                    } else {
                        Log.w("SMSObserver", "收到非标准短信URI: $it")
                    }
                }
            }
        }.also {
            contentResolver.registerContentObserver(
                smsUri,
                true,  // 监听所有后代URI
                it
            )
        }
    }

    // 优化后的短信变更处理方法
    private fun handleSmsChange(context: Context, uri: Uri) {
        lifecycleScope.launch {
            val projection = arrayOf(
                "_id", "address", "body", "date", //"type", "read"
            )

            // 只查询变化的短信记录
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null // 只获取最新的一条
            )
            if (cursor?.moveToFirst() != true) {
                val id = uri.lastPathSegment
                val fallbackUri = Uri.parse("content://sms")
                val fallbackCursor = context.contentResolver.query(
                    fallbackUri,
                    projection,
                    "_id = ?",
                    arrayOf(id),
                    null
                )
                queryFromCursor(fallbackCursor ?: return@launch)
            } else {
                queryFromCursor(cursor)
            }
        }
    }

    private fun queryFromCursor(c: Cursor) {
        if (c.moveToFirst()) {
            val id = c.getString(c.getColumnIndexOrThrow("_id"))
            val address = c.getString(c.getColumnIndexOrThrow("address"))
            val body = c.getString(c.getColumnIndexOrThrow("body"))
            val date = c.getLong(c.getColumnIndexOrThrow("date"))
//                    val type = c.getInt(c.getColumnIndexOrThrow("type"))
//                    val read = c.getInt(c.getColumnIndexOrThrow("read")) == 1

            val smsType = when (5) {
                1 -> "接收"
                2 -> "发送"
                else -> "未知"
            }

            Log.d(
                "SMSChange", """
                    [新短信] ID: $id
                    发件人: $address
                    内容: ${body.take(50)}...
                    时间: ${java.text.DateFormat.getDateTimeInstance().format(date)}
                    类型: $smsType
                """.trimIndent()
            )
        }
    }

    @SuppressLint("Range")
    private fun readSingleCallLog(uri: Uri) {
        lifecycleScope.launch {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )

            // 通过变化URI直接查询单个记录
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER))
                    val type = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE))
                    val date = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE))
                    val duration = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DURATION))

                    Log.d(
                        "CallLog", """
                        [新记录] 号码: $number
                        类型: ${
                            when (type) {
                                CallLog.Calls.INCOMING_TYPE -> "来电"
                                CallLog.Calls.OUTGOING_TYPE -> "去电"
                                CallLog.Calls.MISSED_TYPE -> "未接"
                                else -> "未知"
                            }
                        }
                        时间: ${java.text.DateFormat.getDateTimeInstance().format(date)}
                        时长: ${duration}秒
                    """.trimIndent()
                    )
                }
            }
        }
    }
}