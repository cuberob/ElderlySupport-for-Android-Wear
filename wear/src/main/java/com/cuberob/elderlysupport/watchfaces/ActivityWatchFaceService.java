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

package com.cuberob.elderlysupport.watchfaces;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.cuberob.WatchConfigKeys;
import com.cuberob.elderlysupport.R;
import com.cuberob.elderlysupport.services.MenuButtonService;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class ActivityWatchFaceService extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    public static final String OVERRIDE_MENU_KEY = "OVERRIDE_MENU";
    public static final String DAILY_STEP_GOAL_KEY = "DAILY_STEP_GOAL";
    public static final String CLOCK_COLOR_KEY = "CLOCK_COLOR";

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements SensorEventListener, GoogleApiClient.ConnectionCallbacks, DataApi.DataListener{
        static final int MSG_UPDATE_TIME = 0;
        public static final String TAG = "ActivityWatchFace";

        /**
         * Handler to update the time periodically in interactive mode.
         */
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;
        Paint mTextPaint, mSmallTextPaint;
        Paint mActivityPaint;

        boolean mAmbient;

        Time mTime;

        float mXOffset;
        float mYOffset;


        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        SensorManager mSensorManager;
        float steps = 0;
        float startSteps = -1;
        int mDailyStepGoal;

        GoogleApiClient mGoogleApiClient;

        private boolean mOverrideMenu = false;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(ActivityWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = ActivityWatchFaceService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.digital_background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mTextPaint.setTextAlign(Paint.Align.CENTER);

            mSmallTextPaint = new Paint();
            mSmallTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mSmallTextPaint.setTextAlign(Paint.Align.CENTER);

            mActivityPaint = new Paint();
            mActivityPaint.setShader(new LinearGradient(0, 0, 0, 290, Color.GREEN, Color.YELLOW, Shader.TileMode.MIRROR));

            mTime = new Time();

            initStepSensor();
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            mDailyStepGoal = sp.getInt(WatchConfigKeys.DAILY_STEP_GOAL, 100);

            /* configure the system UI */
            setWatchFaceStyle(new WatchFaceStyle.Builder(ActivityWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle
                            .BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mGoogleApiClient = new GoogleApiClient.Builder(ActivityWatchFaceService.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .build();

            restoreSettings();
        }

        private void initStepSensor() {
            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            Sensor countSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            if (countSensor != null) {
                mSensorManager.registerListener(this, countSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.e(TAG, "Count sensor not available!");
            }
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            unregisterReceiver();
            mSensorManager.unregisterListener(this);
            disconnectGoogleApi();
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();

                mGoogleApiClient.connect();
            } else {
                unregisterReceiver();
                disconnectGoogleApi();
            }
            handleOverrideMenuService();
            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void disconnectGoogleApi() {
            if(mGoogleApiClient != null && mGoogleApiClient.isConnected()){
                Wearable.DataApi.removeListener(mGoogleApiClient, this);
                mGoogleApiClient.disconnect();
            }
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            ActivityWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            ActivityWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = ActivityWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
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
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            int width = bounds.width();
            int height = bounds.height();

            canvas.drawRect(0, 0, width, height, mBackgroundPaint);

            float percentageComplete =  steps / mDailyStepGoal;
            if(!isInAmbientMode()) {
                displayActivityProgress(canvas, width, height, percentageComplete);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);

            canvas.drawText(text, (width / 2), mYOffset, mTextPaint);
            canvas.drawText("Steps: " + steps, (width / 2), mYOffset + 40, mSmallTextPaint);

        }

        private void displayActivityProgress(Canvas canvas, int width, int height, float percentageComplete) {
            if(percentageComplete < 0.1){
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_1), 0, 0, null);
            }else if(percentageComplete < 0.15){
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_2), 0, 0, null);
            }else if(percentageComplete < 0.2){
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_3), 0, 0, null);
            }else if(percentageComplete < 0.25){
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_4), 0, 0, null);
            }else if(percentageComplete < 0.3){
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_5), 0, 0, null);
            }else if(percentageComplete < 0.35){
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_6), 0, 0, null);
            }else if(percentageComplete < 0.4){
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_7), 0, 0, null);
            }else if(percentageComplete < 0.45){
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_8), 0, 0, null);
            }else if(percentageComplete < 0.5){
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_9), 0, 0, null);
            }else if(percentageComplete < 0.55){
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_10), 0, 0, null);
            }else if(percentageComplete < 0.6){
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_11), 0, 0, null);
            }else if(percentageComplete < 0.65){
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_12), 0, 0, null);
            }else if(percentageComplete < 0.7){
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_13), 0, 0, null);
            }else if(percentageComplete < 0.75){
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_14), 0, 0, null);
            }else if(percentageComplete < 0.8){
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_15), 0, 0, null);
            }else if(percentageComplete < 0.85){
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_16), 0, 0, null);
            }else if(percentageComplete < 0.9){
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_17), 0, 0, null);
            }else if(percentageComplete < 0.95){
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_18), 0, 0, null);
            }else{
                canvas.drawBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.flower_19), 0, 0, null);
            }

            //Commented code: Alternative way to display progress, using a gradient bar that fills the screen vertically (bottom up)
            /*float progress = height - (height * percentageComplete);
            progress = (progress < 0) ? 0 : progress;
            canvas.drawRect(0, progress, width, height, mActivityPaint);*/

            //Commented code: Another alternative way of showing activity using a growing pie chart
            /*float progress = (360 * percentageComplete);
            progress = (progress > 360) ? 360 : progress;
            canvas.drawArc(0, 0, width, height, 90, progress, true, mActivityPaint);*/
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

        @Override
        public void onSensorChanged(SensorEvent event) {
            if(event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                Log.d("ElderWatchFace", "Steps: " + String.valueOf(event.values[0]));
                if(startSteps == -1){ //TODO: Reset steps every night by setting startSteps to -1 at 00:00. Currently resets when changing watchface
                    startSteps = event.values[0];
                }
                steps = event.values[0] - startSteps;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }

        @Override
        public void onConnected(Bundle bundle) {
            Log.d(TAG, "Connected to googleApiClient!");
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.d(TAG, "onDataChanged()");
            try {
                for (DataEvent dataEvent : dataEvents) {
                    if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                        continue;
                    }

                    DataItem dataItem = dataEvent.getDataItem();
                    if (!dataItem.getUri().getPath().equals(WatchConfigKeys.ACTIVITY_WATCH_CONFIG_PATH)) {
                        continue;
                    }

                    DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                    DataMap config = dataMapItem.getDataMap();
                    updateUiForConfigDataMap(config);
                }
            } finally {
                dataEvents.close();
            }
        }

        private void updateUiForConfigDataMap(DataMap config) {
            mDailyStepGoal = config.getInt(WatchConfigKeys.DAILY_STEP_GOAL, 1000);
            mTextPaint.setColor(config.getInt(WatchConfigKeys.CLOCK_COLOR, getResources().getColor(android.R.color.white)));
            mOverrideMenu = config.getBoolean(WatchConfigKeys.OVERRIDE_MENU, false);

            stopService(new Intent(ActivityWatchFaceService.this, MenuButtonService.class)); //Stop service in case it was running and should be disabled
            handleOverrideMenuService();

            storeCurrentSettings();
        }

        private void handleOverrideMenuService(){
            if(mOverrideMenu){
                if(!isVisible()) {
                    stopService(new Intent(ActivityWatchFaceService.this, MenuButtonService.class));
                }else {
                    startService(new Intent(ActivityWatchFaceService.this, MenuButtonService.class));
                }
            }
        }

        private void storeCurrentSettings(){
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ActivityWatchFaceService.this);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putBoolean(OVERRIDE_MENU_KEY, mOverrideMenu);
            edit.putInt(DAILY_STEP_GOAL_KEY, mDailyStepGoal);
            edit.putInt(CLOCK_COLOR_KEY, mTextPaint.getColor());
            edit.apply();
        }

        private void restoreSettings(){
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ActivityWatchFaceService.this);
            mOverrideMenu = prefs.getBoolean(OVERRIDE_MENU_KEY, false);
            mTextPaint.setColor(prefs.getInt(CLOCK_COLOR_KEY, mTextPaint.getColor()));
            mDailyStepGoal = prefs.getInt(DAILY_STEP_GOAL_KEY, 1000);
        }
    }
}
