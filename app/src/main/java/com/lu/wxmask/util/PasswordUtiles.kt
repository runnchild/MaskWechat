package com.lu.wxmask.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

/**
 * @Author: zengke
 * @Date: 2018.12
 */
object PasswordUtiles {
    const val WX_ROOT_PATH: String = "/data/data/com.tencent.mm/"

    private const val WX_SP_UIN_PATH = WX_ROOT_PATH + "shared_prefs/auth_info_key_prefs.xml"
    private val `in`: FileInputStream? = null
    private var localDataOutputStream: DataOutputStream? = null


    /**
     * execRootCmd("chmod 777 -R /data/data/com.tencent.mm");
     *
     *
     * 执行linux指令
     */
    fun execRootCmd(paramString: String): Any {
        try {
            val localProcess = Runtime.getRuntime().exec("su")
            var localObject: Any = localProcess.outputStream
            localDataOutputStream = DataOutputStream(localObject as OutputStream)
            val str = paramString.toString()
            localObject = str + "\n"
            localDataOutputStream!!.writeBytes(localObject)
            localDataOutputStream!!.flush()
            localDataOutputStream!!.writeBytes("exit\n")
            localDataOutputStream!!.flush()
            localProcess.waitFor()
            localObject = localProcess.exitValue()
            return localObject
        } catch (localException: Exception) {
            localException.printStackTrace()
        } finally {
            if (localDataOutputStream != null) {
                try {
                    localDataOutputStream!!.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            return ""
        }
    }

    fun setFileChmod(file: File) {
        execRootCmd("chmod 777 -R" + file.absolutePath)
    }

    suspend fun readFileAsInputStream(filePath: String): InputStream = withContext(Dispatchers.IO) {
        // 使用管道流来传递数据
//        val pipedOutputStream = PipedOutputStream()
//        val pipedInputStream = PipedInputStream(pipedOutputStream)
//
//        // 启动协程来异步执行 shell 命令
//        val job = launch {
//            var process: Process? = null
//            var bufferedReader: BufferedReader? = null
//
//            try {
//                // 执行 shell 命令读取文件内容
//                process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $filePath"))
//                bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
//
//                var line: String?
//                while (bufferedReader.readLine().also { line = it } != null) {
//                    pipedOutputStream.write((line + System.lineSeparator()).toByteArray())
//                }
//
//                pipedOutputStream.flush() // 确保数据完全写入
//            } catch (e: Exception) {
//                e.printStackTrace()
//            } finally {
//                try {
//                    bufferedReader?.close()
//                    process?.destroy()
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                }
//            }
//        }
//
//        job.join() // 等待协程完成
//
//        // 返回管道输入流，供调用者使用
//        pipedInputStream
        val process = ProcessBuilder(arrayListOf("su", "-c", "cat $filePath")).start()
        process.inputStream
    }


    fun copyFileToSdCard(sourceFilePath: String, destFilePath: String) {
        var process: Process? = null
        try {
            // 构建shell命令
            val command = "su -c cp $sourceFilePath $destFilePath"
            // 执行shell命令
            process = Runtime.getRuntime().exec(command)
            // 等待命令执行完成并检查退出值
            val exitValue = process.waitFor()
            if (exitValue != 0) {
                throw IOException("Error copying file: exit value $exitValue")
            }
            println("File copied successfully to $destFilePath")
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } finally {
            process?.destroy()
        }
    }
    /**
     * 获取手机的imei
     *
     * @return
     */
    /**
     * 获取微信的uid
     * 微信的uid存储在SharedPreferences里面
     */
}
