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

package com.android.launcher3.views;

import static android.view.HapticFeedbackConstants.CLOCK_TICK;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.launcher3.FastScrollRecyclerView;
import com.android.launcher3.Utilities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.lawnchair.theme.color.tokens.ColorTokens;

/**
 * Horizontal A–Z index bar intended for All Apps.
 *
 * This is intentionally "structure-only": it just renders section labels and enables jumping.
 */
public class AlphabeticalIndexBar extends View {

    private static final char[] DEFAULT_ALPHABET = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final long RELEASE_ANIMATION_MS = 500;

    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDisabledTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPreviewBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPreviewTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mPreviewRect = new RectF();
    private final Paint.FontMetrics mFontMetrics = new Paint.FontMetrics();

    private final Runnable mEndFastScrollRunnable = this::endFastScroll;

    @Nullable
    private FastScrollRecyclerView mRv;

    private int mActiveIndex = -1;
    private int mLastHapticIndex = -1;
    private boolean mPreviewVisible = false;
    private String mPreviewText = "";

    @Nullable
    private TextView mPreviewView;

    public AlphabeticalIndexBar(Context context) {
        this(context, null);
    }

    public AlphabeticalIndexBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AlphabeticalIndexBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        int accentColor = ColorTokens.ColorAccent.resolveColor(getContext());
        mTextPaint.setColor(accentColor);
        mTextPaint.setAlpha(255);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(16f * getResources().getDisplayMetrics().scaledDensity);

        mDisabledTextPaint.set(mTextPaint);
        mDisabledTextPaint.setAlpha(60);

        mBgPaint.setColor(com.android.launcher3.util.Themes.getAttrColor(getContext(), com.android.launcher3.R.attr.popupColorPrimary));

        mPreviewBgPaint.setColor(ColorTokens.TextColorPrimary.resolveColor(getContext()));
        mPreviewBgPaint.setAlpha(28);

