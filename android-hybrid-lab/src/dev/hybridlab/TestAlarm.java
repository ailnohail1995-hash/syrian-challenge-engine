package dev.hybridlab;

import android.app.*;
import android.content.*;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

final class TestAlarm {
    static final String RING = "dev.hybridlab.test.RING";
    static final String STOP = "dev.hybridlab.test.STOP";
    static final String SNOOZE = "dev.hybridlab.test.SNOOZE";
    static final String[] EVENTS = {"بداية الحصة", "قبل نهاية الحصة بـ10 دقائق",
            "قبل نهاية الحصة بـ5 دقائق", "نهاية الحصة", "بداية الفرصة", "نهاية الفرصة"};

    static void schedule(Context context, long at, String title, Uri tone, int snooze) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms())
            throw new SecurityException("exact_alarm_permission_required");
        Intent request = new Intent(context, TestAlarmReceiver.class).setAction(RING)
                .putExtra("title", title).putExtra("tone", tone.toString()).putExtra("snooze", snooze);
        PendingIntent operation = PendingIntent.getBroadcast(context, 730, request,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent show = PendingIntent.getActivity(context, 731, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), operation);
    }

    static Uri fallback(Context context) {
        return Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.alarm_fallback);
    }

    static Uri tone(Intent intent) {
        String value = intent.getStringExtra("tone");
        return value == null ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) : Uri.parse(value);
    }

    static void cancel(Context context) {
        PendingIntent pending = PendingIntent.getBroadcast(context, 730,
                new Intent(context, TestAlarmReceiver.class).setAction(RING),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) {
            context.getSystemService(AlarmManager.class).cancel(pending);
            pending.cancel();
        }
        context.stopService(new Intent(context, TestAlarmService.class));
    }
}
