package com.mumu.locationmocker.location;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.util.Log;

import androidx.annotation.NonNull;

public class LocationPoller implements LocationListener {
    private static final String TAG = "LocationPoller";
    private Context mContext;
    LocationManager mLocationManager;
    private Location mLastLocation;
    private LocationCallback mCallback;

    public LocationPoller(Context context) {
        mContext = context;
        mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        Log.d(TAG, "維度:"+ location.getLatitude() +
                " 經度:"+ location.getLongitude());
        if (mLastLocation != null) {
            float[] distance = new float[3];
            Location.distanceBetween(mLastLocation.getLatitude(), mLastLocation.getLongitude(),
                    location.getLatitude(), location.getLongitude(), distance);
            if (mCallback != null)
                mCallback.onMoveDistance(distance[0]);
        }
        mLastLocation = location;
        if (mCallback != null)
            mCallback.onNewLocation(mLastLocation);
    }

    @SuppressLint("MissingPermission")
    public void startPolling(LocationCallback callback) {
        mLastLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 10, this);
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 100, 10, this);
        mLocationManager.requestLocationUpdates(LocationManager.FUSED_PROVIDER, 100, 10, this);
        mCallback = callback;
        Log.d(TAG, "start polling LAT:"+ mLastLocation.getLatitude() +
                " LONG:"+ mLastLocation.getLongitude());
    }

    public void stopPolling() {
        mLocationManager.removeUpdates(this);
    }

    public interface LocationCallback {
        void onNewLocation(Location location);
        void onMoveDistance(float distance);
    }
}
