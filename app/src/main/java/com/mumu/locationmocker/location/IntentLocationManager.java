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
    private final IntentPropertyImpl mIntentPropImpl;

    private double mCurrentLat = 25.0335;
    private double mCurrentLong = 121.5642;
    private double mCurrentAlt = 10.2;
    private float  mCurrentAccuracy = 6.91f;
    private float  mCurrentBearing = 30.0f;
    private float  mCurrentSpeed = 0.3f;
    private Location mOriginalLocation;

    private double mPaceAmount = 0.000002;
    private double mPaceShift = 0.0000001;
    private double mPaceSpeed = 10;

    private AutoPilot mAutoPilot;
    private MockLocationListener mMockListenerClient;

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

    public void sendMock(boolean enable) {
        mIntentPropImpl.sendMock(enable ? "1" : "0");
    }

    @Override
    public void onJoystickMoved(float xPercent, float yPercent) {
        if (mAutoPilot != null) {
            mAutoPilot.cancelPilot();
        }
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
        if (mMockListenerClient != null) {
            mMockListenerClient.onMockLocation(new LatLng(mCurrentLat, mCurrentLong));
        }
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

    public void setMockListener(MockLocationListener listener) {
        mMockListenerClient = listener;
    }

    public void removeMockListener() {
        mMockListenerClient = null;
    }

    public boolean hasOriginalLocation() {
        return mOriginalLocation != null;
    }

    private void controlRandomShift() {
        // shift is controlled to be within -0.000001 ~ 0.000001
        double shift = Math.random() / 10000000 - 0.00000005;
        float accShift = (float) (Math.random() * 2 - 1);
        mPaceShift = mPaceShift + shift;
        mCurrentAccuracy += accShift;

        if (mPaceShift > 0.0000002 || mPaceShift < -0.0000002)
            mPaceShift = 0.0000001;

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
        OnNavigationCompleteListener autoPilotListener = () -> {
            Log.d(TAG, "receive navigation done. unset pilot");
            mAutoPilot = null;
            l.onNavigationComplete();
        };

        if (mAutoPilot != null) {
            mAutoPilot.cancelPilot();
            mAutoPilot = null;
        }

        mAutoPilot = new AutoPilot(this, latLng, mPaceAmount, mPaceSpeed, autoPilotListener);
        mAutoPilot.startPilot();
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
        LatLng currentPosition;
        IntentLocationManager ilm;
        OnNavigationCompleteListener listener;

        public AutoPilot(IntentLocationManager lm, LatLng target, double p, double spd, OnNavigationCompleteListener l) {
            ilm = lm;
            targetPosition = target;
            pace = p;
            paceSpeed = spd;
            listener = l;
        }

        private void sendAndApplyLocation(double lat, double lng) {
            ilm.sendLocation(lat, lng);
            ilm.applyLocation();
        }

        public void setPilotSpeed(double spd) {
            paceSpeed = spd;
        }

        public void startPilot() {
            isAutoPilot = true;
            start();
        }

        public void cancelPilot() {
            isAutoPilot = false;
            interrupt();
        }

        @Override
        public void run() {
            currentPosition = ilm.getLocation();
            double diffLat, diffLng, incLat, incLng;

            Log.d(TAG, "Start auto piloting from <" + currentPosition.latitude + "," + currentPosition.longitude + "> to <" +
                    targetPosition.latitude + "," + targetPosition.longitude + ">");

            while(isAutoPilot) {
                double currentLat = currentPosition.latitude;
                double currentLng = currentPosition.longitude;

                if ((Math.abs(currentPosition.latitude - targetPosition.latitude) < pace)
                        || (Math.abs(currentPosition.longitude - targetPosition.longitude) < pace)) {
                    break;
                }

                diffLat = targetPosition.latitude - currentLat;
                diffLng = targetPosition.longitude - currentLng;
                double normalizeAmount = (Math.abs(diffLng) + Math.abs(diffLat));
                incLat = (diffLat / normalizeAmount) * (pace + paceShift) * paceSpeed;
                incLng = (diffLng / normalizeAmount) * (pace + paceShift) * paceSpeed;

                if (Math.abs(incLat) > 2 * pace * paceSpeed ||
                        Math.abs(incLng) > 2 * pace * paceSpeed) {
                    Log.w(TAG, "Calculate next increment of lat or long too high, abort it");
                    Log.w(TAG, "incrementLat = " + incLat + ", incrementLong = " + incLng);
                    Log.w(TAG, "incrementLat bound = " + 2 * pace * paceSpeed + ", incrementLong bound = " + 2 * pace * paceSpeed);
                    break;
                }

                sendAndApplyLocation(currentLat + incLat, currentLng + incLng);
                currentPosition = new LatLng(currentLat + incLat, currentLng + incLng);

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

    public interface MockLocationListener {
        void onMockLocation(LatLng location);
    }
}
