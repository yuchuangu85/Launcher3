/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.shared.plugins

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.core.net.toUri
import com.android.internal.messages.nano.SystemMessageProto
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.log.core.LogLevel
import com.android.systemui.log.core.Logger
import com.android.systemui.plugins.Plugin
import com.android.systemui.plugins.PluginListener
import com.android.systemui.plugins.PluginManager
import com.android.systemui.shared.plugins.PluginEnabler.DisableReason
import com.android.systemui.shared.plugins.PluginManagerImpl.Companion.DEFAULT_LOGBUFFER
import com.android.systemui.shared.plugins.VersionInfo.InvalidVersionException
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Coordinates all the available plugins for a given action.
 *
 * The available plugins are queried from the [PackageManager] via an an [Intent] action.
 *
 * @param <T> The type of plugin that this contains.
 */
class PluginActionManager<T : Plugin>
private constructor(
    private val hostContext: Context,
    private val packageManager: PackageManager,
    private val action: String,
    private val listener: PluginListener<T>,
    private val pluginClass: Class<T>,
    private val allowMultiple: Boolean,
    private val mainExecutor: Executor,
    private val bgExecutor: Executor,
    private val env: PluginEnvironment,
    private val notificationManager: NotificationManager,
    private val pluginEnabler: PluginEnabler,
    private val packages: PackageConfig,
    private val pluginInstanceFactory: PluginInstance.Factory,
    private val pluginPrefs: PluginPrefs,
) {
    private val pluginInstances = mutableListOf<PluginInstance<T>>()
    private val logger = Logger(listener.logBuffer ?: DEFAULT_LOGBUFFER, "$TAG[$action]")

    /** Load all plugins matching this instance's action. */
    fun loadAll() {
        logger.d("startListening")
        bgExecutor.execute { queryAll() }
    }

    /** Unload all plugins managed by this instance. */
    fun destroy() {
        logger.d("stopListening")
        val plugins = ArrayList(pluginInstances)
        for (plugInstance in plugins) {
            mainExecutor.execute { onPluginDisconnected(plugInstance) }
        }
    }

    /** Unload all matching plugins managed by this instance. */
    fun onPackageRemoved(pkg: String) {
        bgExecutor.execute { removePkg(pkg) }
    }

    /** Unload and then reload all matching plugins managed by this instance. */
    fun reloadPackage(pkg: String) {
        bgExecutor.execute {
            removePkg(pkg)
            queryPkg(pkg)
        }
    }

    /** Disable a specific plugin managed by this instance. */
    fun checkAndDisable(className: String): Boolean {
        var disableAny = false
        val plugins = ArrayList(pluginInstances)
        for (info in plugins) {
            if (className.startsWith(info.packageName)) {
                disableAny = disableAny || disable(info, DisableReason.DISABLED_FROM_EXPLICIT_CRASH)
            }
        }
        return disableAny
    }

    /** Disable all plugins managed by this instance. */
    fun disableAll(): Boolean {
        val plugins = ArrayList(pluginInstances)
        var disabledAny = false
        for (i in plugins.indices) {
            disabledAny =
                disabledAny || disable(plugins[i], DisableReason.DISABLED_FROM_SYSTEM_CRASH)
        }
        return disabledAny
    }

    /** Misbehaving plugins get disabled and won't come back until uninstall/reinstall. */
    private fun disable(pluginInstance: PluginInstance<T>, reason: DisableReason): Boolean {
        val pluginComponent = pluginInstance.componentName

        if (packages.isPrivileged(pluginComponent)) {
            logger.i({ "Ignoring request to disable privileged plugin: $str1" }) {
                str1 = pluginComponent.flattenToShortString()
            }
            return false
        }

        logger.w({ "Disabling plugin: $str1" }) { str1 = pluginComponent.flattenToShortString() }
        pluginEnabler.setDisabled(pluginComponent, reason)
        return true
    }

    fun <C> dependsOn(p: Plugin, cls: Class<C>): Boolean {
        val instances = ArrayList(pluginInstances)
        for (instance in instances) {
            if (instance.containsPluginClass(p.javaClass)) {
                return instance.versionInfo?.hasClass(cls) ?: false
            }
        }
        return false
    }

    override fun toString(): String = "${this::class.simpleName}@${hashCode()} (action=$action)"

    private fun onPluginConnected(pluginInstance: PluginInstance<T>) {
        logger.d("onPluginConnected")
        pluginPrefs.hasPlugins = true
        pluginInstance.onCreate()
    }

    private fun onPluginDisconnected(pluginInstance: PluginInstance<T>) {
        logger.d("onPluginDisconnected")
        pluginInstance.onDestroy()
    }

    private fun queryAll() {
        logger.d("queryAll")
        for (i in pluginInstances.indices.reversed()) {
            val pluginInstance = pluginInstances[i]
            mainExecutor.execute { onPluginDisconnected(pluginInstance) }
        }
        pluginInstances.clear()
        handleQueryPlugins(null)
    }

    private fun removePkg(pkg: String) {
        for (i in pluginInstances.indices.reversed()) {
            val pluginInstance = pluginInstances[i]
            if (pluginInstance.packageName == pkg) {
                mainExecutor.execute { onPluginDisconnected(pluginInstance) }
                pluginInstances.removeAt(i)
            }
        }
    }

    private fun queryPkg(pkg: String) {
        logger.d({ "queryPkg($str1)" }) { str1 = pkg }
        if (allowMultiple || (pluginInstances.size == 0)) {
            handleQueryPlugins(pkg)
        } else {
            logger.d("Too many matching packages found")
        }
    }

    private fun handleQueryPlugins(pkgName: String?) {
        // This isn't actually a service and shouldn't ever be started, but is
        // a convenient PM based way to manage our plugins.
        val intent = Intent(action)
        if (pkgName != null) {
            intent.setPackage(pkgName)
        }
        val result = packageManager.queryIntentServices(intent, 0)
        var logLevel = if (result.size <= 0) LogLevel.DEBUG else LogLevel.INFO
        val logMessage = buildString {
            append("Found ")
            append(result.size)
            append(" plugins")

            if (result.size > 1 && !allowMultiple) {
                append(", but multiple plugins are disallowed.")
                logLevel = LogLevel.ERROR
            }

            append(" ($env)")

            for (info in result) {
                val name = ComponentName(info.serviceInfo.packageName, info.serviceInfo.name)
                append("\n $name")

                if (logLevel != LogLevel.ERROR) {
                    val pluginInstance = loadPluginComponent(name)
                    if (pluginInstance != null) {
                        // add plugin before sending PLUGIN_CONNECTED message
                        pluginInstances.add(pluginInstance)
                        mainExecutor.execute { onPluginConnected(pluginInstance) }
                    }
                }
            }
        }

        logger.log(logLevel, logMessage)
    }

    private fun loadPluginComponent(component: ComponentName): PluginInstance<T>? {
        // Do not load non-privileged plugins in production builds.
        if (!env.isDebuggable && !packages.isPrivileged(component)) {
            logger.e({ "Plugin cannot be loaded in production: $str1" }) { str1 = "$component" }
            return null
        }

        if (!pluginEnabler.isEnabled(component)) {
            logger.w({ "Plugin is not enabled, aborting load: $str1" }) { str1 = "$component" }
            return null
        }

        try {
            val packageName = component.packageName
            // This isn't needed given that we don't have IGNORE_SECURITY on, but given the number
            // different contexts plugins is executed in, we prefer to check ourselves again.
            if (
                packageManager.checkPermission(PLUGIN_PERMISSION, packageName) !=
                    PackageManager.PERMISSION_GRANTED
            ) {
                logger.e({ "Plugin doesn't have permission: $str1" }) { str1 = packageName }
                return null
            }

            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            logger.d({ "createPlugin: $str1" }) { str1 = "$component" }

            try {
                return pluginInstanceFactory.create(
                    hostContext,
                    appInfo,
                    component,
                    pluginClass,
                    listener,
                )
            } catch (e: InvalidVersionException) {
                reportInvalidVersion(component, component.className, e)
            }
        } catch (ex: Throwable) {
            logger.e({ "Couldn't load plugin: $str1" }, ex) { str1 = "$component" }
            return null
        }

        return null
    }

    private fun reportInvalidVersion(
        component: ComponentName,
        className: String,
        ex: InvalidVersionException,
    ) {
        val icon = Resources.getSystem().getIdentifier("stat_sys_warning", "drawable", "android")
        val color =
            Resources.getSystem()
                .getIdentifier("system_notification_accent_color", "color", "android")
        val nb =
            Notification.Builder(hostContext, PluginManager.NOTIFICATION_CHANNEL_ID)
                .setStyle(Notification.BigTextStyle())
                .setSmallIcon(icon)
                .setWhen(0)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(hostContext.getColor(color))
        val label =
            try {
                "${packageManager.getServiceInfo(component, 0).loadLabel(packageManager)}"
            } catch (_: PackageManager.NameNotFoundException) {
                className
            }

        if (!ex.isTooNew) {
            nb.setContentTitle("Plugin '$label' is too old")
                .setContentText("Contact plugin developer to get an updated version. ${ex.message}")
        } else {
            nb.setContentTitle("Plugin '$label' is too new")
                .setContentText("Check to see if an OTA is available. ${ex.message}")
        }

        val i =
            Intent(PluginManagerImpl.DISABLE_PLUGIN)
                .setData("package://${component.flattenToString()}".toUri())
        val pi = PendingIntent.getBroadcast(hostContext, 0, i, PendingIntent.FLAG_IMMUTABLE)
        nb.addAction(Notification.Action.Builder(null, "Disable plugin", pi).build())
        notificationManager.notify(SystemMessageProto.SystemMessage.NOTE_PLUGIN, nb.build())
        logger.e("Error loading plugin", ex)
    }

    /**
     * Construct a [PluginActionManager]
     *
     * Note: This doesn't use @AssistedFactory because dagger doesn't support generic return types
     * from factory methods. See https://github.com/google/dagger/issues/2279 for more information.
     */
    @Singleton
    class Factory
    @Inject
    constructor(
        @Application private val hostContext: Context,
        private val packageManager: PackageManager,
        @Main private val mainExecutor: Executor,
        @Named(PluginManagerImpl.PLUGIN_THREAD) private val bgExecutor: Executor,
        private val notificationManager: NotificationManager,
        private val pluginEnabler: PluginEnabler,
        private val packages: PackageConfig,
        private val pluginInstanceFactory: PluginInstance.Factory,
        private val pluginPrefs: PluginPrefs,
        private val env: PluginEnvironment,
    ) {
        fun <T : Plugin> create(
            action: String,
            listener: PluginListener<T>,
            pluginClass: Class<T>,
            allowMultiple: Boolean,
        ): PluginActionManager<T> {
            return PluginActionManager(
                hostContext,
                packageManager,
                action,
                listener,
                pluginClass,
                allowMultiple,
                mainExecutor,
                bgExecutor,
                env,
                notificationManager,
                pluginEnabler,
                packages,
                pluginInstanceFactory,
                pluginPrefs,
            )
        }
    }

    companion object {
        private const val TAG = "PluginActionManager"
        const val PLUGIN_PERMISSION: String = "com.android.systemui.permission.PLUGIN"
    }
}
