package com.example.libcontacts

import PhoneUtils
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.lu.magic.util.log.LogUtil

class SmsPlugin : WebSocketClientListener {

    private var smsObserver: ContentObserver? = null
    private var previousSmsIds: Set<String> = emptySet()
    private var mContext: Context? = null
    private var client: WebSocketClient? = null
    private val smsUri: Uri = Uri.parse("content://sms")

    override fun onMessage(message: String) {

    }

     fun handleHook(context: Context) {
        mContext = context
        LogUtil.i("CallLogPlugin handleHook：$this", mContext)
        client = WebSocketClient(context)
        client?.setOnWebSocketListener(this)
        client?.connect()

        // 注册短信内容观察者
        context.contentResolver?.registerContentObserver(
            smsUri,
            true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    // 短信内容发生变化时触发
                    onSmsChanged(context)
                }
            }
        )

         PhoneUtils.context = context
         val list = PhoneUtils.getSmsInfoList(0, 100, 0, "")
         LogUtil.i("list = $list")
    }

    private fun onSmsChanged(context: Context?) {
        Log.e(">>>", "onSmsChanged")
        context?.let {
            val projection = arrayOf("_id", "address", "body", "date", "type")
            val cursor: Cursor? = it.contentResolver.query(smsUri, projection, null, null, "date DESC")

            cursor?.use { c ->
                val currentSmsIds = mutableSetOf<String>()
                val newSms = mutableListOf<String>()
                val deletedSms = mutableListOf<String>()

                while (c.moveToNext()) {
                    val id = c.getString(c.getColumnIndexOrThrow("_id"))
                    currentSmsIds.add(id)

                    if (!previousSmsIds.contains(id)) {
                        val address = c.getString(c.getColumnIndexOrThrow("address"))
                        val body = c.getString(c.getColumnIndexOrThrow("body"))
                        val date = c.getLong(c.getColumnIndexOrThrow("date"))
                        val type = c.getInt(c.getColumnIndexOrThrow("type"))

                        newSms.add("ID: $id, Address: $address, Body: $body, Date: $date, Type: $type")
                        Log.e(">>>", "onSmsChanged: $id, Address: $address, Body: $body, Date: $date, Type: $type")
                    }
                }

                previousSmsIds.forEach { id ->
                    Log.e(">>>", "onSmsChanged: $id")
                    if (!currentSmsIds.contains(id)) {
                        deletedSms.add("ID: $id")
                    }
                }

                previousSmsIds = currentSmsIds

                if (newSms.isNotEmpty()) {
                    println("New SMS: ${newSms.joinToString("\n")}")
                }

                if (deletedSms.isNotEmpty()) {
                    println("Deleted SMS: ${deletedSms.joinToString("\n")}")
                }
            }
        }
    }
}