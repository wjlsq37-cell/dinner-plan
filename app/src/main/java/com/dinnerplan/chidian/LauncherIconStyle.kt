package com.dinnerplan.chidian

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

enum class LauncherIconStyle(
    val storageId: String,
    val displayName: String,
    internal val aliasClassName: String,
    internal val enabledByDefault: Boolean
) {
    Classic(
        storageId = "classic",
        displayName = "经典图标",
        aliasClassName = "ClassicLauncherAlias",
        enabledByDefault = true
    ),
    EnergyChef(
        storageId = "energy_chef",
        displayName = "元气厨师",
        aliasClassName = "EnergyChefLauncherAlias",
        enabledByDefault = false
    );

    companion object {
        fun fromStorageId(value: String?): LauncherIconStyle {
            return entries.firstOrNull { it.storageId == value } ?: Classic
        }
    }
}

internal const val LAUNCH_SPLASH_MIN_VISIBLE_MILLIS = 1_800L
internal const val ANDROID_PREFER_QUICK_RECIPES = false

internal object LaunchSplashSession {
    private var completed = false
    private var firstFrameAtMillis: Long? = null

    @Synchronized
    fun shouldShow(): Boolean = !completed

    @Synchronized
    fun remainingAfterFirstFrame(nowMillis: Long): Long {
        if (completed) return 0L
        val startedAt = firstFrameAtMillis ?: nowMillis.also { firstFrameAtMillis = it }
        return (LAUNCH_SPLASH_MIN_VISIBLE_MILLIS - (nowMillis - startedAt)).coerceAtLeast(0L)
    }

    @Synchronized
    fun complete() {
        completed = true
    }
}

internal class LauncherIconManager(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    fun apply(style: LauncherIconStyle): Boolean {
        val components = LauncherIconStyle.entries.associateWith(::componentFor)
        val previousStates = components.mapValues { (_, component) ->
            packageManager.getComponentEnabledSetting(component)
        }
        val alreadyApplied = LauncherIconStyle.entries.all { candidate ->
            val state = previousStates.getValue(candidate)
            val enabled = when (state) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
                else -> candidate.enabledByDefault
            }
            enabled == (candidate == style)
        }
        if (alreadyApplied) return true

        return try {
            setState(
                component = components.getValue(style),
                state = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            )
            LauncherIconStyle.entries
                .filterNot { it == style }
                .forEach { candidate ->
                    setState(
                        component = components.getValue(candidate),
                        state = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    )
                }
            true
        } catch (_: RuntimeException) {
            previousStates.forEach { (candidate, state) ->
                runCatching { setState(components.getValue(candidate), state) }
            }
            false
        }
    }

    private fun componentFor(style: LauncherIconStyle): ComponentName {
        return ComponentName(
            appContext.packageName,
            "${appContext.packageName}.${style.aliasClassName}"
        )
    }

    private fun setState(component: ComponentName, state: Int) {
        packageManager.setComponentEnabledSetting(
            component,
            state,
            PackageManager.DONT_KILL_APP
        )
    }
}
