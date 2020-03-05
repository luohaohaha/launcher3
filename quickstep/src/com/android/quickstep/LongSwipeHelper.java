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
package com.android.quickstep;

import static com.android.launcher3.LauncherAnimUtils.MIN_PROGRESS_TO_ALL_APPS;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.Interpolators.DEACCEL;
import static com.android.quickstep.WindowTransformSwipeHandler.MAX_SWIPE_DURATION;
import static com.android.systemui.shared.recents.utilities.Utilities.getNextFrameNumber;
import static com.android.systemui.shared.recents.utilities.Utilities.getSurface;

import android.animation.ValueAnimator;
import android.view.Surface;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.allapps.DiscoveryBounce;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.util.FlingBlockCheck;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.TransactionCompat;

/**
 * Utility class to handle long swipe from an app.
 * This assumes the presence of Launcher activity as long swipe is not supported on the
 * fallback activity.
 */
public class LongSwipeHelper {

    private static final float SWIPE_DURATION_MULTIPLIER =
            Math.min(1 / MIN_PROGRESS_TO_ALL_APPS, 1 / (1 - MIN_PROGRESS_TO_ALL_APPS));

    private final Launcher mLauncher;
    private final RemoteAnimationTargetSet mTargetSet;

    private float mMaxSwipeDistance = 1;
    private AnimatorPlaybackController mAnimator;
    private FlingBlockCheck mFlingBlockCheck = new FlingBlockCheck();

    LongSwipeHelper(Launcher launcher, RemoteAnimationTargetSet targetSet) {
        mLauncher = launcher;
        mTargetSet = targetSet;
        init();
    }

    private void init() {
        setTargetAlpha(0, true);
        mFlingBlockCheck.blockFling();

        // Init animations
        AllAppsTransitionController controller = mLauncher.getAllAppsController();
        // TODO: Scale it down so that we can reach all-apps in screen space
        mMaxSwipeDistance = Math.max(1, controller.getProgress() * controller.getShiftRange());
        mAnimator = mLauncher.getStateManager()
                .createAnimationToNewWorkspace(ALL_APPS, Math.round(2 * mMaxSwipeDistance));
        mAnimator.dispatchOnStart();
    }

    public void onMove(float displacement) {
        mAnimator.setPlayFraction(displacement / mMaxSwipeDistance);
        mFlingBlockCheck.onEvent();
    }

    public void destroy() {
        // TODO: We can probably also hide the task view
        setTargetAlpha(1, false);

        mLauncher.getStateManager().goToState(OVERVIEW, false);
    }

    public void end(float velocity, boolean isFling, Runnable callback) {
        long duration = MAX_SWIPE_DURATION;

        final float currentFraction = mAnimator.getProgressFraction();
        final boolean toAllApps;
        float endProgress;

        boolean blockedFling = isFling && mFlingBlockCheck.isBlocked();
        if (blockedFling) {
            isFling = false;
        }

        if (!isFling) {
            toAllApps = currentFraction > MIN_PROGRESS_TO_ALL_APPS;
            endProgress = toAllApps ? 1 : 0;

            long expectedDuration = Math.abs(Math.round((endProgress - currentFraction)
                    * MAX_SWIPE_DURATION * SWIPE_DURATION_MULTIPLIER));
            duration = Math.min(MAX_SWIPE_DURATION, expectedDuration);
        } else {
            toAllApps = velocity < 0;
            endProgress = toAllApps ? 1 : 0;

            float minFlingVelocity = mLauncher.getResources()
                    .getDimension(R.dimen.quickstep_fling_min_velocity);
            if (Math.abs(velocity) > minFlingVelocity && mMaxSwipeDistance > 0) {
                float distanceToTravel = (endProgress - currentFraction) * mMaxSwipeDistance;

                // we want the page's snap velocity to approximately match the velocity at
                // which the user flings, so we scale the duration by a value near to the
                // derivative of the scroll interpolator at zero, ie. 2.
                long baseDuration = Math.round(1000 * Math.abs(distanceToTravel / velocity));
                duration = Math.min(MAX_SWIPE_DURATION, 2 * baseDuration);
            }
        }

        if (blockedFling && !toAllApps) {
            duration *= LauncherAnimUtils.blockedFlingDurationFactor(0);
        }
        final boolean finalIsFling = isFling;
        mAnimator.setEndAction(() -> onSwipeAnimationComplete(toAllApps, finalIsFling, callback));
        ValueAnimator animator = mAnimator.getAnimationPlayer();
        animator.setDuration(duration).setInterpolator(DEACCEL);
        animator.setFloatValues(currentFraction, endProgress);
        animator.start();
    }

    private void setTargetAlpha(float alpha, boolean defer) {
        final Surface surface = getSurface(mLauncher.getDragLayer());
        final long frameNumber = defer && surface != null ? getNextFrameNumber(surface) : -1;
        if (defer) {
            if (frameNumber == -1) {
                defer = false;
            } else {
                mLauncher.getDragLayer().invalidate();
            }
        }

        TransactionCompat transaction = new TransactionCompat();
        for (RemoteAnimationTargetCompat app : mTargetSet.apps) {
            if (!(app.isNotInRecents
                    || app.activityType == RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME)) {
                transaction.setAlpha(app.leash, alpha);
                if (defer) {
                    transaction.deferTransactionUntil(app.leash, surface, frameNumber);
                }
            }
        }
        transaction.setEarlyWakeup();
        transaction.apply();
    }

    private void onSwipeAnimationComplete(boolean toAllApps, boolean isFling, Runnable callback) {
        mLauncher.getStateManager().goToState(toAllApps ? ALL_APPS : OVERVIEW, false);
        if (!toAllApps) {
            DiscoveryBounce.@id/layout(mLauncher);
            mLauncher.<RecentsView>getOverviewPanel().setSwipeDownShouldLaunchApp(true);
        }

        mLauncher.getUserEventDispatcher().logStateChangeAction(
                isFling ? Touch.FLING : Touch.SWIPE, Direction.UP,
                ContainerType.NAVBAR, ContainerType.APP,
                toAllApps ? ContainerType.ALLAPPS : ContainerType.TASKSWITCHER,
                0);

        callback.run();
    }
}
