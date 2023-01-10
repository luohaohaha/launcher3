/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.widget;

import static com.android.launcher3.logging.LoggerUtils.newContainerTarget;

import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Toast;

import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.graphics.ColorScrim;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.AbstractSlideInView;

/**
 * Base class for various widgets popup
 */
abstract class BaseWidgetSheet extends AbstractSlideInView
        implements OnClickListener, OnLongClickListener, DragSource {


    /* Touch handling related member variables. */
    private Toast mWidgetInstructionToast;

    protected final ColorScrim mColorScrim;

    public BaseWidgetSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mColorScrim = ColorScrim.createExtractedColorScrim(this);
    }

    @Override
    public final void onClick(View v) {
        // Let the user know that they have to long press to add a widget
        if (mWidgetInstructionToast != null) {
            mWidgetInstructionToast.cancel();
        }

        CharSequence msg = Utilities.wrapForTts(
                getContext().getText(R.string.long_press_widget_to_add),
                getContext().getString(R.string.long_accessible_way_to_add));
        mWidgetInstructionToast = Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT);
        mWidgetInstructionToast.show();
    }

    @Override
    public final boolean onLongClick(View v) {
        if (!ItemLongClickListener.canStartDrag(mLauncher)) return false;

        if (v instanceof WidgetCell) {
            return beginDraggingWidget((WidgetCell) v);
        }
        return true;
    }

    protected void setTranslationShift(float translationShift) {
        super.setTranslationShift(translationShift);
        mColorScrim.setProgress(1 - mTranslationShift);
    }

    private boolean beginDraggingWidget(WidgetCell v) {
        // Get the widget preview as the drag representation
        WidgetImageView image = v.getWidgetView();

        // If the ImageView doesn't have a drawable yet, the widget preview hasn't been loaded and
        // we abort the drag.
        if (image.getBitmap() == null) {
            return false;
        }

        int[] loc = new int[2];
        mLauncher.getDragLayer().getLocationInDragLayer(image, loc);

        new PendingItemDragHelper(v).startDrag(
                image.getBitmapBounds(), image.getBitmap().getWidth(), image.getWidth(),
                new Point(loc[0], loc[1]), this, new DragOptions());
        close(true);
        return true;
    }

    //
    // Drag related handling methods that implement {@link DragSource} interface.
    //

    @Override
    public void onDropCompleted(View target, DragObject d, boolean success) { }


    protected void onCloseComplete() {
        super.onCloseComplete();
        clearNavBarColor();
    }

    protected void clearNavBarColor() {
        mLauncher.getSystemUiController().updateUiState(
                SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET, 0);
    }

    protected void setupNavBarColor() {
        boolean isSheetDark = Themes.getAttrBoolean(mLauncher, R.attr.isMainColorDark);
        mLauncher.getSystemUiController().updateUiState(
                SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET,
                isSheetDark ? SystemUiController.FLAG_DARK_NAV : SystemUiController.FLAG_LIGHT_NAV);
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent) {
        targetParent.containerType = ContainerType.WIDGETS;
        targetParent.cardinality = getElementsRowCount();
    }

    @Override
    public final void logActionCommand(int command) {
        Target target = newContainerTarget(ContainerType.WIDGETS);
        target.cardinality = getElementsRowCount();
        mLauncher.getUserEventDispatcher().logActionCommand(command, target);
    }

    protected abstract int getElementsRowCount();

}
