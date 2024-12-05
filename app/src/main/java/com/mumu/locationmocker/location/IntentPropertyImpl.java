package com.mumu.locationmocker.location;

import android.content.Context;
import android.content.Intent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class IntentPropertyImpl {
    private final static String INTENT_ACTION = "com.mumu.pokemongogo.action.SETPROP";
    private Context mContext;

    public IntentPropertyImpl(Context context) {
        mContext = context;
    }

    public void setSystemProperty(String intent_property, String value) {
        Intent intent = new Intent(INTENT_ACTION);
        intent.putExtra(intent_property, value);
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
