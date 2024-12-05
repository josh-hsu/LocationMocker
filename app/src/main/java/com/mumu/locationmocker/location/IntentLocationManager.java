package com.mumu.locationmocker.location;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.mumu.locationmocker.service.JoystickView;

public class IntentLocationManager implements JoystickView.JoystickListener {
    private final String TAG = "PokemonGoGo";
    private Context mContext;
    private IntentPropertyImpl mIntentPropImpl;

    private double mCurrentLat = 25.0335;
    private double mCurrentLong = 121.5642;
    private double mCurrentAlt = 10.2;
    private float  mCurrentAccuracy = 6.91f;
    private float  mCurrentBearing = 30.0f;
    private float  mCurrentSpeed = 0.3f;

    private double mAutoLat = -1;
    private double mAutoLong = -1;
    private double mPaceAmount = 0.00007;
    private double mPaceShift = 0.000001;
    private double mPaceSpeed = 10;

    public IntentLocationManager(Context context) {
        mContext = context;
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
        walkPace(0.5, xPercent, -yPercent);
    }

    public void setPaceSpeed(double speed) {
        mPaceSpeed = speed;
    }

    private void applyLocation() {
        sendIntentLocation(mCurrentLat, mCurrentLong, mCurrentAlt, mCurrentAccuracy, mCurrentBearing, mCurrentSpeed);
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

    private void walkPace(double ratio, float x, float y) {
        // must introduce random variable
        controlRandomShift();

        // ratio must be within 0.0 ~ 1.0
        if (ratio > 1.0 || ratio < 0.0) {
            Log.e(TAG, "Unacceptable ratio " + ratio + " set to 1.0");
            ratio = 1.0;
        }

        Log.d(TAG, "x = " + x + ", y = " + y);
        double nextPace = (mPaceAmount + mPaceShift) * mPaceSpeed;
        mCurrentLat = mCurrentLat + nextPace * y;
        mCurrentLong = mCurrentLong + nextPace * x;
        applyLocation();
    }

}
