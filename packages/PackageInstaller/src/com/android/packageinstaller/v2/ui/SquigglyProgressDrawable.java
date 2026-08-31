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

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/**
 * A determinate/indeterminate progress bar rendered as an animated squiggly wave.
 */
public class SquigglyProgressDrawable extends Drawable {

    private final Paint mWavePaint = new Paint(Paint.ANTI_ALIAS_FLAG) {
        {
            setStyle(Paint.Style.STROKE);
            setStrokeWidth(9f);
            setStrokeCap(Paint.Cap.ROUND);
            setColor(0xFFFFFFFF);
        }
    };

    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {
        {
            setStyle(Paint.Style.STROKE);
            setStrokeWidth(6f);
            setStrokeCap(Paint.Cap.ROUND);
            setColor(0x4DFFFFFF);
        }
    };

    private int mWaveColor = 0xFFFFFFFF;

    private final Path mPath = new Path();

    private float mProgress = 0f;
    private boolean mIndeterminate = false;
    private boolean mAnimateWave = true;
    private float mPhase = 0f;

    /** Progress in the range [0, 1]. Ignored while indeterminate. */
    public synchronized void setProgress(float progress) {
        mProgress = Math.max(0f, Math.min(1f, progress));
        invalidateSelf();
    }

    public synchronized float getProgress() {
        return mProgress;
    }

    /** When true, the wave spans the full width and keeps animating. */
    public synchronized void setIndeterminate(boolean indeterminate) {
        mIndeterminate = indeterminate;
        invalidateSelf();
    }

    public synchronized boolean isIndeterminate() {
        return mIndeterminate;
    }

    public synchronized void setAnimateWave(boolean animateWave) {
        mAnimateWave = animateWave;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        final android.graphics.Rect bounds = getBounds();
        final float width = bounds.width();
        final float centerY = bounds.centerY();

        if (width <= 0) return;

        final boolean indeterminate;
        final float progress;
        synchronized (this) {
            indeterminate = mIndeterminate;
            progress = mProgress;
        }

        final float activeWidth = indeterminate ? width : width * progress;

        final float waveLength = 110f;
        final float waveHeight = 10f;
        final float rampDistance = 45f;
        final float halfStroke = mWavePaint.getStrokeWidth() / 2f;
        final float startX = Math.min(halfStroke, activeWidth);
        final float endX = Math.max(startX, activeWidth - halfStroke);

        if (activeWidth > 0) {
            mPath.reset();

            mPath.moveTo(startX, centerY);
            for (float x = startX + 2f; x <= endX; x += 2f) {
                final float startDamping = Math.min(1f, (x - startX) / rampDistance);
                final float endDamping = Math.min(1f, (endX - x) / rampDistance);
                final float currentAmplitude = waveHeight * Math.min(startDamping, endDamping);

                final float y;
                if (mAnimateWave) {
                    final float sin = (float) Math.sin(
                            (x - mPhase) / waveLength * 2 * Math.PI);
                    y = centerY + sin * currentAmplitude;
                } else {
                    y = centerY;
                }
                mPath.lineTo(x, y);
            }
            canvas.drawPath(mPath, mWavePaint);
        }

        if (activeWidth < width) {
            final float trackStartX = Math.min(endX, width);
            final float trackEndX = Math.max(trackStartX,
                    width - mTrackPaint.getStrokeWidth() / 2f);
            canvas.drawLine(trackStartX, centerY, trackEndX, centerY, mTrackPaint);
        }

        if (mAnimateWave) {
            mPhase += 0.35f;
            invalidateSelf();
        }
    }

    public void setWaveColor(int color) {
        mWaveColor = color;
        mWavePaint.setColor(mWaveColor);
        mTrackPaint.setColor(mWaveColor);
        mTrackPaint.setAlpha(80);
        invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
        mWavePaint.setAlpha(alpha);
        mTrackPaint.setAlpha(alpha / 3);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mWavePaint.setColorFilter(colorFilter);
    }

    @Override
    @Deprecated
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return 100;
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(WAVE_HEIGHT * 2f + mWavePaint.getStrokeWidth());
    }

    private static final float WAVE_HEIGHT = 10f;
}
