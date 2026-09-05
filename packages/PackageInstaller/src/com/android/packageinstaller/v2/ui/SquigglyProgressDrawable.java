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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.Nullable;

/**
 * A Material 3 Expressive "wavy" linear progress indicator, rendered as an animated
 * sinusoidal stroke instead of a plain bar.
 *
 * <ul>
 *   <li><b>Determinate</b> ({@link #setProgress(float)}) &mdash; the wave fills up to the
 *       current progress; its amplitude ramps down near the leading edge, and the remainder
 *       of the track is drawn as a flat line. Mirrors the determinate variant of Compose's
 *       {@code LinearWavyProgressIndicator}.</li>
 *   <li><b>Indeterminate</b> ({@link #setIndeterminate(boolean)}) &mdash; no filled/track
 *       split, since there's no known completion point. Two motion styles are available via
 *       {@link #setIndeterminateStyle(int)}:
 *       <ul>
 *         <li>{@link #INDETERMINATE_STYLE_SCROLL} (default) &mdash; a full-amplitude wave
 *             spans the entire width and scrolls continuously, amplitude constant at every x
 *             (no ramping at the edges) &mdash; matching
 *             {@code LinearWavyProgressIndicator}'s indeterminate default.</li>
 *         <li>{@link #INDETERMINATE_STYLE_GROW} &mdash; a single wavy segment grows from
 *             nothing to the full width, then recedes from the left until nothing's left, on
 *             a repeating cycle &mdash; the classic two-phase "growing bar" motion, just wavy.
 *             The not-yet-reached (or already-receded) portion is drawn as a flat track.</li>
 *         <li>{@link #INDETERMINATE_STYLE_DASH} &mdash; a fixed split that never moves: the
 *             wave scrolls continuously across most of the width (see
 *             {@link #setFixedDashLength}), and the remainder is a permanently flat dash.
 *             Purely decorative — doesn't depend on any real progress value, unlike
 *             determinate mode.</li>
 *       </ul></li>
 *   <li><b>Resolved</b> ({@link #resolve(Runnable)}) &mdash; a transient state, entered from
 *       either of the above, where the wave's amplitude smoothly decays to zero so it settles
 *       into a solid, straight line: a "the process just finished" signal, instead of the
 *       indicator disappearing abruptly mid-motion. {@link #unresolve()} reverses it.</li>
 * </ul>
 *
 * Wave motion is driven by a single infinite {@link ValueAnimator} advancing at a constant
 * {@code waveSpeed} (one full wavelength per second by default, matching Compose's
 * {@code waveSpeed: Dp = wavelength} default), so the animation is frame-rate independent:
 * it looks and moves identically on a 60Hz and a 120Hz display.
 */
public class SquigglyProgressDrawable extends Drawable implements Animatable {

    /** Full-amplitude wave scrolling edge to edge. See class javadoc. */
    public static final int INDETERMINATE_STYLE_SCROLL = 0;
    /** A wavy segment growing in then receding, on a repeat cycle. See class javadoc. */
    public static final int INDETERMINATE_STYLE_GROW = 1;
    /** A fixed-length static flat dash always at the trailing edge. See class javadoc. */
    public static final int INDETERMINATE_STYLE_DASH = 2;

