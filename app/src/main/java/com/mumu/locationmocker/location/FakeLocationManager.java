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

package com.mumu.locationmocker.location;

import static android.location.LocationManager.FUSED_PROVIDER;
import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;
import static android.location.LocationManager.PASSIVE_PROVIDER;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.provider.ProviderProperties;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

public class FakeLocationManager {
    private final static String TAG = "PokemonGoGo";
    private Context mContext;
    private double mCurrentLat = 25.0335;
    private double mCurrentLong = 121.5642;
    private double mCurrentAlt = 10.2;
    private double mCurrentAccuracy = 6.91;
    private FakeLocation mCurrentFakeLocation;
    private double mAutoLat = -1;
    private double mAutoLong = -1;
    private static final double mPaceLat = 0.000038;
    private static final double mPaceLong = 0.000039;
    private static double mPaceLatShift = 0.000001;
    private static double mPaceLongShift = 0.000001;
    private static double mSpeed = 1;
    private static boolean mIsAutoPilot = false;
    private static boolean mIsAutoPilotInterrupter = true;
    private OnNavigationCompleteListener mOnNavigationCompleteListener = null;
    private static FakeLocationManager mSelf;
    LocationManager mLocationManager;

    private LocationUpdater mUpdateThread;
    private boolean mKeepUpdating = false;

    public FakeLocationManager(Context context, FakeLocation defaultLoc) {
        FakeLocation defaultLocation;
        mContext = context;
        mCurrentFakeLocation = new FakeLocation(mCurrentLat, mCurrentLong, mCurrentAlt, mCurrentAccuracy);

        // Start fetch information from framework hacking
        mLocationManager = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
        boolean shouldUseLastLocation = initLastLocation();

        if (defaultLoc != null) {
            defaultLocation = defaultLoc;
        } else if (shouldUseLastLocation) {
            defaultLocation = new FakeLocation(mCurrentLat, mCurrentLong, mCurrentAlt, mCurrentAccuracy);
        } else {
            defaultLocation = new FakeLocation(25.0335, 121.5642, 10.2, 6.91); //this is the location of Taipei 101
        }

        // Override location now, maybe in the future we should restore actual location
        setDefaultLocation(defaultLocation);

        mSelf = this;
    }

