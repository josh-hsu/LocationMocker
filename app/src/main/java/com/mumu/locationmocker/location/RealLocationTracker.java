package com.mumu.locationmocker.location;

import static android.content.Context.LOCATION_SERVICE;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.mumu.locationmocker.AppSharedObject;

import java.text.DecimalFormat;

public class RealLocationTracker implements LocationListener {
    private final String TAG = "PokemonGoGo";
    private final boolean mEnableVerbose = false;

    private final Context mContext;
    private final LocationManager mLocationManager;
    private final FusedLocationProviderClient mFusedLocationClient;
    private final LocationCallback mFusedLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            for (Location location : locationResult.getLocations()) {
                // Use location data
                printLocationLog("Fus", location);
                mFusedLocation = location;
                mFusedCallbackTime = System.currentTimeMillis();
            }
        }
    };

    private Location mGpsLocation;
    private Location mFusedLocation;
    private long mGpsCallbackTime;
    private long mFusedCallbackTime;

    public RealLocationTracker(Context context) {
        mContext = context;
        mLocationManager = (LocationManager) mContext.getSystemService(LOCATION_SERVICE);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        IntentLocationManager ilm = AppSharedObject.get().getIntentLocationManager();
        if (ilm != null && !ilm.hasOriginalLocation()) {
            ilm.setOriginalLocation(location);
        }
        printLocationLog("Loc", location);
        mGpsLocation = location;
        mGpsCallbackTime = System.currentTimeMillis();
    }

    public void startListening() {
        Log.d(TAG, "RealLocationTracker: start listening");
        // GPS real location
        String gpsProvider = LocationManager.GPS_PROVIDER;
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mLocationManager.requestLocationUpdates(gpsProvider, 1000, 0, this);
        mGpsLocation = mLocationManager.getLastKnownLocation(gpsProvider);

        // Fused location
        LocationRequest locationRequest = new LocationRequest.Builder(
                2000).build();
        mFusedLocationClient.requestLocationUpdates(locationRequest, mFusedLocationCallback, Looper.getMainLooper());
    }

    public void stopListening() {
        Log.d(TAG, "RealLocationTracker: stop listening");
        mLocationManager.removeUpdates(this);
        mFusedLocationClient.removeLocationUpdates(mFusedLocationCallback);
    }

    private void printLocationLog(String tag, Location location) {
        DecimalFormat df = new DecimalFormat("0.000000");
        String sb = tag +
                ": " +
                "<" +
                df.format(location.getLatitude()) +
                "," +
                df.format(location.getLongitude()) +
                "> acc: " +
                df.format(location.getAccuracy());
        if (mEnableVerbose)
            Log.d(TAG, sb);
    }

    public String getLocationString(Location location) {
        DecimalFormat df = new DecimalFormat("0.00000");
        return df.format(location.getLatitude()) + ", " + df.format(location.getLongitude());
    }

    public Location getLastGpsLocation() {
        return mGpsLocation;
    }

    public long getLastGpsLocationElapsedTimeMs() {
        if (mGpsLocation == null)
            return 0;
        return System.currentTimeMillis() - mGpsLocation.getTime();
    }

    public long getLastGpsLocationCallbackTimeMs() {
        if (mGpsLocation == null)
            return 0;
        return System.currentTimeMillis() - mGpsCallbackTime;
    }

    public String getLastGpsLocationElapsedTimeStr() {
        DecimalFormat df = new DecimalFormat("0.0");
        return df.format((getLastGpsLocationElapsedTimeMs() / 1000));
    }

    public Location getLastFusedLocation() {
        return mFusedLocation;
    }

    public long getLastFusedLocationElapsedTimeMs() {
        if (mFusedLocation == null)
            return 0;
        return System.currentTimeMillis() - mFusedLocation.getTime();
    }

    public String getLastFusedLocationElapsedTimeStr() {
        DecimalFormat df = new DecimalFormat("0.0");
        return df.format((getLastFusedLocationElapsedTimeMs() / 1000));
    }

    public long getLastFusedLocationCallbackTimeMs() {
        if (mFusedLocation == null)
            return 0;
        return System.currentTimeMillis() - mFusedCallbackTime;
    }
}
