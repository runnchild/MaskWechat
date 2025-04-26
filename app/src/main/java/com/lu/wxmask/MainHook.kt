package com.lu.wxmask

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.os.Process
import androidx.annotation.Keep
import com.lu.lposed.api2.XC_MethodHook2
import com.lu.lposed.api2.XposedHelpers2
import com.lu.lposed.plugin.PluginRegistry.register
import com.lu.magic.util.AppUtil
import com.lu.magic.util.log.LogUtil
import com.lu.magic.util.log.SimpleLogger
import com.lu.wxmask.http.scheduleZeroOClockTask
import com.lu.wxmask.plugin.CallLogPlugin
import com.lu.wxmask.plugin.CommonPlugin
import com.lu.wxmask.plugin.MessagePlugin
import com.lu.wxmask.plugin.SmsPlugin
import com.lu.wxmask.plugin.WXConfigPlugin
import com.lu.wxmask.plugin.WXDbPlugin
import com.lu.wxmask.plugin.WXMaskPlugin
import de.robv.android.xposed.IXposedHookInitPackageResources
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.IXposedHookZygoteInit.StartupParam
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArraySet

@Keep
class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {
    private var hasInit = false
    private val initUnHookList: MutableList<XC_MethodHook.Unhook> = ArrayList()
    private var isHookEntryHandle = false

    private val allowList by lazy {
        val allowList = HashSet<String>()
        allowList.add(BuildConfig.APPLICATION_ID)
        allowList.add(TARGET_PACKAGE)
        allowList.add(CONTACTS_PACKAGE)
        allowList
    }

    @Throws(Throwable::class)
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
//        if (BuildConfig.APPLICATION_ID.equals(lpparam.packageName)) {
//            SelfHook.getInstance().handleLoadPackage(lpparam);
//            return;
//        }
        if (isHookEntryHandle) {
            return
        }
        isHookEntryHandle = true

        if (!allowList.contains(lpparam.processName) || !lpparam.isFirstApplication) {
            return
        }
        LogUtil.e("start main plugin for ", lpparam.processName)

        LogUtil.setLogger(object : SimpleLogger() {
            override fun onLog(level: Int, objects: Array<out Any?>) {
                if (BuildConfig.DEBUG) {
                    super.onLog(level, objects)
                } else {
                    //release 打印i以上级别的log，其他的忽略
                    if (level > 1) {
                        val msgText = buildLogText(objects)
                        XposedHelpers2.log(TAG + " " + msgText)
                    }
                }
            }
        })
        XposedHelpers2.Config
            .setCallMethodWithProxy(true)
            .setThrowableCallBack { throwable: Throwable? ->
                LogUtil.w(
                    "MaskPlugin error",
                    throwable
                )
            }
            .setOnErrorReturnFallback { method: Method, throwable: Throwable? ->
                val returnType = method.returnType
                // 函数执行错误时，给定一个默认的返回值值。
                // 没什么鸟用。xposed api就没有byte/short/int/long/这些基本类型的返回值函数
                if (String::class.java == returnType || CharSequence::class.java.isAssignableFrom(
                        returnType
                    )
                ) {
                    return@setOnErrorReturnFallback ""
                }
                if (Integer.TYPE == returnType || Int::class.java == returnType) {
                    return@setOnErrorReturnFallback 0
                }
                if (java.lang.Long.TYPE == returnType || Long::class.java == returnType) {
                    return@setOnErrorReturnFallback 0L
                }
                if (java.lang.Double.TYPE == returnType || Double::class.java == returnType) {
                    return@setOnErrorReturnFallback 0.0
                }
                if (java.lang.Float.TYPE == returnType || Float::class.java == returnType) {
                    return@setOnErrorReturnFallback 0f
                }
                if (java.lang.Byte.TYPE == returnType || Byte::class.java == returnType) {
                    return@setOnErrorReturnFallback byteArrayOf()
                }
                if (java.lang.Short.TYPE == returnType || Short::class.java == returnType) {
                    return@setOnErrorReturnFallback 0.toShort()
                }
                if (BuildConfig.DEBUG) {
                    LogUtil.w("setOnErrorReturnFallback", throwable)
                }
                null
            }