    private static final float DEFAULT_WAVELENGTH_DP = 40f;
    private static final float DEFAULT_AMPLITUDE_DP = 3f;
    private static final float DEFAULT_STROKE_WIDTH_DP = 4f;
    private static final float DEFAULT_TRACK_STROKE_WIDTH_DP = 4f;
    private static final float DEFAULT_RAMP_DISTANCE_DP = 24f;
    // Default length of the permanent flat dash for INDETERMINATE_STYLE_DASH.
    private static final float DEFAULT_FIXED_DASH_LENGTH_DP = 24f;
    // Default radius of the round "stop dot" drawn at the trailing edge for
    // INDETERMINATE_STYLE_DASH, and the gap left between it and the flat dash before it.
    // Radius defaults to half of DEFAULT_TRACK_STROKE_WIDTH_DP, so the dot's diameter matches
    // the track's own stroke width instead of bulging out bigger than the bar. Gap defaults
    // to 0 so the dot sits flush against the dash's end — embedded right at the bar's own
    // tip, instead of floating separately past it.
    private static final float DEFAULT_STOP_DOT_RADIUS_DP = DEFAULT_TRACK_STROKE_WIDTH_DP / 2f;
    private static final float DEFAULT_STOP_DOT_GAP_DP = 0f;
    // Sample the sine curve every 2dp of screen space: smooth enough, cheap enough to
    // rebuild every frame.
    private static final float PATH_SEGMENT_DP = 2f;
    // Duration/easing for the wave-to-line "resolve" transition. 350ms with a standard
    // decelerate curve reads as a deliberate settle rather than a snap.
    private static final long RESOLVE_DURATION_MS = 350L;
    private static final TimeInterpolator RESOLVE_INTERPOLATOR =
            new PathInterpolator(0.2f, 0f, 0f, 1f);
    // INDETERMINATE_STYLE_GROW timing: how long one grow-then-recede cycle takes, and what
    // fraction of that cycle is spent growing (the rest is spent receding).
    private static final long GROW_CYCLE_DURATION_MS = 1800L;
    private static final float GROW_PHASE_FRACTION = 0.7f;
    private static final TimeInterpolator GROW_EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final TimeInterpolator RECEDE_EASE = new PathInterpolator(0.4f, 0f, 0.6f, 1f);

    private final Paint mWavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStopDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final float mDensity;
    private final float mRampDistancePx;
    private final float mPathSegmentPx;
    private final ValueAnimator mAnimator;

    private float mWaveLengthPx;
    private float mAmplitudePx;
    private float mWaveSpeedPxPerSec;
    private float mFixedDashLengthPx;
    private float mStopDotRadiusPx;
    private float mStopDotGapPx;

    private float mProgress = 0f;
    private boolean mIndeterminate = false;
    private int mIndeterminateStyle = INDETERMINATE_STYLE_SCROLL;
    private boolean mAnimateWave = true;
    private volatile float mPhasePx = 0f;

    // Only meaningful for INDETERMINATE_STYLE_GROW: fraction [0,1] of the width the growing
    // wave currently reaches (right bound) and how far the trailing edge has receded from the
    // left (left bound). Driven by mGrowAnimator.
    private volatile float mHeadFraction = 1f;
    private volatile float mTailFraction = 0f;
    private final ValueAnimator mGrowAnimator;

    // 1f = normal wave amplitude, 0f = fully resolved (flat line). Only ever touched from the
    // main thread (it's driven by mResolveAnimator), but kept behind the same monitor as the
    // other render-affecting fields for consistency with how setProgress() etc. are guarded.
    private float mAmplitudeScale = 1f;
    @Nullable
    private ValueAnimator mResolveAnimator;

    public SquigglyProgressDrawable(Context context) {
        this(context.getResources().getDisplayMetrics().density);
    }

