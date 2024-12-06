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

package com.mumu.locationmocker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMyLocationButtonClickListener;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.mumu.locationmocker.location.IntentLocationManager;
import com.mumu.locationmocker.service.HeadService;

import java.text.DecimalFormat;

public class MapLocationViewer extends AppCompatActivity
        implements
        OnMyLocationButtonClickListener,
        OnMapReadyCallback,
        LocationListener,
        ActivityCompat.OnRequestPermissionsResultCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "PokemonGoGo";

    private boolean mPermissionDenied = false;
    private boolean mCameraTracking = false;
    private GoogleMap mMap;
    private LatLng mUserSelectPoint;
    private LongPressLocationSource mLocationSource;
    private LocationManager mLocationManager;
    private FusedLocationProviderClient mFusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_location_viewer);

        mLocationSource = new LongPressLocationSource();
        mUserSelectPoint = null;

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_finish) {
            if (mUserSelectPoint == null) {
                Toast.makeText(this, getString(R.string.msg_map_no_point), Toast.LENGTH_SHORT).show();
            } else {
                final Intent intent = new Intent(this, HeadService.class);
                intent.setAction(HeadService.ACTION_HANDLE_NAVIGATION);
                intent.putExtra(HeadService.EXTRA_DATA, mUserSelectPoint);
                startService(intent);
            }
            return true;
        } else if (id == R.id.action_cancel) {
            finish();
            return true;
        } else if (id == R.id.action_teleport) {
            if (mUserSelectPoint == null) {
                Toast.makeText(this, getString(R.string.msg_map_no_point), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.msg_map_shu), Toast.LENGTH_SHORT).show();
                final Intent intent = new Intent(this, HeadService.class);
                intent.setAction(HeadService.ACTION_HANDLE_TELEPORT);
                intent.putExtra(HeadService.EXTRA_DATA, mUserSelectPoint);
                startService(intent);
            }
            return true;
        } else if (id == R.id.action_camera_track) {
            StringBuilder sb = new StringBuilder();

            mCameraTracking = !mCameraTracking;
            sb.append("Now ");
            sb.append(mCameraTracking ? "will " : "will not ");
            sb.append("tracking location in map camera viewport.");
            Toast.makeText(this, sb.toString(), Toast.LENGTH_SHORT).show();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mLocationManager != null) {
            mLocationManager.removeUpdates(this);
        }
        if (mFusedLocationClient != null) {
            mFusedLocationClient.removeLocationUpdates(mFusedLocationCallback);
        }
    }

    private void printLocationLog(String tag, Location location) {
        StringBuilder sb = new StringBuilder();
        DecimalFormat df = new DecimalFormat("0.000000");
        sb.append(tag);
        sb.append(": ");
        sb.append("<");
        sb.append(df.format(location.getLatitude()));
        sb.append(",");
        sb.append(df.format(location.getLongitude()));
        sb.append("> acc: ");
        sb.append(df.format(location.getAccuracy()));
        Log.d(TAG, sb.toString());
    }

    @Override
    public void onLocationChanged(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        IntentLocationManager ilm = AppSharedObject.get().getIntentLocationManager();
        if (ilm != null && !ilm.hasOriginalLocation()) {
            ilm.setOriginalLocation(location);
        }
        printLocationLog("Loc", location);

        LatLng latLng = new LatLng(latitude, longitude);
        if (mMap != null && mCameraTracking) {
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(15));
        }
    }

    LocationCallback mFusedLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            for (Location location : locationResult.getLocations()) {
                // Use location data
                printLocationLog("Fus", location);
            }
        }
    };

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    /**
     * A {@link LocationSource} which reports a new location whenever a user long presses the map
     * at the point at which a user long pressed the map.
     */
    private class LongPressLocationSource implements GoogleMap.OnMapLongClickListener {

        @Override
        public void onMapLongClick(@NonNull LatLng point) {
            Log.d(TAG, "User hit LAT = " + point.latitude + " and LONG = " + point.longitude);
            mUserSelectPoint = point;
            mMap.clear();
            mMap.addMarker(new MarkerOptions().position(mUserSelectPoint).title("Marker"));
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        mMap = map;
        mMap.setOnMyLocationButtonClickListener(this);
        mMap.setOnMapLongClickListener(mLocationSource);

        enableLocationUpdate();
        enableFusedLocationUpdate();
        enableMyLocation();
    }

    /**
     * Enables the My Location layer if the fine location permission has been granted.
     */
    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            PermissionUtils.requestPermission(this, LOCATION_PERMISSION_REQUEST_CODE,
                    Manifest.permission.ACCESS_FINE_LOCATION, true);
        } else if (mMap != null) {
            mMap.setMyLocationEnabled(true);
        }
    }

    private void enableLocationUpdate() {
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        String bestProvider = mLocationManager.getBestProvider(criteria, true);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Location location = mLocationManager.getLastKnownLocation(bestProvider);
        if (location != null) {
            onLocationChanged(location);
        }
        mLocationManager.requestLocationUpdates(bestProvider, 2000, 0, this);
    }

    private void enableFusedLocationUpdate() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        // Use location data
                        double latitude = location.getLatitude();
                        double longitude = location.getLongitude();
                        printLocationLog("Fus", location);
                    } else {
                        Log.d(TAG, "No last known location available");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("FusedLocation", "Failed to get location: " + e.getMessage());
                });
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(2000); // 10 seconds
        locationRequest.setFastestInterval(1000); // 5 seconds
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        mFusedLocationClient.requestLocationUpdates(locationRequest, mFusedLocationCallback, Looper.getMainLooper());
    }

    @Override
    public boolean onMyLocationButtonClick() {
        Toast.makeText(this, getString(R.string.msg_map_locating), Toast.LENGTH_SHORT).show();
        // Return false so that we don't consume the event and the default behavior still occurs
        // (the camera animates to the user's current position).
        return false;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            return;
        }

        if (PermissionUtils.isPermissionGranted(permissions, grantResults,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Enable the my location layer if the permission has been granted.
            enableMyLocation();
        } else {
            // Display the missing permission error dialog when the fragments resume.
            mPermissionDenied = true;
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        if (mPermissionDenied) {
            // Permission was not granted, display error dialog.
            showMissingPermissionError();
            mPermissionDenied = false;
        }
    }

    /**
     * Displays a dialog with error message explaining that the location permission is missing.
     */
    private void showMissingPermissionError() {
        PermissionUtils.PermissionDeniedDialog
                .newInstance(true).show(getSupportFragmentManager(), "dialog");
    }
}