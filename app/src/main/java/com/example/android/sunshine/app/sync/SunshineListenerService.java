package com.example.android.sunshine.app.sync;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class SunshineListenerService extends WearableListenerService {
    public static final String PATH = "/sunshine_update";

    @Override
    public void onMessageReceived(MessageEvent ev) {
        Log.i(SunshineListenerService.class.getCanonicalName(), "onMessageReceived");
        if (ev.getPath().equals(PATH)) {
            SunshineSyncAdapter.syncImmediately(this);
        } else {
            super.onMessageReceived(ev);
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        super.onDataChanged(dataEventBuffer);
        Log.i(SunshineListenerService.class.getCanonicalName(), "onDataChanged");
    }
}
