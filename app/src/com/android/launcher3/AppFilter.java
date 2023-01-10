package com.android.launcher3;

import android.content.ComponentName;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AppFilter {
    private static List<String> mFilterPackages;
    private static String TAG = "AppFilter";

    public static AppFilter newInstance(Context context) {
        loadFilterPackages(context);
        return Utilities.getOverrideObject(AppFilter.class, context, R.string.app_filter_class);
    }

    public boolean shouldShowApp(ComponentName app) {
        String pkg = app.getPackageName();
        Log.i("AppFilter", pkg);
        return mFilterPackages.isEmpty() ? true : !mFilterPackages.contains(pkg);
    }

    private static void loadFilterPackages(Context context) {
        if (null != mFilterPackages)
            return;
        mFilterPackages = new ArrayList<>();
        try {
            String filterJson = AssetHelper.stream_2String(AssetHelper.asset_getFile(context, "app_default_filter.json"), null);
            if (TextUtils.isEmpty(filterJson)) {
                Log.e(TAG, "filterJson is null");
                return;
            }
            JSONArray filterArray = new JSONArray(filterJson);
            for (int i = 0, count = filterArray.length(); i < count; i++) {
                String pkg = filterArray.optString(i);
                if (TextUtils.isEmpty(pkg))
                    continue;
                mFilterPackages.add(pkg);
            }
            Log.i(TAG, "mFilterPackages == " + mFilterPackages.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
