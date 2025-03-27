package com.lu.wxmask.util

import android.annotation.SuppressLint
import android.database.Cursor
import com.lu.lposed.api2.XposedHelpers2
import com.lu.magic.util.CursorUtil
import com.lu.magic.util.log.LogUtil
import com.lu.wxmask.ClazzN
import com.lu.wxmask.bean.DBItem
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Array

class WxSQLiteManager {
    companion object {
        val Store = HashMap<String, DBItem>()
        fun sqlite(dbName: String, password: String?): Any? {
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
                LogUtil.i("sqliteInstance == $sqliteInstance")
                // 获取目标类的 Class 对象
                val dbClass: Class<*> = sqliteInstance.javaClass
                val rawQueryMethod = dbClass.getMethod(
                    "rawQuery",
                    String::class.java,
                    kotlin.Array<Any>::class.java
                )
                LogUtil.d("rawQueryMethod", rawQueryMethod)
                val cursor = rawQueryMethod.invoke(sqliteInstance, sql, null) as Cursor?
                LogUtil.d("rawQueryMethod", cursor)

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
                    LogUtil.i("invokeSql columnNames == ${it.columnNames}")
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
            } ?: openDbItemWithName(dbName)

            LogUtil.d("executeSql item from", sqliteInstance)
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
                getDataBase(path, password)?.apply {
                    Store[path] = DBItem(path, password, this)
                    LogUtil.d(
                        "testOpenDbItemWithName path=", path,
                        " ,password=", password,
                        ",sqliteDatabase=", this
                    )
                    return@forEach
                }
            }
            return null
        }

        fun getWxId(): String {
            val data = executeSql("EnMicroMsg.db", "select * from userinfo where id = 2")
            for (i in 0 until data.length()) {
                val item = data.get(i)
                if (item is JSONObject) {
                    val id = item.optString("id")
                    LogUtil.i("id ", id, item)
                    return item.optString("value")
                } else {
                    LogUtil.i("item ", item)
                }
            }
            return ""
        }

        private fun getDataBase(path: String, password: String?): Any? {
            val cipher = null // 如果不需要加密，可以设置为 null
            val factory = null // 如果不需要自定义 CursorFactory，可以设置为 null
            val flags = 805306368 // 根据需要设置标志位
            val errorHandler =
                ClazzN.from("com.tencent.wcdb.DefaultDatabaseErrorHandler") // 如果不需要自定义错误处理，可以设置为 null
            val poolSize = 0 // 根据需要设置连接池大小
            return XposedHelpers2.callStaticMethod<Any?>(
                ClazzN.from("com.tencent.wcdb.database.SQLiteDatabase"),
                "openDatabase",
                path,
                password?.toByteArray(),
                cipher,
                factory,
                flags,
                XposedHelpers2.newInstance(errorHandler),
                poolSize
            )
        }
    }
}