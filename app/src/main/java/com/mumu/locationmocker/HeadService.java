/*
 * Copyright (C) 2016 The Josh Tool Project
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

import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;

import com.google.android.gms.maps.model.LatLng;
import com.mumu.locationmocker.headicon.HeadIconView;
import com.mumu.locationmocker.location.*;

import java.util.ArrayList;

public class HeadService extends Service {
    private static final String TAG = "PokemonGoGo";
    private final Handler mHandler = new Handler();
    private FakeLocationManager mFakeLocationManager;
    private LocationPoller mLocationPoller;
    public static final String ACTION_HANDLE_NAVIGATION = "ActionNavigation";
    public static final String ACTION_HANDLE_TELEPORT = "ActionTeleport";
    public static final String ACTION_HANDLE_INCUBATING = "ActionIncubating";
    public static final String EXTRA_DATA = "DataLocation";
    private Context mContext;

    // View objects
    private WindowManager mWindowManager;
    private ArrayList<HeadIconView> mHeadIconList;
    private ArrayList<HeadIconView> mDirectionIconList;
    private static final int IDX_HEAD_ICON = 0;
    private static final int IDX_MSG_TEXT = 1;
    private static final int IDX_START_ICON = 2;
    private static final int IDX_HOME_ICON = 3;
    private static final int IDX_INCUBATOR_ICON = 4;
    private static final int IDX_SPEED_ICON = 5;
    private static final int IDX_UP_BUTTON = 0;
    private static final int IDX_DOWN_BUTTON = 1;
    private static final int IDX_LEFT_BUTTON = 2;
    private static final int IDX_RIGHT_BUTTON = 3;

    // game control
    private String mMessageText = "";
    private boolean mThreadStart = false;
    private boolean mFreeWalking = false;
    private boolean mAutoIncubating = false;
    private double mAutoIncubatingRadius = 50; // default 50 meters
    private FakeLocation mAutoIncubatingOrigin;
    private double mWalkSpeed = 1.0;
    private int mTouchHeadIconCount = 0;

    // map control
    private static LatLng mMapLocation;

    /*
     * Runnable threads
     */
    private final Runnable updateRunnable = new Runnable() {
        public void run() {
            updateUI();
        }
    };

    private void updateUI() {
        ((TextView) mHeadIconList.get(IDX_MSG_TEXT).getView()).setText(mMessageText);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Not used
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mContext = this;
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mContext.setTheme(R.style.AppTheme);

        // On screen view initializing, do not mess around here
        initGamePanelViews();
        initGameControlButtons();

        mThreadStart = true;
        new GetMessageThread().start();

        // Config fake location manager
        mFakeLocationManager = new FakeLocationManager(mContext, null);

        // Set enable
        mFakeLocationManager.setEnable(true);

        mLocationPoller = new LocationPoller(mContext);
        mLocationPoller.startPolling(new LocationPoller.LocationCallback() {
            @Override
            public void onNewLocation(Location location) {
                Log.d(TAG, "new location " + location);
            }

            @Override
            public void onMoveDistance(float distance) {
                mMessageText = "距離: " + distance + " 公尺";
            }
        });
    }

    private void initGamePanelViews() {
        mHeadIconList = new ArrayList<>();
        Log.d(TAG, "init game panel view");
        // Head Icon
        HeadIconView headIcon = new HeadIconView(new ImageView(this), mWindowManager, 0, 0);
        headIcon.getImageView().setImageResource(R.drawable.ic_menu_home_outline);
        headIcon.setOnTapListener(new HeadIconView.OnTapListener() {
            @Override
            public void onTap(View view) {
                configHeadIconShowing(HeadIconView.AUTO);
            }

            @Override
            public void onLongPress(View view) {
                showExitConfirmDialog();
            }
        });
        mHeadIconList.add(headIcon);

        // Message Text Icon
        HeadIconView msgText = new HeadIconView(new TextView(this), mWindowManager, 140, 45);
        msgText.getTextView().setText("");
        msgText.getTextView().setTextColor(Color.BLACK);
        msgText.getView().setBackgroundColor(Color.WHITE);
        mHeadIconList.add(msgText);

        // Start Game Pad Icon
        HeadIconView startIcon = new HeadIconView(new ImageView(this), mWindowManager, 0, 120);
        startIcon.getImageView().setImageResource(R.drawable.ic_play);
        startIcon.setOnTapListener(new HeadIconView.OnTapListener() {
            @Override
            public void onTap(View view) {
                configFreeWalking();
            }

            @Override
            public void onLongPress(View view) {

            }
        });
        mHeadIconList.add(startIcon);

        // Home Icon
        HeadIconView homeIcon = new HeadIconView(new ImageView(this), mWindowManager, 100, 140);
        homeIcon.getImageView().setImageResource(R.drawable.ic_location_pin);
        homeIcon.setOnTapListener(new HeadIconView.OnTapListener() {
            @Override
            public void onTap(View view) {
                Log.d(TAG, "config map icon");
                mFakeLocationManager.cancelAutoPilot();
                configHeadIconShowing(HeadIconView.INVISIBLE);
                Intent mapIntent = new Intent(mContext, MapLocationViewer.class);
                mapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(mapIntent);
            }

            @Override
            public void onLongPress(View view) {

            }
        });
        mHeadIconList.add(homeIcon);

        // Incubator Icon
        HeadIconView incubatorIcon = new HeadIconView(new ImageView(this), mWindowManager, 200, 140);
        incubatorIcon.getImageView().setImageResource(R.drawable.ic_egg_disabled);
        incubatorIcon.setOnTapListener(new HeadIconView.OnTapListener() {
            @Override
            public void onTap(View view) {
                Log.d(TAG, "config auto incubating");
                configAutoIncubating();
            }

            @Override
            public void onLongPress(View view) {

            }
        });
        mHeadIconList.add(incubatorIcon);

        // Speed control Icon
        HeadIconView speedIcon = new HeadIconView(new ImageView(this), mWindowManager, 300, 140);
        speedIcon.getImageView().setImageResource(R.drawable.ic_one);
        speedIcon.setOnTapListener(new HeadIconView.OnTapListener() {
            @Override
            public void onTap(View view) {
                Log.d(TAG, "config speed");
                if (!mAutoIncubating)
                    configSpeed();
                else
                    mMessageText = "You can't";
            }

            @Override
            public void onLongPress(View view) {

            }
        });
        mHeadIconList.add(speedIcon);

        // Share the same on move listener for moving in the same time
        HeadIconView.OnMoveListener moveListener = new HeadIconView.OnMoveListener() {
            @Override
            public void onMove(int initialX, int initialY, float initialTouchX, float initialTouchY, MotionEvent event) {
                for(HeadIconView icon : mHeadIconList) {
                    icon.moveIconDefault(initialX, initialY, initialTouchX, initialTouchY, event);
                }
            }
        };

        // Set all to add
        for(HeadIconView icon : mHeadIconList) {
            icon.addView();
            icon.setOnMoveListener(moveListener);
        }

        // Set default visibility
        mHeadIconList.get(IDX_START_ICON).setVisibility(View.INVISIBLE);
        mHeadIconList.get(IDX_HOME_ICON).setVisibility(View.INVISIBLE);
        mHeadIconList.get(IDX_INCUBATOR_ICON).setVisibility(View.INVISIBLE);
        mHeadIconList.get(IDX_SPEED_ICON).setVisibility(View.INVISIBLE);
    }

    private void initGameControlButtons() {
        mDirectionIconList = new ArrayList<>();

        // Up button
        HeadIconView upButton = new HeadIconView(new ImageView(this), mWindowManager, 150, 240);
        upButton.getImageView().setImageResource(R.drawable.ic_arrow_up);
        upButton.getImageView().setBackgroundColor(getColor(R.color.button_half_transparent));
        upButton.setOnTapListener(new HeadIconView.OnTapListener() {
            @Override
            public void onTap(View view) {
                mFakeLocationManager.setSpeed(mWalkSpeed);
                mFakeLocationManager.walkPace(FakeLocation.NORTH, 1.0);
            }

            @Override
            public void onLongPress(View view) {

            }
        });
        mDirectionIconList.add(upButton);

        // Down button
        HeadIconView downButton = new HeadIconView(new ImageView(this), mWindowManager, 150, 0);
        downButton.getImageView().setImageResource(R.drawable.ic_arrow_down);
        downButton.getImageView().setBackgroundColor(getColor(R.color.button_half_transparent));
        downButton.setOnTapListener(new HeadIconView.OnTapListener() {
            @Override
            public void onTap(View view) {
                mFakeLocationManager.setSpeed(mWalkSpeed);
                mFakeLocationManager.walkPace(FakeLocation.SOUTH, 1.0);
            }

            @Override
            public void onLongPress(View view) {

            }
        });
        mDirectionIconList.add(downButton);

        // Left button
        HeadIconView leftButton = new HeadIconView(new ImageView(this), mWindowManager, 30, 120);
        leftButton.getImageView().setImageResource(R.drawable.ic_arrow_left);
        leftButton.getImageView().setBackgroundColor(getColor(R.color.button_half_transparent));
        leftButton.setOnTapListener(new HeadIconView.OnTapListener() {
            @Override
            public void onTap(View view) {
                mFakeLocationManager.setSpeed(mWalkSpeed);
                mFakeLocationManager.walkPace(FakeLocation.WEST, 1.0);
            }

            @Override
            public void onLongPress(View view) {

            }
        });
        mDirectionIconList.add(leftButton);

        // Right button
        HeadIconView rightButton = new HeadIconView(new ImageView(this), mWindowManager, 270, 120);
        rightButton.getImageView().setImageResource(R.drawable.ic_arrow_right);
        rightButton.getImageView().setBackgroundColor(getColor(R.color.button_half_transparent));
        rightButton.setOnTapListener(new HeadIconView.OnTapListener() {
            @Override
            public void onTap(View view) {
                mFakeLocationManager.setSpeed(mWalkSpeed);
                mFakeLocationManager.walkPace(FakeLocation.EAST, 1.0);
            }

            @Override
            public void onLongPress(View view) {

            }
        });
        mDirectionIconList.add(rightButton);

        // North West button
        HeadIconView upLeftButton = new HeadIconView(new ImageView(this), mWindowManager, 30, 240);
        upLeftButton.getImageView().setImageResource(R.drawable.ic_arrow_squart);
        upLeftButton.getImageView().setBackgroundColor(getColor(R.color.button_half_transparent));
        upLeftButton.setOnTapListener(new HeadIconView.OnTapListener() {
            @Override
            public void onTap(View view) {
                mFakeLocationManager.setSpeed(mWalkSpeed);
                mFakeLocationManager.walkPace(FakeLocation.NORTHWEST, 1.0);
            }

            @Override
            public void onLongPress(View view) {

            }
        });
        mDirectionIconList.add(upLeftButton);

        // West South button
        HeadIconView leftDownButton = new HeadIconView(new ImageView(this), mWindowManager, 30, 0);
        leftDownButton.getImageView().setImageResource(R.drawable.ic_arrow_squart);
        leftDownButton.getImageView().setBackgroundColor(getColor(R.color.button_half_transparent));
        leftDownButton.setOnTapListener(new HeadIconView.OnTapListener() {
            @Override
            public void onTap(View view) {
                mFakeLocationManager.setSpeed(mWalkSpeed);
                mFakeLocationManager.walkPace(FakeLocation.WESTSOUTH, 1.0);
            }

            @Override
            public void onLongPress(View view) {

            }
        });
        mDirectionIconList.add(leftDownButton);

        // South East button
        HeadIconView downRightButton = new HeadIconView(new ImageView(this), mWindowManager, 270, 0);
        downRightButton.getImageView().setImageResource(R.drawable.ic_arrow_squart);
        downRightButton.getImageView().setBackgroundColor(getColor(R.color.button_half_transparent));
        downRightButton.setOnTapListener(new HeadIconView.OnTapListener() {
            @Override
            public void onTap(View view) {
                mFakeLocationManager.setSpeed(mWalkSpeed);
                mFakeLocationManager.walkPace(FakeLocation.SOUTHEAST, 1.0);
            }

            @Override
            public void onLongPress(View view) {

            }
        });
        mDirectionIconList.add(downRightButton);

        // East North button
        HeadIconView rightUpButton = new HeadIconView(new ImageView(this), mWindowManager, 270, 240);
        rightUpButton.getImageView().setImageResource(R.drawable.ic_arrow_squart);
        rightUpButton.getImageView().setBackgroundColor(getColor(R.color.button_half_transparent));
        rightUpButton.setOnTapListener(new HeadIconView.OnTapListener() {
            @Override
            public void onTap(View view) {
                mFakeLocationManager.setSpeed(mWalkSpeed);
                mFakeLocationManager.walkPace(FakeLocation.EASTNORTH, 1.0);
            }

            @Override
            public void onLongPress(View view) {

            }
        });
        mDirectionIconList.add(rightUpButton);

        // add view and set invisible
        for (HeadIconView icon : mDirectionIconList) {
            icon.setVisibility(View.INVISIBLE);
            icon.setGravity(Gravity.BOTTOM | Gravity.START, false);
            icon.addView();
        }
    }

    private void StopService() {
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThreadStart = false;

        // Game tool
        for (HeadIconView icon : mHeadIconList) {
            icon.removeView();
        }

        // Game control tool
        for (HeadIconView icon : mDirectionIconList) {
            icon.removeView();
        }

        if (mFakeLocationManager != null) mFakeLocationManager.setEnable(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LatLng mapLocation;
        double mapRadius;

        if (intent != null) {
            final String action = intent.getAction();
            if (action != null) {
                configHeadIconShowing(HeadIconView.VISIBLE);

                switch (action) {
                    case ACTION_HANDLE_NAVIGATION:
                        mapLocation = intent.getParcelableExtra(EXTRA_DATA);
                        Log.d(TAG, "Service receive LAT = " + mapLocation.latitude + " and LONG = " + mapLocation.longitude);
                        mMapLocation = mapLocation;
                        mMessageText = mContext.getString(R.string.msg_map_navigating);
                        doMapNavigation();
                        break;
                    case ACTION_HANDLE_TELEPORT:
                        mapLocation = intent.getParcelableExtra(EXTRA_DATA);
                        Log.d(TAG, "Service receive LAT = " + mapLocation.latitude + " and LONG = " + mapLocation.longitude);
                        mMapLocation = mapLocation;
                        mMessageText = mContext.getString(R.string.msg_map_teleporting);
                        doMapTeleporting();
                        break;
                    case ACTION_HANDLE_INCUBATING:
                        mapRadius = intent.getDoubleExtra(EXTRA_DATA, 50.0);
                        Log.d(TAG, "Service receive Radius = " + mapRadius);
                        mAutoIncubatingRadius = mapRadius;
                        mMessageText = mContext.getString(R.string.msg_start_incubating);
                        mHeadIconList.get(IDX_INCUBATOR_ICON).getImageView().setImageResource(R.drawable.ic_egg_enabled);
                        mHeadIconList.get(IDX_SPEED_ICON).getImageView().setImageResource(R.drawable.ic_slow);
                        mAutoIncubating = true;
                        startAutoIncubating();
                        break;
                }
            }
        }
        return START_NOT_STICKY;
    }

    public int getCurrentHeadIconVisibility() {
        if (mTouchHeadIconCount % 2 == 0)
            return HeadIconView.INVISIBLE;
        else
            return HeadIconView.VISIBLE;
    }

    private void configHeadIconShowing(int visible) {

        // If current status is what we want, return here
        if (getCurrentHeadIconVisibility() == visible)
            return;

        // Increase icon touch count
        mTouchHeadIconCount++;
        mHeadIconList.get(IDX_START_ICON).setVisibility(getCurrentHeadIconVisibility());
        mHeadIconList.get(IDX_HOME_ICON).setVisibility(getCurrentHeadIconVisibility());
        mHeadIconList.get(IDX_INCUBATOR_ICON).setVisibility(getCurrentHeadIconVisibility());
        mHeadIconList.get(IDX_SPEED_ICON).setVisibility(getCurrentHeadIconVisibility());
    }

    private void showExitConfirmDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(mContext, R.style.myDialog))
                .setTitle(getString(R.string.headservice_stop_title))
                .setMessage(getString(R.string.headservice_stop_info))
                .setPositiveButton(getString(R.string.headservice_stop_button), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Let's do some background stuff
                        StopService();
                    }
                })
                .setNegativeButton(getString(R.string.startup_cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
        AlertDialog alert = builder.create();
        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        alert.show();
    }

    private void configFreeWalking() {
        mFreeWalking = !mFreeWalking;
        mHeadIconList.get(IDX_START_ICON).getImageView().setImageResource(mFreeWalking ? R.drawable.ic_pause : R.drawable.ic_play);

        for (HeadIconView icon : mDirectionIconList) {
            icon.setVisibility(mFreeWalking ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void configAutoIncubating() {
        if (mAutoIncubating) {
            Log.d(TAG, "Stop auto incubating");
            mMessageText = mContext.getString(R.string.msg_stop_incubating);
            mHeadIconList.get(IDX_INCUBATOR_ICON).getImageView().setImageResource(R.drawable.ic_egg_disabled);
            mAutoIncubating = false;
        } else {
            Log.d(TAG, "Start auto incubating");
            mFakeLocationManager.cancelAutoPilot();
            configHeadIconShowing(HeadIconView.INVISIBLE);
            Intent mapIntent = new Intent(mContext, MapAutoIncubating.class);
            mapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(mapIntent);
        }
    }

    private void configSpeed() {
        ImageView iv = mHeadIconList.get(IDX_SPEED_ICON).getImageView();
        if (mWalkSpeed == 1.0) {
            mWalkSpeed = 2.0;
            iv.setImageResource(R.drawable.ic_two);
        } else if (mWalkSpeed == 2.0) {
            mWalkSpeed = 4.0;
            iv.setImageResource(R.drawable.ic_three);
        } else if (mWalkSpeed == 4.0) {
            mWalkSpeed = 8.0;
            iv.setImageResource(R.drawable.ic_four);
        } else if (mWalkSpeed == 8.0) {
            mWalkSpeed = 32.0;
            iv.setImageResource(R.drawable.ic_five);
        } else if (mWalkSpeed == 32.0) {
            mWalkSpeed = 128.0;
            iv.setImageResource(R.drawable.ic_six);
        } else if (mWalkSpeed == 128.0) {
            mWalkSpeed = 0.7;
            iv.setImageResource(R.drawable.ic_slow);
        } else if (mWalkSpeed == 0.7) {
            mWalkSpeed = 1.0;
            iv.setImageResource(R.drawable.ic_one);
        }
        mFakeLocationManager.setSpeed(mWalkSpeed);
    }

    private void doMapNavigation() {
        if (mAutoIncubating) {
            mMessageText = mContext.getString(R.string.msg_stop_incubating_forcely);
            configAutoIncubating();
        }

        mFakeLocationManager.setOnNavigationCompleteListener(new FakeLocationManager.OnNavigationCompleteListener() {
            @Override
            public void onNavigationComplete() {
                mMessageText = mContext.getString(R.string.msg_map_navi_done);
            }
        });
        mFakeLocationManager.autoPilotTo(mMapLocation.latitude, mMapLocation.longitude, true);
    }

    private void doMapTeleporting() {
        if (mAutoIncubating) {
            mMessageText = mContext.getString(R.string.msg_stop_incubating_forcely);
            configAutoIncubating();
        }

        FakeLocation loc = new FakeLocation(mMapLocation.latitude, mMapLocation.longitude, 13.3122, 7.91231);
        mFakeLocationManager.setLocation(loc);
    }

    class GetMessageThread extends Thread {
        public void run() {
            while (mThreadStart) {
                mHandler.post(updateRunnable);

                try {
                    Thread.sleep(500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    void startAutoIncubating() {
        mAutoIncubatingOrigin = mFakeLocationManager.getCurrentLocation();
        new StartAutoIncubatingThread().start();
    }

    class StartAutoIncubatingThread extends Thread {
        public void run() {
            HumanWalkSimulator walkSimulator = new HumanWalkSimulator();
            int previousDirection = -1;
            int directionCheck;
            int directionKeep = 0;

            while (mAutoIncubating) {
                int nextDirection = walkSimulator.getNextDirection();
                double speedChange = Math.sqrt(Math.random() + 1.0); // limit speed to 1 to 1.5
                double directionRatio = 1.0 - Math.random() / 4.0; // the ratio of the direction, limit to 0.75 ~ 1

                // check if out of bound
                if (directionKeep > 0) {
                    directionKeep--;
                    nextDirection = previousDirection;
                } else {
                    directionCheck = mFakeLocationManager.getNewDirectionInBound(mAutoIncubatingOrigin,
                            mAutoIncubatingRadius, nextDirection);
                    if (directionCheck != nextDirection) {
                        Log.w(TAG, "Out of bound detected, ignoring walk simulator");
                        nextDirection = directionCheck;
                        directionKeep = 6;
                    }
                }

                mFakeLocationManager.walkPace(nextDirection, directionRatio);
                mFakeLocationManager.setSpeed(speedChange);

                if (previousDirection != nextDirection) {
                    Log.d(TAG, "Direction has changed from " + previousDirection + " to " + nextDirection);
                    previousDirection = nextDirection;
                }

                try {
                    Thread.sleep((int)(Math.random() * 1000) + 500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
