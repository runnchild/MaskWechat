package com.lu.wxmask.http

import com.lu.magic.util.log.LogUtil
import com.example.libcontacts.ShellUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min

class FileUploader(private val androidId: String, private var send: (Any) -> Unit) {

    private val chunkSize = 1024 * 1024  // 1MB
    private var totalChunks: Int = 0
    private var callback: UploadCallback? = null
    var file: File? = null
    private val uploadMutex = Mutex()  // 用于同步上传任务

    // 上传状态回调
    interface UploadCallback {
        fun onProgress(progress: Float, speed: String, remainingTime: String)
        fun onSuccess(path: String, md5: String)
        fun onError(message: String)
    }

    // 开始上传文件
    fun uploadFile(file: File, callback: UploadCallback) {
        this.callback = callback
        this.file = file
        // 配置分片大小和数量
        totalChunks = ceil(file.length().toDouble() / chunkSize).toInt()
        CoroutineScope(Dispatchers.IO).launch {
            uploadMutex.withLock {
                sendStartMessage(file)
            }
        }
    }

    fun sendErrMessage(err: String) {
        val startRequest = JSONObject().apply {
            put("type", "chunk_upload_err")
            put("status", -1)
            put("message", err)
        }
        println(startRequest.toString())
        send(startRequest.toString())
    }

    private var totalMd5: String = ""

    //    private var inputStream: InputStream? = null
    private suspend fun sendStartMessage(file: File) {
        // 计算文件MD5
        totalMd5 = calculateFileMd5(file.asInputStream())
        // 生成文件ID
        val fileId = UUID.randomUUID().toString()
        val startRequest = JSONObject().apply {
            put("type", "chunk_upload_start")
            put("mMap", JSONObject().apply {
                put("file_id", fileId)
                put("total_chunks", totalChunks)
                put("chunk_size", chunkSize)
                put("path", file.absolutePath)
                put("file_size", file.length())
                put("total_md5", totalMd5)
                put("last_modified", file.lastModified())
//                put("upload_id", System.currentTimeMillis().toString())
            })
        }
        println("sendStartMessage$startRequest")
        send(startRequest.toString())
    }

    fun handleResponse(response: JSONObject) {
        val status = response.optInt("status")
        val message = response.opt("message")
        val file = this.file ?: return

        when (status) {
            0 -> { // 成功响应
                if (message is JSONObject && message.has("progress")) {
                    // 处理进度更新
                    val progress = message.optString("progress").replace("%", "").toFloat()
                    val speed = message.optString("speed")
                    val remainingTime = message.optString("remaining_time")
                    callback?.onProgress(progress, speed, remainingTime)
                } else if (message is JSONObject && message.has("upload_id")) {
                    // 获取uploadId后开始发送分片
                    val uploadId = message.optString("upload_id")
                    startSendingChunks(file, uploadId, callback)
                }
            }

            1 -> { // 上传完成
                if (message is JSONObject) {
                    val path = message.optString("path")
                    val md5 = message.optString("md5")
                    callback?.onSuccess(path, md5)
                }
            }

            2 -> { // 断点续传
                if (message is JSONObject) {
                    val uploadId = message.optString("upload_id")
                    val receivedChunks = message.getJSONObject("received_chunks")
                    // 继续发送未上传的分片
                    startSendingChunks(
                        file,
                        uploadId,
                        callback,
                        receivedChunks
                    )
                }
            }

            -1 -> { // 错误
                callback?.onError(message?.toString() ?: "Err")
            }
        }
    }

    private fun mapByteArrayInputStream(inputStream: InputStream): ByteArrayInputStream {
        val buffer = ByteArrayOutputStream()
        val data = ByteArray(1024)
        var bytesRead: Int
        while ((inputStream.read(data, 0, data.size).also { bytesRead = it }) != -1) {
            buffer.write(data, 0, bytesRead)
        }
        buffer.flush()
        val byteArray = buffer.toByteArray()

        // 创建新的 InputStream
        return ByteArrayInputStream(byteArray)
    }

    private fun startSendingChunks(
        file: File,
        uploadId: String,
        callback: UploadCallback?,
        receivedChunks: JSONObject? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val totalChunks = ceil(file.length().toDouble() / chunkSize).toInt()
                val buffer = ByteArray(chunkSize)
                val inputStream = file.asInputStream()

                for (i in 0 until totalChunks) {
                    // 检查分片是否已上传(断点续传)
                    if (receivedChunks?.optBoolean(i.toString()) == true) {
                        continue
                    }

                    // 读取分片数据
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead <= 0) break

                    val chunkData = if (bytesRead == chunkSize) buffer else buffer.copyOf(bytesRead)
                    // 计算分片的MD5
                    val md = MessageDigest.getInstance("MD5")
                    val chunkMd5 = md.digest(chunkData).joinToString("") { "%02x".format(it) }

                    // 构建消息头(28字节)
                    val header = ByteArray(28)

                    // androidID (8字节)
                    System.arraycopy(
                        androidId.toByteArray(),
                        0,
                        header,
                        0,
                        min(androidId.length, 8)
                    )

                    // uploadID (8字节)
                    System.arraycopy(
                        uploadId.toByteArray(),
                        0,
                        header,
                        8,
                        min(uploadId.length, 8)
                    )

                    // fileID (8字节)
                    val fileId = UUID.randomUUID().toString()
                    System.arraycopy(
                        fileId.toByteArray(),
                        0,
                        header,
                        16,
                        min(fileId.length, 8)
                    )

                    // chunkIndex (4字节，小端序)
                    header[24] = (i and 0xFF).toByte()
                    header[25] = ((i shr 8) and 0xFF).toByte()
                    header[26] = ((i shr 16) and 0xFF).toByte()
                    header[27] = ((i shr 24) and 0xFF).toByte()

                    // 组合header和数据
                    val message = ByteArray(header.size + chunkMd5.length + chunkData.size)
                    System.arraycopy(header, 0, message, 0, header.size)
                    System.arraycopy(
                        chunkMd5.toByteArray(),
                        0,
                        message,
                        header.size,
                        chunkMd5.length
                    )
                    System.arraycopy(
                        chunkData,
                        0,
                        message,
                        header.size + chunkMd5.length,
                        chunkData.size
                    )

                    LogUtil.i("sendChunkMessage: ${message.size}")
                    // 发送二进制消息
                    send(message.toByteString())

                    // 控制发送速度
                    delay(50) // 可根据需要调整延迟
                }

                inputStream.close()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError("发送分片失败: ${e.message}")
                }
            }
        }
    }

    // 计算文件MD5
    private fun calculateFileMd5(inputStream: InputStream): String {
        val md = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(8192)
        var bytesRead: Int

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            md.update(buffer, 0, bytesRead)
        }
        inputStream.close()

        val md5Bytes = md.digest()
        return md5Bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun File.asInputStream(): InputStream {
        return if (canRead()) inputStream() else ShellUtils.readFileAsInputStream(absolutePath)
    }
}