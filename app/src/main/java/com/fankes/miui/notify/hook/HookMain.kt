/**
 * Copyright (C) 2021. Fankes Studio(qzmmcn@163.com)
 *
 * This file is part of MIUINativeNotifyIcon.
 *
 * MIUINativeNotifyIcon is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MIUINativeNotifyIcon is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This file is Created by fankes on 2022/01/24.
 */
@file:Suppress("DEPRECATION", "SameParameterValue")

package com.fankes.miui.notify.hook

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import androidx.annotation.Keep
import androidx.core.graphics.drawable.toBitmap
import com.fankes.miui.notify.hook.HookMedium.QQ_PACKAGE_NAME
import com.fankes.miui.notify.hook.HookMedium.SELF_PACKAGE_NAME
import com.fankes.miui.notify.hook.HookMedium.SYSTEMUI_PACKAGE_NAME
import com.fankes.miui.notify.utils.XPrefUtils
import com.fankes.miui.notify.utils.dp
import com.fankes.miui.notify.utils.isNotMIUI
import com.fankes.miui.notify.utils.round
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage

@Keep
class HookMain : IXposedHookLoadPackage {

    companion object {

        private const val NotificationUtilClass =
            "$SYSTEMUI_PACKAGE_NAME.statusbar.notification.NotificationUtil"

        private const val NotificationHeaderViewWrapperInjectorClass =
            "$SYSTEMUI_PACKAGE_NAME.statusbar.notification.row.wrapper.NotificationHeaderViewWrapperInjector"

        private const val ExpandedNotificationClass =
            "$SYSTEMUI_PACKAGE_NAME.statusbar.notification.ExpandedNotification"

        private const val SystemUIApplicationClass = "$SYSTEMUI_PACKAGE_NAME.SystemUIApplication"

        private const val ContrastColorUtilClass = "com.android.internal.util.ContrastColorUtil"
    }

