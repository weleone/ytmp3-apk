package com.weleone.ytmp3;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;
import androidx.annotation.AttrRes;

/** 风格切换：默认深色 / 新版安卓配色（Material You 浅色） */
public class ThemeHelper {
    private static final String PREF = "ytmp3_theme";
    private static final String KEY_MATERIAL_YOU = "material_you";

    public static boolean isMaterialYou(Context c) {
        return prefs(c).getBoolean(KEY_MATERIAL_YOU, false);
    }

    /** 在 onCreate() 的 setContentView 之前调用 */
    public static void applyTheme(Activity a) {
        a.setTheme(isMaterialYou(a) ? R.style.Theme_YTMP3_MaterialYou
                : R.style.Theme_YTMP3);
    }

    public static void toggle(Context c) {
        prefs(c).edit().putBoolean(KEY_MATERIAL_YOU, !isMaterialYou(c)).apply();
    }

    /** 取当前主题下的自定义颜色属性 */
    public static int getColor(Context c, @AttrRes int attr) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
