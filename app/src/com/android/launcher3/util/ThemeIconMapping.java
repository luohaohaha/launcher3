package com.android.launcher3.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import com.android.launcher3.R;

/**
 * Project: launcher3-github<br/>
 * Package: com.android.launcher3<br/>
 * ClassName: ThemeIconMapping<br/>
 * Description: 指定包名映射icon<br/>
 * Date: 2022-12-27 01:45 <br/>
 * <p>
 * Author luohao<br/>
 * Version 1.0<br/>
 * since JDK 1.6<br/>
 * <p>
 */
public class ThemeIconMapping {
    private static final String TAG = ThemeIconMapping.class.getSimpleName();

    private ThemeIconMapping(){

    }

    private static final Map<String, Integer> THEME_ICONS = new HashMap<String, Integer>() {
        {
//            put("com.android.deskclock", R.drawable.icon_theme_clock);
//            put("com.android.calendar", R.drawable.icon_theme_calendar);
//            put("com.android.calculator2", R.drawable.icon_theme_calculator2);
//            put("com.android.gallery3d", R.drawable.icon_theme_gallery3d);
//            put("com.android.settings", R.drawable.icon_theme_settings);
//            put("com.android.messaging", R.drawable.icon_theme_messaging);
//            put("com.android.contacts", R.drawable.icon_theme_contacts);
//            put("com.android.dialer", R.drawable.icon_theme_dialer);
//            put("com.android.email", R.drawable.icon_theme_email);
//            put("com.android.documentsui", R.drawable.icon_theme_documentsui);
//            put("com.android.music", R.drawable.icon_theme_music);
        }
    };

    /**
     * 根据包名获取映射图片
     * @param context 上下文
     * @param packageName 包名
     * @return 如果有映射，返回 {@link  BitmapFactory#decodeResource(Resources, int)} 没有映射返回 null
     */
    public static Bitmap getThemeBitmap(Context context, String packageName) {
        Log.d(TAG, "packageName=" + packageName);
        Integer resId = THEME_ICONS.get(packageName);
        if ( null != resId && resId.intValue() > 0)
            return BitmapFactory.decodeResource(context.getResources(), resId.intValue());
        return null;
    }
}
