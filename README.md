# Launcher3客制化记录（基于Android P）
### 1.去除搜索页(第一屏)

BaseFlags类里面修改
```
public static final boolean QSB_ON_FIRST_SCREEN = false;
```
### 2.将应用移到桌面
LoaderTask类修改
##### (1)新增方法
``` /**
     * 加载所有应用
     */
    private void getAllApplications() {
        final Context context = mApp.getContext();
        ArrayList<Pair<ItemInfo, Object>> installQueue = new ArrayList<>();
        final List<UserHandle> profiles = mUserManager.getUserProfiles();
        for (UserHandle user : profiles) {
            final List<LauncherActivityInfo> apps = mLauncherApps.getActivityList(null, user);
            ArrayList<InstallShortcutReceiver.PendingInstallShortcutInfo> added = new ArrayList<InstallShortcutReceiver.PendingInstallShortcutInfo>();
            synchronized (this) {
                for (LauncherActivityInfo app : apps) {
                    //隐藏不需要显示的应用 隐藏配置文件assets-app_default_filter.json(包名过滤)
                    if(isHideApplication(app.getComponentName().getPackageName())){
                        continue;
                    }
                    InstallShortcutReceiver.PendingInstallShortcutInfo pendingInstallShortcutInfo = new InstallShortcutReceiver.PendingInstallShortcutInfo(app, context);
                    added.add(pendingInstallShortcutInfo);
                    installQueue.add(pendingInstallShortcutInfo.getItemInfo());
                }
            }
            if (!added.isEmpty()) {
                mApp.getModel().addAndBindAddedWorkspaceItems(installQueue);
            }
        }
    }
```
##### (2)修改run方法 
``` // second step
            TraceHelper.partitionSection(TAG, "step 2.1: loading all apps");
            loadAllApps();
            getAllApplications();

            TraceHelper.partitionSection(TAG, "step 2.2: Binding all apps");
            verifyNotStopped();
            mResults.bindAllApps(); 
``` 

BaseModelUpdateTask修改,run方法去掉return

```
public final void run() {
        if (!mModel.isModelLoaded()) {
            if (DEBUG_TASKS) {
                Log.d(TAG, "Ignoring model task since loader is pending=" + this);
            }
            // Loader has not yet run.
//            return;
        }
        execute(mApp, mDataModel, mAllAppsList);
    }
```

PackageUpdatedTask修改
##### (1)新增添加方法
```
public void updateToWorkSpace( Context context, LauncherAppState app , AllAppsList appsList){
            List<Pair<ItemInfo, Object>> installQueue = new ArrayList<>();
            final List<UserHandle> profiles = UserManagerCompat.getInstance(context).getUserProfiles();
            ArrayList<InstallShortcutReceiver.PendingInstallShortcutInfo> added = new ArrayList<>();
            for (UserHandle user : profiles) {
                final List<LauncherActivityInfo> apps = LauncherAppsCompat.getInstance(context).getActivityList(null, user);
                synchronized (this) {
                    for (LauncherActivityInfo info : apps) {for (AppInfo appInfo : appsList.added) {
                        if(info.getComponentName().equals(appInfo.componentName)){
                            InstallShortcutReceiver.PendingInstallShortcutInfo mPendingInstallShortcutInfo =  new InstallShortcutReceiver.PendingInstallShortcutInfo(info,context);
                            added.add(mPendingInstallShortcutInfo);
                            installQueue.add(mPendingInstallShortcutInfo.getItemInfo());
                        }
                    }
                    }
                }
            }
            if (!added.isEmpty()) {
                app.getModel().addAndBindAddedWorkspaceItems(installQueue);
            }
        }
```
##### (2)添加到workspace
```
final ArrayList<AppInfo> addedOrModified = new ArrayList<>();
        addedOrModified.addAll(appsList.added);
        updateToWorkSpace(context, app, appsList);
        appsList.added.clear();
        addedOrModified.addAll(appsList.modified);
        appsList.modified.clear();

        final ArrayList<AppInfo> removedApps = new ArrayList<>(appsList.removed);
        appsList.removed.clear();
```
### 3.去除上滑抽屉
AllAppsTransitionController修改

