package com.lu.wxmask.util

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import com.lu.lposed.api2.XposedHelpers2
import com.lu.magic.util.CursorUtil
import com.lu.magic.util.log.LogUtil
import com.lu.wxmask.ClazzN
import com.lu.wxmask.bean.DBItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.lang.reflect.Array

class WxSQLiteManager {
    companion object {
        val Store = HashMap<String, DBItem>()
        private fun sqlite(dbName: String, password: String?): Any? {
            return Store[dbName]?.sqliteDatabase
        }

        fun getAllTables(dbName: String, password: String?): MutableList<String> {
            return try {
                val sql = "SELECT name FROM sqlite_master WHERE type='table'"
                val sqliteInstance = sqlite(dbName, password)
                val queryRaw = sqliteInstance?.javaClass?.getMethod(
                    "rawQuery",
                    String::class.java,
                    Array.newInstance(Object::class.java, 0)::class.java
                )
                queryRaw?.isAccessible = true
                val cursor = queryRaw?.invoke(sqliteInstance, sql, null)
//                val cursor = ReflectUtil.invokeMethod(sqlite(dbName, password), "rawQuery", sql, null)
//                val cursor = sqlite(dbName, password)?.rawQuery(".table", arrayOf());
                CursorUtil.getAll(cursor as Cursor?, String::class.java)
            } catch (e: Throwable) {
                e.printStackTrace()
                mutableListOf()
            }
        }

        @SuppressLint("Range")
        fun invokeSql(sqliteInstance: Any, sql: String): JSONArray {
            val resultArray = JSONArray()
            try {
                LogUtil.i("invokeSql sqliteInstance == $sqliteInstance")
                // 获取目标类的 Class 对象
                val dbClass: Class<*> = sqliteInstance.javaClass
                val rawQueryMethod = dbClass.getMethod(
                    "rawQuery",
                    String::class.java,
                    kotlin.Array<Any>::class.java
                )
                val cursor = rawQueryMethod.invoke(sqliteInstance, sql, null) as Cursor?

                // 打印表中所有信息
                cursor?.use {
                    val columnCount = cursor.columnCount
                    while (cursor.moveToNext()) {
                        val rowObject = JSONObject()
                        for (i in 0 until columnCount) {
                            val columnName = cursor.getColumnName(i)
                            val columnType = cursor.getType(i)
                            val columnValue = when (columnType) {
                                Cursor.FIELD_TYPE_NULL -> null
                                Cursor.FIELD_TYPE_INTEGER -> cursor.getInt(i)
                                Cursor.FIELD_TYPE_FLOAT -> cursor.getFloat(i)
                                Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                                Cursor.FIELD_TYPE_BLOB -> {
                                    val blob = cursor.getBlob(i)
                                    if (blob != null) {
                                        android.util.Base64.encodeToString(
                                            blob,
                                            android.util.Base64.DEFAULT
                                        )
                                    } else {
                                        null
                                    }
                                }

                                else -> null
                            }
                            rowObject.put(columnName, columnValue)
                        }
                        resultArray.put(rowObject)
                    }
                    LogUtil.i("invokeSql: $sql, result: $resultArray")
                }
                cursor?.close()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            return resultArray
        }

        fun executeSql(dbName: String, sql: String): JSONArray {
            val sqliteInstance = Store.filterKeys {
                it.endsWith(dbName)
            }.values.firstOrNull {
                true
            }?.let {
                sqlite(it.name, it.password)
            }// ?: openDbItemWithName(dbName)

            LogUtil.d("executeSql item from", sqliteInstance, "sql=", sql)
            sqliteInstance ?: return JSONArray().apply {
                put(0, JSONObject().put("message", "数据库不存在"))
            }
            return invokeSql(sqliteInstance, sql)
        }

        fun openDbItemWithName(dbName: String): Any? {
            Store.filterValues {
                !it.password.isNullOrBlank()
            }.values.forEach {
                val path = it.name.substring(0, it.name.lastIndexOf("/") + 1) + dbName
                val password = it.password
                val db = getDataBase(path, password)
                LogUtil.d(
                    "testOpenDbItemWithName path=", path,
                    " ,password=", password,
                    ",sqliteDatabase=", db
                )
                if (db != null) {
                    Store[path] = DBItem(path, password, db)
                    return db
                }
            }
            return null
        }

        fun getWxId(context: Context): String {
            return getUinFromPrefs(context)
        }

        private fun getUinFromPrefs(context: Context): String {
            val prefs = context.getSharedPreferences(
                "com.tencent.mm_preferences",
                Context.MODE_PRIVATE
            )
            return prefs.getString("login_weixin_username", "") ?: ""
        }

        private fun getDataBase(path: String, password: String?): Any? {
            // 1. 添加数据库存在性检查
            if (!File(path).exists()) {
                LogUtil.e("Database file not exist: $path")
                return null
            }
            // 获取正确的Cipher实现类
            val cipherClazz = ClazzN.from("com.tencent.wcdb.database.SQLiteCipherSpec")
            val cipher = XposedHelpers2.newInstance(cipherClazz).apply {
                // 2. 添加版本检测逻辑
                val detectedVersion = detectSQLCipherVersion(path) // 需要实现检测方法
                XposedHelpers2.callMethod<Any>(this, "setSQLCipherVersion", detectedVersion ?: 1)

                // 3. 使用更安全的默认页大小
                XposedHelpers2.callMethod(this, "setPageSize", 4096)
            }

            val factory = null // 如果不需要自定义 CursorFactory，可以设置为 null
//            val flags = 805306368 // 根据需要设置标志位
            // 4. 调整标志位为只读模式(0x1)避免意外写入
            val flags = 0x1// or 0x10
            val safeErrorHandler = XposedHelpers2.newInstance(
                ClazzN.from("com.tencent.wcdb.DefaultDatabaseErrorHandler")
            )
            val poolSize = 1 // 根据需要设置连接池大小
            return XposedHelpers2.callStaticMethod<Any?>(
                ClazzN.from("com.tencent.wcdb.database.SQLiteDatabase"),
                "openDatabase",
                path,
                password?.toByteArray(),
                cipher,
                factory,
                flags,
                safeErrorHandler,
                poolSize
            )
        }

        private fun detectSQLCipherVersion(path: String): Int? {
            return try {
                RandomAccessFile(path, "r").use { file ->
                    val buffer = ByteArray(16)
                    file.read(buffer)

                    // SQLCipher 3.x 魔数 "SQLite format 3\u0000"
                    if (buffer.copyOfRange(0, 16).contentEquals(
                            byteArrayOf(
                                0x53, 0x51, 0x4C, 0x69, 0x74, 0x65, 0x20, 0x66,
                                0x6F, 0x72, 0x6D, 0x61, 0x74, 0x20, 0x33, 0x00
                            )
                        )
                    ) {
                        1 // 对应微信使用的3.x版本
                    }
                    // SQLCipher 4.x 魔数 (不同)
                    else if (buffer.copyOfRange(0, 12).contentEquals(
                            byteArrayOf(
                                0x53, 0x51, 0x4C, 0x69, 0x74, 0x65, 0x20, 0x66,
                                0x6F, 0x72, 0x6D, 0x61
                            )
                        )
                    ) {
                        2 // 对应4.x版本
                    } else {
                        null // 未知版本
                    }
                }
            } catch (e: Exception) {
                LogUtil.e("detectSQLCipherVersion error", e)
                null
            }
        }
    }
}