package com.maxwai.nclientv3.utility;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;
import com.maxwai.nclientv3.R;

public final class AccentTheme {
    private AccentTheme() { }
    public static String selected(Context context) {
        return context.getSharedPreferences("Settings", 0).getString("accent_color", "gold");
    }
    public static void apply(Resources.Theme theme, String accent) {
        int style;
        switch (accent) {
            case "jade": style = R.style.Accent_Jade; break;
            case "ocean": style = R.style.Accent_Ocean; break;
            case "amethyst": style = R.style.Accent_Amethyst; break;
            case "rose": style = R.style.Accent_Rose; break;
            case "copper": style = R.style.Accent_Copper; break;
            default: style = R.style.Accent_Gold;
        }
        theme.applyStyle(style, true);
    }
    public static int color(Context context) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, value, true);
        return value.data;
    }
}
