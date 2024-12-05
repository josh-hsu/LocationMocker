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

package com.mumu.locationmocker;

import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

public class TopUIController {
    private static final String TAG = "TopUI";
    private final Context mContext;
    private final Service mService;
    private final Handler mHandler;
    private WindowManager mWindowManager;

    private WindowManager.LayoutParams mMainLayoutParams;
    private RelativeLayout mMainLayout;
    private ScrollView mTopUIScrollView;
    private TopUIView mHandleBarView;
    private TopUIView mTimerTextView, mMsgTextView;
    private TopUIView mFirstButton, mSecondButton, mThirdButton;
    private TopUIView mFirstText, mSecondText, mThirdText;

    private static final int sTopViewLeftInsetDp = 24;
    private static final int sTopViewPositionY = 200;
    private static final float sTopViewAlpha = 0.75f;
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
    public static final int SCRIPT_CREATION_CAPTURE = 5;
    public static final int SCRIPT_CREATION_POINT_SELECT_EQUAL = 6;
    public static final int SCRIPT_CREATION_POINT_SELECT_NOT_EQUAL = 7;
    public static final int BUTTON_FIRST_STATE_PAUSED = 0;
    public static final int BUTTON_FIRST_STATE_PLAYING = 1;
    public static final int BUTTON_FIRST_STATE_ADD_NEW = 2;
    public static final int BUTTON_FIRST_STATE_SESSION = 3;
    public static final int BUTTON_FIRST_STATE_ADD_POINT = 4;
    public static final int BUTTON_SECOND_STATE_QUICK_EDIT = 0;
    public static final int BUTTON_SECOND_STATE_OPEN_XML = 1;
    public static final int BUTTON_SECOND_STATE_SCREENSHOT = 2;
    public static final int BUTTON_SECOND_STATE_CANCEL = 3;
    public static final int BUTTON_THIRD_STATE_HOME = 0;
    public static final int BUTTON_THIRD_STATE_SAVE = 1;
    public static final int BUTTON_THIRD_STATE_MATCH_EQUAL = 2;
    public static final int BUTTON_THIRD_STATE_MATCH_NOT_EQUAL = 3;

