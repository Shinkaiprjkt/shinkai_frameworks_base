/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.packageinstaller.v2.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.packageinstaller.R;

/**
 * A horizontal progress bar rendered with an animated squiggly wave.
 * Supports both determinate ({@link #setProgress(int)}) and indeterminate
 * ({@link #setIndeterminate(boolean)}) modes.
 *
 * <p>The wave keeps animating (and stops) automatically as the view's visibility changes,
 * since {@link SquigglyProgressDrawable} is set as this view's background and the platform
 * forwards {@link View#onVisibilityAggregated(boolean)} to background drawables already.
 */
public class SquigglyProgressBar extends View {

    private final SquigglyProgressDrawable mProgressDrawable;
    private boolean mIndeterminate = false;

    public SquigglyProgressBar(Context context) {
        this(context, null);
    }

    public SquigglyProgressBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SquigglyProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mProgressDrawable = new SquigglyProgressDrawable(context);

        final int indicatorColor = context.getColor(R.color.primaryColor);
        mProgressDrawable.setWaveColor(indicatorColor);

        setBackground(mProgressDrawable);
    }

    /** Sets the progress in the range [0, 100]. */
    public void setProgress(int progress) {
        mIndeterminate = false;
        mProgressDrawable.setIndeterminate(false);
        mProgressDrawable.setProgress(progress / 100f);
    }

    public int getProgress() {
        return Math.round(mProgressDrawable.getProgress() * 100f);
    }

    public void setIndeterminate(boolean indeterminate) {
        if (mIndeterminate == indeterminate) {
            return;
        }
        mIndeterminate = indeterminate;
        mProgressDrawable.setIndeterminate(indeterminate);
    }

    public boolean isIndeterminate() {
        return mIndeterminate;
    }

    /**
     * Selects the indeterminate motion style: {@link SquigglyProgressDrawable#INDETERMINATE_STYLE_SCROLL}
     * (default, a full-amplitude wave scrolling edge to edge),
     * {@link SquigglyProgressDrawable#INDETERMINATE_STYLE_GROW} (a wavy segment growing in and
     * receding on a repeat cycle), or {@link SquigglyProgressDrawable#INDETERMINATE_STYLE_DASH}
     * (wave scrolls across most of the width, with a fixed-length static flat dash always at
     * the trailing edge — see {@link #setFixedDashLength}). No effect while determinate.
     */
    public void setIndeterminateStyle(int style) {
        mProgressDrawable.setIndeterminateStyle(style);
    }

    /** Length of the permanent dash for {@code INDETERMINATE_STYLE_DASH}, in dp. Default 24dp. */
    public void setFixedDashLength(float dashLengthDp) {
        mProgressDrawable.setFixedDashLength(dashLengthDp);
    }

    /**
     * Smoothly flattens the wave into a solid line, signaling that the process this bar
     * represents has finished (successfully, with an error, or by cancellation). Call this
     * instead of jumping straight to {@code setVisibility(GONE)} so the indicator doesn't
     * vanish mid-wave.
     *
     * @param onResolved optional callback once the wave has fully settled; a common pattern is
     *                   {@code bar.resolve(() -> bar.setVisibility(View.GONE))}.
     */
    public void resolve(@Nullable Runnable onResolved) {
        mProgressDrawable.resolve(onResolved);
    }

    /** Reverses {@link #resolve}, restoring the normal wave amplitude. */
    public void unresolve() {
        mProgressDrawable.unresolve();
    }

    /**
     * Plays the "installation finished" transition — see
     * {@link SquigglyProgressDrawable#finish}. Use this instead of {@link #resolve} when the
     * process actually completed (rather than, say, just being paused).
     */
    public void finish(@Nullable Runnable onFinished) {
        mProgressDrawable.finish(onFinished);
    }

    /** True once the wave has fully settled into a flat line via {@link #resolve}. */
    public boolean isResolved() {
        return mProgressDrawable.isResolved();
    }

    /** Horizontal wavelength of the wave, in dp. Default is 40dp. */
    public void setWaveLength(float wavelengthDp) {
        mProgressDrawable.setWaveLength(wavelengthDp);
    }

    /** Peak-to-center wave amplitude, in dp. Default is 3dp. */
    public void setAmplitude(float amplitudeDp) {
        mProgressDrawable.setAmplitude(amplitudeDp);
    }

    /** How fast the wave scrolls, in dp per second. Default matches one wavelength/second. */
    public void setWaveSpeed(float dpPerSecond) {
        mProgressDrawable.setWaveSpeed(dpPerSecond);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = getPaddingLeft() + getPaddingRight()
                + mProgressDrawable.getIntrinsicWidth();
        int desiredHeight = getPaddingTop() + getPaddingBottom()
                + mProgressDrawable.getIntrinsicHeight();

        setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }
}
