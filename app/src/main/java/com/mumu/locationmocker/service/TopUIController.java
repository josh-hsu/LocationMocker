/*
 * Copyright (C) 2023-2024 The Josh Tool Project
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

package com.mumu.locationmocker.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

import com.mumu.locationmocker.MapLocationViewer;
import com.mumu.locationmocker.R;
import com.mumu.locationmocker.location.IntentLocationManager;

public class TopUIController {
    private static final String TAG = "TopUI";
    private final Context mContext;
    private final Service mService;
    private final Handler mHandler;
    private final IntentLocationManager mIntentLocationManager;
    private WindowManager mWindowManager;
    private LayoutInflater mLayoutInflater;

    private WindowManager.LayoutParams mMainLayoutParams;
    private WindowManager.LayoutParams mJoystickParams;
    private RelativeLayout mMainLayout;
    private ScrollView mTopUIScrollView;
    private TopUIView mHandleBarView;
    private TopUIView mTimerTextView, mMsgTextView;
    private TopUIView mFirstButton, mSecondButton, mThirdButton;
    private TopUIView mFirstText, mSecondText, mThirdText;
    private JoystickView mJoystickView;

    private static final int sTopViewLeftInsetDp = 24;
    private static final int sTopViewPositionY = 200;
    private static final float sTopViewAlpha = 0.75f;
    private static final int sJoystickPositionX = 100;
    private static final int sJoystickPositionY = 1600;
    private static final int mUpdateUIInterval = 100;
    private static final int mMessageLastTimeMs = 3000;
    private String mMessageText = "";
    private long mMessageTimestamp = -1;
    private boolean mMessageThreadRunning = false;
    private int mCurrentIconModeState = EXECUTION_MODE_NORMAL;
    private int mLastIconModeState = 0;
    private int mMainLayoutPositionIndex = 0;
    private float mDensityMultiplier = 1.0f;

    public static final int EXECUTION_MODE_HIDE_ALL = 1;
    public static final int EXECUTION_MODE_NORMAL = 2;
    public static final int BUTTON_FIRST_STATE_PAUSED = 0;
    public static final int BUTTON_FIRST_STATE_PLAYING = 1;
    public static final int BUTTON_SECOND_STATE_SPEED_CONFIG = 0;
    public static final int BUTTON_THIRD_STATE_OPEN_MAP = 0;

    private int mCurrentSpeedIndex = 0;
    private final int[] mSpeedResArray = new int[] {
            R.drawable.ic_one,
            R.drawable.ic_two,
            R.drawable.ic_three,
            R.drawable.ic_four,
            R.drawable.ic_five,
            R.drawable.ic_six};
    private final double[] mSpeedValueArray = new double[] {
            10.0f, 20.0f, 40.0f, 80.0f, 300.0f, 1000.0f
    };

    protected TopUIController(Context context, Service service, Handler handler, IntentLocationManager ilm) {
        mContext = context;
        mService = service;
        mHandler = handler;
        mIntentLocationManager = ilm;
    }

    public void initOnce() {
        initTopUIs();
        initThreads();
    }

    public void destroy() {
        mMessageThreadRunning = false;
        if (mWindowManager != null && mMainLayout != null) {
            mWindowManager.removeView(mMainLayout);
        }
    }

    public void disableSelf() {
        mService.stopSelf();
    }

    private void initJoystick() {
        if (mWindowManager != null && mLayoutInflater != null) {
            mJoystickParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // able to present in negative position
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            mJoystickParams.gravity = Gravity.TOP | Gravity.START;
            mJoystickParams.x = sJoystickPositionX;
            mJoystickParams.y = sJoystickPositionY;
            mJoystickParams.width = 400;
            mJoystickParams.height = 400;

            mJoystickView = new JoystickView(mContext);
            mJoystickView.setJoystickListener(mIntentLocationManager);
            mJoystickView.setVisibility(View.INVISIBLE);

            mWindowManager.addView(mJoystickView, mJoystickParams);
        }
    }

    private void initTopUIs() {
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mContext.setTheme(R.style.AppTheme);
        mLayoutInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // density setting
        mDensityMultiplier = mContext.getResources().getDisplayMetrics().density;

        // Main Layout
        mMainLayout = (RelativeLayout) mLayoutInflater.inflate(R.layout.top_ui_main, null);
        mMainLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // able to present in negative position
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        mMainLayoutParams.gravity = Gravity.TOP | Gravity.START;
        mMainLayoutParams.x = -((int)(sTopViewLeftInsetDp * mDensityMultiplier));
        mMainLayoutParams.y = sTopViewPositionY;
        mMainLayout.setAlpha(sTopViewAlpha);

        // Main Scroll View
        mTopUIScrollView = findViewById(R.id.top_ui_scroll_view);

        // Timer TextView
        mTimerTextView = new TopUIView(findViewById(R.id.textview_progress_time), TopUIView.VIEW_TYPE_TEXTVIEW);
        mMsgTextView = new TopUIView(findViewById(R.id.textview_log_see), TopUIView.VIEW_TYPE_TEXTVIEW);

        // Handler Bar
        mHandleBarView = new TopUIView(findViewById(R.id.arrow_handle), TopUIView.VIEW_TYPE_SWITCH);
        mHandleBarView.setAnimationType(TopUIView.ANIMATION_TYPE_ROTATION);
        mHandleBarView.setOnTapListener(new TopUIView.OnTapListener() {
            @Override
            public void onTap(View view) {
                showBarView(mHandleBarView.getInteractValue() == 1);
            }

            @Override
            public void onLongPress(View view) {
                moveTopUIView();
            }
        });

        // Left button
        mFirstButton = new TopUIView(findViewById(R.id.button_first), TopUIView.VIEW_TYPE_BUTTON);
        mFirstButton.setViewStateAction(BUTTON_FIRST_STATE_PAUSED,
                R.drawable.ic_play,
                view -> {
                    mFirstButton.setState(BUTTON_FIRST_STATE_PLAYING);
                    startJoystick(true);
                });
        mFirstButton.setViewStateAction(BUTTON_FIRST_STATE_PLAYING,
                R.drawable.ic_pause,
                view -> {
                    mFirstButton.setState(BUTTON_FIRST_STATE_PAUSED);
                    startJoystick(false);
                });

        // Middle button
        mSecondButton = new TopUIView(findViewById(R.id.button_second), TopUIView.VIEW_TYPE_SWITCH);
        mSecondButton.setViewStateAction(BUTTON_SECOND_STATE_SPEED_CONFIG,
                R.drawable.ic_one,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        changeSpeed();
                    }
                });

        // Right button
        mThirdButton = new TopUIView(findViewById(R.id.button_third), TopUIView.VIEW_TYPE_BUTTON);
        mThirdButton.setViewStateAction(BUTTON_THIRD_STATE_OPEN_MAP,
                R.drawable.map_24px,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startMapView();
                    }
                });

        mFirstText = new TopUIView(findViewById(R.id.textview_inst_first), TopUIView.VIEW_TYPE_TEXTVIEW);
        mSecondText = new TopUIView(findViewById(R.id.textview_inst_second), TopUIView.VIEW_TYPE_TEXTVIEW);
        mThirdText = new TopUIView(findViewById(R.id.textview_inst_third), TopUIView.VIEW_TYPE_TEXTVIEW);

        mWindowManager.addView(mMainLayout, mMainLayoutParams);

        initJoystick();

        iconTransition(EXECUTION_MODE_NORMAL);
        updateLogMessage("");
    }

    public final <T extends View> T findViewById(int id) {
        if (mMainLayout != null)
            return mMainLayout.findViewById(id);
        else
            return null;
    }

    private void startMapView() {
        Intent mapIntent = new Intent(mContext, MapLocationViewer.class);
        mapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(mapIntent);
    }

    public String getString(int id) {
        return mContext.getString(id);
    }

    private void initThreads() {
        mMessageThreadRunning = true;
        new GetMessageThread().start();
    }

    /*
     * Button actions
     */
    private void startJoystick(boolean show) {
        mJoystickView.setVisibility(show ? View.VISIBLE: View.INVISIBLE);
    }

    private void changeSpeed() {
        mCurrentSpeedIndex++;
        if (mCurrentSpeedIndex >= mSpeedResArray.length) {
            mCurrentSpeedIndex = 0;
        }

        mSecondButton.setImageResource(mSpeedResArray[mCurrentSpeedIndex]);
        mIntentLocationManager.setPaceSpeed(mSpeedValueArray[mCurrentSpeedIndex]);
    }

    private void showBarView(boolean show) {
        int clipToWidth = show ?
                WindowManager.LayoutParams.WRAP_CONTENT : 0;
        ViewGroup.LayoutParams beforeParams = mTopUIScrollView.getLayoutParams();
        beforeParams.width = clipToWidth;
        mTopUIScrollView.setLayoutParams(beforeParams);
        mHandleBarView.setState(show ? 0 : 1);
    }

    private void iconVisible(boolean[] visible) {
        if (visible.length == 4) {
            mFirstButton.setVisible(visible[0]);
            mSecondButton.setVisible(visible[1]);
            mThirdButton.setVisible(visible[2]);
            mTimerTextView.setVisible(visible[3]);
        } else {
            Log.e(TAG, "iconVisible size error: " + visible.length);
        }
    }

    public void iconTransition(int toState) {
        switch (toState) {
            case EXECUTION_MODE_HIDE_ALL:
                showBarView(false);
                break;
            case EXECUTION_MODE_NORMAL:
                iconVisible(new boolean[] {true, true, true, true});
                mFirstButton.setState(BUTTON_FIRST_STATE_PAUSED);
                mSecondButton.setState(BUTTON_SECOND_STATE_SPEED_CONFIG);
                mThirdButton.setState(BUTTON_THIRD_STATE_OPEN_MAP);
                mFirstText.showInstruction(mHandler, getString(R.string.topui_inst_first_play));
                mSecondText.showInstruction(mHandler, getString(R.string.topui_inst_second));
                mThirdText.showInstruction(mHandler, getString(R.string.topui_inst_third));
                showBarView(true);
                break;
            default:
                break;
        }
    }

    private void updateLogMessage(String msg) {
        mMessageText = msg;
        mMsgTextView.setText(mMessageText);
        ViewGroup.LayoutParams params = mMsgTextView.getView().getLayoutParams();
        if (mMessageText.isEmpty()) {
            params.height = (int)(8 * mDensityMultiplier);
        } else {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        mMsgTextView.getView().setLayoutParams(params);
        mMessageTimestamp = System.currentTimeMillis();
    }

    private void moveTopUIView() {
        if (mMainLayoutParams != null && mMainLayout != null) {
            mMainLayoutPositionIndex = (mMainLayoutPositionIndex + 1) % 2;
            mMainLayoutParams.y = 200 * (mMainLayoutPositionIndex + 1);
            mWindowManager.updateViewLayout(mMainLayout, mMainLayoutParams);
        }
    }

    private class GetMessageThread extends Thread {
        public void run() {
            while(mMessageThreadRunning) {
                long currentTimestamp = System.currentTimeMillis();
                if (currentTimestamp - mMessageTimestamp > mMessageLastTimeMs) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            updateLogMessage("");
                        }
                    });
                }
                try {
                    Thread.sleep(mUpdateUIInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
