/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.uioverrides;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_VERTICAL_PROGRESS;
import static com.android.launcher3.anim.Interpolators.LINEAR;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.AnimationComponents;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.touch.AbstractStateChangeTouchController;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.TouchInteractionService;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;

/**
 * Touch controller for handling various state transitions in portrait UI.
 */
public class PortraitStatesTouchController extends AbstractStateChangeTouchController {

    private static final String TAG = "PortraitStatesTouchCtrl";

    private InterpolatorWrapper mAllAppsInterpolatorWrapper = new InterpolatorWrapper();

    // If true, we will finish the current animation instantly on second touch.
    private boolean mFinishFastOnSecondTouch;


    public PortraitStatesTouchController(Launcher l) {
        super(l, SwipeDetector.VERTICAL);
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        if (mCurrentAnimation != null) {
            if (mFinishFastOnSecondTouch) {
                // TODO: Animate to finish instead.
                mCurrentAnimation.getAnimationPlayer().end();
            }

            // If we are already animating from a previous state, we can intercept.
            return true;
        }
        if (mLauncher.isInState(ALL_APPS)) {
            // In all-apps only listen if the container cannot scroll itself
            if (!mLauncher.getAppsView().shouldContainerScroll(ev)) {
                return false;
            }
        } else {
            // For all other states, only listen if the event originated below the hotseat height
            DeviceProfile dp = mLauncher.getDeviceProfile();
            int hotseatHeight = dp.hotseatBarSizePx + dp.getInsets().bottom;
            if (ev.getY() < (mLauncher.getDragLayer().getHeight() - hotseatHeight)) {
                return false;
            }
        }
        if (AbstractFloatingView.getTopOpenView(mLauncher) != null) {
            return false;
        }
        return true;
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        if (fromState == ALL_APPS && !isDragTowardPositive) {
            // Should swipe down go to OVERVIEW instead?
            return TouchInteractionService.isConnected() ?
                    mLauncher.getStateManager().getLastState() : NORMAL;
        } else if (fromState == OVERVIEW) {
            return isDragTowardPositive ? ALL_APPS : NORMAL;
        } else if (fromState == NORMAL && isDragTowardPositive) {
            return TouchInteractionService.isConnected() ? OVERVIEW : ALL_APPS;
        }
        return fromState;
    }

    @Override
    protected int getLogContainerTypeForNormalState() {
        return ContainerType.HOTSEAT;
    }

    private AnimatorSetBuilder getNormalToOverviewAnimation() {
        mAllAppsInterpolatorWrapper.baseInterpolator = LINEAR;

        AnimatorSetBuilder builder = new AnimatorSetBuilder();
        builder.setInterpolator(ANIM_VERTICAL_PROGRESS, mAllAppsInterpolatorWrapper);

        return builder;
    }

    @Override
    protected float initCurrentAnimation(@AnimationComponents int animComponents) {
        float range = getShiftRange();
        long maxAccuracy = (long) (2 * range);

        float startVerticalShift = mFromState.getVerticalProgress(mLauncher) * range;
        float endVerticalShift = mToState.getVerticalProgress(mLauncher) * range;

        float totalShift = endVerticalShift - startVerticalShift;

        final AnimatorSetBuilder builder;

        if (mFromState == NORMAL && mToState == OVERVIEW && totalShift != 0) {
            builder = getNormalToOverviewAnimation();
        } else {
            builder = new AnimatorSetBuilder();
        }

        cancelPendingAnim();

        RecentsView recentsView = mLauncher.getOverviewPanel();
        TaskView taskView = (TaskView) recentsView.getChildAt(recentsView.getNextPage());
        if (recentsView.shouldSwipeDownLaunchApp() && mFromState == OVERVIEW && mToState == NORMAL
                && taskView != null) {
            mPendingAnimation = recentsView.createTaskLauncherAnimation(taskView, maxAccuracy);
            mPendingAnimation.anim.setInterpolator(Interpolators.ZOOM_IN);

            Runnable onCancelRunnable = () -> {
                cancelPendingAnim();
                clearState();
            };
            mCurrentAnimation = AnimatorPlaybackController.wrap(mPendingAnimation.anim, maxAccuracy,
                    onCancelRunnable);
            mLauncher.getStateManager().setCurrentUserControlledAnimation(mCurrentAnimation);
        } else {
            mCurrentAnimation = mLauncher.getStateManager()
                    .createAnimationToNewWorkspace(mToState, builder, maxAccuracy, this::clearState,
                            animComponents);
        }

        if (totalShift == 0) {
            totalShift = Math.signum(mFromState.ordinal - mToState.ordinal)
                    * OverviewState.getDefaultSwipeHeight(mLauncher);
        }
        return 1 / totalShift;
    }

    private void cancelPendingAnim() {
        if (mPendingAnimation != null) {
            mPendingAnimation.finish(false, Touch.SWIPE);
            mPendingAnimation = null;
        }
    }

    @Override
    protected void updateSwipeCompleteAnimation(ValueAnimator animator, long expectedDuration,
            LauncherState targetState, float velocity, boolean isFling) {
        super.updateSwipeCompleteAnimation(animator, expectedDuration, targetState,
                velocity, isFling);
        handleFirstSwipeToOverview(animator, expectedDuration, targetState, velocity, isFling);
    }

    private void handleFirstSwipeToOverview(final ValueAnimator animator,
            final long expectedDuration, final LauncherState targetState, final float velocity,
            final boolean isFling) {
        if (mFromState == NORMAL && mToState == OVERVIEW && targetState == OVERVIEW) {
            mFinishFastOnSecondTouch = true;
            if (isFling && expectedDuration != 0) {
                // Update all apps interpolator to add a bit of overshoot starting from currFraction
                final float currFraction = mCurrentAnimation.getProgressFraction();
                mAllAppsInterpolatorWrapper.baseInterpolator = Interpolators.clampToProgress(
                        new OvershootInterpolator(Math.min(Math.abs(velocity), 3f)), currFraction, 1);
                animator.setDuration(Math.min(expectedDuration, ATOMIC_DURATION))
                        .setInterpolator(LINEAR);
            }
        } else {
            mFinishFastOnSecondTouch = false;
        }
    }

    @Override
    protected void onSwipeInteractionCompleted(LauncherState targetState, int logAction) {
        super.onSwipeInteractionCompleted(targetState, logAction);
        if (mStartState == NORMAL && targetState == OVERVIEW) {
            RecentsModel.getInstance(mLauncher).onOverviewShown(true, TAG);
        }
    }

    private static class InterpolatorWrapper implements Interpolator {

        public TimeInterpolator baseInterpolator = LINEAR;

        @Override
        public float getInterpolation(float v) {
            return baseInterpolator.getInterpolation(v);
        }
    }
}