    private boolean initLastLocation() {
        LocationManager lm = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
        String networkProvider = LocationManager.NETWORK_PROVIDER;

        if (lm == null) {
            Log.e(TAG, "Mock Location Provider is null");
            return false;
        }
        @SuppressLint("MissingPermission") Location gpsLastLocation = lm.getLastKnownLocation(GPS_PROVIDER);
        @SuppressLint("MissingPermission") Location networkLastLocation = lm.getLastKnownLocation(networkProvider);

        if (gpsLastLocation != null) {
            Log.d(TAG, "gps " + gpsLastLocation);
            setMockLocation(gpsLastLocation);
            Toast.makeText(mContext, "Apply GPS location", Toast.LENGTH_SHORT).show();
        }
        if (networkLastLocation != null) {
            Log.d(TAG, "net: " + networkLastLocation);
            setMockLocation(networkLastLocation);
            Toast.makeText(mContext, "Apply network location", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    public FakeLocation getCurrentLocation() {
        return mCurrentFakeLocation;
    }

    //TODO: fix
    public static FakeLocation getCurrentLocationStatic() {
        FakeLocation defaultLocation = new FakeLocation(25.0335, 121.5642, 2.0, 10.4);
        if (mSelf != null)
            return mSelf.getCurrentLocation();
        else
            return defaultLocation;
    }

    public double getDistance(FakeLocation start, FakeLocation end) {
        float[] results = new float[1];

        if (start != null && end != null) {
            Location.distanceBetween(start.latitude, start.longitude,
                    end.latitude, end.longitude, results);
            return results[0];
        }

        return 0.0;
    }

    /*
     * This function check if current location is out of bound of give radius and origin
     * returns currentDirection if not out of bound or it will return the opposite direction
     */
    public int getNewDirectionInBound(FakeLocation origin, double radius, int currentDirection) {
        if (origin == null) {
            Log.e(TAG, "cannot get new direction in bound, origin is null");
            return currentDirection;
        }

        // When we out of bound
        double ori_lat = origin.latitude;
        double ori_lng = origin.longitude;

        if (getDistance(getCurrentLocation(), origin) > radius) {
            if ((mCurrentLat - ori_lat) > 0 && (mCurrentLong - ori_lng) > 0)
                return FakeLocation.WESTSOUTH;

            if ((mCurrentLat - ori_lat) > 0 && (mCurrentLong - ori_lng) < 0)
                return FakeLocation.SOUTHEAST;

            if ((mCurrentLat - ori_lat) < 0 && (mCurrentLong - ori_lng) > 0)
                return FakeLocation.NORTHWEST;

            if ((mCurrentLat - ori_lat) < 0 && (mCurrentLong - ori_lng) < 0)
                return FakeLocation.EASTNORTH;
        }
        return currentDirection;
    }

    // Setters
    public void setEnable(boolean enable) {
        if (enable) {
            if (mUpdateThread != null) {
                mUpdateThread.interrupt();
            }
            mKeepUpdating = true;
            mUpdateThread = new LocationUpdater(this);
            mUpdateThread.start();
        } else {
            if (mUpdateThread != null) {
                mUpdateThread.interrupt();
            }
            mKeepUpdating = false;
        }
    }

    private void setDefaultLocation(FakeLocation loc) {
        if (loc == null) {
            Log.e(TAG, "Cannot set default location null");
        } else {
            setLocation(loc);
        }
    }

    private void setMockLocation(Location location) {
        FakeLocation fakeLocation = new FakeLocation(location.getLatitude(), location.getLongitude(),
                location.getAltitude(), location.getAccuracy());
        setLocation(fakeLocation);
    }

    private void setMockLocation(FakeLocation fakeLocation) {
        try {
            String[] providers = {GPS_PROVIDER, NETWORK_PROVIDER, FUSED_PROVIDER};
            Location mockLocation;

            for(String provider : providers) {
                mLocationManager.addTestProvider(provider, false, false, false,
                        false, false, true, true,
                        ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_FINE);
                mLocationManager.setTestProviderEnabled(provider, true);

                mockLocation = new Location(provider);
                mockLocation.setLatitude(fakeLocation.latitude);
                mockLocation.setLongitude(fakeLocation.longitude);
                mockLocation.setAltitude(fakeLocation.altitude);
                mockLocation.setTime(System.currentTimeMillis());
                mockLocation.setAccuracy(1);
                mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                mLocationManager.setTestProviderLocation(provider, mockLocation);
            }
        }  catch (IllegalArgumentException e) {
            Log.d(TAG, "set mock location failed: " + e.getLocalizedMessage());
        }

        mCurrentFakeLocation = fakeLocation;
    }

    private void commitCurrentLocation() {
        FakeLocation fakeLocation = new FakeLocation(mCurrentLat, mCurrentLong, mCurrentAlt, mCurrentAccuracy);
        setMockLocation(fakeLocation);
    }

    public void setLocation(FakeLocation loc) {
        Log.d(TAG, "Set location " + loc.toString());
        mCurrentLat = loc.latitude;
        mCurrentLong = loc.longitude;
        mCurrentAlt = loc.altitude;
        mCurrentAccuracy = loc.accuracy;

        setMockLocation(loc);
    }

    public void setSpeed(double speed) {
        if (speed > 0.0)
            mSpeed = speed;
        else
            Log.w(TAG, "Unsupported speed");
    }

    public void setOnNavigationCompleteListener(OnNavigationCompleteListener o) {
        mOnNavigationCompleteListener = o;
    }

    // main functions
    public void autoPilotTo(double targetLat, double targetLong, boolean interruptible) {
        mIsAutoPilot = true;
        mIsAutoPilotInterrupter = interruptible;
        mAutoLat = targetLat;
        mAutoLong = targetLong;
        new AutoPilotThread().start();
    }

    public void cancelAutoPilot() {
        mIsAutoPilot = false;
    }

    public void walkPace(int direction, double ratio) {
        double coordinateChange;
        // must introduce random variable
        controlRandomShift();

        // ratio must be within 0.0 ~ 1.0
        if (ratio > 1.0 || ratio < 0.0) {
            Log.e(TAG, "Unacceptable ratio " + ratio + " set to 1.0");
            ratio = 1.0;
        }

        // if auto pilot is on going, cancel it if interruptible
        if (mIsAutoPilot && mIsAutoPilotInterrupter) {
            Log.w(TAG, "Auto pilot is in progress, cancel auto pilot first");
            mIsAutoPilot = false;
        }

        switch (direction) {
            case FakeLocation.EAST:
                coordinateChange = (mPaceLong + mPaceLongShift) * mSpeed;
                mCurrentLong += coordinateChange * ratio;
                mCurrentLat += coordinateChange * (1 - ratio);
                break;
            case FakeLocation.WEST:
                coordinateChange = (mPaceLong + mPaceLongShift) * mSpeed;
                mCurrentLong -= coordinateChange * ratio;
                mCurrentLat -= coordinateChange * (1 - ratio);
                break;
            case FakeLocation.NORTH:
                coordinateChange = (mPaceLat + mPaceLatShift) * mSpeed;
                mCurrentLat += coordinateChange * ratio;
                mCurrentLong += coordinateChange * (1 - ratio);
                break;
            case FakeLocation.SOUTH:
                coordinateChange = (mPaceLat + mPaceLatShift) * mSpeed;
                mCurrentLat -= coordinateChange * ratio;
                mCurrentLong -= coordinateChange * (1 - ratio);
                break;
            case FakeLocation.NORTHWEST:
                coordinateChange = (mPaceLat + mPaceLongShift) * mSpeed;
                mCurrentLong -= coordinateChange * 0.5;
                mCurrentLat += coordinateChange * 0.5;
                break;
            case FakeLocation.WESTSOUTH:
                coordinateChange = (mPaceLong + mPaceLongShift) * mSpeed;
                mCurrentLong -= coordinateChange * 0.5;
                mCurrentLat -= coordinateChange * 0.5;
                break;
            case FakeLocation.SOUTHEAST:
                coordinateChange = (mPaceLat + mPaceLatShift) * mSpeed;
                mCurrentLat -= coordinateChange * 0.5;
                mCurrentLong += coordinateChange * 0.5;
                break;
            case FakeLocation.EASTNORTH:
                coordinateChange = (mPaceLong + mPaceLongShift) * mSpeed;
                mCurrentLong += coordinateChange * 0.5;
                mCurrentLat += coordinateChange * 0.5;
                break;
            case FakeLocation.STAY:
                return;
            default:
                Log.d(TAG, "Not supported direction");
                return;
        }

        commitCurrentLocation();
    }

    private void controlRandomShift() {
        // shift is controlled to be within -0.000001 ~ 0.000001
        double shift = Math.random() / 1000000 - 0.0000005;
        double accShift = Math.random() * 2 - 1;
        mPaceLatShift = mPaceLatShift + shift;
        mPaceLongShift = mPaceLongShift + shift;
        mCurrentAccuracy += accShift;

        if (mPaceLatShift > 0.000002 || mPaceLatShift < -0.000002)
            mPaceLatShift = 0.000001;

        if (mPaceLongShift > 0.000002 || mPaceLongShift < -0.000002)
            mPaceLongShift = 0.000001;

        if (mCurrentAccuracy > 9.9 || mCurrentAccuracy < 1.5)
            mCurrentAccuracy = 5.2;
    }

    private class LocationUpdater extends Thread {
        private FakeLocationManager flm;

        public LocationUpdater(FakeLocationManager m) {
            flm = m;
        }
        @Override
        public void run() {
            while (mKeepUpdating) {
                flm.commitCurrentLocation();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    mKeepUpdating = false;
                    return;
                }
            }
        }
    }

    // Threading runnable
    private class AutoPilotThread extends Thread {
        @Override
        public void run() {
            Log.d(TAG, "Start auto piloting .. ");
            double diffLat, diffLong, incrementLat, incrementLong;
            while(mIsAutoPilot
                    && !(Math.abs(mCurrentLat - mAutoLat) < mPaceLat)
                    && !(Math.abs(mCurrentLong - mAutoLong) < mPaceLong)) {

                controlRandomShift();
                diffLat = mAutoLat - mCurrentLat;
                diffLong = mAutoLong - mCurrentLong;
                incrementLat = (diffLat / (Math.abs(diffLong) + Math.abs(diffLat))) * (mPaceLat + mPaceLatShift) * mSpeed;
                incrementLong = (diffLong / (Math.abs(diffLong) + Math.abs(diffLat))) * (mPaceLong + mPaceLongShift) * mSpeed;

                if (Math.abs(incrementLat) > 2 * mPaceLat * mSpeed ||
                        Math.abs(incrementLong) > 2 * mPaceLong * mSpeed) {
                    Log.w(TAG, "Calculate next increment of lat or long too high, abort it");
                    Log.w(TAG, "incrementLat = " + incrementLat + ", incrementLong = " + incrementLong);
                    Log.w(TAG, "incrementLat bound = " + 2 * mPaceLat * mSpeed + ", incrementLong bound = " + 2 * mPaceLong * mSpeed);
                    break;
                }

                mCurrentLong += incrementLong;
                mCurrentLat += incrementLat;

                commitCurrentLocation();

                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            Log.d(TAG, "Auto pilot has sent you home.");
            if (mOnNavigationCompleteListener != null)
                mOnNavigationCompleteListener.onNavigationComplete();

            mIsAutoPilot = false;
        }
    }

    /*
     * Interfaces
     */
    public interface OnNavigationCompleteListener {
        void onNavigationComplete();
    }
}