        var unhook = XposedHelpers2.findAndHookMethod(
            Application::class.java.name,
            lpparam.classLoader,
            "onCreate",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    LogUtil.i("hook", "onCreate")
                    initPlugin(param.thisObject as Context, lpparam)
                }
            }
        )
        initUnHookList.add(unhook)

        //        initHookCallBack = XposedHelpers2.findAndHookMethod(
//                Activity.class.getName(),
//                lpparam.classLoader,
//                "onCreate",
//                Bundle.class,
//                new XC_MethodHook() {
//                    @Override
//                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                        initPlugin(((Activity) param.thisObject).getApplicationContext(), lpparam);
//                    }
//                }
//        );
//
        //"com.tencent.mm.app.com.Application"的父类
        //"tencent.tinker.loader.app.TinkerApplication"

//        XposedHelpers2.findAndHookMethod(
//                Application.class.getName(),
//                lpparam.classLoader,
//                "attach",
//                Context.class.getName(),
//                new XC_MethodHook() {
//                    @Override
//                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                        initPlugin((Context) param.args[0], lpparam);
//                    }
//                }
//        );
//
//        unhook = XposedHelpers2.findAndHookMethod(
//            Instrumentation::class.java.name,
//            lpparam.classLoader,
//            "callApplicationOnCreate",
//            Application::class.java.name,
//            object : XC_MethodHook2() {
//                @Throws(Throwable::class)
//                override fun afterHookedMethod(param: MethodHookParam) {
////                    LogUtil.i("hook", "callApplicationOnCreate")
////                    initPlugin(param.args[0] as Context, lpparam)
//                }
//            }
//        )
//        initUnHookList.add(unhook)
        //
//        XposedHelpers2.findAndHookMethod(
//                Activity.class.getName(),
//                lpparam.classLoader,
//                "onCreate",
//                Bundle.class.getName(),
//                new XC_MethodHook2() {
//                    @Override
//                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                        initPlugin((Context) param.thisObject, lpparam);
//                    }
//                }
//        );
//
    }

    private fun initPlugin(context: Context?, lpparam: LoadPackageParam) {
      if (context == null) {
            LogUtil.w("context is null")
            return
        }
        if (hasInit) {
            return
        }
        LogUtil.i("start init Plugin: $context")
        hasInit = true
        AppUtil.attachContext(context)

        if (BuildConfig.APPLICATION_ID == lpparam.packageName) {
            initSelfPlugins(context, lpparam)
        } else {
            initTargetPlugins(context, lpparam)
        }

        for (unhook in initUnHookList) {
            unhook.unhook()
        }
    }

    private fun initSelfPlugins(context: Context, lpparam: LoadPackageParam) {
        SelfHook.getInstance().handleHook(context, lpparam)
    }

    private fun initTargetPlugins(context: Context, lpparam: LoadPackageParam) {
        if (CONTACTS_PACKAGE == lpparam.processName) {
            register(CallLogPlugin()).handleHooks(context, lpparam)
        } else if (TARGET_PACKAGE == lpparam.processName){
            register(
                CommonPlugin(),
                WXDbPlugin(),
                WXConfigPlugin(),
                WXMaskPlugin(),
                MessagePlugin()
            ).handleHooks(context, lpparam)

            LogUtil.i("init plugin finish")
            scheduleZeroOClockTask(context)
        }
    }

    @Throws(Throwable::class)
    override fun initZygote(startupParam: StartupParam) {
        MODULE_PATH = startupParam.modulePath
    }

    @Throws(Throwable::class)
    override fun handleInitPackageResources(resparam: InitPackageResourcesParam) {
        if (BuildConfig.APPLICATION_ID == resparam.packageName) {
            return
        }
        if (TARGET_PACKAGE == resparam.packageName) {
//            XModuleResources xRes = XModuleResources.createInstance(MODULE_PATH, resparam.res);
//            Rm.mask_layout_plugin_manager = resparam.res.addResource(xRes, R.layout.mask_layout_plugin_manager);
        }
    }

    companion object {
        private const val TARGET_PACKAGE = "com.tencent.mm"
        private const val CONTACTS_PACKAGE = "com.android.contacts"

        var uniqueMetaStore: CopyOnWriteArraySet<String> = CopyOnWriteArraySet()
        private var MODULE_PATH: String? = null
    }
}