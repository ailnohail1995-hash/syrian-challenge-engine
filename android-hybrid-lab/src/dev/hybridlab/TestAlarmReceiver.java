package dev.hybridlab;

import android.content.*;
import android.util.Log;

public final class TestAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (TestAlarm.RING.equals(action)) {
            try {
                context.startForegroundService(new Intent(context, TestAlarmService.class).putExtras(intent));
            } catch (IllegalStateException | SecurityException ex) {
                Log.e("HybridTestAlarm", "Android refused to start the test alarm", ex);
            }
        } else if (TestAlarm.STOP.equals(action)) {
            TestAlarm.cancel(context);
        } else if (TestAlarm.SNOOZE.equals(action)) {
            int minutes = intent.getIntExtra("snooze", 5);
            TestAlarm.cancel(context);
            try {
                TestAlarm.schedule(context, System.currentTimeMillis() + minutes * 60_000L,
                        intent.getStringExtra("title"), TestAlarm.tone(intent), minutes);
            } catch (SecurityException ex) {
                android.widget.Toast.makeText(context, "تعذّرت الغفوة. اسمح بالمنبّهات الدقيقة.", android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }
}
