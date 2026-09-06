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

package com.android.launcher3.dragndrop

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.net.Uri
import android.os.Looper
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.util.AttributeSet
import android.view.DragAndDropPermissions
import android.view.DragEvent
import android.view.View
import android.widget.ImageView
import androidx.core.view.size
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.Flags.FLAG_ENABLE_SYSTEM_DRAG
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.RoboApiWrapper
import com.android.launcher3.views.ActivityContext
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

/** Tests for {@link SystemDragListener}. */
@SmallTest
@EnableFlags(FLAG_ENABLE_SYSTEM_DRAG)
@RunWith(ParameterizedAndroidJunit4::class)
class SystemDragListenerTest(val name: String, private val params: SystemDragParams?) {

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams() =
            listOf(
                arrayOf("WithItemInfoParams", createParams(mock<ItemInfo>(), false)),
                arrayOf("WithNullParams", createParams(null)),
                arrayOf("WithSystemDragItemInfoParams", createParams(mock<SystemDragItemInfo>())),
            )

        private fun createParams(itemInfo: ItemInfo?, closeAllOpenViews: Boolean = true) =
            itemInfo?.let { dragInfo ->
                SystemDragParams(
                    clipData = mock(),
                    extraDragFlags = 0,
                    closeAllOpenViews = closeAllOpenViews,
                    dragImage = mock(),
                    draggableView = mock(),
                    dragLayerX = DRAG_LAYER_X,
                    dragLayerY = DRAG_LAYER_Y,
                    dragSource = mock(),
                    dragInfo = dragInfo,
                    dragRegion = mock(),
                    initialDragViewScale = INITIAL_DRAG_VIEW_SCALE,
                    dragViewScaleOnDrop = DRAG_VIEW_SCALE_ON_DROP,
                    dragOptions = mock(),
                )
            }