        mPreviewTextPaint.set(mTextPaint);
        mPreviewTextPaint.setAlpha(220);
        setClickable(true);
    }

    public void setRecyclerView(@Nullable FastScrollRecyclerView rv) {
        mRv = rv;
        invalidate();
    }

    public void setPreviewView(@Nullable TextView previewView) {
        mPreviewView = previewView;
        if (mPreviewView != null) {
            mPreviewView.setAlpha(0f);
            mPreviewView.setTextColor(ColorTokens.ColorAccent.resolveColor(getContext()));
        }
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        // Insets are handled by applying bottom *margin* from the parent container.
        return super.onApplyWindowInsets(insets);
    }

    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float bgR = 0f;
        canvas.drawRoundRect(0, 0, w, h, bgR, bgR, mBgPaint);

        Set<Character> enabled = getEnabledLetters();

        mTextPaint.getFontMetrics(mFontMetrics);
        float baseline = (h / 2f) - (mFontMetrics.ascent + mFontMetrics.descent) / 2f;

        float margin = dp(24);
        float availableWidth = w - 2 * margin;
        int count = DEFAULT_ALPHABET.length;
        float cell = availableWidth / count;
        for (int i = 0; i < count; i++) {
            float cx = margin + (i + 0.5f) * cell;
            char ch = DEFAULT_ALPHABET[i];
            boolean isEnabled = enabled.contains(ch);
            Paint p = isEnabled ? mTextPaint : mDisabledTextPaint;
            canvas.drawText(String.valueOf(ch), cx, baseline, p);
        }

        // If an overlay preview view is provided, use that instead of drawing (drawing can be
        // clipped by parents).
        if (mPreviewView == null && mPreviewVisible && mActiveIndex >= 0 && mActiveIndex < count) {
            drawPreview(canvas, cell, h);
        }
    }

    private void drawPreview(Canvas canvas, float cellW, float barH) {
        float cx = (mActiveIndex + 0.5f) * cellW;
        float previewW = Math.max(cellW, dp(28));
        float previewH = dp(34);
        float gap = dp(6);
        float left = cx - previewW / 2f;
        float top = -previewH - gap;
        float right = cx + previewW / 2f;
        float bottom = -gap;
        float r = dp(10);
        mPreviewRect.set(left, top, right, bottom);
        canvas.drawRoundRect(mPreviewRect, r, r, mPreviewBgPaint);

        mPreviewTextPaint.getFontMetrics(mFontMetrics);
        float baseline = (top + bottom) / 2f - (mFontMetrics.ascent + mFontMetrics.descent) / 2f;
        canvas.drawText(mPreviewText, cx, baseline, mPreviewTextPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // If the view is effectively invisible (e.g. app drawer is faded out), do not intercept touches.
        // This prevents the scrollbar from unintentionally eating long presses on the workspace.
        if (getAlpha() < 0.01f) {
            return false;
        }

        if (mRv == null || !mRv.supportsFastScrolling()) {
            return super.onTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                handleTouch((int) event.getX(), /*fromMove*/ event.getActionMasked() == MotionEvent.ACTION_MOVE);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // Keep preview briefly (tap) then hide.
                postDelayed(this::hidePreview, RELEASE_ANIMATION_MS);
                // Also end the “selected” state (fixes the first icon staying slightly enlarged).
                removeCallbacks(mEndFastScrollRunnable);
                postDelayed(mEndFastScrollRunnable, RELEASE_ANIMATION_MS);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void handleTouch(int x, boolean fromMove) {
        int width = getWidth();
        if (width <= 0) return;

        float margin = dp(24);
        float availableWidth = width - 2 * margin;
        float touchX = x - margin;
        if (touchX < 0) touchX = 0;
        if (touchX >= availableWidth) touchX = availableWidth - 0.001f;

        int idx = Utilities.boundToRange(
                (int) ((touchX / availableWidth) * DEFAULT_ALPHABET.length),
                0,
                DEFAULT_ALPHABET.length - 1);
        char target = DEFAULT_ALPHABET[idx];

        Set<Character> enabled = getEnabledLetters();
        if (!enabled.contains(Character.toUpperCase(target))) {
            int snapIdx = idx - 1;
            while (snapIdx >= 0) {
                if (enabled.contains(Character.toUpperCase(DEFAULT_ALPHABET[snapIdx]))) break;
                snapIdx--;
            }
            if (snapIdx < 0) {
                snapIdx = idx + 1;
                while (snapIdx < DEFAULT_ALPHABET.length) {
                    if (enabled.contains(Character.toUpperCase(DEFAULT_ALPHABET[snapIdx]))) break;
                    snapIdx++;
                }
            }
            if (snapIdx >= 0 && snapIdx < DEFAULT_ALPHABET.length) {
                idx = snapIdx;
                target = DEFAULT_ALPHABET[idx];
            } else {
                return;
            }
        }

        int sectionIndex = resolveSectionIndex(target, mRv.getFastScrollSectionNames());
        if (sectionIndex < 0) return;

        setActiveIndex(idx, String.valueOf(target), /*haptic*/ idx != mLastHapticIndex);

        // scrollToPositionAtProgress maps touchFraction -> section index, using (int)(fraction*count)
        // so use the center of the bucket to be stable.
        int sectionCount = Math.max(1, mRv.getFastScrollSectionNames().size());
        float touchFraction = (sectionIndex + 0.5f) / sectionCount;
        mRv.scrollToPositionAtProgress(touchFraction);

        // When actively dragging, don’t immediately end fast scroll; allow scrubbing.
        if (!fromMove) {
            removeCallbacks(mEndFastScrollRunnable);
            postDelayed(mEndFastScrollRunnable, RELEASE_ANIMATION_MS);
        }
    }

    private void setActiveIndex(int idx, String previewText, boolean haptic) {
        if (mActiveIndex != idx) {
            mActiveIndex = idx;
            mPreviewText = previewText;
            mPreviewVisible = true;
            updatePreviewOverlay();
        } else if (!previewText.equals(mPreviewText) || !mPreviewVisible) {
            mPreviewText = previewText;
            mPreviewVisible = true;
            updatePreviewOverlay();
        }
        if (haptic) {
            mLastHapticIndex = idx;
            performHapticFeedback(CLOCK_TICK);
        }
    }

    private void hidePreview() {
        if (!mPreviewVisible) return;
        mPreviewVisible = false;
        updatePreviewOverlay();
    }

    private void endFastScroll() {
        if (mRv != null) {
            mRv.onFastScrollCompleted();
        }
    }

    private void updatePreviewOverlay() {
        if (mPreviewView == null) {
            invalidate();
            return;
        }

        if (!mPreviewVisible || mActiveIndex < 0) {
            if (mPreviewView.getAlpha() > 0f) {
                mPreviewView.animate().cancel();
                mPreviewView.animate().alpha(0f).setDuration(120).start();
            }
            return;
        }

        mPreviewView.setText(mPreviewText);

        float margin = dp(24);
        float availableWidth = getWidth() - 2 * margin;
        int count = DEFAULT_ALPHABET.length;
        float cell = availableWidth / (float) count;
        float cx = margin + (mActiveIndex + 0.5f) * cell;

        // Wait for measure to get correct width.
        if (mPreviewView.getWidth() == 0) {
            mPreviewView.post(this::updatePreviewOverlay);
            return;
        }

        float x = getX() + cx - (mPreviewView.getWidth() / 2f);
        mPreviewView.setX(x);
        if (mPreviewView.getAlpha() < 1f) {
            mPreviewView.animate().cancel();
            mPreviewView.animate().alpha(1f).setDuration(60).start();
        }
    }

    private final Set<Character> mEnabledLetters = new HashSet<>();
    private List<CharSequence> mCachedSections = null;

    private Set<Character> getEnabledLetters() {
        if (mRv == null) return mEnabledLetters;
        List<CharSequence> sections = mRv.getFastScrollSectionNames();
        if (sections.equals(mCachedSections)) return mEnabledLetters;
        
        mCachedSections = sections;
        mEnabledLetters.clear();
        for (CharSequence s : sections) {
            char c = normalizeSectionChar(s);
            if (c != 0) mEnabledLetters.add(c);
        }
        return mEnabledLetters;
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private static int resolveSectionIndex(char targetLetter, List<CharSequence> sectionNames) {
        if (sectionNames == null || sectionNames.isEmpty()) return -1;

        List<Character> normalized = new ArrayList<>(sectionNames.size());
        for (CharSequence s : sectionNames) {
            char c = normalizeSectionChar(s);
            if (c != 0) normalized.add(c);
        }
        if (normalized.isEmpty()) return -1;

        // '#' should always sort before letters.
        if (targetLetter == '#') {
            for (int i = 0; i < normalized.size(); i++) {
                if (normalized.get(i) == '#') return i;
            }
            // No explicit '#': snap to first available section.
            return 0;
        }

        targetLetter = Character.toUpperCase(targetLetter);

        // Find first section >= requested letter.
        for (int i = 0; i < normalized.size(); i++) {
            char cur = normalized.get(i);
            if (cur == '#') continue;
            if (cur >= targetLetter) {
                return i;
            }
        }
        // Otherwise snap to the last available section.
        return normalized.size() - 1;
    }

    private static char normalizeSectionChar(@Nullable CharSequence label) {
        if (label == null) return 0;
        String s = String.valueOf(label).trim();
        if (s.isEmpty()) return 0;
        if ("ⓘ".equals(s)) return 0;
        char c = s.charAt(0);
        if (Character.isLetterOrDigit(c)) {
            return Character.toUpperCase(c);
        }
        if (c == '#') return '#';
        return 0;
    }
}

