/*
 * Copyright (C) 2026
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

package com.android.launcher3.allapps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.view.View;
import android.graphics.Rect;

import androidx.recyclerview.widget.RecyclerView;

import app.lawnchair.theme.color.tokens.ColorTokens;

import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.views.ActivityContext;

/**
 * Draws the alphabetical section letter in the left gutter without consuming adapter space,
 * so app icons start immediately to the right of the gutter.
 */
public class AllAppsSectionGutterDecoration extends RecyclerView.ItemDecoration {

    private final ActivityContext mActivityContext;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public AllAppsSectionGutterDecoration(ActivityContext activityContext) {
        mActivityContext = activityContext;
        mPaint.setColor(ColorTokens.ColorAccent.resolveColor((Context) activityContext));
        mPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
        if (!(parent instanceof AllAppsRecyclerView rv)) return;
        AlphabeticalAppsList<?> apps = rv.getApps();
        if (apps == null) return;

        int gutter = getGutterWidthPx();
        if (gutter <= 0) return;

        c.save();
        c.clipRect(
                0,
                parent.getPaddingTop(),
                parent.getWidth(),
                parent.getHeight() - parent.getPaddingBottom()
        );

        // Center of the gutter column (RecyclerView is padded left by gutter width).
        float x = parent.getPaddingLeft() - gutter / 2f;

        // Size based on drawer icon size (already respects drawer icon size setting).
        float iconPx = mActivityContext.getDeviceProfile().allAppsIconSizePx;
        mPaint.setTextSize(iconPx * 0.65f);

        Paint.FontMetrics fm = mPaint.getFontMetrics();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos == RecyclerView.NO_POSITION) continue;

            BaseAllAppsAdapter.AdapterItem item =
                    (BaseAllAppsAdapter.AdapterItem) apps.getAdapterItems().get(pos);
            if (item.viewType != BaseAllAppsAdapter.VIEW_TYPE_ICON) continue;
            if (!(item.itemInfo instanceof AppInfo)) continue;
            AppInfo info = (AppInfo) item.itemInfo;

            String section = getSectionKey(info);
            if (TextUtils.isEmpty(section)) continue;

            // Only draw for the first item of the section.
            if (pos > 0) {
                BaseAllAppsAdapter.AdapterItem prev = apps.getAdapterItems().get(pos - 1);
                if (BaseAllAppsAdapter.isIconViewType(prev.viewType)
                        && prev.itemInfo instanceof AppInfo
                        && TextUtils.equals(getSectionKey((AppInfo) prev.itemInfo), section)) {
                    continue;
                }
            }

            String label = section.length() > 1 ? section.substring(0, 1) : section;

            int iconSize = mActivityContext.getDeviceProfile().allAppsIconSizePx;
            float centerY = child.getTop() + child.getPaddingTop() + iconSize / 2f;
            float baseline = centerY - (fm.ascent + fm.descent) / 2f;

            c.drawText(label, x, baseline, mPaint);
        }
        c.restore();
    }


    private int getGutterWidthPx() {
        return Math.round(mActivityContext.getDeviceProfile().allAppsIconSizePx * 0.8f);
    }

    private int dp(int v) {
        float d = ((Context) mActivityContext).getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }

    private static String getSectionKey(AppInfo info) {
        CharSequence titleCs = info.title;
        String label = titleCs == null ? "" : titleCs.toString().trim();
        if (label.isEmpty()) return "#";
        char c = Character.toUpperCase(label.charAt(0));
        return (c >= 'A' && c <= 'Z') ? String.valueOf(c) : "#";
    }
}