    /** 仅作用于替换的 Hook 方法体 */
    private val replaceToNull = object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam?): Any? {
            return null
        }
    }

    /** 仅作用于替换的 Hook 方法体 */
    private val replaceToTrue = object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam?): Any {
            return true
        }
    }

    /** 仅作用于替换的 Hook 方法体 */
    private val replaceToFalse = object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam?): Any {
            return false
        }
    }

    /**
     * 忽略异常运行
     * @param it 正常回调
     */
    private fun runWithoutError(error: String, it: () -> Unit) {
        try {
            it()
        } catch (e: Error) {
            logE("hookFailed: $error", e)
        } catch (e: Exception) {
            logE("hookFailed: $error", e)
        } catch (e: Throwable) {
            logE("hookFailed: $error", e)
        }
    }

    /**
     * Print the log
     * @param content
     */
    private fun logD(content: String) {
        XposedBridge.log("[MIUINativeNotifyIcon][D]>$content")
        Log.d("MIUINativeNotifyIcon", content)
    }

    /**
     * Print the log
     * @param content
     */
    private fun logE(content: String, e: Throwable? = null) {
        XposedBridge.log("[MIUINativeNotifyIcon][E]>$content")
        XposedBridge.log(e)
        Log.e("MIUINativeNotifyIcon", content, e)
    }

    /**
     * 查找目标类
     * @param name 类名
     * @return [Class]
     */
    private fun XC_LoadPackage.LoadPackageParam.findClass(name: String) =
        classLoader.loadClass(name)

    /**
     * ⚠️ 这个是修复彩色图标的关键核心代码判断
     * 判断是否为灰度图标 - 反射执行系统方法
     * @param context 实例
     * @param icon 要判断的图标
     * @return [Boolean]
     */
    private fun XC_LoadPackage.LoadPackageParam.isGrayscaleIcon(context: Context, icon: Drawable) =
        findClass(ContrastColorUtilClass).let {
            val instance = it.getDeclaredMethod("getInstance", Context::class.java)
                .apply { isAccessible = true }.invoke(null, context)
            it.getDeclaredMethod("isGrayscaleIcon", Drawable::class.java)
                .apply { isAccessible = true }.invoke(instance, icon) as Boolean
        }

    /**
     * 获取全局上下文
     * @return [Context]
     */
    private val XC_LoadPackage.LoadPackageParam.globalContext
        get() = findClass(SystemUIApplicationClass)
            .getDeclaredMethod("getContext").apply { isAccessible = true }
            .invoke(null) as Context

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (lpparam == null) return
        when (lpparam.packageName) {
            /** Hook 自身 */
            SELF_PACKAGE_NAME ->
                XposedHelpers.findAndHookMethod(
                    "$SELF_PACKAGE_NAME.hook.HookMedium",
                    lpparam.classLoader,
                    "isHooked",
                    replaceToTrue
                )
            /** Hook 系统 UI */
            SYSTEMUI_PACKAGE_NAME -> {
                /** 若不是 MIUI 系统直接停止 Hook */
                if (isNotMIUI) return
                /** 若没开启模块直接停止 Hook */
                if (!XPrefUtils.getBoolean(HookMedium.ENABLE_MODULE, default = true)) return
                /** 强制回写系统的状态栏图标样式为原生 */
                runWithoutError("SubstituteSmallIcon") {
                    XposedHelpers.findAndHookMethod(
                        NotificationUtilClass,
                        lpparam.classLoader,
                        "shouldSubstituteSmallIcon",
                        lpparam.findClass(ExpandedNotificationClass),
                        replaceToFalse
                    )
                }
                /** 修复通知图标为彩色 */
                runWithoutError("IgnoreStatusBarIconColor") {
                    XposedHelpers.findAndHookMethod(
                        NotificationUtilClass,
                        lpparam.classLoader,
                        "ignoreStatusBarIconColor",
                        lpparam.findClass(ExpandedNotificationClass),
                        object : XC_MethodReplacement() {
                            override fun replaceHookedMethod(param: MethodHookParam) =
                                if (XPrefUtils.getBoolean(HookMedium.ENABLE_COLOR_ICON_HOOK, default = true))
                                    try {
                                        /** 获取发送通知的 APP */
                                        val packageName = (param.args[0] as StatusBarNotification).opPkg

                                        /** 获取通知小图标 */
                                        val iconDrawable = (param.args[0] as StatusBarNotification)
                                            .notification.smallIcon.loadDrawable(lpparam.globalContext)
                                        /** 如果开启了修复聊天 APP 的图标 */
                                        if (packageName == QQ_PACKAGE_NAME &&
                                            XPrefUtils.getBoolean(HookMedium.ENABLE_CHAT_ICON_HOOK, default = true)
                                        ) false
                                        /** 只要不是灰度就返回彩色图标 */
                                        else !lpparam.isGrayscaleIcon(lpparam.globalContext, iconDrawable)
                                    } catch (e: Exception) {
                                        logE("Failed to hook ignoreStatusBarIconColor", e)
                                        false
                                    }
                                else false
                        }
                    )
                }
                /** 强制回写系统的状态栏图标样式为原生 */
                runWithoutError("GetSmallIcon") {
                    XposedHelpers.findAndHookMethod(
                        NotificationUtilClass,
                        lpparam.classLoader,
                        "getSmallIcon",
                        lpparam.findClass(ExpandedNotificationClass),
                        Int::class.java,
                        object : XC_MethodHook() {

                            override fun afterHookedMethod(param: MethodHookParam) {
                                runWithoutError("GetSmallIconOnSet") {
                                    /** 获取通知小图标 */
                                    val iconDrawable = (param.result as Icon).loadDrawable(lpparam.globalContext)
                                    /** 判断要设置的图标 */
                                    when {
                                        /** 如果开启了修复聊天 APP 的图标 */
                                        (param.args[0] as StatusBarNotification).opPkg == QQ_PACKAGE_NAME &&
                                                XPrefUtils.getBoolean(HookMedium.ENABLE_CHAT_ICON_HOOK, default = true) ->
                                            param.result = Icon.createWithBitmap(IconPackParams.qqSmallIcon)
                                        /** 若不是灰度图标自动处理为圆角 */
                                        !lpparam.isGrayscaleIcon(lpparam.globalContext, iconDrawable) ->
                                            param.result = Icon.createWithBitmap(
                                                iconDrawable.toBitmap().round(15.dp(lpparam.globalContext))
                                            )
                                    }
                                }
                            }
                        }
                    )
                }
                /** 干掉下拉通知图标自动设置回 APP 图标的方法 - Android 12 */
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R)
                    runWithoutError("ResetIconBgAndPaddings") {
                        XposedHelpers.findAndHookMethod(
                            NotificationHeaderViewWrapperInjectorClass,
                            lpparam.classLoader,
                            "resetIconBgAndPaddings",
                            ImageView::class.java,
                            lpparam.findClass(ExpandedNotificationClass),
                            replaceToNull
                        )
                    }
                /** 修复下拉通知图标自动设置回 APP 图标的方法 - Android 12 */
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R)
                    runWithoutError("AutoSetAppIcon") {
                        XposedHelpers.findAndHookMethod(
                            NotificationHeaderViewWrapperInjectorClass,
                            lpparam.classLoader,
                            "setAppIcon",
                            Context::class.java,
                            ImageView::class.java,
                            lpparam.findClass(ExpandedNotificationClass),
                            object : XC_MethodReplacement() {
                                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                                    runWithoutError("AutoSetAppIconOnSet") {
                                        /** 获取 [Context] */
                                        val context = param.args[0] as Context

                                        /** 获取图标框 */
                                        val iconImageView = param.args[1] as ImageView

                                        /** 获取通知小图标 */
                                        val iconDrawable = (param.args[2] as StatusBarNotification)
                                            .notification.smallIcon.loadDrawable(context)

                                        /** 获取发送通知的 APP */
                                        val packageName = (param.args[2] as StatusBarNotification).opPkg
                                        /** 如果开启了修复聊天 APP 的图标 */
                                        if (packageName == QQ_PACKAGE_NAME &&
                                            XPrefUtils.getBoolean(HookMedium.ENABLE_CHAT_ICON_HOOK, default = true)
                                        )
                                            iconImageView.apply {
                                                /** 设置自定义小图标 */
                                                setImageDrawable(BitmapDrawable(IconPackParams.qqSmallIcon))
                                                /** 上色 */
                                                setColorFilter(Color.WHITE)
                                            }
                                        else {
                                            /** 重新设置图标 - 防止系统更改它 */
                                            iconImageView.setImageDrawable(iconDrawable)
                                            /** 判断如果是灰度图标就给他设置一个白色颜色遮罩 */
                                            if (lpparam.isGrayscaleIcon(context, iconDrawable))
                                                iconImageView.setColorFilter(Color.WHITE)
                                            else
                                                iconImageView.apply {
                                                    clipToOutline = true
                                                    /** 设置一个圆角轮廓裁切 */
                                                    outlineProvider = object : ViewOutlineProvider() {
                                                        override fun getOutline(view: View, out: Outline) {
                                                            out.setRoundRect(0, 0, view.width, view.height, 5.dp(context))
                                                        }
                                                    }
                                                    /** 清除原生的背景边距设置 */
                                                    setPadding(0, 0, 0, 0)
                                                    /** 清除原生的主题色背景圆圈颜色 */
                                                    setBackgroundDrawable(null)
                                                }
                                        }
                                    }
                                    return null
                                }
                            }
                        )
                    }
                logD("hook Completed!")
            }
        }
    }
}