    protected TopUIController(Context context, Service service, Handler handler) {
        mContext = context;
        mService = service;
        mHandler = handler;
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

    private void initTopUIs() {
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mContext.setTheme(R.style.AppTheme);
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // density setting
        mDensityMultiplier = mContext.getResources().getDisplayMetrics().density;

        // Main Layout
        mMainLayout = (RelativeLayout) inflater.inflate(R.layout.top_ui_main, null);
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
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mFirstButton.setState(BUTTON_FIRST_STATE_PLAYING);
                        startOrStopJob(true);
                    }
                });
        mFirstButton.setViewStateAction(BUTTON_FIRST_STATE_PLAYING,
                R.drawable.ic_pause,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mFirstButton.setState(BUTTON_FIRST_STATE_PAUSED);
                        startOrStopJob(false);
                    }
                });
        mFirstButton.setViewStateAction(BUTTON_FIRST_STATE_ADD_NEW,
                R.drawable.baseline_post_add_24,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                    }
                });
        mFirstButton.setViewStateAction(BUTTON_FIRST_STATE_SESSION,
                R.drawable.baseline_view_list_24,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                    }
                });
        mFirstButton.setViewStateAction(BUTTON_FIRST_STATE_ADD_POINT,
                R.drawable.add_task_48px,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                    }
                });

        // Middle button
        mSecondButton = new TopUIView(findViewById(R.id.button_second), TopUIView.VIEW_TYPE_SWITCH);
        mSecondButton.setViewStateAction(BUTTON_SECOND_STATE_QUICK_EDIT,
                R.drawable.baseline_edit_note_24,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                    }
                });
        mSecondButton.setViewStateAction(BUTTON_SECOND_STATE_OPEN_XML,
                R.drawable.baseline_snippet_folder_24,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                    }
                });
        mSecondButton.setViewStateAction(BUTTON_SECOND_STATE_SCREENSHOT,
                R.drawable.ic_menu_camera,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                    }
                });
        mSecondButton.setViewStateAction(BUTTON_SECOND_STATE_CANCEL,
                R.drawable.close_48px,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                    }
                });

        // Right button
        mThirdButton = new TopUIView(findViewById(R.id.button_third), TopUIView.VIEW_TYPE_BUTTON);
        mThirdButton.setViewStateAction(BUTTON_THIRD_STATE_HOME,
                R.drawable.ic_menu_home_outline,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                    }
                });
        mThirdButton.setViewStateAction(BUTTON_THIRD_STATE_SAVE,
                R.drawable.baseline_save_24,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                    }
                });
        mThirdButton.setViewStateAction(BUTTON_THIRD_STATE_MATCH_EQUAL,
                R.drawable.equal_24px,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // transition to match not equal
                        iconTransition(SCRIPT_CREATION_POINT_SELECT_NOT_EQUAL);
                    }
                });
        mThirdButton.setViewStateAction(BUTTON_THIRD_STATE_MATCH_NOT_EQUAL,
                R.drawable.rule_24px,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // transition to match equal
                        iconTransition(SCRIPT_CREATION_POINT_SELECT_EQUAL);
                    }
                });

        mFirstText = new TopUIView(findViewById(R.id.textview_inst_first), TopUIView.VIEW_TYPE_TEXTVIEW);
        mSecondText = new TopUIView(findViewById(R.id.textview_inst_second), TopUIView.VIEW_TYPE_TEXTVIEW);
        mThirdText = new TopUIView(findViewById(R.id.textview_inst_third), TopUIView.VIEW_TYPE_TEXTVIEW);

        mWindowManager.addView(mMainLayout, mMainLayoutParams);

        iconTransition(EXECUTION_MODE_NORMAL);
        updateLogMessage("");
    }

    public final <T extends View> T findViewById(int id) {
        if (mMainLayout != null)
            return mMainLayout.findViewById(id);
        else
            return null;
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
    private void startOrStopJob(boolean start) {

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
                mSecondButton.setState(BUTTON_SECOND_STATE_QUICK_EDIT);
                mThirdButton.setState(BUTTON_THIRD_STATE_HOME);
                //mFirstText.showInstruction(mHandler, getString(R.string.acc_guide_play_pause));
                //mSecondText.showInstruction(mHandler, getString(R.string.acc_guide_quick_edit));
                //mThirdText.showInstruction(mHandler, getString(R.string.acc_guide_back_to_app));
                showBarView(true);
                break;
            case SCRIPT_CREATION_CAPTURE:
                iconVisible(new boolean[] {true, true, true, true});
                mFirstButton.setState(BUTTON_FIRST_STATE_SESSION);
                mSecondButton.setState(BUTTON_SECOND_STATE_SCREENSHOT);
                mThirdButton.setState(BUTTON_THIRD_STATE_SAVE);
                //mFirstText.showInstruction(mHandler, getString(R.string.acc_guide_session_detail));
               // mSecondText.showInstruction(mHandler, getString(R.string.acc_guide_take_screenshot));
                //mThirdText.showInstruction(mHandler, getString(R.string.acc_guide_save_session));
                showBarView(true);
                break;
            case SCRIPT_CREATION_POINT_SELECT_EQUAL:
                iconVisible(new boolean[] {true, true, true, true});
                mFirstButton.setState(BUTTON_FIRST_STATE_ADD_POINT);
                mSecondButton.setState(BUTTON_SECOND_STATE_CANCEL);
                mThirdButton.setState(BUTTON_THIRD_STATE_MATCH_EQUAL);
                //mFirstText.showInstruction(mHandler, getString(R.string.acc_guide_add_point));
                //mSecondText.showInstruction(mHandler, getString(R.string.acc_guide_cancel_point));
                //mThirdText.showInstruction(mHandler, getString(R.string.acc_guide_match_equal));
                showBarView(true);
                break;
            case SCRIPT_CREATION_POINT_SELECT_NOT_EQUAL:
                iconVisible(new boolean[] {true, true, true, true});
                mFirstButton.setState(BUTTON_FIRST_STATE_ADD_POINT);
                mSecondButton.setState(BUTTON_SECOND_STATE_CANCEL);
                mThirdButton.setState(BUTTON_THIRD_STATE_MATCH_NOT_EQUAL);
                //mFirstText.showInstruction(mHandler, getString(R.string.acc_guide_add_point));
                //mSecondText.showInstruction(mHandler, getString(R.string.acc_guide_cancel_point));
                //mThirdText.showInstruction(mHandler, getString(R.string.acc_guide_match_not_equal));
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
        if (mMessageText.length() < 1) {
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
