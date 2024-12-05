package com.mumu.locationmocker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "PokemonGoGo";
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;

        Button mStartServiceButton;
        mStartServiceButton = (Button)findViewById(R.id.buttonStartService);
        mStartServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startChatHeadService();
            }
        });

        Button mStartMapView;
        mStartMapView = (Button)findViewById(R.id.buttonMapView);
        mStartMapView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startMapView();
            }
        });

        requestPermissions();
    }

    @Override
    protected void onStop() {

        Log.d(TAG, "on onStop ++");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "on onStop --");

        super.onStop();
    }

    private void startMapView() {
        Intent mapIntent = new Intent(mContext, MapLocationViewer.class);
        mapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(mapIntent);
    }

    private void startChatHeadService() {
        if (Build.VERSION.SDK_INT >= 23) {
            Toast.makeText(MainActivity.this, "Text", Toast.LENGTH_SHORT).show();
            if (!Settings.canDrawOverlays(MainActivity.this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 10);
                Log.d(TAG, "No permission for drawing on screen, prompt one.");

            } else {
                Toast.makeText(MainActivity.this, "How to stop", Toast.LENGTH_SHORT).show();
                startService(new Intent(mContext, HeadService.class));
                returnHomeScreen();
            }
        } else {
            Log.d(TAG, "Permission granted, starting service.");
            Toast.makeText(MainActivity.this, "stop", Toast.LENGTH_SHORT).show();
            startService(new Intent(mContext, HeadService.class));
            returnHomeScreen();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 10) {
            if (Build.VERSION.SDK_INT >= 23) {
                if (!Settings.canDrawOverlays(this)) {
                    // SYSTEM_ALERT_WINDOW permission not granted
                    Toast.makeText(MainActivity.this, "permission fail", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void requestPermissions() {
        if (ActivityCompat.checkSelfPermission(mContext, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(mContext, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION}, 200);
        }
    }

    @Override
    public void onRequestPermissionsResult(int permsRequestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(permsRequestCode, permissions, grantResults);
        switch (permsRequestCode) {
            case 200:
                boolean writeAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                if (writeAccepted) {
                    Log.d(TAG, "User gave us permission to write sdcard");
                } else {
                    Toast.makeText(this, "R.string.msg_no_sdcard_perms", Toast.LENGTH_LONG).show();
                    Log.w(TAG, "User didn't give us permission to write sdcard");
                }
                break;
            default:
                Toast.makeText(this, "No handle permission grant", Toast.LENGTH_LONG).show();
        }
    }

    private void returnHomeScreen() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        startActivity(intent);
    }
}