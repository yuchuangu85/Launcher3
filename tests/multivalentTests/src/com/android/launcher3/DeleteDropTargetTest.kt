package com.android.launcher3

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.Utilities.enableRunningInTestHarnessForTests
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.dragndrop.DragView
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.MSDLPlayerWrapper
import com.android.launcher3.util.TestActivityContext
import com.android.providers.media.flags.Flags.FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API
import com.google.android.msdl.data.model.MSDLToken
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeleteDropTargetTest {

    @get:Rule val mSetFlagsRule = SetFlagsRule()
    @get:Rule val mContext = TestActivityContext()

    @Mock private val msdlPlayerWrapper = mock<MSDLPlayerWrapper>()

    // Use a non-abstract class implementation
    private var buttonDropTarget: DeleteDropTarget = DeleteDropTarget(mContext)

    @Before
    fun setup() {
        enableRunningInTestHarnessForTests()
    }

    @Test
    fun isTextClippedVerticallyTest() {
        buttonDropTarget.updateText("My Test")
        buttonDropTarget.setPadding(0, 0, 0, 0)
        buttonDropTarget.setTextMultiLine(false)

        // No space for text
        assertThat(buttonDropTarget.isTextClippedVertically(1)).isTrue()

        // A lot of space for text so the text should not be clipped
        assertThat(buttonDropTarget.isTextClippedVertically(1000)).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_MSDL_FEEDBACK)
    fun onDragEnter_performsMSDLSwipeThresholdFeedback() {
        buttonDropTarget.setMSDLPlayerWrapper(msdlPlayerWrapper)
        val target = DropTarget.DragObject(mContext)
        target.dragView = mock<DragView>()
        buttonDropTarget.onDragEnter(target)

        verify(msdlPlayerWrapper, times(1)).playToken(eq(MSDLToken.SWIPE_THRESHOLD_INDICATOR))
        verifyNoMoreInteractions(msdlPlayerWrapper)
    }

    @Test
    fun setsTextBasedOnDragSource() {
        verifyTextForItemInfo(ItemInfo().apply { id = ItemInfo.NO_ID }, "Cancel")
        verifyTextForItemInfo(
            ItemInfo().apply {
                id = 1
                itemType = Favorites.ITEM_TYPE_APPLICATION
            },
            "Remove",
        )
    }

    @Test
    @DisableFlags(
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_TRASHING,
        FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API,
    )
    fun setsTextForFileSystemItemsWhenTrashingDisabled() {
        verifyTextForItemInfo(
            ItemInfo().apply {
                id = 1
                itemType = Favorites.ITEM_TYPE_FILE_SYSTEM_FILE
            },
            "Delete permanently",
        )
        verifyTextForItemInfo(
            ItemInfo().apply {
                id = 1
                itemType = Favorites.ITEM_TYPE_FILE_SYSTEM_FOLDER
            },
            "Delete permanently",
        )
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_HOME_SCREEN_FILES_TRASHING,
        FLAG_ENABLE_TRASH_AND_RESTORE_BY_FILE_PATH_API,
    )
    fun setsTextForFileSystemItemsWhenTrashingEnabled() {
        verifyTextForItemInfo(
            ItemInfo().apply {
                id = 1
                itemType = Favorites.ITEM_TYPE_FILE_SYSTEM_FILE
            },
            "Move to trash",
        )
        verifyTextForItemInfo(
            ItemInfo().apply {
                id = 1
                itemType = Favorites.ITEM_TYPE_FILE_SYSTEM_FOLDER
            },
            "Move to trash",
        )
    }

    private fun verifyTextForItemInfo(item: ItemInfo, expectedText: String) {
        buttonDropTarget.updateText("My Test")

        val target = DropTarget.DragObject(mContext)
        target.dragInfo = item
        buttonDropTarget.onDragStart(target, DragOptions())

        assertThat(buttonDropTarget.mText).isEqualTo(expectedText)
    }
}