    public SquigglyProgressDrawable(float density) {
        mDensity = density;

        mWaveLengthPx = dp(DEFAULT_WAVELENGTH_DP);
        mAmplitudePx = dp(DEFAULT_AMPLITUDE_DP);
        mWaveSpeedPxPerSec = mWaveLengthPx; // one wavelength per second, per M3 default
        mRampDistancePx = dp(DEFAULT_RAMP_DISTANCE_DP);
        mPathSegmentPx = dp(PATH_SEGMENT_DP);
        mFixedDashLengthPx = dp(DEFAULT_FIXED_DASH_LENGTH_DP);
        mStopDotRadiusPx = dp(DEFAULT_STOP_DOT_RADIUS_DP);
        mStopDotGapPx = dp(DEFAULT_STOP_DOT_GAP_DP);

        mWavePaint.setStyle(Paint.Style.STROKE);
        mWavePaint.setStrokeWidth(dp(DEFAULT_STROKE_WIDTH_DP));
        mWavePaint.setStrokeCap(Paint.Cap.ROUND);
        mWavePaint.setStrokeJoin(Paint.Join.ROUND);
        mWavePaint.setColor(0xFFFFFFFF);

        mTrackPaint.setStyle(Paint.Style.STROKE);
        mTrackPaint.setStrokeWidth(dp(DEFAULT_TRACK_STROKE_WIDTH_DP));
        mTrackPaint.setStrokeCap(Paint.Cap.ROUND);
        mTrackPaint.setColor(0x4DFFFFFF);

        // Solid round "stop dot" marking the trailing edge in INDETERMINATE_STYLE_DASH —
        // a separate filled circle instead of relying on a stroke's round cap, so the end
        // reads as a deliberate, clearly rounded full-stop rather than the line trailing off.
        mStopDotPaint.setStyle(Paint.Style.FILL);
        mStopDotPaint.setColor(0xFFFFFFFF);

        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mAnimator.setRepeatMode(ValueAnimator.RESTART);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.addUpdateListener(animation -> {
            mPhasePx = (float) animation.getAnimatedValue() * mWaveLengthPx;
            invalidateSelf();
        });
        restartAnimatorDuration();

        mGrowAnimator = ValueAnimator.ofFloat(0f, 1f);
        mGrowAnimator.setDuration(GROW_CYCLE_DURATION_MS);
        mGrowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mGrowAnimator.setRepeatMode(ValueAnimator.RESTART);
        mGrowAnimator.setInterpolator(new LinearInterpolator());
        mGrowAnimator.addUpdateListener(animation -> {
            float f = (float) animation.getAnimatedValue();
            if (f <= GROW_PHASE_FRACTION) {
                mHeadFraction = GROW_EASE.getInterpolation(f / GROW_PHASE_FRACTION);
                mTailFraction = 0f;
            } else {
                mHeadFraction = 1f;
                mTailFraction = RECEDE_EASE.getInterpolation(
                        (f - GROW_PHASE_FRACTION) / (1f - GROW_PHASE_FRACTION));
            }
            invalidateSelf();
        });

        updateAnimatorState();
    }

    private float dp(float value) {
        return value * mDensity;
    }

    /** Progress in the range [0, 1]. Ignored while indeterminate. */
    public synchronized void setProgress(float progress) {
        mProgress = Math.max(0f, Math.min(1f, progress));
        invalidateSelf();
    }

    public synchronized float getProgress() {
        return mProgress;
    }

    /** When true, renders using whichever style {@link #setIndeterminateStyle} selects. */
    public synchronized void setIndeterminate(boolean indeterminate) {
        if (mIndeterminate == indeterminate) {
            return;
        }
        mIndeterminate = indeterminate;
        updateAnimatorState();
        invalidateSelf();
    }

    public synchronized boolean isIndeterminate() {
        return mIndeterminate;
    }

    /**
     * Selects the indeterminate motion style: {@link #INDETERMINATE_STYLE_SCROLL} (default) or
     * {@link #INDETERMINATE_STYLE_GROW}. No effect while in determinate mode.
     */
    public void setIndeterminateStyle(int style) {
        synchronized (this) {
            if (mIndeterminateStyle == style) {
                return;
            }
            mIndeterminateStyle = style;
            if (style != INDETERMINATE_STYLE_GROW) {
                // Reset so a later switch back to GROW starts its cycle from scratch instead
                // of wherever the animator happened to leave off.
                mHeadFraction = 1f;
                mTailFraction = 0f;
            }
        }
        updateAnimatorState();
        invalidateSelf();
    }

    public synchronized int getIndeterminateStyle() {
        return mIndeterminateStyle;
    }

    /** When false, the stroke is drawn as a flat line instead of a wave. */
    public void setAnimateWave(boolean animateWave) {
        synchronized (this) {
            if (mAnimateWave == animateWave) {
                return;
            }
            mAnimateWave = animateWave;
        }
        updateAnimatorState();
        invalidateSelf();
    }

    /**
     * Smoothly flattens the wave into a solid line: use this to signal that the process this
     * indicator represents has reached a terminal state (success, failure, or cancellation),
     * instead of just hiding the indicator abruptly mid-wave. Works from both indeterminate
     * and determinate mode.
     *
     * <p>Safe to call again while a resolve/unresolve transition is already running &mdash; the
     * in-flight one is cancelled and replaced.
     *
     * @param onResolved optional callback invoked on the main thread once the wave has fully
     *                   flattened; commonly used to hide the view only after it settles.
     */
    public void resolve(@Nullable Runnable onResolved) {
        animateAmplitudeScale(0f, onResolved);
    }

    /** Reverses {@link #resolve}, restoring the normal wave amplitude. */
    public void unresolve() {
        animateAmplitudeScale(1f, null);
    }

