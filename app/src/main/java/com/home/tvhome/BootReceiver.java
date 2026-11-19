package com.home.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** "Start When the TV Turns On": opens TV Home once the TV has started, if you switched that on. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!c.getSharedPreferences("tvhome", Context.MODE_PRIVATE).getBoolean("start_on_boot", false)) return;
        try {
            c.startActivity(new Intent(c, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (Exception ignored) { }
    }
}
