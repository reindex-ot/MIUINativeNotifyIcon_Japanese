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
@file:Suppress("DEPRECATION", "SetWorldReadable", "SetTextI18n")

package com.fankes.miui.notify.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.utils.widget.ImageFilterView
import com.fankes.miui.notify.BuildConfig
import com.fankes.miui.notify.R
import com.fankes.miui.notify.hook.HookMedium
import com.fankes.miui.notify.utils.*
import com.gyf.immersionbar.ImmersionBar
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {

        /** 模块版本 */
        private const val moduleVersion = BuildConfig.VERSION_NAME

        /** MIUI 版本 */
        private val miuiVersion by lazy {
            if (isMIUI)
                findPropString(key = "ro.miui.ui.version.code", default = "无法获取") +
                        " " + findPropString(key = "ro.system.build.version.incremental")
            else "不是 MIUI 系统"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        /** 隐藏系统的标题栏 */
        supportActionBar?.hide()
        /** 初始化沉浸状态栏 */
        ImmersionBar.with(this)
            .statusBarColor(R.color.white)
            .autoDarkModeEnable(false)
            .statusBarDarkFont(true)
            .navigationBarColor(R.color.white)
            .navigationBarDarkIcon(true)
            .fitsSystemWindows(true)
            .init()
        /** 设置文本 */
        findViewById<TextView>(R.id.main_text_version).text = "当前版本：$moduleVersion"
        findViewById<TextView>(R.id.main_text_miui_version).text = "MIUI 版本：$miuiVersion"
        /** 判断是否为 MIUI 系统 */
        if (isNotMIUI) {
            showDialog {
                title = "不是 MIUI 系统"
                msg = "此模块专为 MIUI 系统打造，当前无法识别你的系统为 MIUI，所以模块无法工作。\n" +
                        "如有问题请联系 酷安 @星夜不荟"
                confirmButton(text = "退出") { finish() }
                noCancelable()
            }
            return
        }
        /** 判断 Hook 状态 */
        if (isHooked()) {
            findViewById<LinearLayout>(R.id.main_lin_status).setBackgroundResource(R.drawable.green_round)
            findViewById<ImageFilterView>(R.id.main_img_status).setImageResource(R.mipmap.succcess)
            findViewById<TextView>(R.id.main_text_status).text = "模块已激活"
        } else
            showDialog {
                title = "模块没有激活"
                msg = "检测到模块没有激活，模块需要 Xposed 环境依赖，" +
                        "同时需要系统拥有 Root 权限，" +
                        "请自行查看本页面使用帮助与说明第二条。\n" +
                        "由于需要修改系统应用达到效果，模块不支持太极阴、应用转生。"
                confirmButton(text = "我知道了")
                noCancelable()
            }
        /** 初始化 View */
        val moduleEnableSwitch = findViewById<SwitchCompat>(R.id.module_enable_switch)
        val hideIconInLauncherSwitch = findViewById<SwitchCompat>(R.id.hide_icon_in_launcher_switch)
        val colorIconHookSwitch = findViewById<SwitchCompat>(R.id.color_icon_fix_switch)
        val chatIconHookSwitch = findViewById<SwitchCompat>(R.id.chat_icon_fix_switch)
        /** 获取 Sp 存储的信息 */
        moduleEnableSwitch.isChecked = getBoolean(HookMedium.ENABLE_MODULE, default = true)
        hideIconInLauncherSwitch.isChecked = getBoolean(HookMedium.ENABLE_HIDE_ICON)
        colorIconHookSwitch.isChecked = getBoolean(HookMedium.ENABLE_COLOR_ICON_HOOK, default = true)
        chatIconHookSwitch.isChecked = getBoolean(HookMedium.ENABLE_CHAT_ICON_HOOK, default = true)
        moduleEnableSwitch.setOnCheckedChangeListener { btn, b ->
            if (!btn.isPressed) return@setOnCheckedChangeListener
            putBoolean(HookMedium.ENABLE_MODULE, b)
        }
        hideIconInLauncherSwitch.setOnCheckedChangeListener { btn, b ->
            if (!btn.isPressed) return@setOnCheckedChangeListener
            putBoolean(HookMedium.ENABLE_HIDE_ICON, b)
            packageManager.setComponentEnabledSetting(
                ComponentName(this@MainActivity, "com.fankes.miui.notify.Home"),
                if (b) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        colorIconHookSwitch.setOnCheckedChangeListener { btn, b ->
            if (!btn.isPressed) return@setOnCheckedChangeListener
            putBoolean(HookMedium.ENABLE_COLOR_ICON_HOOK, b)
        }
        chatIconHookSwitch.setOnCheckedChangeListener { btn, b ->
            if (!btn.isPressed) return@setOnCheckedChangeListener
            putBoolean(HookMedium.ENABLE_CHAT_ICON_HOOK, b)
        }
        /** 重启按钮点击事件 */
        findViewById<View>(R.id.title_restart_icon).setOnClickListener {
            showDialog {
                title = "重启系统界面"
                msg = "你确定要立即重启系统界面吗？"
                confirmButton { restartSystemUI() }
                cancelButton()
            }
        }
        /** 恰饭！ */
        findViewById<View>(R.id.link_with_follow_me).setOnClickListener {
            try {
                startActivity(Intent().apply {
                    setPackage("com.coolapk.market")
                    action = "android.intent.action.VIEW"
                    data = Uri.parse("https://www.coolapk.com/u/876977")
                    /** 防止顶栈一样重叠在自己的 APP 中 */
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (e: Exception) {
                Toast.makeText(this, "你可能没有安装酷安", Toast.LENGTH_SHORT).show()
            }
        }
        /** 项目地址点击事件 */
        findViewById<View>(R.id.link_with_project_address).setOnClickListener {
            try {
                startActivity(Intent().apply {
                    action = "android.intent.action.VIEW"
                    data = Uri.parse("https://github.com/fankes/MIUINativeNotifyIcon")
                    /** 防止顶栈一样重叠在自己的 APP 中 */
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (e: Exception) {
                Toast.makeText(this, "无法启动系统默认浏览器", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 判断模块是否激活
     * @return [Boolean] 激活状态
     */
    private fun isHooked() = HookMedium.isHooked()

    /** 重启系统界面 */
    private fun restartSystemUI() =
        execShellCmd(cmd = "pgrep systemui").also { pid ->
            if (pid.isNotBlank())
                execShellCmd(cmd = "kill -9 $pid")
            else Toast.makeText(this, "ROOT 权限获取失败", Toast.LENGTH_SHORT).show()
        }

    override fun onResume() {
        super.onResume()
        setWorldReadable()
    }

    override fun onRestart() {
        super.onRestart()
        setWorldReadable()
    }

    override fun onPause() {
        super.onPause()
        setWorldReadable()
    }

    /**
     * 获取保存的值
     * @param key 名称
     * @param default 默认值
     * @return [Boolean] 保存的值
     */
    private fun getBoolean(key: String, default: Boolean = false) =
        getSharedPreferences(
            packageName + "_preferences",
            Context.MODE_PRIVATE
        ).getBoolean(key, default)

    /**
     * 保存值
     * @param key 名称
     * @param bool 值
     */
    private fun putBoolean(key: String, bool: Boolean) {
        getSharedPreferences(
            packageName + "_preferences",
            Context.MODE_PRIVATE
        ).edit().putBoolean(key, bool).apply()
        setWorldReadable()
        /** 延迟继续设置强制允许 SP 可读可写 */
        Handler().postDelayed({ setWorldReadable() }, 500)
        Handler().postDelayed({ setWorldReadable() }, 1000)
        Handler().postDelayed({ setWorldReadable() }, 1500)
    }

    /**
     * 强制设置 Sp 存储为全局可读可写
     * 以供模块使用
     */
    private fun setWorldReadable() {
        try {
            if (FileUtils.getDefaultPrefFile(this).exists()) {
                for (file in arrayOf<File>(
                    FileUtils.getDataDir(this),
                    FileUtils.getPrefDir(this),
                    FileUtils.getDefaultPrefFile(this)
                )) {
                    file.setReadable(true, false)
                    file.setExecutable(true, false)
                }
            }
        } catch (_: Exception) {
            Toast.makeText(this, "无法写入模块设置，请检查权限\n如果此提示一直显示，请不要双开模块", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        setWorldReadable()
        super.onBackPressed()
    }
}