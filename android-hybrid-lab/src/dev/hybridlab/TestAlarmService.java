package dev.hybridlab;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;

public final class TestAlarmService extends Service {
    static final int NOTIFICATION = 734;
    private static final String CHANNEL = "test_alarm_controls";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    static volatile boolean audioStarted;
    private final Runnable timeout = this::stopSelf;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) { stopSelf(); return START_NOT_STICKY; }
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "منبّه التجربة", NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
        if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL).getImportance() == NotificationManager.IMPORTANCE_NONE) {
            stopSelf(); return START_NOT_STICKY;
        }
        int minutes = intent.getIntExtra("snooze", 5);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.lab_icon).setContentTitle(intent.getStringExtra("title"))
                .setContentText("منبّه تجريبي • مختبر التطبيقات").setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "إيقاف", action(intent, TestAlarm.STOP, 735)).build())
                .addAction(new Notification.Action.Builder(null, "غفوة " + minutes + " د", action(intent, TestAlarm.SNOOZE, 736)).build())
                .build();
        startForeground(NOTIFICATION, notification);
        handler.removeCallbacks(timeout);
        releasePlayer();
        if (!play(TestAlarm.tone(intent))) play(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
        handler.postDelayed(timeout, 5 * 60_000L);
        return START_NOT_STICKY;
    }

    private PendingIntent action(Intent original, String action, int code) {
        Intent command = new Intent(this, TestAlarmReceiver.class).setAction(action).putExtras(original);
        return PendingIntent.getBroadcast(this, code, command, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private boolean play(android.net.Uri tone) {
        if (tone == null) return false;
        player = new MediaPlayer();
        try {
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.setDataSource(this, tone);
            player.setLooping(true);
            player.prepare();
            player.start();
            audioStarted = player.isPlaying();
            return true;
        } catch (Exception ex) {
            releasePlayer();
            android.util.Log.w("HybridTestAlarm", "Alarm audio could not start", ex);
            return false;
        }
    }

    private void releasePlayer() {
        audioStarted = false;
        if (player != null) { player.release(); player = null; }
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(timeout);
        releasePlayer();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