    /**
     * Plays the "installation finished" transition: snaps out of indeterminate mode into a
     * full determinate bar ({@code progress = 1f}) and then flattens the wave into a solid,
     * full-width line — so completion reads as "the bar filled up and settled", not just an
     * indeterminate wave arbitrarily going still wherever it happened to be. Works whether
     * called from indeterminate or determinate mode, and whatever the progress was.
     *
     * @param onFinished optional callback invoked once the line has fully settled; commonly
     *                   used to hide the view only after it settles.
     */
    public void finish(@Nullable Runnable onFinished) {
        setIndeterminate(false);
        setProgress(1f);
        resolve(onFinished);
    }

    /** True once the wave has fully settled into a flat line via {@link #resolve}. */
    public boolean isResolved() {
        return mAmplitudeScale <= 0f && (mResolveAnimator == null || !mResolveAnimator.isRunning());
    }

    private void animateAmplitudeScale(float target, @Nullable Runnable onEnd) {
        if (mResolveAnimator != null) {
            mResolveAnimator.cancel();
        }
        // The transition itself needs the wave-scroll animator running even if we were
        // already flat, otherwise a resolve->unresolve right after a resolve wouldn't scroll.
        synchronized (this) {
            mAnimateWave = true;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(mAmplitudeScale, target);
        animator.setDuration(RESOLVE_DURATION_MS);
        animator.setInterpolator(RESOLVE_INTERPOLATOR);
        animator.addUpdateListener(a -> {
            mAmplitudeScale = (float) a.getAnimatedValue();
            invalidateSelf();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAmplitudeScale = target;
                updateAnimatorState();
                if (onEnd != null) {
                    onEnd.run();
                }
            }
        });
        mResolveAnimator = animator;
        updateAnimatorState();
        animator.start();
    }

    /** Horizontal wavelength of the wave, in dp. */
    public void setWaveLength(float wavelengthDp) {
        mWaveLengthPx = Math.max(1f, dp(wavelengthDp));
        restartAnimatorDuration();
        invalidateSelf();
    }

    /** Peak-to-center wave amplitude, in dp. */
    public void setAmplitude(float amplitudeDp) {
        mAmplitudePx = Math.max(0f, dp(amplitudeDp));
        invalidateSelf();
    }

    /** How fast the wave scrolls, in dp per second. */
    public void setWaveSpeed(float dpPerSecond) {
        mWaveSpeedPxPerSec = Math.max(1f, dp(dpPerSecond));
        restartAnimatorDuration();
    }

    /** Length of the permanent flat dash for {@link #INDETERMINATE_STYLE_DASH}, in dp. */
    public void setFixedDashLength(float dashLengthDp) {
        mFixedDashLengthPx = Math.max(0f, dp(dashLengthDp));
        invalidateSelf();
    }

    /**
     * Radius of the round "stop dot" drawn at the trailing edge for
     * {@link #INDETERMINATE_STYLE_DASH}, in dp. Defaults to half the track's stroke width, so
     * the dot's diameter matches the bar's own thickness. Set to 0 to hide the dot entirely
     * and fall back to a plain flat dash.
     */
    public void setStopDotRadius(float radiusDp) {
        mStopDotRadiusPx = Math.max(0f, dp(radiusDp));
        invalidateSelf();
    }

    /**
     * Gap left between the end of the flat dash and the stop dot for
     * {@link #INDETERMINATE_STYLE_DASH}, in dp. Default 0dp (dot sits flush against the
     * dash, embedded at the bar's own tip instead of floating past it).
     */
    public void setStopDotGap(float gapDp) {
        mStopDotGapPx = Math.max(0f, dp(gapDp));
        invalidateSelf();
    }

    private void restartAnimatorDuration() {
        long durationMs = Math.max(16L, Math.round((mWaveLengthPx / mWaveSpeedPxPerSec) * 1000L));
        mAnimator.setDuration(durationMs);
    }

