import com.lu.wxmask.util.PasswordUtiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// 定义回调接口
typealias CompressCallback = () -> Unit

object ZipUtils {
    val bufferSize = 64 * 1024 // 64KB buffer size
    /**
     * 压缩文件夹到临时 ZIP 文件
     * @param sourceDir 源文件夹
     * @return 临时 ZIP 文件
     */
    fun zipFolderToChunks(sourceDir: File): File {
        val tempZipFile = File.createTempFile("tempZip", ".zip")
        val bufferSize = 64 * 1024 // 64KB buffer size
        val zipOutputStream =
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZipFile), bufferSize))

        // 使用协程并发处理文件压缩
        runBlocking {
            val job = CoroutineScope(Dispatchers.IO).launch {
                zip(sourceDir, sourceDir, zipOutputStream)
            }
            job.join()
        }

        zipOutputStream.close()

        return tempZipFile
    }

    private suspend fun zip(file: File, sourceDir: File, zipOutputStream: ZipOutputStream)  {
        if (file.isDirectory) {
            zipDirectory(file, sourceDir, zipOutputStream)
        } else {
            val zipEntry = ZipEntry(file.relativeTo(sourceDir).path)
            withContext(Dispatchers.IO) {
                zipOutputStream.putNextEntry(zipEntry)
            }
            BufferedInputStream(
                if (file.canRead()) file.inputStream() else PasswordUtiles.readFileAsInputStream(file.absolutePath),
                bufferSize
            ).use { input ->
                input.copyTo(zipOutputStream, bufferSize)
            }
            withContext(Dispatchers.IO) {
                zipOutputStream.closeEntry()
            }
        }
    }

    private suspend fun zipDirectory(dir: File, sourceDir: File, zipOutputStream: ZipOutputStream) {
        val files = getFileList(dir)
        if (files.isEmpty()) {
            val zipEntry = ZipEntry(dir.relativeTo(sourceDir).path + "/")
            withContext(Dispatchers.IO) {
                zipOutputStream.putNextEntry(zipEntry)
                zipOutputStream.closeEntry()
            }
        } else {
            files.forEach {
                zip(it, sourceDir, zipOutputStream)
            }
        }
    }

    private fun getFileList(file: File): List<File> {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls", "-a", "$file"))
        return process.inputStream.readBytes().toByteString().string(Charset.forName("utf-8"))
            .split("\n")
            .filter { it.isNotEmpty() }
            .map {
                File(file, it)
            }
    }
}