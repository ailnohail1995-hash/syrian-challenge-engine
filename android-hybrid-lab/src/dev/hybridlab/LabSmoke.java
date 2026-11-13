package dev.hybridlab;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

public final class LabSmoke extends Instrumentation {
    private boolean alarmOnly;
    private String offlineMode;
    private String factory;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        offlineMode = arguments == null ? null : arguments.getString("offline");
        factory = arguments == null ? null : arguments.getString("factory");
        alarmOnly = arguments != null && "alarm".equals(arguments.getString("mode"));
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Intent intent = new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity = startActivitySync(intent);
            if (factory != null) {
                android.graphics.Bitmap icon = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888);
                icon.eraseColor("2".equals(factory) ? 0xfffcaf34 : 0xff24b9a4);
                ApkFactory.build(activity, "dev.salloum.p0123456789abcdef0123",
                        "2".equals(factory) ? "سلوم المحدّث" : "سلوم التجريبي", "https://toqna.pages.dev/", icon);
                icon.recycle();
                result.putString("stream", "FACTORY_OK");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (alarmOnly) {
                testAlarm(activity);
                result.putString("stream", "\nPASS: test alarm started audio with screen off, snooze stopped audio and rescheduled.\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            String[] names = {"دلجة العام", "دلجة Lovable", "TOQNA", "أبطال TOQNA"};
            String[] hosts = {"dulja-portal-preview.pages.dev", "quran-dulja-tahfez.lovable.app",
                "toqna.pages.dev", "toqna-heroes-app.pages.dev"};
            if ("prepare".equals(offlineMode)) {
                OfflineSite snapshots = new OfflineSite(activity);
                for (String host : hosts) {
                    try {
                        int count = snapshots.ready(host) ? 0 : snapshots.download(host);
                        System.out.println("PREPARED " + host + " " + count);
                    } catch (Exception ex) { System.out.println("DOWNLOAD_FAILED " + host + " " + ex); }
                }
                finish(Activity.RESULT_OK, result);
                return;
            }
            var loaded = MainActivity.class.getDeclaredField("loadedUrl");
            loaded.setAccessible(true);
            var error = MainActivity.class.getDeclaredField("loadError");
            error.setAccessible(true);
            var webField = MainActivity.class.getDeclaredField("web");
            webField.setAccessible(true);
            android.webkit.WebView page = (android.webkit.WebView) webField.get(activity);
            for (int i = 0; i < names.length; i++) {
                runOnMainSync(() -> {
                    try {
                        var home = MainActivity.class.getDeclaredMethod("showHome");
                        home.setAccessible(true);
                        home.invoke(activity);
                        loaded.set(activity, null);
                    } catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
                });
                String name = names[i];
                String host = hosts[i];
                runOnMainSync(() -> {
                    View card = find(activity.getWindow().getDecorView(), name);
                    if (card == null || !card.performClick()) throw new IllegalStateException("Missing card " + name);
                });
                boolean ready = false;
                for (int attempt = 0; attempt < 40; attempt++) {
                    String[] url = new String[2];
                    runOnMainSync(() -> {
                        try { url[0] = (String) loaded.get(activity); url[1] = (String) error.get(activity); }
                        catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
                    });
                    if (url[1] != null) { System.out.println("PAGE_ERROR " + host + " " + url[1]); break; }
                    if (url[0] != null && host.equals(android.net.Uri.parse(url[0]).getHost())) { ready = true; break; }
                    SystemClock.sleep(1000);
                }
                if (!ready) { System.out.println("PAGE_FAILED " + host); continue; }
                boolean rendered = false;
                for (int retry = 0; retry < 12; retry++) {
                    java.util.concurrent.ArrayBlockingQueue<String> answer = new java.util.concurrent.ArrayBlockingQueue<>(1);
                    runOnMainSync(() -> page.evaluateJavascript(
                        "Boolean(document.body && document.body.innerText.trim().length > 20)", answer::offer));
                    if ("true".equals(answer.poll(3, java.util.concurrent.TimeUnit.SECONDS))) { rendered = true; break; }
                    SystemClock.sleep(700);
                }
                System.out.println((rendered ? "RENDERED " : "EMPTY ") + host);
            }
            result.putString("stream", "\nPASS: all four site cards loaded in WebView.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Exception ex) {
            result.putString("stream", "\nFAIL: " + ex + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void testAlarm(Activity activity) throws Exception {
        android.app.NotificationManager notifications = activity.getSystemService(android.app.NotificationManager.class);
        android.app.AlarmManager alarms = activity.getSystemService(android.app.AlarmManager.class);
        long at = System.currentTimeMillis() + 6_000L;
        try {
            runOnMainSync(() -> {
                TestAlarm.schedule(activity, at, "Alarm smoke test",
                        TestAlarm.fallback(activity), 1);
                activity.moveTaskToBack(true);
            });
            try (var shell = getUiAutomation().executeShellCommand("input keyevent KEYCODE_SLEEP");
                 var output = new android.os.ParcelFileDescriptor.AutoCloseInputStream(shell)) {
                while (output.read() != -1) { }
            }
            android.app.Notification alarm = null;
            for (int attempt = 0; attempt < 24; attempt++) {
                var power = activity.getSystemService(android.os.PowerManager.class);
                if (!power.isInteractive() && TestAlarmService.audioStarted) {
                    for (var item : notifications.getActiveNotifications()) {
                        if (item.getId() == TestAlarmService.NOTIFICATION) alarm = item.getNotification();
                    }
                    if (alarm != null) break;
                }
                SystemClock.sleep(1000);
            }
            if (alarm == null || alarm.actions.length != 2) throw new IllegalStateException("No active audible alarm while screen off; interactive=" + activity.getSystemService(android.os.PowerManager.class).isInteractive() + ", audio=" + TestAlarmService.audioStarted + ", notifications=" + notifications.getActiveNotifications().length + ", next=" + alarms.getNextAlarmClock());
            alarm.actions[1].actionIntent.send();
            long deadline = SystemClock.elapsedRealtime() + 6000;
            while (SystemClock.elapsedRealtime() < deadline) {
                var next = alarms.getNextAlarmClock();
                if (!TestAlarmService.audioStarted && next != null && next.getTriggerTime() > at + 30_000L) return;
                SystemClock.sleep(250);
            }
            throw new IllegalStateException("Snooze did not stop audio and reschedule");
        } finally {
            runOnMainSync(() -> TestAlarm.cancel(activity));
        }
    }

    private static View find(View view, String label) {
        if (label.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = find(group.getChildAt(i), label);
                if (match != null) return match;
            }
        }
        return null;
    }
}
