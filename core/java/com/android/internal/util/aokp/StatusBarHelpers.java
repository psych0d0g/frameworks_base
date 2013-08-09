/*
 * Copyright (C) 2013 Android Open Kang Project
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

package com.android.internal.util.aokp;

import com.android.internal.R;

import android.content.Context;
import android.util.TypedValue;
import android.util.Log;

public class StatusBarHelpers {

    private StatusBarHelpers() {
    }

    public static int pixelsToSp(Context c, Float px) {
        float scaledDensity = c.getResources().getDisplayMetrics().scaledDensity;
        return (int) (px/scaledDensity);
    }

    public static int getIconWidth(Context c, int fontsize) {

        int toppadding = c.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_icon_top_padding);
        int bottompadding = c.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_icon_bottom_padding);
        int padding = c.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_padding);

        int naturalBarHeight;
        if (fontsize == -1) { // No custom Font Size - so obey @dimen
            naturalBarHeight = c.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height);
        } else {
            float scale = c.getResources().getDisplayMetrics().density;
            // Convert the dps to pixels, based on density scale
            float fontSizepx = (int) (fontsize * scale + 0.5f);
            naturalBarHeight = (int) (fontSizepx + padding);
        }
        
        int newIconSize = naturalBarHeight - (toppadding + bottompadding);
        Log.d("maxwen", "newIconSize="+newIconSize);

        return newIconSize;
    }
    
    public static float getStatusbarHeight(Context c, int fontsize) {
        float statusBarHeight;
        if (fontsize == -1) { // No custom Font Size - so obey @dimen
            statusBarHeight = c.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height);
        } else { // Custom Font size, so let's adjust Statusbar Height
            float scale = c.getResources().getDisplayMetrics().density;
            // Convert the dps to pixels, based on density scale
            float fontSizepx = (fontsize * scale + 0.5f);

            int padding = c.getResources().getDimensionPixelSize(
                    com.android.internal.R.dimen.status_bar_padding);
            statusBarHeight = fontSizepx + padding;
            // This gives the StatusBar room for the Font, plus a little padding.
            
            //Log.d("maxwen", "statusBarHeight="+statusBarHeight+ " fontsize="+fontsize + " fontSizepx="+fontSizepx + " padding="+padding);
        }

        return statusBarHeight;
    }
    
}
