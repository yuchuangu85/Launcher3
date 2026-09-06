/*
 * Copyright (C) 2008 The Android Open Source Project
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
package com.android.launcher3.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller.SessionInfo
import android.content.pm.ShortcutInfo
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.os.Looper
import android.os.Trace
import android.os.UserHandle
import android.text.TextUtils
import android.util.Log
import android.util.SparseArray
import androidx.annotation.AnyThread
import androidx.annotation.VisibleForTesting
import com.android.launcher3.Flags
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.Utilities
import com.android.launcher3.Utilities.ATLEAST_V
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.homescreenfiles.HomeScreenFilesCachingLogic
import com.android.launcher3.homescreenfiles.HomeScreenFilesCachingLogic.getComponent
import com.android.launcher3.homescreenfiles.homeScreenFile
import com.android.launcher3.homescreenfiles.isFileSystemFolderItem
import com.android.launcher3.homescreenfiles.isFileSystemItem
import com.android.launcher3.icons.CacheableShortcutCachingLogic.getComponent
import com.android.launcher3.icons.CacheableShortcutCachingLogic.getUser
import com.android.launcher3.icons.LauncherIcons.IconPool
import com.android.launcher3.icons.cache.BaseIconCache
import com.android.launcher3.icons.cache.CacheLookupFlag
import com.android.launcher3.icons.cache.CachedObject
import com.android.launcher3.icons.cache.CachedObjectCachingLogic
import com.android.launcher3.icons.cache.LauncherActivityCachingLogic
import com.android.launcher3.logging.FileLog
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.IconRequestInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.PackageItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.pm.InstallSessionHelper
import com.android.launcher3.pm.UserCache
import com.android.launcher3.provider.LauncherDbUtils.asSequence
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.ApplicationInfoWrapper
import com.android.launcher3.util.CancellableTask
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.InstantAppResolver
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.widget.WidgetSections
import java.util.concurrent.Executor
import java.util.function.Supplier
import javax.inject.Inject
import javax.inject.Named

/** Cache of application icons. Icons can be made from any thread. */
@LauncherAppSingleton
class IconCache
@Inject
constructor(
    @ApplicationContext context: Context,
    idp: InvariantDeviceProfile,
    @Named("ICONS_DB") dbFileName: String?,
    private val userManager: UserCache,
    iconProvider: LauncherIconProvider,
    private val installSessionHelper: InstallSessionHelper,
    private val iconPool: IconPool,
    private val instantAppResolver: InstantAppResolver,
    lifecycle: DaggerSingletonTracker,
) :
    BaseIconCache(
        context,
        dbFileName,
        bgLooper = Executors.MODEL_EXECUTOR.looper,
        iconDpi = idp.fillResIconDpi,
        iconPixelSize = idp.iconBitmapSize,
        inMemoryCache = true,
        iconProvider,
    ) {

    private val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)!!

    private val cancelledTask =
        CancellableTask<Any?>({ null }, MAIN_EXECUTOR, {}).apply { cancel() }

    private val widgetCategoryBitmapInfos = SparseArray<BitmapInfo>()
    private var pendingIconRequestCount = 0

    init {
        lifecycle.addCloseable { this.close() }
    }

    override fun getSerialNumberForUser(user: UserHandle): Long =
        userManager.getSerialNumberForUser(user)

    override fun isInstantApp(info: ApplicationInfo): Boolean =
        instantAppResolver.isInstantApp(info)

    override val iconFactory: BaseIconFactory
        get() = iconPool.obtain()

    /** Updates the entries related to the given package in memory and persistent DB. */
    @Synchronized
    fun updateIconsForPkg(packageName: String, user: UserHandle) {
        val apps = launcherApps.getActivityList(packageName, user)

        if (
            Flags.restoreArchivedAppIconsFromDb() &&
                ATLEAST_V &&
                apps.any { it.applicationInfo.isArchived }
        ) {
            // When archiving app icon, don't delete old icon so it can be re-used.
            return
        }
        removeIconsForPkg(packageName, user)
        val userSerial = userManager.getSerialNumberForUser(user)
        apps.forEach { addIconToDBAndMemCache(it, LauncherActivityCachingLogic, userSerial) }
    }

    /** Closes the cache DB. This will clear any in-memory cache. */
    fun close() {
        // Close the actual DB on the same thread where the cache is used.
        Executors.MODEL_EXECUTOR.execute {
            // This will clear all pending updates
            getUpdateHandler()
            iconDb.close()
        }
    }

    /**
     * Fetches high-res icon for the provided ItemInfo and updates the caller when done.
     *
     * @return a request ID that can be used to cancel the request.
     */
    @AnyThread
    fun updateIconInBackground(
        uiExecutor: Executor,
        caller: ItemInfoUpdateReceiver,
        info: ItemInfoWithIcon?,
        lookupFlag: CacheLookupFlag,
    ): CancellableTask<*> {
        val task: Supplier<ItemInfoWithIcon> =
            when (info) {
                is AppInfo,
                is WorkspaceItemInfo ->
                    Supplier {
                        getTitleAndIcon(info, lookupFlag)
                        info
                    }
                is PackageItemInfo ->
                    Supplier {
                        getTitleAndIconForApp(info, lookupFlag)
                        info
                    }
                else -> {
                    Log.i(TAG, "Icon update not supported for " + info?.javaClass?.name)
                    return cancelledTask
                }
            }

        val endRunnable: Runnable
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (pendingIconRequestCount <= 0) {
                Executors.MODEL_EXECUTOR.elevatePriority(LooperExecutor.CALLER_ICON_CACHE)
            }
            pendingIconRequestCount++
            endRunnable = Runnable { this.onIconRequestEnd() }
        } else {
            endRunnable = Runnable {}
        }

        val request = CancellableTask(task, uiExecutor, { caller.reapplyItemInfo(it) }, endRunnable)
        Utilities.postAsyncCallback(workerHandler, request)
        return request
    }

    private fun onIconRequestEnd() {
        pendingIconRequestCount--
        if (pendingIconRequestCount <= 0) {
            Executors.MODEL_EXECUTOR.restorePriority(LooperExecutor.CALLER_ICON_CACHE)
        }
    }

    /** Updates {@param application} only if a valid entry is found. */
    @Synchronized
    fun updateTitleAndIcon(application: AppInfo) {
        val entry =
            cacheLocked(
                requireNotNull(application.componentName) { "Component can't be null" },
                application.user,
                { null },
                LauncherActivityCachingLogic,
                application.matchingLookupFlag,
            )
        applyCacheEntry(entry, application)
    }

    /** Fill in `info` with the icon and label for `activityInfo` */
    @Synchronized
    fun getTitleAndIcon(
        info: ItemInfoWithIcon,
        activityInfo: LauncherActivityInfo?,
        lookupFlag: CacheLookupFlag,
    ) {
        val isAppArchived =
            Flags.enableSupportForArchiving() &&
                ATLEAST_V &&
                activityInfo != null &&
                activityInfo.activityInfo.isArchived
        // If we already have activity info, no need to use package icon
        getTitleAndIcon(info, lookupFlag.withUsePackageIcon(isAppArchived)) { activityInfo }
    }

    /** Fill in [info] with the icon for [si] */
    @JvmOverloads
    fun getShortcutIcon(
        info: ItemInfoWithIcon,
        si: ShortcutInfo,
        appInfo: ApplicationInfoWrapper =
            ApplicationInfoWrapper(context, si.getPackage(), si.userHandle),
        lookupFlags: CacheLookupFlag = CacheLookupFlag.DEFAULT_LOOKUP_FLAG.withThemeIcon(),
    ) {
        val oldIcon = info.bitmap
        val csi = CacheableShortcutInfo(si, appInfo) { oldIcon }
        getShortcutIcon(info, csi, lookupFlags)
    }

    /**
     * Fill in [info] with the icon and label for [si]. If the icon is not available, and fallback
     * check returns true, it keeps the old icon. Shortcut entries are not kept in memory since they
     * are not frequently used
     */
    fun <T : ItemInfoWithIcon> getShortcutIcon(
        info: T,
        si: CacheableShortcutInfo,
        lookupFlags: CacheLookupFlag,
    ) {
        val user = getUser(si)
        var bitmapInfo =
            cacheLocked(
                    getComponent(si),
                    user,
                    { si },
                    CacheableShortcutCachingLogic,
                    lookupFlags.withSkipAddToMemCache(),
                )
                .bitmap
        if (bitmapInfo.isLowRes) {
            bitmapInfo = getDefaultIcon(user)
        }
        if (isDefaultIcon(bitmapInfo, user)) return
        info.bitmap = bitmapInfo.withBadgeInfo(getShortcutInfoBadge(si.shortcutInfo, lookupFlags))
    }

    /** Returns the badging info for the shortcut */
    fun getShortcutInfoBadge(shortcutInfo: ShortcutInfo, lookupFlags: CacheLookupFlag): BitmapInfo =
        getShortcutInfoBadgeItem(shortcutInfo, lookupFlags).bitmap

    @VisibleForTesting
    fun getShortcutInfoBadgeItem(
        shortcutInfo: ShortcutInfo,
        lookupFlags: CacheLookupFlag,
    ): ItemInfoWithIcon {
        // Check for badge override first.
        var pkg = shortcutInfo.getPackage()
        val override = shortcutInfo.extras?.getString(EXTRA_SHORTCUT_BADGE_OVERRIDE_PACKAGE)
        if (
            !override.isNullOrEmpty() &&
                installSessionHelper.isTrustedPackage(pkg, shortcutInfo.userHandle)
        ) {
            pkg = override
        } else {
            // Try component based badge before trying the normal package badge
            val cn = shortcutInfo.activity
            if (cn != null) {
                // Get the app info for the source activity.
                val appInfo = AppInfo()
                appInfo.user = shortcutInfo.userHandle
                appInfo.componentName = cn
                appInfo.intent =
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(cn)
                getTitleAndIcon(appInfo, lookupFlags)
                return appInfo
            }
        }
        val pkgInfo = PackageItemInfo(pkg, shortcutInfo.userHandle)
        getTitleAndIconForApp(pkgInfo, lookupFlags)
        return pkgInfo
    }

    /**
     * Fill in {@param info} with the icon and label. If the corresponding activity is not found, it
     * reverts to the package icon.
     */
    @Synchronized
    fun getTitleAndIcon(info: ItemInfoWithIcon, lookupFlag: CacheLookupFlag) {
        if (info.isFileSystemItem()) {
            val hsf = info.homeScreenFile
            if (hsf == null) {
                info.bitmap = getDefaultIcon(info.user)
            } else {
                val entry =
                    cacheLocked(
                        getComponent(hsf),
                        info.user,
                        { hsf },
                        HomeScreenFilesCachingLogic,
                        lookupFlag,
                    )
                applyCacheEntry(entry, info)
            }
        } else if (info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
            val sk = ShortcutKey.fromItemInfo(info)
            val si: ShortcutInfo =
                sk.buildRequest(context).query(ShortcutRequest.ALL).getOrNull(0) ?: return
            getShortcutIcon(
                info,
                si,
                ApplicationInfoWrapper(context, si.getPackage(), si.userHandle),
                lookupFlag,
            )
        } else if (info.targetComponent == null) {
            // null info means not installed, but if we have a component from the intent then
            // we should still look in the cache for restored app icons.
            info.bitmap = getDefaultIcon(info.user)
            info.title = ""
            info.contentDescription = ""
        } else {
            val intent = info.intent
            getTitleAndIcon(info, lookupFlag.withUsePackageIcon()) {
                launcherApps.resolveActivity(intent, info.user)
            }
        }
    }

    /** Loads and returns the icon for the provided object without adding it to memCache */
    @Synchronized
    fun getTitleNoCache(info: CachedObject): String {
        val entry =
            cacheLocked(
                info.component,
                info.user,
                { info },
                CachedObjectCachingLogic,
                CacheLookupFlag.DEFAULT_LOOKUP_FLAG.withUseLowRes().withSkipAddToMemCache(),
            )
        return Utilities.trim(entry.title)
    }

    /** Fill in {@param mWorkspaceItemInfo} with the icon and label for {@param info} */
    @Synchronized
    fun getTitleAndIcon(
        infoInOut: ItemInfoWithIcon,
        lookupFlag: CacheLookupFlag,
        activityInfoProvider: Supplier<LauncherActivityInfo?>,
    ) {
        val entry =
            cacheLocked(
                requireNotNull(infoInOut.targetComponent) { "Component can't be null" },
                infoInOut.user,
                activityInfoProvider,
                LauncherActivityCachingLogic,
                lookupFlag,
            )
        applyCacheEntry(entry, infoInOut)
    }

    /**
     * Creates an sql cursor for a query of a set of ItemInfoWithIcon icons and titles.
     *
     * @param iconRequestInfos List of IconRequestInfos representing titles and icons to query.
     * @param user UserHandle all the given iconRequestInfos share
     * @param lookupFlag what flags to use when loading the icon.
     */
    @Throws(SQLiteException::class)
    private fun <T : ItemInfoWithIcon> createBulkQueryCursor(
        iconRequestInfos: List<IconRequestInfo<T>>,
        user: UserHandle,
        lookupFlag: CacheLookupFlag,
    ): Cursor {
        val distinctComponentNames =
            iconRequestInfos
                .mapNotNull { it.itemInfo.targetComponent?.flattenToString() }
                .distinct()

        val queryParams = distinctComponentNames + listOf(getSerialNumberForUser(user).toString())
        val componentNameQuery = Array(distinctComponentNames.size) { "?" }.joinToString(",")

        return iconDb.query(
            lookupFlag.toLookupColumns(),
            "$COLUMN_COMPONENT IN ( $componentNameQuery ) AND $COLUMN_USER = ?",
            queryParams.toTypedArray(),
        )
    }

    /** Load and fill icons requested in iconRequestInfos using a single bulk sql query. */
    @Synchronized
    fun <T : ItemInfoWithIcon> getTitlesAndIconsInBulk(iconRequestInfos: List<IconRequestInfo<T>>) {
        Trace.beginSection("loadIconsInBulk")

        iconRequestInfos
            .filter {
                if (it.itemInfo.targetComponent == null) {
                    Log.i(TAG, "Skipping Item info with null component name: ${it.itemInfo}")
                    it.itemInfo.bitmap = getDefaultIcon(it.itemInfo.user)
                    false
                } else if (it.itemInfo.itemType == ITEM_TYPE_DEEP_SHORTCUT) {
                    // Filter out icons that should not share the same bitmap and title
                    Log.e(
                        TAG,
                        "Skipping Item info for deep shortcut: ${it.itemInfo}",
                        IllegalStateException(),
                    )
                    false
                } else true
            }
            .groupBy { BulkLookupKey(it.itemInfo.user, it.lookupFlag) }
            .forEach { (bulkLookupKey, sectionRequestInfos) ->
                Trace.beginSection("loadIconSubsectionInBulk")
                loadIconSubsection(bulkLookupKey, sectionRequestInfos)
                Trace.endSection()
            }
        Trace.endSection()
    }

    private fun <T : ItemInfoWithIcon> loadIconSubsection(
        sectionKey: BulkLookupKey,
        filteredList: List<IconRequestInfo<T>>,
    ) {
        val duplicateIconRequestsMap = filteredList.groupBy { it.itemInfo.targetComponent!! }
        Trace.beginSection("loadIconSubsectionWithDatabase")
        try {
            createBulkQueryCursor(filteredList, sectionKey.user, sectionKey.lookupFlag).use { c ->
                // Database title and icon loading
                val componentNameColumnIndex = c.getColumnIndexOrThrow(COLUMN_COMPONENT)
                c.asSequence().forEach { _ ->
                    val cn =
                        ComponentName.unflattenFromString(c.getString(componentNameColumnIndex))
                            ?: return@forEach
                    val duplicateIconRequests = duplicateIconRequestsMap[cn]
                    if (duplicateIconRequests == null) {
                        Log.e(
                            TAG,
                            "Found entry in icon database but no main activity entry for cn: $cn",
                        )
                        return@forEach
                    }

                    val entry =
                        cacheLocked(
                            cn,
                            sectionKey.user,
                            { duplicateIconRequests[0].launcherActivityInfo },
                            LauncherActivityCachingLogic,
                            sectionKey.lookupFlag,
                            c,
                        )
                    duplicateIconRequests.forEach { applyCacheEntry(entry, it.itemInfo) }
                }
            }
        } catch (e: SQLiteException) {
            Log.d(TAG, "Error reading icon cache", e)
        } finally {
            Trace.endSection()
        }

        Trace.beginSection("loadIconSubsectionWithFallback")

        // Fallback title and icon loading
        duplicateIconRequestsMap.forEach { (cn, iconRequestInfos) ->
            val iconRequestInfo = iconRequestInfos[0]
            val itemInfo = iconRequestInfo.itemInfo
            val icon = itemInfo.bitmap
            val loadFallbackTitle = TextUtils.isEmpty(itemInfo.title)
            val loadFallbackIcon =
                isDefaultIcon(icon, itemInfo.user) || icon === BitmapInfo.LOW_RES_INFO

            if (loadFallbackTitle || loadFallbackIcon) {
                Log.i(
                    TAG,
                    "Database bulk icon loading failed, using fallback bulk icon loading for: $cn",
                )
                val entry = CacheEntry()
                val lai = iconRequestInfo.launcherActivityInfo

                // Fill fields that are not updated below so they are not subsequently
                // deleted.
                entry.title = itemInfo.title ?: ""
                entry.bitmap = icon
                entry.contentDescription = itemInfo.contentDescription ?: ""

                if (loadFallbackIcon) {
                    loadFallbackIcon(
                        lai,
                        entry,
                        LauncherActivityCachingLogic,
                        iconRequestInfo.lookupFlag.withUsePackageIcon(false),
                        usePackageTitle = loadFallbackTitle,
                        cn,
                        sectionKey.user,
                    )
                }
                if (loadFallbackTitle && TextUtils.isEmpty(entry.title) && lai != null) {
                    loadFallbackTitle(lai, entry, LauncherActivityCachingLogic, sectionKey.user)
                }

                iconRequestInfos.forEach { applyCacheEntry(entry, it.itemInfo) }
            }
        }
        Trace.endSection()
    }

    /** Fill in {@param infoInOut} with the corresponding icon and label. */
    @Synchronized
    fun getTitleAndIconForApp(infoInOut: PackageItemInfo, lookupFlag: CacheLookupFlag) {
        val entry = getEntryForPackageLocked(infoInOut.packageName, infoInOut.user, lookupFlag)
        applyCacheEntry(entry, infoInOut)
        if (infoInOut.widgetCategory == WidgetSections.NO_CATEGORY) return

        val widgetSection = WidgetSections.getWidgetSections(context)[infoInOut.widgetCategory]
        val title = context.getString(widgetSection.mSectionTitle)
        infoInOut.title = title
        infoInOut.contentDescription = getUserBadgedLabel(title, infoInOut.user)
        val cachedBitmap = widgetCategoryBitmapInfos[infoInOut.widgetCategory]
        if (cachedBitmap != null) {
            infoInOut.bitmap = getBadgedIcon(cachedBitmap, infoInOut.user)
            return
        }

        try {
            iconPool.obtain().use { li ->
                val tempBitmap =
                    li.createBadgedIconBitmap(context.getDrawable(widgetSection.mSectionDrawable))
                widgetCategoryBitmapInfos.put(infoInOut.widgetCategory, tempBitmap)
                infoInOut.bitmap = getBadgedIcon(tempBitmap, infoInOut.user)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing bitmap for icons with widget category", e)
        }
    }

    @Synchronized
    private fun getBadgedIcon(bitmap: BitmapInfo?, user: UserHandle): BitmapInfo =
        bitmap?.withFlags(getUserFlagOpLocked(user)) ?: getDefaultIcon(user)

    private fun applyCacheEntry(entry: CacheEntry, info: ItemInfoWithIcon) {
        info.title = Utilities.trim(entry.title)
        info.contentDescription =
            if (info.isFileSystemFolderItem())
                context.getString(
                    com.android.launcher3.R.string.files_folder_name,
                    entry.contentDescription,
                )
            else entry.contentDescription
        info.bitmap = entry.bitmap
        // Clear any previously set appTitle, if the packageOverride is no longer valid
        info.appTitle = null
        if (entry.bitmap == null) {
            // TODO: entry.bitmap can never be null, so this should not happen at all.
            Log.wtf(TAG, "Cannot find bitmap from the cache, default icon was loaded.")
            info.bitmap = getDefaultIcon(info.user)
        }

        // apply package override
        if (!Flags.enableSupportForArchiving() || !info.isArchived) return

        val targetPackage = info.targetPackage ?: return
        val packageEntry = getInMemoryPackageEntryLocked(targetPackage, info.user)
        if (packageEntry == null || packageEntry.bitmap.isLowRes) return

        info.appTitle = Utilities.trim(info.title)
        info.title = Utilities.trim(packageEntry.title)
        info.contentDescription = packageEntry.contentDescription
        info.bitmap = packageEntry.bitmap
    }

    fun updateSessionCache(key: PackageUserKey, info: SessionInfo) =
        cachePackageInstallInfo(key.mPackageName, key.mUser, info.getAppIcon(), info.getAppLabel())

    @VisibleForTesting
    @Synchronized
    fun isItemInDb(cacheKey: ComponentKey): Boolean =
        getEntryFromDBLocked(
            cacheKey,
            CacheEntry(),
            CacheLookupFlag.DEFAULT_LOOKUP_FLAG,
            LauncherActivityCachingLogic,
        )

    /** Interface for receiving itemInfo with high-res icon. */
    fun interface ItemInfoUpdateReceiver {
        fun reapplyItemInfo(info: ItemInfoWithIcon?)
    }

    /** Log persistently to FileLog.d for debugging. */
    override fun logPersistently(message: String, e: Exception?) {
        FileLog.d(TAG, message, e)
    }

    private data class BulkLookupKey(val user: UserHandle, val lookupFlag: CacheLookupFlag)

    companion object {
        // Shortcut extra which can point to a packageName and can be used to indicate an alternate
        // badge info. Launcher only reads this if the shortcut comes from a system app.
        const val EXTRA_SHORTCUT_BADGE_OVERRIDE_PACKAGE: String =
            "extra_shortcut_badge_override_package"

        private const val TAG = "Launcher.IconCache"
    }
}