    private void updateAnimatorState() {
        boolean animateWave;
        boolean indeterminate;
        int style;
        synchronized (this) {
            animateWave = mAnimateWave;
            indeterminate = mIndeterminate;
            style = mIndeterminateStyle;
        }
        boolean midResolveTransition = mResolveAnimator != null && mResolveAnimator.isRunning();
        // Once fully flat and settled there's nothing left to scroll — a phase shift on a
        // zero-amplitude line is invisible, so stop ticking and save battery.
        boolean fullyFlat = mAmplitudeScale <= 0f && !midResolveTransition;
        boolean shouldRun = animateWave && isVisible() && !fullyFlat;
        setAnimatorRunning(mAnimator, shouldRun);

        boolean shouldRunGrow = shouldRun && indeterminate && style == INDETERMINATE_STYLE_GROW;
        setAnimatorRunning(mGrowAnimator, shouldRunGrow);
    }

    private static void setAnimatorRunning(ValueAnimator animator, boolean shouldRun) {
        if (shouldRun && !animator.isStarted()) {
            animator.start();
        } else if (!shouldRun && animator.isStarted()) {
            animator.cancel();
        }
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        updateAnimatorState();
        return changed;
    }

    // --- Animatable ---

    @Override
    public void start() {
        setAnimateWave(true);
    }

    @Override
    public void stop() {
        setAnimateWave(false);
    }

    @Override
    public boolean isRunning() {
        return mAnimator.isRunning();
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect bounds = getBounds();
        final float width = bounds.width();
        final float centerY = bounds.exactCenterY();
        if (width <= 0f) {
            return;
        }

        final boolean indeterminate;
        final int indeterminateStyle;
        final float progress;
        final float phase;
        final boolean animate;
        final float amplitudeScale;
        synchronized (this) {
            indeterminate = mIndeterminate;
            indeterminateStyle = mIndeterminateStyle;
            progress = mProgress;
            phase = mPhasePx;
            animate = mAnimateWave;
            amplitudeScale = mAmplitudeScale;
        }

        final float halfWaveStroke = mWavePaint.getStrokeWidth() / 2f;

        if (indeterminate) {
            if (indeterminateStyle == INDETERMINATE_STYLE_GROW) {
                // A single wavy segment grows in from the left, then recedes from the left
                // until nothing's left, repeating. Whatever the segment hasn't reached (or
                // has already receded past) is drawn as a flat track, same as determinate.
                final float rawStartX = mTailFraction * width;
                final float rawEndX = mHeadFraction * width;
                final float waveStartX = Math.max(rawStartX, halfWaveStroke);
                final float waveEndX = Math.min(rawEndX, width - halfWaveStroke);
                if (waveEndX > waveStartX) {
                    drawWave(canvas, waveStartX, waveEndX, centerY, phase, amplitudeScale,
                            /* rampStart= */ true, /* rampEnd= */ true, animate);
                    drawFlatTrackOutsideActiveRegion(canvas, waveStartX, waveEndX, width, centerY);
                } else {
                    // Momentary zero-width segment right at a cycle boundary: nothing to
                    // wave, the whole width is flat track.
                    canvas.drawLine(0f, centerY, width, centerY, mTrackPaint);
                }
                return;
            }
            if (indeterminateStyle == INDETERMINATE_STYLE_DASH) {
                // A fixed-length flat "dash" always sits at the trailing edge, capped with a
                // small round stop dot right at the edge; the wave fills everything before it
                // and just scrolls (no growing/shrinking, unlike GROW).
                final float dashLenPx = Math.min(mFixedDashLengthPx, width);
                final float waveEndX = Math.max(halfWaveStroke, width - dashLenPx);
                final float waveStartX = Math.min(halfWaveStroke, waveEndX);
                drawWave(canvas, waveStartX, waveEndX, centerY, phase, amplitudeScale,
                        /* rampStart= */ false, /* rampEnd= */ true, animate);
                drawFlatDashWithStopDot(canvas, waveEndX, width, centerY);
                return;
            }
            // INDETERMINATE_STYLE_SCROLL: no filled/remaining split, the wave runs edge to
            // edge at full amplitude and just scrolls (amplitude = 1f, no ramping).
            final float startX = Math.min(halfWaveStroke, width);
            final float endX = Math.max(startX, width - halfWaveStroke);
            drawWave(canvas, startX, endX, centerY, phase, amplitudeScale,
                    /* rampStart= */ false, /* rampEnd= */ false, animate);
            return;
        }

        final float activeWidth = width * progress;
        final float startX = Math.min(halfWaveStroke, activeWidth);
        final float endX = Math.max(startX, activeWidth - halfWaveStroke);
        if (activeWidth > 0f) {
            drawWave(canvas, startX, endX, centerY, phase, amplitudeScale,
                    /* rampStart= */ true, /* rampEnd= */ true, animate);
        }
        drawFlatTrackOutsideActiveRegion(canvas, startX, endX, width, centerY);
    }

