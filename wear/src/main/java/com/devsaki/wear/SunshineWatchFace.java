/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.devsaki.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mDateTextPaint;
        Paint mLine;
        Paint mTempText;
        Paint mBitmapPaint;
        boolean mAmbient;
        TimeZone timeZone = TimeZone.getDefault();
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                timeZone = TimeZone.getTimeZone(intent.getStringExtra("time-zone"));

            }
        };

        float mXOffset;
        float mYOffset;

        float mXDateOffset;
        float mYDateOffset;


        int mBitmapXOffset;
        int mBitmapYOffset;

        int mBitmapWidth;
        int mBitmapHeight;

        float mLineYOffset;
        float mLineLengthOffset;

        float mTempXOffset;
        float mTempYOffset;

        SunshineBean sunshineBean;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mYDateOffset = resources.getDimension(R.dimen.digital_date_y_offset);
            mTempYOffset = resources.getDimension(R.dimen.digital_temp_y_offset);
            mBitmapYOffset = resources.getDimensionPixelSize(R.dimen.bitmap_y_offset);

            mLineYOffset = resources.getDimension(R.dimen.line_y_offset);
            mLineLengthOffset = resources.getDimension(R.dimen.line_length_offset);

            mBitmapWidth = resources.getDimensionPixelSize(R.dimen.bitmap_width);
            mBitmapHeight = resources.getDimensionPixelSize(R.dimen.bitmap_height);

            mBitmapPaint = new Paint();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.primary));

            int color = resources.getColor(R.color.digital_text);

            mLine = new Paint();
            mLine.setColor(color);

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(color, NORMAL_TYPEFACE);

            mDateTextPaint = new Paint();
            mDateTextPaint = createTextPaint(color, NORMAL_TYPEFACE);

            mTempText = new Paint();
            mTempText = createTextPaint(color, NORMAL_TYPEFACE);

            sunshineBean = new SunshineBean();
            sunshineBean.max = 19;
            sunshineBean.min = 19;

            sunshineBean.bitmap = Utility.loadingBitmap(getResources(), 200, mBitmapWidth, mBitmapHeight);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                timeZone=TimeZone.getDefault();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);

            mXDateOffset = resources.getDimension(isRound
                    ? R.dimen.digital_date_x_offset: R.dimen.digital_date_x_offset);

            mBitmapXOffset = resources.getDimensionPixelSize(isRound
                    ? R.dimen.bitmap_x_offset: R.dimen.bitmap_x_offset);

            mTempXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_temp_x_offset: R.dimen.digital_temp_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float textDateSize = resources.getDimension(isRound
                    ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);
            float textTempSize = resources.getDimension(isRound
                    ? R.dimen.digital_temp_text_size_round : R.dimen.digital_temp_text_size);


            mTextPaint.setTextSize(textSize);
            mDateTextPaint.setTextSize(textDateSize);
            mTempText.setTextSize(textTempSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.

            Calendar mCalendar = Calendar.getInstance();
            mCalendar.setTimeZone(timeZone);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");

            Date time = mCalendar.getTime();
            String text = sdf.format(time);
            canvas.drawText(text, mXOffset, mYOffset, mTextPaint);

            sdf = new SimpleDateFormat("EEE, MMM dd yyyy");
            String dateText = sdf.format(time).toUpperCase();
            canvas.drawText(dateText, mXDateOffset, mYDateOffset, mDateTextPaint);

            canvas.drawLine(bounds.centerX() - mLineLengthOffset/2, mLineYOffset, bounds.centerX() + mLineLengthOffset/2, mLineYOffset, mLine);

            drawTemp(canvas);

        }

        private void drawTemp(Canvas canvas){
            if(sunshineBean!=null){
                String tempText = String.format("%d° %d°", sunshineBean.max, sunshineBean.min);
                canvas.drawText(tempText, mTempXOffset, mTempYOffset, mTempText);

                if(!isInAmbientMode()){
                    canvas.drawBitmap(sunshineBean.bitmap, null, new Rect(mBitmapXOffset, mBitmapYOffset, mBitmapXOffset + mBitmapWidth, mBitmapYOffset + mBitmapHeight), mBitmapPaint);
                }
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }

    private class SunshineBean{

        int max;
        int min;
        Bitmap bitmap;

    }
}
