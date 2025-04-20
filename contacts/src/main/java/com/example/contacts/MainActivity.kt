package com.example.contacts

import android.annotation.SuppressLint
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.CallLog
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

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
        contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true, // 监听所有后代URI
            callLogObserver
        )

        findViewById<View>(R.id.btn_call_log).setOnClickListener {
            // 读取通话记录
//            readCallLog()
            val params = JSONObject().apply {
//                put("projection", JSONArray(listOf("name", "number", "date", "duration", "type")))
                put("selection", "type = 2 AND name != null") // 自动参数化转换
                put("sortOrder", "date DESC")
            }
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

    @SuppressLint("Range")
    fun readCallLog() {
        // 读取通话记录
        lifecycleScope.launch {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )

            var cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val number = it.getString(it.getColumnIndex(CallLog.Calls.NUMBER))
                    val type = it.getInt(it.getColumnIndex(CallLog.Calls.TYPE))
                    val date = it.getLong(it.getColumnIndex(CallLog.Calls.DATE))
                    val duration = it.getLong(it.getColumnIndex(CallLog.Calls.DURATION))

                    Log.d(
                        "CallLog", """
                            号码: $number
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
            } ?: run {
                Log.e("CallLog", "无法读取通话记录")
            }
        }

    }
}