    /** Draws a flat track line covering whatever part of the width isn't the active wave. */
    private void drawFlatTrackOutsideActiveRegion(Canvas canvas, float startX, float endX,
            float width, float centerY) {
        final float trackHalfStroke = mTrackPaint.getStrokeWidth() / 2f;
        if (startX > trackHalfStroke) {
            canvas.drawLine(0f, centerY, startX, centerY, mTrackPaint);
        }
        if (endX < width - trackHalfStroke) {
            canvas.drawLine(endX, centerY, width, centerY, mTrackPaint);
        }
    }

    /**
     * Draws the flat dash between the end of the wave and the trailing edge for
     * {@link #INDETERMINATE_STYLE_DASH}, capped with a small solid "stop dot" right at the
     * very end of the track. By default the dash runs right up to the dot with no gap, so
     * the dot reads as part of the bar's own tip rather than a separate mark floating past
     * it; {@link #setStopDotGap} can reintroduce a gap if a detached look is wanted instead.
     */
    private void drawFlatDashWithStopDot(Canvas canvas, float dashStartX, float width,
            float centerY) {
        final float dotCenterX = Math.max(dashStartX, width - mStopDotRadiusPx);
        final float dashEndX = Math.max(dashStartX,
                Math.min(dotCenterX - mStopDotRadiusPx - mStopDotGapPx, dotCenterX));
        if (dashEndX > dashStartX) {
            canvas.drawLine(dashStartX, centerY, dashEndX, centerY, mTrackPaint);
        }
        if (mStopDotRadiusPx > 0f) {
            canvas.drawCircle(dotCenterX, centerY, mStopDotRadiusPx, mStopDotPaint);
        }
    }

    /** Draws one continuous stroke from {@code startX} to {@code endX}. */
    private void drawWave(Canvas canvas, float startX, float endX, float centerY, float phase,
            float amplitudeScale, boolean rampStart, boolean rampEnd, boolean animate) {
        if (endX <= startX) {
            canvas.drawPoint(startX, centerY, mWavePaint);
            return;
        }
        mPath.reset();
        boolean first = true;
        float x = startX;
        while (x < endX) {
            float y = computeY(x, startX, endX, centerY, phase, amplitudeScale, rampStart, rampEnd,
                    animate);
            if (first) {
                mPath.moveTo(x, y);
                first = false;
            } else {
                mPath.lineTo(x, y);
            }
            x += mPathSegmentPx;
        }
        // Always close exactly on endX, even if the last step overshot it, so the stroke
        // never falls short of (or beyond) the intended active width.
        mPath.lineTo(endX, computeY(endX, startX, endX, centerY, phase, amplitudeScale, rampStart,
                rampEnd, animate));
        canvas.drawPath(mPath, mWavePaint);
    }

    private float computeY(float x, float startX, float endX, float centerY, float phase,
            float amplitudeScale, boolean rampStart, boolean rampEnd, boolean animate) {
        if (!animate) {
            return centerY;
        }
        float amplitude = mAmplitudePx * amplitudeScale;
        if (rampStart) {
            amplitude *= Math.max(0f, Math.min(1f, (x - startX) / mRampDistancePx));
        }
        if (rampEnd) {
            amplitude *= Math.max(0f, Math.min(1f, (endX - x) / mRampDistancePx));
        }
        float sin = (float) Math.sin((x - phase) / mWaveLengthPx * 2 * Math.PI);
        return centerY + sin * amplitude;
    }

    public void setWaveColor(int color) {
        mWavePaint.setColor(color);
        mTrackPaint.setColor(color);
        mTrackPaint.setAlpha(80);
        mStopDotPaint.setColor(color);
        invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
        mWavePaint.setAlpha(alpha);
        mTrackPaint.setAlpha(alpha / 3);
        mStopDotPaint.setAlpha(alpha);
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
        return Math.round(dp(240f));
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(mAmplitudePx * 2f + mWavePaint.getStrokeWidth());
    }
}