```@Override
    public void setStateWithAnimation(LauncherState toState,
            AnimatorSetBuilder builder, AnimationConfig config) {
        //去除上滑抽屉菜单注释
        /*
        float targetProgress = toState.getVerticalProgress(mLauncher);
        if (Float.compare(mProgress, targetProgress) == 0) {
            setAlphas(toState, config.getPropertySetter(builder));
            // Fail fast
            onProgressAnimationEnd();
            return;
        }

        if (!config.playNonAtomicComponent()) {
            // There is no atomic component for the all apps transition, so just return early.
            return;
        }

        Interpolator interpolator = config.userControlled ? LINEAR : toState == OVERVIEW
                ? builder.getInterpolator(ANIM_OVERVIEW_SCALE, FAST_OUT_SLOW_IN)
                : FAST_OUT_SLOW_IN;
        ObjectAnimator anim =
                ObjectAnimator.ofFloat(this, ALL_APPS_PROGRESS, mProgress, targetProgress);
        anim.setDuration(config.duration);
        anim.setInterpolator(builder.getInterpolator(ANIM_VERTICAL_PROGRESS, interpolator));
        anim.addListener(getProgressAnimatorListener());

        builder.play(anim);

        setAlphas(toState, config.getPropertySetter(builder));*/
    }
```
AllAppsSwipeController修改 canInterceptTouch 返回false

```@Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mTouchDownEvent = ev;
        }
        if (mCurrentAnimation != null) {
            // If we are already animating from a previous state, we can intercept.
            return true;
        }
        if (AbstractFloatingView.getTopOpenView(mLauncher) != null) {
            return false;
        }
        if (!mLauncher.isInState(NORMAL) && !mLauncher.isInState(ALL_APPS)) {
            // Don't listen for the swipe gesture if we are already in some other state.
            return false;
        }
        if (mLauncher.isInState(ALL_APPS) && !mLauncher.getAppsView().shouldContainerScroll(ev)) {
            return false;
        }
        return false;
    }
```

### 4.长按拖动改成取消和卸载
DeleteDropTarget修改

```
private void setTextBasedOnDragSource(ItemInfo item) {
        if (!TextUtils.isEmpty(mText)) {
           /* mText = getResources().getString(item.id != ItemInfo.NO_ID
                    ? R.string.remove_drop_target_label
                    : android.R.string.cancel);*/
                mText = getResources().getString( android.R.string.cancel);
            requestLayout();
        }
    }
```
```
private void setControlTypeBasedOnDragSource(ItemInfo item) {
        mControlType = ControlType.CANCEL_TARGET;
    }
```
DragController修改

```
private void drop(DropTarget dropTarget, Runnable flingAnimation) {
        final int[] coordinates = mCoordinatesTemp;
        mDragObject.x = coordinates[0];
        mDragObject.y = coordinates[1];

        // Move dragging to the final target.
        if (dropTarget != mLastDropTarget) {
            if (mLastDropTarget != null) {
                mLastDropTarget.onDragExit(mDragObject);
            }
            mLastDropTarget = dropTarget;
            if (dropTarget != null) {
                dropTarget.onDragEnter(mDragObject);
            }
        }

        mDragObject.dragComplete = true;
        if (mIsInPreDrag) {
            if (dropTarget != null) {
                dropTarget.onDragExit(mDragObject);
            }
            return;
        }


        // Drop onto the target.
        boolean accepted = false;
        if (dropTarget != null) {
            dropTarget.onDragExit(mDragObject);
            if (dropTarget.acceptDrop(mDragObject)) {
                if (flingAnimation != null) {
                    flingAnimation.run();
                } else {
                    dropTarget.onDrop(mDragObject, mOptions);
                }
                accepted = true;
            
                if (FeatureFlags.REMOVE_DRAWER && dropTarget instanceof DeleteDropTarget &&
                        isNeedCancelDrag(mDragObject.dragInfo)) {
                    cancelDrag();
                }
            }
        }
        final View dropTargetAsView = dropTarget instanceof View ? (View) dropTarget : null;
        mLauncher.getUserEventDispatcher().logDragNDrop(mDragObject, dropTargetAsView);
        dispatchDropComplete(dropTargetAsView, accepted);
    }

    private boolean isNeedCancelDrag(ItemInfo item){
        return (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                item.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER);
    }
```
DeleteDropTarget修改

