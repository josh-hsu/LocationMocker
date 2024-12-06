package com.mumu.locationmocker;

import com.mumu.locationmocker.location.IntentLocationManager;

public class AppSharedObject {
    private static AppSharedObject mThis;
    private IntentLocationManager mIntentLocationManager;

    private AppSharedObject() {

    }

    public static AppSharedObject get() {
        if (mThis == null)
            mThis = new AppSharedObject();
        return mThis;
    }

    public void setIntentLocationManager(IntentLocationManager ilm) {
        mIntentLocationManager = ilm;
    }

    public IntentLocationManager getIntentLocationManager() {
        return mIntentLocationManager;
    }

}
