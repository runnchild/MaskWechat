package com.lu.wxmask.util

import android.annotation.SuppressLint
import android.database.Cursor
import com.lu.magic.util.CursorUtil
import com.lu.magic.util.log.LogUtil
import com.lu.wxmask.bean.DBItem
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Array


//import com.tencent.wcdb.database.SQLiteDatabase

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
        fun invokeSql(dbName: String, password: String?, sql: String): JSONArray {
            val resultArray = JSONArray()
            try {
                val sqliteInstance = sqlite(dbName, password) ?: return resultArray
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
                cursor?.use {
                    val columnCount = cursor.columnCount
                    while (cursor.moveToNext()) {
                        val rowObject = JSONObject()
                        for (i in 0 until columnCount) {
                            val columnName = cursor.getColumnName(i)
                            val columnValue = cursor.getString(i)
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
            val item = Store.filterKeys {
                it.endsWith(dbName)
            }.values.firstOrNull {
                true
            }
            LogUtil.d("executeSql item from", item?.name, item?.sqliteDatabase)
            item ?: return JSONArray().apply {
                put(0, JSONObject().put("message", "数据库不存在"))
            }
            return invokeSql(item.name, item.password, sql)
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
    }
}