```
@Override
    public void onAccessibilityDrop(View view, ItemInfo item) {
        // Remove the item from launcher and the db, we can ignore the containerInfo in this call
        // because we already remove the drag view from the folder (if the drag originated from
        // a folder) in Folder.beginDrag()
        if(isCanDrop(item)) {
            mLauncher.removeItem(view, item, true /* deleteFromDb */);
            mLauncher.getWorkspace().stripEmptyScreens();
            mLauncher.getDragLayer()
                    .announceForAccessibility(getContext().getString(R.string.item_removed));
        }
    }

    private boolean isCanDrop(ItemInfo item){
        return !(item.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                item.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER);
    }
```
Launcher修改

```
@Override
    protected void onResume() {
        TraceHelper.beginSection("ON_RESUME");
        super.onResume();
        TraceHelper.partitionSection("ON_RESUME", "superCall");

        mHandler.removeCallbacks(mLogOnDelayedResume);
        Utilities.postAsyncCallback(mHandler, mLogOnDelayedResume);

        setOnResumeCallback(null);
        // Process any items that were added while Launcher was away.
        InstallShortcutReceiver.disableAndFlushInstallQueue(
                InstallShortcutReceiver.FLAG_ACTIVITY_PAUSED, this);

        // Refresh shortcuts if the permission changed.
        mModel.refreshShortcutsIfRequired();
        注释onresume跳动动画
        //DiscoveryBounce.showForHomeIfNeeded(this);
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onResume();
        }
        UiFactory.onLauncherStateOrResumeChanged(this);

        TraceHelper.endSection("ON_RESUME");
    }
```

### 5.去除默认shortcut和hotseat、修改添加顺序
修改xml文件夹下地default_workspace_xxx和dw_phone_hotseat。
修改添加顺序，默认为1，因为我去除了搜索页面，需要从0开始添加，修改AddWorkspaceItemsTask的findSpaceForItem方法

```if (!found) {
            // Search on any of the screens starting from the first screen.
            for (int screen = 0; screen < screenCount; screen++) {
                screenId = workspaceScreens.get(screen);
                if (findNextAvailableIconSpaceInScreen(
                        app, screenItems.get(screenId), cordinates, spanX, spanY)) {
                    // We found a space for it
                    found = true;
                    break;
                }
            }
        }
```
### 6.去除向上小箭头
修改ScrimView的mDragHandle初始化。

```protected Drawable mDragHandle = new ColorDrawable(Color.TRANSPARENT);```

### 7.App显示过滤
配置在assets/app_default_filter下面，根据包名过滤
实现在AppFilter的shouldShowApp方法

### 8.hotseat禁用文件夹
Workspace类的createUserFolderIfNecessary方法

```if(LauncherSettings.Favorites.CONTAINER_HOTSEAT == container) return false;```

---
- [x] 去除搜索，去除第一页
- [x] 应用移到桌面(单层桌面)
- [x] 去除向上小箭头
- [x] app过滤
- [x] hotseat禁用文件夹
- [ ] 桌面应用卸载重排序
- [ ] 修复应用自身生成shortcut导致图标重复
- [ ] 默认壁纸不影响壁纸更换

记录，后续更新...


## Thanks
[大木头_的帖子——android P (9.0) Launcher3 去掉抽屉式,显示所有app][1]

[1]: https://blog.csdn.net/yxdspirit/article/details/84634454
