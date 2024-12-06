package com.mumu.locationmocker;

import com.mumu.locationmocker.location.IntentLocationManager;
import com.mumu.locationmocker.service.TopUIController;

public class AppSharedObject {
    private static AppSharedObject mThis;
    private IntentLocationManager mIntentLocationManager;
    private TopUIController mTopUIController;

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

    public void setTopUIController(TopUIController uiController) {
        mTopUIController = uiController;
    }

    public TopUIController getTopUIController() {
        return mTopUIController;
    }

}
