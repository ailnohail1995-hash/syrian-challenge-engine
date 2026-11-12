package dev.hybridlab;

import android.app.*;
import android.content.*;
import android.os.Build;

public final class AlarmReceiver extends BroadcastReceiver {
    static final String CHANNEL = "hybrid_lab_reminders";

    @Override public void onReceive(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "تنبيهات التطبيقات", NotificationManager.IMPORTANCE_HIGH);
        manager.createNotificationChannel(channel);
        int id = intent.getIntExtra("id", 1);
        PendingIntent launch = PendingIntent.getActivity(context, id,
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        manager.notify(id, new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.lab_icon).setContentTitle(intent.getStringExtra("title"))
                .setContentText(intent.getStringExtra("body")).setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(launch).setAutoCancel(true).build());
    }
}
