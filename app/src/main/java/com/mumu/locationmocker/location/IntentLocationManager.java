/*
 * Copyright (C) 2024 The Josh Tool Project
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

package com.mumu.locationmocker.location;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.mumu.locationmocker.service.JoystickView;

public class IntentLocationManager implements JoystickView.JoystickListener {
    private final String TAG = "PokemonGoGo";
    private IntentPropertyImpl mIntentPropImpl;

    private double mCurrentLat = 25.0335;
    private double mCurrentLong = 121.5642;
    private double mCurrentAlt = 10.2;
    private float  mCurrentAccuracy = 6.91f;
    private float  mCurrentBearing = 30.0f;
    private float  mCurrentSpeed = 0.3f;
    private Location mOriginalLocation;

    private double mPaceAmount = 0.00007;
    private double mPaceShift = 0.000001;
    private double mPaceSpeed = 10;

    private AutoPilot mAutoPilot;

    public IntentLocationManager(Context context) {
        mIntentPropImpl = new IntentPropertyImpl(context);
    }

    public void sendIntentLocation(Location location) {
        mIntentPropImpl.sendLocation(
                "" + location.getLatitude(),
                "" + location.getLongitude(),
                "" + location.getAltitude(),
                "" + location.getAccuracy(),
                "" + location.getBearing(),
                "" + location.getSpeed()
        );
    }

    public void sendIntentLocation(double lat, double lng, double alt, float acc, float bear, float spd) {
        mIntentPropImpl.sendLocation(
                "" + lat,
                "" + lng,
                "" + alt,
                "" + acc,
                "" + bear,
                "" + spd
        );
    }

    @Override
    public void onJoystickMoved(float xPercent, float yPercent) {
        walkPace(xPercent, -yPercent);
    }

    public void setPaceSpeed(double speed) {
        mPaceSpeed = speed;
        if (mAutoPilot != null)
            mAutoPilot.setPilotSpeed(speed);
    }

    public void setPaceShift(double shift) {
        mPaceShift = shift;
    }

    public void sendLocation(double lat, double lng) {
        mCurrentLat = lat;
        mCurrentLong = lng;
    }

    public LatLng getLocation() {
        return new LatLng(mCurrentLat, mCurrentLong);
    }

    public void applyLocation() {
        sendIntentLocation(mCurrentLat, mCurrentLong, mCurrentAlt, mCurrentAccuracy, mCurrentBearing, mCurrentSpeed);
    }

    public void setOriginalLocation(Location location) {
        mOriginalLocation = location;
        mCurrentLat = location.getLatitude();
        mCurrentLong = location.getLongitude();
        mCurrentAlt = location.getAltitude();
        mCurrentAccuracy = location.getAccuracy();
        mCurrentBearing = location.getBearing();
        mCurrentSpeed = location.getSpeed();
    }

    public boolean hasOriginalLocation() {
        return mOriginalLocation != null;
    }

    private void controlRandomShift() {
        // shift is controlled to be within -0.000001 ~ 0.000001
        double shift = Math.random() / 1000000 - 0.0000005;
        float accShift = (float) (Math.random() * 2 - 1);
        mPaceAmount = mPaceShift + shift;
        mCurrentAccuracy += accShift;

        if (mPaceAmount > 0.000002 || mPaceAmount < -0.000002)
            mPaceAmount = 0.000001;

        if (mCurrentAccuracy > 9.9f || mCurrentAccuracy < 1.5f)
            mCurrentAccuracy = 5.2f;
    }

    private void walkPace(float x, float y) {
        // must introduce random variable
        controlRandomShift();

        double nextPace = (mPaceAmount + mPaceShift) * mPaceSpeed;
        mCurrentLat = mCurrentLat + nextPace * y;
        mCurrentLong = mCurrentLong + nextPace * x;
        applyLocation();
    }

    public void teleportTo(LatLng latLng) {
        mCurrentLat = latLng.latitude;
        mCurrentLong = latLng.longitude;
        applyLocation();
    }

    public void navigateTo(LatLng latLng, OnNavigationCompleteListener l) {
        OnNavigationCompleteListener autoPilotListener = new OnNavigationCompleteListener() {
            @Override
            public void onNavigationComplete() {
                mAutoPilot = null;
                l.onNavigationComplete();
            }
        };

        if (mAutoPilot != null) {
            mAutoPilot.cancelPilot();
            mAutoPilot = null;
        }

        mAutoPilot = new AutoPilot(this, latLng, mPaceAmount, mPaceSpeed, autoPilotListener);
        mAutoPilot.start();
    }

    /*
     * Auto Pilot Component
     */
    private class AutoPilot extends Thread {
        boolean isAutoPilot = true;
        double pace;
        double paceSpeed;
        double paceShift = 0.000001;
        LatLng targetPosition;
        IntentLocationManager ilm;
        OnNavigationCompleteListener listener;

        public AutoPilot(IntentLocationManager lm, LatLng target, double p, double spd, OnNavigationCompleteListener l) {
            ilm = lm;
            targetPosition = target;
            pace = p;
            paceSpeed = spd / 10;
            listener = l;
        }

        private void sendAndApplyLocation(double lat, double lng) {
            ilm.sendLocation(lat, lng);
            ilm.applyLocation();
        }

        public void setPilotSpeed(double spd) {
            paceSpeed = spd / 10;
        }

        public void cancelPilot() {
            isAutoPilot = false;
            interrupt();
        }

        @Override
        public void run() {
            Log.d(TAG, "Start auto piloting .. ");
            double diffLat, diffLong, incrementLat, incrementLong;

            while(isAutoPilot
                    && !(Math.abs(ilm.getLocation().latitude - targetPosition.latitude) < pace)
                    && !(Math.abs(ilm.getLocation().longitude - targetPosition.longitude) < pace)) {
                double currentLat = ilm.getLocation().latitude;
                double currentLng = ilm.getLocation().longitude;
                diffLat = targetPosition.latitude - currentLat;
                diffLong = targetPosition.longitude - currentLng;
                incrementLat = (diffLat / (Math.abs(diffLong) + Math.abs(diffLat))) * (pace + paceShift) * paceSpeed;
                incrementLong = (diffLong / (Math.abs(diffLong) + Math.abs(diffLat))) * (pace + paceShift) * paceSpeed;

                if (Math.abs(incrementLat) > 2 * pace * paceSpeed ||
                        Math.abs(incrementLong) > 2 * pace * paceSpeed) {
                    Log.w(TAG, "Calculate next increment of lat or long too high, abort it");
                    Log.w(TAG, "incrementLat = " + incrementLat + ", incrementLong = " + incrementLong);
                    Log.w(TAG, "incrementLat bound = " + 2 * pace * paceSpeed + ", incrementLong bound = " + 2 * pace * paceSpeed);
                    break;
                }

                sendAndApplyLocation(currentLat + incrementLat, currentLng + incrementLong);

                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    break;
                }
            }

            Log.d(TAG, "Auto pilot has sent you home.");
            if (listener != null)
                listener.onNavigationComplete();

            isAutoPilot = false;
        }
    }

    public interface OnNavigationCompleteListener {
        void onNavigationComplete();
    }
}
