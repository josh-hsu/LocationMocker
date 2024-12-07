/*
 * Copyright (C) 2016-2024 The Josh Tool Project
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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.android.gms.maps.model.LatLng;

import com.mumu.locationmocker.AppSharedObject;
import com.mumu.locationmocker.MainActivity;
import com.mumu.locationmocker.R;
import com.mumu.locationmocker.location.*;

public class HeadService extends Service {
    private static final String TAG = "PokemonGoGo";
    public static final String ACTION_HANDLE_NAVIGATION = "ActionNavigation";
    public static final String ACTION_HANDLE_TELEPORT = "ActionTeleport";
    public static final String ACTION_HANDLE_INCUBATING = "ActionIncubating";
    public static final String EXTRA_DATA = "DataLocation";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Context mContext;
    private TopUIController mUIController;
    private RealLocationTracker mRealLocationTracker;
    private IntentLocationManager mIntentLocationManager;

    private boolean mMonitorLocationFlag = false;

    @Override
    public IBinder onBind(Intent intent) {
        // Not used
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
        initOnce();
    }

    @Override
    public void onDestroy() {
        if (mUIController != null)
            mUIController.destroy();
        if (mRealLocationTracker != null)
            mRealLocationTracker.stopListening();
        stopMonitorLocation();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LatLng mapLocation;
        double mapRadius;

        if (intent != null) {
            final String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_HANDLE_NAVIGATION:
                        mapLocation = intent.getParcelableExtra(EXTRA_DATA);
                        if (mapLocation != null) {
                            Log.d(TAG, "Service receive LAT = " + mapLocation.latitude + " and LONG = " + mapLocation.longitude);
                            mUIController.sendMessage(mContext.getString(R.string.msg_map_navigating));
                            mIntentLocationManager.navigateTo(mapLocation, () ->
                                    mUIController.sendMessage("Navigation Done!"));
                        }
                        break;
                    case ACTION_HANDLE_TELEPORT:
                        mapLocation = intent.getParcelableExtra(EXTRA_DATA);
                        if (mapLocation != null) {
                            Log.d(TAG, "Service receive LAT = " + mapLocation.latitude + " and LONG = " + mapLocation.longitude);
                            mUIController.sendMessage(mContext.getString(R.string.msg_map_teleporting));
                            mIntentLocationManager.teleportTo(mapLocation);
                        }
                        break;
                    case ACTION_HANDLE_INCUBATING:
                        mapRadius = intent.getDoubleExtra(EXTRA_DATA, 50.0);
                        if (mapRadius > 0) {
                            Log.d(TAG, "Service receive Radius = " + mapRadius);
                            mUIController.sendMessage(mContext.getString(R.string.msg_map_shu));
                        }
                        break;
                }
            }
        }
        return START_NOT_STICKY;
    }

    private void initOnce() {
        mIntentLocationManager = new IntentLocationManager(mContext);
        mUIController = new TopUIController(mContext, this, mHandler, mIntentLocationManager);
        mUIController.initOnce();
        mRealLocationTracker = new RealLocationTracker(mContext);
        mRealLocationTracker.startListening();

        AppSharedObject.get().setIntentLocationManager(mIntentLocationManager);
        AppSharedObject.get().setTopUIController(mUIController);

        initNotification();
        startMonitorLocation();
    }

    private void monitorWorkFunc() {
        String gpsString = "GPS: ", fusedString = "FUS: ";
        Location location;

        location = mRealLocationTracker.getLastGpsLocation();
        if (location != null) {
            gpsString += mRealLocationTracker.getLocationString(location);
            gpsString += " ";
            gpsString += mRealLocationTracker.getLastGpsLocationElapsedTimeStr();
            gpsString += " s ago";
        }

        location = mRealLocationTracker.getLastFusedLocation();
        if (location != null) {
            fusedString += mRealLocationTracker.getLocationString(location);
            fusedString += " ";
            fusedString += mRealLocationTracker.getLastFusedLocationElapsedTimeStr();
            fusedString += " s ago";
        }

        mUIController.sendMessage(gpsString + "\n" + fusedString);
        if (mMonitorLocationFlag)
            startMonitorLocation();
    }

    private void startMonitorLocation() {
        mMonitorLocationFlag = true;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                monitorWorkFunc();
            }
        }, 500);
    }

    private void stopMonitorLocation() {
        mMonitorLocationFlag = false;
    }

    private void initNotification() {
        Notification notification;
        final int id = 1235;
        String NOTIFICATION_CHANNEL_ID = getString(R.string.app_package_name);
        String channelName = getString(R.string.app_name);
        Intent intent = new Intent(mContext, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        notificationChannel.setLightColor(Color.BLUE);
        notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(notificationChannel);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(mContext, NOTIFICATION_CHANNEL_ID);
        notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.mipmap.icon_tx)
                .setContentTitle(getString(R.string.headservice_notification_title))
                .setContentText(getString(R.string.headservice_notification_text))
                .setPriority(NotificationManager.IMPORTANCE_HIGH)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(contentIntent)
                .build();

        startForeground(id, notification);
    }
}
