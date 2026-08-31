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

import com.android.packageinstaller.R;

/**
 * A horizontal progress bar rendered with an animated squiggly wave.
 * Supports both determinate ({@link #setProgress(int)}) and indeterminate
 * ({@link #setIndeterminate(boolean)}) modes.
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

        mProgressDrawable = new SquigglyProgressDrawable();

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
