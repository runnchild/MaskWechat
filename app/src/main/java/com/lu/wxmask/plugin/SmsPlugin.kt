package com.lu.wxmask.plugin

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.example.libcontacts.WebSocketClient
import com.example.libcontacts.WebSocketClientListener
import com.lu.lposed.plugin.IPlugin
import com.lu.magic.util.log.LogUtil
import de.robv.android.xposed.callbacks.XC_LoadPackage
import androidx.core.net.toUri
import org.json.JSONArray

class SmsPlugin : WebSocketClientListener, IPlugin {

    private var smsObserver: ContentObserver? = null
    private var previousSmsIds: Set<String> = emptySet()
    private var mContext: Context? = null
    private var client: WebSocketClient? = null

    override fun onMessage(message: String) {

    }

    override fun handleHook(context: Context, p1: XC_LoadPackage.LoadPackageParam?) {
        mContext = context
        LogUtil.i("CallLogPlugin handleHook：$this", mContext)
        client = WebSocketClient(context)
        client?.setOnWebSocketListener(this)
        client?.connect()

        // 注册短信内容观察者
        context.contentResolver?.registerContentObserver(
            "content://sms".toUri(),
            true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    // 短信内容发生变化时触发
                    onSmsChanged(context)
                }
            }
        )
    }

    private fun onSmsChanged(context: Context?) {
        context?.let {
            val uri = "content://sms".toUri()
            val projection = arrayOf("_id", "address", "body", "date", "type")
            val cursor: Cursor? = it.contentResolver.query(uri, projection, null, null, "date DESC")

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
                    }
                }

                previousSmsIds.forEach { id ->
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