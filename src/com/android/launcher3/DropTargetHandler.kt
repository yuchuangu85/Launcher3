package com.android.launcher3

import android.content.ComponentName
import android.util.Log
import android.view.View
import com.android.launcher3.BaseActivity.EVENT_RESUMED
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.LauncherConstants.ActivityCodes
import com.android.launcher3.SecondaryDropTarget.DeferredOnComplete
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesUtils
import com.android.launcher3.homescreenfiles.isFileSystemItem
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.IntSet
import com.android.launcher3.util.PendingRequestArgs
import com.android.launcher3.views.Snackbar
import java.util.concurrent.Executor

/**
 * Handler class for drop target actions that require modifying or interacting with launcher.
 *
 * This class is created by Launcher and provided the instance of launcher when created, which
 * allows us to decouple drop target controllers from Launcher to enable easier testing.
 */
class DropTargetHandler(
    private val launcher: Launcher,
    private val undoDeleteController: UndoDeleteController,
    private val homeScreenFilesProvider: HomeScreenFilesProvider,
    private val mainExecutor: Executor,
) {
    fun onDropAnimationComplete() {
        launcher.stateManager.goToState(LauncherState.NORMAL)
    }

    fun onSecondaryTargetCompleteDrop(target: ComponentName?, d: DragObject) {
        when (val dragSource = d.dragSource) {
            is DeferredOnComplete -> {
                val deferred: DeferredOnComplete = dragSource
                if (d.dragSource is SecondaryDropTarget.DeferredOnComplete) {
                    target?.let {
                        deferred.mPackageName = it.packageName
                        launcher.addEventCallback(EVENT_RESUMED) { deferred.onLauncherResume() }
                    } ?: deferred.sendFailure()
                }
            }
        }
    }

    fun reconfigureWidget(widgetId: Int, info: ItemInfo) {
        launcher.setWaitingForResult(PendingRequestArgs.forWidgetInfo(widgetId, null, info))
        launcher.appWidgetHolder?.also {
            it.startConfigActivity(launcher, widgetId, ActivityCodes.REQUEST_RECONFIGURE_APPWIDGET)
        } ?: Log.e(TAG, "appWidgetHolder is null, cannot start config activity.")
    }

    fun getViewUnderDrag(info: ItemInfo): View? {
        return if (
            info is LauncherAppWidgetInfo &&
                info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                launcher.workspace.dragInfo != null
        ) {
            launcher.workspace.dragInfo.cell
        } else null
    }

    fun prepareToUndoDelete(item: ItemInfo) {
        if (item.isFileSystemItem() && HomeScreenFilesUtils.isTrashingEnabled()) {
            // Home screen file items rely on their own unidirectional data flow
            // (`HomeScreenFilesProvider` -> `HomeScreenFilesUpdateTask`), so there is no need
            // to manually call `mLauncher.modelWriter` from here.
            return
        }
        undoDeleteController.prepareToUndoDelete()
    }

    fun onDeleteComplete(item: ItemInfo, view: View?) {
        if (item.isFileSystemItem() && HomeScreenFilesUtils.isTrashingEnabled()) {
            onDeleteCompleteForHomeScreenFile(item)
            return
        }

        removeItemAndStripEmptyScreens(view, item)
        AbstractFloatingView.closeOpenViews(
            launcher,
            false,
            AbstractFloatingView.TYPE_WIDGET_RESIZE_FRAME or AbstractFloatingView.TYPE_FOLDER,
        )
        var pageItem: ItemInfo = item
        if (item.container >= 0) {
            launcher.workspace.getViewByItemId(item.container)?.let {
                pageItem = it.tag as ItemInfo
            }
        }
        val pageIds =
            if (pageItem.container == LauncherSettings.Favorites.CONTAINER_DESKTOP)
                IntSet.wrap(pageItem.screenId)
            else launcher.workspace.currentPageScreenIds
        val onDismissed = Runnable {
            if (item.isFileSystemItem()) {
                // The old "Delete permanently" action is delayed until `onDismissed`, otherwise
                // it's impossible to restore the file item on "Undo".
                homeScreenFilesProvider.deletePermanently(
                    requireNotNull(requireNotNull(item.intent).data)
                )
            }
            undoDeleteController.commit()
        }
        val onUndoClicked = Runnable {
            launcher.setPagesToBindSynchronously(pageIds)
            undoDeleteController.abort()
            launcher.statsLogManager.logger().log(LauncherEvent.LAUNCHER_UNDO)
        }

        Snackbar.show(launcher, R.string.item_removed, R.string.undo, onDismissed, onUndoClicked)
    }

    private fun onDeleteCompleteForHomeScreenFile(item: ItemInfo) {
        val title = item.title.toString()
        val restoreFromTrashResultHandler: (Boolean) -> Unit = { success ->
            if (!success) {
                Snackbar.show(
                    launcher,
                    R.string.home_screen_files_restore_from_trash_error_message,
                )
                /*onDismissed=*/ {}
            }
        }
        val moveToTrashResultHandler: (String?) -> Unit = { trashPath ->
            if (trashPath != null) {
                Snackbar.show(
                    launcher,
                    launcher.resources.getString(R.string.home_screen_files_moved_to_trash_message),
                    R.string.undo,
                    /*onDismissed=*/ {},
                    /*onActionClicked=*/ {
                        homeScreenFilesProvider
                            .restoreFromTrash(trashPath)
                            .thenAcceptAsync(restoreFromTrashResultHandler, mainExecutor)
                    },
                )
            } else {
                Snackbar.show(launcher, R.string.home_screen_files_move_to_trash_error_message)
                /*onDismissed=*/ {}
            }
        }

        homeScreenFilesProvider
            .moveToTrash(title)
            .thenAcceptAsync(moveToTrashResultHandler, mainExecutor)
    }

    fun getDragLayer(): DragLayer {
        return launcher.dragLayer
    }

    fun onClick(buttonDropTarget: ButtonDropTarget) {
        launcher.accessibilityDelegate.handleAccessibleDrop(buttonDropTarget, null)
    }

    private fun removeItemAndStripEmptyScreens(view: View?, item: ItemInfo) {
        // Remove the item from launcher ONLY (not the db). The DB deletion is handled by
        // resuming the UndoDeleteController.
        launcher.removeItem(view, item, true /* deleteFromDb */, "removed by accessibility drop")
        launcher.workspace.stripEmptyScreens()
    }

    companion object {
        private const val TAG = "DropTargetHandler"
    }
}
