package dev.hybridlab;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

public final class LabSmoke extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Intent intent = new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity = startActivitySync(intent);
            String[] names = {"دلجة العام", "دلجة Lovable", "TOQNA", "أبطال TOQNA"};
            String[] hosts = {"dulja-portal-preview.pages.dev", "quran-dulja-tahfez.lovable.app",
                "toqna.pages.dev", "toqna-heroes-app.pages.dev"};
            var loaded = MainActivity.class.getDeclaredField("loadedUrl");
            loaded.setAccessible(true);
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
                    String[] url = new String[1];
                    runOnMainSync(() -> {
                        try { url[0] = (String) loaded.get(activity); }
                        catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
                    });
                    if (url[0] != null && host.equals(android.net.Uri.parse(url[0]).getHost())) { ready = true; break; }
                    SystemClock.sleep(1000);
                }
                if (!ready) throw new IllegalStateException("WebView did not finish loading " + host);
                result.putString("site_" + i, host + " loaded");
            }
            result.putString("stream", "\nPASS: all four site cards loaded in WebView.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Exception ex) {
            result.putString("stream", "\nFAIL: " + ex + "\n");
            finish(Activity.RESULT_CANCELED, result);
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