        private const val DRAG_EVENT_X = 10.0f
        private const val DRAG_EVENT_Y = 20.0f
        private const val DRAG_LAYER_X = 1
        private const val DRAG_LAYER_Y = 2
        private const val DRAG_VIEW_SCALE_ON_DROP = 3.0f
        private const val IDP_ICON_BITMAP_SIZE = 24
        private const val INITIAL_DRAG_VIEW_SCALE = 4.0f
    }

    @get:Rule val flags: SetFlagsRule = SetFlagsRule()
    @get:Rule val mockito = MockitoJUnit.rule()

    @Mock private lateinit var mockContext: ActivityContext
    @Mock private lateinit var mockDragEvent: DragEvent
    @Mock private lateinit var mockDragLayer: DragLayer
    @Mock private lateinit var mockFloatingView: TestFloatingView
    @Mock private lateinit var mockIdp: InvariantDeviceProfile

    private lateinit var listener: SystemDragListener

    @Before
    fun setUp() {
        initMock(mockContext)
        initMock(mockDragEvent)
        initMock(mockDragLayer)
        initMock(mockFloatingView)
        initMock(mockIdp)

        if (params?.dragInfo is SystemDragItemInfo && mockingDetails(params.dragInfo).isMock) {
            initMock(params.dragInfo as SystemDragItemInfo)
        }

        listener =
            SystemDragListener(
                mockContext,
                mockIdp,
                { mock<ImageView>().apply(::initMock) },
                params,
            )

        // NOTE: The system drag listener registers itself with the launcher's drag controller
        // during construction. Verify the expected registration but then clear invocations so that
        // tests below don't need to be mindful of constructor-related interactions.
        verify(mockContext.dragController).addDragSessionListener(listener)
        verify(mockContext.dragController).addSystemDragHandler(listener)
        clearInvocations(mockContext.dragController)
    }

    @After
    fun tearDown() {
        // NOTE: Ensure that any tasks posted by the system drag listener under test have a chance
        // to run prior to test completion. Failure to do so may negatively impact subsequent tests.
        RoboApiWrapper.waitForLooperSync(Looper.getMainLooper())
    }

    @Test
    fun testCleanupCallback() {
        val callback = mock<Runnable>()
        listener.setCleanupCallback(callback)
        listener.onDropCompleted(mock<View>(), mock<DragObject>(), /* success= */ true)
        verify(callback).run()
    }

    @Test
    fun testCloseAllOpenViews() {
        val closeAllOpenViews = params?.closeAllOpenViews ?: true
        val times = if (closeAllOpenViews) times(1) else times(0)
        verify(mockFloatingView, times).close(any())
    }

    @Test
    fun testDragSessionEnd() {
        val callback = mock<Runnable>()
        listener.setCleanupCallback(callback)
        listener.onDragSessionEnd()
        verify(callback).run()
        verify(mockContext.dragController).removeDragSessionListener(listener)
    }

    @Test
    fun testDragLocation() {
        testDragLocation(dragImageCaptor = argumentCaptor(), dragInfoCaptor = argumentCaptor())
    }

    private fun testDragLocation(
        dragImageCaptor: KArgumentCaptor<ImageView>,
        dragInfoCaptor: KArgumentCaptor<ItemInfo>,
    ) {
        testDragStart(dragImageCaptor = dragImageCaptor, dragInfoCaptor = dragInfoCaptor)

        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_LOCATION)
        whenever(mockContext.dragController.isDragging).thenReturn(true)

        assertTrue(listener.onDrag(mockDragEvent))
        verify(mockContext.dragController).isDragging
        verifyNoMoreInteractions(mockContext.dragController)

        assertEquals(0.0f, dragImageCaptor.firstValue.alpha)
    }

    @Test
    fun testDragStart() {
        testDragStart(dragImageCaptor = argumentCaptor(), dragInfoCaptor = argumentCaptor())
    }

    private fun testDragStart(
        dragImageCaptor: KArgumentCaptor<ImageView>,
        dragInfoCaptor: KArgumentCaptor<ItemInfo>,
    ) {
        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_STARTED)
        whenever(mockContext.dragController.isDragging).thenReturn(false)

        assertTrue(listener.onDrag(mockDragEvent))

        if (params != null) {
            verify(mockContext.dragController)
                .startDrag(
                    dragImageCaptor.capture(),
                    eq(params.draggableView),
                    eq(params.dragLayerX),
                    eq(params.dragLayerY),
                    eq(params.dragSource),
                    dragInfoCaptor.capture(),
                    eq(params.dragRegion),
                    eq(params.initialDragViewScale),
                    eq(params.dragViewScaleOnDrop),
                    eq(params.dragOptions),
                )

            with(dragImageCaptor.firstValue) {
                assertEquals(0.0f, alpha)
                assertEquals(params.dragImage, drawable)
            }

            assertEquals(params.dragInfo, dragInfoCaptor.firstValue)
        } else {
            val screenPos = Point(mockDragEvent.x.toInt(), mockDragEvent.y.toInt())
            val dragLayerX = screenPos.x - (mockIdp.iconBitmapSize / 2)
            val dragLayerY = screenPos.y - (mockIdp.iconBitmapSize / 2)

            verify(mockContext.dragController)
                .startDrag(
                    /*view=*/ dragImageCaptor.capture(),
                    /*originalView=*/ argThat { viewType == DraggableView.DRAGGABLE_ICON },
                    /*dragLayerX=*/ eq(dragLayerX),
                    /*dragLayerY=*/ eq(dragLayerY),
                    /*source=*/ eq(listener),
                    /*dragInfo=*/ dragInfoCaptor.capture(),
                    /*dragRegion=*/ eq(Rect()),
                    /*initialDragViewScale=*/ eq(1.0f),
                    /*dragViewScaleOnDrop=*/ eq(1.0f),
                    /*options=*/ argThat { isSystemDrag && simulatedDndStartPoint == screenPos },
                )

            with(dragImageCaptor.firstValue) {
                assertEquals(0.0f, alpha)
                with(drawable as DrawableWrapper) {
                    assertEquals(mockIdp.iconBitmapSize, intrinsicHeight)
                    assertEquals(mockIdp.iconBitmapSize, intrinsicWidth)
                    assertEquals(
                        BitmapInfo.LOW_RES_INFO,
                        (drawable as? FastBitmapDrawable)?.bitmapInfo,
                    )
                }
            }

            assertTrue(dragInfoCaptor.firstValue is SystemDragItemInfo)
        }
    }

    @Test
    fun testDropWhenRequestingPermissionsSucceeds() {
        testDrop(/* throwExceptionWhenRequestingPermissions= */ false)
    }

    @Test
    fun testDropWhenRequestingPermissionsThrowsException() {
        testDrop(/* throwExceptionWhenRequestingPermissions= */ true)
    }

    private fun testDrop(throwExceptionWhenRequestingPermissions: Boolean) {
        val dragImageCaptor = argumentCaptor<ImageView>()
        val dragInfoCaptor = argumentCaptor<ItemInfo>()

        testDragLocation(dragImageCaptor, dragInfoCaptor)
        clearInvocations(mockContext.dragController)

        val systemDragItemInfo =
            (dragInfoCaptor.firstValue as? SystemDragItemInfo)?.also {
                assertEquals(SystemDragItemInfo.EmptyPayload, it.payload)
            }

        val mockUri1 = mock<Uri>()
        val mockUri2 = mock<Uri>()

        val mockClipItems =
            listOf(
                null,
                ClipData.Item(mock<CharSequence>()),
                ClipData.Item(mock<Intent>()),
                ClipData.Item(mockUri1),
                ClipData.Item(mockUri1),
                ClipData.Item(mockUri2),
            )

        val mockClipData =
            mock<ClipData>().apply {
                whenever(itemCount).thenReturn(mockClipItems.size)
                whenever(getItemAt(any())).thenAnswer { mockClipItems[it.getArgument(0)] }
            }

        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DROP)
        whenever(mockDragEvent.clipData).thenReturn(mockClipData)
        whenever(mockContext.requestDragAndDropPermissions(mockDragEvent)).thenAnswer {
            if (throwExceptionWhenRequestingPermissions) throw RuntimeException()
            mock<DragAndDropPermissions>()
        }

        assertTrue(listener.onDrag(mockDragEvent))
        verify(mockContext.dragController).isDragging
        verifyNoMoreInteractions(mockContext.dragController)

        assertEquals(1.0f, dragImageCaptor.firstValue.alpha)

        if (systemDragItemInfo != null) {
            verify(mockContext).requestDragAndDropPermissions(mockDragEvent)

            with(systemDragItemInfo) {
                if (throwExceptionWhenRequestingPermissions) {
                    assertEquals(SystemDragItemInfo.EmptyPayload, payload)
                } else {
                    with(payload as SystemDragItemInfo.UriListPayload) {
                        assertNotNull(permissions)
                        assertEquals(listOf(mockUri1, mockUri2), uriList)
                    }
                }
            }
        }
    }

    @Test
    fun testStartDrag() {
        val dragImageCaptor = argumentCaptor<ImageView>()
        val dragView = mock<DragView>()
        val screenPos = params?.dragOptions?.simulatedDndStartPoint ?: mock<Point>()

        whenever(
                mockContext.dragController.startDrag(
                    dragImageCaptor.capture(),
                    if (params != null) eq(params.draggableView) else any(),
                    if (params != null) eq(params.dragLayerX) else any(),
                    if (params != null) eq(params.dragLayerY) else any(),
                    if (params != null) eq(params.dragSource) else any(),
                    if (params != null) eq(params.dragInfo) else any(),
                    if (params != null) eq(params.dragRegion) else any(),
                    if (params != null) eq(params.initialDragViewScale) else any(),
                    if (params != null) eq(params.dragViewScaleOnDrop) else any(),
                    argThat {
                        isSystemDrag &&
                            simulatedDndStartPoint == screenPos &&
                            (params == null || this == params.dragOptions)
                    },
                )
            )
            .thenReturn(dragView)

        val expectedResult = if (params != null) dragView else null

        assertEquals(expectedResult, listener.startDrag(screenPos))

        if (params != null) {
            with(dragImageCaptor.firstValue) {
                assertEquals(0.0f, alpha)
                assertEquals(params.dragImage, drawable)
            }
        }
    }

    private fun initMock(mockContext: ActivityContext) {
        whenever(mockContext.lifecycle).thenReturn(mock())
        whenever(mockContext.lifecycle.currentState).thenReturn(mock())
        whenever(mockContext.dragController).thenReturn(mock())
        whenever(mockContext.dragLayer).thenReturn(mockDragLayer)
        doReturn(ApplicationProvider.getApplicationContext()).whenever(mockContext).asContext()
    }

    private fun initMock(mockDragEvent: DragEvent) {
        val mockClipDescription = mock<ClipDescription>()
        whenever(mockClipDescription.hasMimeType("*/*")).thenReturn(true)
        whenever(mockDragEvent.clipDescription).thenReturn(mockClipDescription)
        whenever(mockDragEvent.x).thenReturn(DRAG_EVENT_X)
        whenever(mockDragEvent.y).thenReturn(DRAG_EVENT_Y)
    }

    private fun initMock(mockDragLayer: DragLayer) {
        whenever(mockDragLayer.size).thenReturn(1)
        whenever(mockDragLayer.getChildAt(0)).thenReturn(mockFloatingView)
    }

    private fun initMock(mockFloatingView: TestFloatingView) {
        whenever(mockFloatingView.isOfType(AbstractFloatingView.TYPE_ALL)).thenReturn(true)
    }

    private fun initMock(mockImageView: ImageView) {
        var alpha: Float = 1.0f
        whenever(mockImageView.alpha).thenAnswer { alpha }
        whenever(mockImageView.setAlpha(any<Float>())).thenAnswer {
            alpha = it.getArgument(0)
            Unit
        }

        var drawable: Drawable? = null
        whenever(mockImageView.drawable).thenAnswer { drawable }
        whenever(mockImageView.setImageDrawable(anyOrNull())).thenAnswer {
            drawable = it.getArgument(0)
            Unit
        }
    }

    private fun initMock(mockIdp: InvariantDeviceProfile) {
        mockIdp.iconBitmapSize = IDP_ICON_BITMAP_SIZE
    }

    private fun initMock(mockSystemDragItemInfo: SystemDragItemInfo) {
        val payload = AtomicReference<SystemDragItemInfo.Payload>(SystemDragItemInfo.EmptyPayload)
        whenever(mockSystemDragItemInfo.payload).thenAnswer { payload.get() }
        whenever(mockSystemDragItemInfo::payload.setter.invoke(any())).thenAnswer {
            payload.set(it.getArgument<SystemDragItemInfo.Payload>(0))
        }
    }

    // NOTE: This sub-class exists only to increase visibility of [#isOfType()].
    private abstract class TestFloatingView(context: Context, attrs: AttributeSet) :
        AbstractFloatingView(context, attrs) {
        public abstract override fun isOfType(@FloatingViewType type: Int): Boolean
    }
}
