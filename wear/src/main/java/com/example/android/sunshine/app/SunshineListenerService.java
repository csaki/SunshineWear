package com.example.android.sunshine.app;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class SunshineListenerService extends WearableListenerService {
    public static final String PATH = "/sunshine";

    @Override
    public void onMessageReceived(MessageEvent ev) {
        if (ev.getPath().equals(PATH)) {
            String canonicalName = SunshineListenerService.class.getCanonicalName();
            Log.i(canonicalName, "onMessageReceived Watch");
            String message = new String(ev.getData());
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra("data", message);
            Log.i(canonicalName, message);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        } else {
            super.onMessageReceived(ev);
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        super.onDataChanged(dataEventBuffer);
        Log.i(SunshineListenerService.class.getCanonicalName(), "onDataChanged Watch");
    }
}
