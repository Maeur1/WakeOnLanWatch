package com.example.mayur.wakeonlanwatch;

import android.widget.Toast;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by Mayur on 24/10/2015.
 */
public class ListenerService extends WearableListenerService {

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        MainActivity.PacketTask p = new MainActivity.PacketTask(getApplicationContext());
        p.execute();
        showToast("PC Turned On");
    }

    public void showToast(String message){
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
