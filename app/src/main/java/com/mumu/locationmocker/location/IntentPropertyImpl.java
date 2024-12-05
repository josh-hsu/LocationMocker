package com.mumu.locationmocker.location;

import android.content.Context;
import android.content.Intent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class IntentPropertyImpl {
    private final static String INTENT_ACTION = "com.mumu.pokemongogo.action.SETPROP";
    private Context mContext;
    String EXTRA_EN  = "enable";
    String EXTRA_LAT = "lat";
    String EXTRA_LNG = "lng";
    String EXTRA_ALT = "alt";
    String EXTRA_ACC = "acc";
    String EXTRA_BER = "bear";
    String EXTRA_SPD = "speed";

    public IntentPropertyImpl(Context context) {
        mContext = context;
    }

    public void sendIntentProperty(String intent_property, String value) {
        Intent intent = new Intent(INTENT_ACTION);
        intent.putExtra(intent_property, value);
        mContext.sendBroadcast(intent);
    }

    public void sendLocation(String lat, String lng, String alt, String acc, String bear, String spd) {
        Intent intent = new Intent(INTENT_ACTION);
        intent.putExtra(EXTRA_EN, "1");
        intent.putExtra(EXTRA_LAT, lat);
        intent.putExtra(EXTRA_LNG, lng);
        intent.putExtra(EXTRA_ALT, alt);
        intent.putExtra(EXTRA_ACC, acc);
        intent.putExtra(EXTRA_BER, bear);
        intent.putExtra(EXTRA_SPD, spd);
        mContext.sendBroadcast(intent);
    }

    public static String getSystemProperty(String property) {
        return runCommand("getprop " + property);
    }

    /*
     * Run the specific command, you should not execute a command that will
     * cost more than 5 seconds.
     */
    public static String runCommand(String cmdInput){
        String retStr = "";
        BufferedReader output;

        String[] cmd = {"/system/bin/sh", "-c", cmdInput};
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            output = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            retStr = output.readLine();
            if (retStr == null) {
                retStr = "";
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return retStr;
    }
}
