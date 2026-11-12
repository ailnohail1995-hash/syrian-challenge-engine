package dev.hybridlab;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final String[] SITES = {
        "https://dulja-portal-preview.pages.dev/",
        "https://quran-dulja-tahfez.lovable.app/",
        "https://toqna.pages.dev/",
        "https://toqna-heroes-app.pages.dev/"
    };
    private static final String[] NAMES = {"دلجة العام", "دلجة Lovable", "TOQNA", "أبطال TOQNA"};
    private static final Set<String> TRUSTED = new java.util.HashSet<>(java.util.Arrays.asList(
        "https://dulja-portal-preview.pages.dev", "https://quran-dulja-tahfez.lovable.app",
        "https://toqna.pages.dev", "https://toqna-heroes-app.pages.dev"));
    private SharedPreferences settings;
    private WebView web;
    private WebMessagePort bridge;
    private ValueCallback<Uri[]> files;
    private PermissionRequest camera;
    private Bitmap shortcutImage;
    private long touchStart = -1;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings = getSharedPreferences("lab", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(28), 0, dp(28));
        Button options = new Button(this);
        options.setText("إعدادات التطبيق");
        options.setOnClickListener(view -> showSettings());
        root.addView(options);
        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        configureWebView();
        open(settings.getString("url", SITES[0]));
    }

    private void configureWebView() {
        WebSettings config = web.getSettings();
        config.setJavaScriptEnabled(true);
        config.setDomStorageEnabled(true);
        config.setAllowFileAccess(false);
        config.setAllowContentAccess(false);
        config.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        config.setJavaScriptCanOpenWindowsAutomatically(false);
        config.setTextZoom(settings.getInt("zoom", 100));
        web.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
                touchStart = web.getScrollY() == 0 ? (long) event.getY() : -1;
            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) touchStart = -1;
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (touchStart >= 0 && event.getY() - touchStart > dp(120) && web.getScrollY() == 0) web.reload();
                touchStart = -1;
            }
            return false;
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri target = request.getUrl();
                String destination = origin(target);
                String current = origin(Uri.parse(settings.getString("url", SITES[0])));
                if (destination != null && (destination.equals(current) || TRUSTED.contains(destination))) return false;
                if (destination != null) openExternally(target);
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, Bitmap image) {
                closeBridge();
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!url.equals(view.getUrl())) return;
                connectBridge(Uri.parse(url));
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (files != null) files.onReceiveValue(null);
                files = callback;
                try { startActivityForResult(params.createIntent(), 21); }
                catch (ActivityNotFoundException ex) { files.onReceiveValue(null); files = null; }
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    if (!trustedCurrentOrigin(request.getOrigin()) || request.getResources().length != 1 ||
                            !PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(request.getResources()[0])) {
                        request.deny(); return;
                    }
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE}); return;
                    }
                    if (camera != null) camera.deny();
                    camera = request;
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, 22);
                });
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (camera == request) camera = null;
            }
        });
    }

    private boolean trustedCurrentOrigin(Uri uri) {
        String requested = origin(uri);
        return requested != null && TRUSTED.contains(requested) &&
            web.getUrl() != null && requested.equals(origin(Uri.parse(web.getUrl())));
    }

    private void connectBridge(Uri page) {
        if (!trustedCurrentOrigin(page)) return;
        closeBridge();
        WebMessagePort[] ports = web.createWebMessageChannel();
        bridge = ports[0];
        String pageOrigin = origin(page);
        bridge.setWebMessageCallback(new WebMessagePort.WebMessageCallback() {
            @Override public void onMessage(WebMessagePort port, WebMessage message) {
                if (port != bridge || !trustedCurrentOrigin(page)) return;
                handleMessage(port, pageOrigin, message.getData());
            }
        });
        web.postWebMessage(new WebMessage("hybrid-lab:v1", new WebMessagePort[]{ports[1]}), Uri.parse(pageOrigin));
    }

    private void handleMessage(WebMessagePort port, String sourceOrigin, String payload) {
        if (payload == null || payload.length() > 4096) return;
        JSONObject result = new JSONObject();
        try {
            JSONObject request = new JSONObject(payload);
            result.put("id", request.optString("id"));
            String method = request.getString("method");
            if ("capabilities".equals(method)) {
                result.put("version", 1);
                result.put("methods", new org.json.JSONArray(new String[]{"notify", "scheduleAlarm", "cancelAlarm", "download", "share"}));
            } else if ("notify".equals(method) || "scheduleAlarm".equals(method)) {
                int id = alarmId(sourceOrigin, request);
                long when = "notify".equals(method) ? 0 : request.getLong("at");
                schedule(id, when, limit(request.optString("title"), 100), limit(request.optString("body"), 300));
            } else if ("cancelAlarm".equals(method)) {
                getSystemService(AlarmManager.class).cancel(alarmIntent(alarmId(sourceOrigin, request), "", ""));
            } else if ("download".equals(method)) {
                Uri uri = Uri.parse(request.getString("url"));
                if (!sourceOrigin.equals(origin(uri))) throw new IllegalArgumentException("same_origin_required");
                DownloadManager.Request download = new DownloadManager.Request(uri)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                result.put("downloadId", getSystemService(DownloadManager.class).enqueue(download));
            } else if ("share".equals(method)) {
                Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, limit(request.getString("text"), 2000));
                startActivity(Intent.createChooser(share, "مشاركة"));
            } else {
                throw new IllegalArgumentException("unsupported_method");
            }
            result.put("ok", true);
        } catch (Exception ex) {
            try { result.put("ok", false); result.put("error", ex instanceof IllegalArgumentException ? ex.getMessage() : "request_failed"); }
            catch (Exception ignored) { return; }
        }
        port.postMessage(new WebMessage(result.toString()));
    }

    private int alarmId(String source, JSONObject request) {
        String key = request.optString("key");
        if (!key.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException("invalid_alarm_key");
        return (source + "/" + key).hashCode();
    }

    private void schedule(int id, long when, String title, String body) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 23);
            throw new IllegalArgumentException("notification_permission_required");
        }
        if (!getSystemService(NotificationManager.class).areNotificationsEnabled())
            throw new IllegalArgumentException("notifications_disabled");
        if (when == 0) {
            sendBroadcast(alarmBroadcast(id, title, body));
            return;
        }
        if (when <= System.currentTimeMillis()) throw new IllegalArgumentException("invalid_alarm_time");
        AlarmManager alarms = getSystemService(AlarmManager.class);
        if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms())
            throw new IllegalArgumentException("exact_alarm_permission_required");
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, alarmIntent(id, title, body));
    }

    private Intent alarmBroadcast(int id, String title, String body) {
        return new Intent(this, AlarmReceiver.class).putExtra("id", id).putExtra("title", title).putExtra("body", body);
    }

    private PendingIntent alarmIntent(int id, String title, String body) {
        return PendingIntent.getBroadcast(this, id, alarmBroadcast(id, title, body),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void showSettings() {
        String[] actions = {"اختيار أحد التطبيقات الأربعة", "تعديل الرابط والاسم وحجم الخط", "اختيار صورة للاختصار",
                "إضافة اختصار بالاسم والصورة", "اختبار تنبيه بعد 30 ثانية", "إذن التنبيه الدقيق"};
        new AlertDialog.Builder(this).setTitle("إعدادات التطبيق").setItems(actions, (dialog, item) -> {
            switch (item) {
                case 0 -> new AlertDialog.Builder(this).setTitle(actions[0]).setItems(NAMES, (d, i) -> saveAndOpen(SITES[i], NAMES[i], settings.getInt("zoom", 100))).show();
                case 1 -> editSettings();
                case 2 -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), 24);
                case 3 -> pinShortcut();
                case 4 -> {
                    try { schedule(417, System.currentTimeMillis() + 30_000, "مختبر التطبيقات", "تنبيه محلي مجدول"); toast("تمت الجدولة. اقفل الشاشة للاختبار."); }
                    catch (IllegalArgumentException ex) { toast("اسمح بالإشعارات والتنبيه الدقيق ثم أعد المحاولة."); }
                }
                case 5 -> {
                    if (Build.VERSION.SDK_INT >= 31)
                        startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
                }
            }
        }).show();
    }

    private void editSettings() {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(20), 0, dp(20), 0);
        EditText url = new EditText(this);
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(settings.getString("url", SITES[0]));
        EditText name = new EditText(this);
        name.setHint("اسم الاختصار");
        name.setText(settings.getString("name", NAMES[0]));
        EditText zoom = new EditText(this);
        zoom.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        zoom.setHint("حجم الخط من 80 إلى 160٪");
        zoom.setText(Integer.toString(settings.getInt("zoom", 100)));
        fields.addView(url); fields.addView(name); fields.addView(zoom);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("الرابط والاسم وحجم الخط")
            .setView(fields).setNegativeButton("إلغاء", null).setPositiveButton("حفظ", null).create();
        dialog.setOnShowListener(event -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = url.getText().toString().trim();
            if (origin(Uri.parse(value)) == null) { url.setError("أدخل رابط HTTPS صالحًا"); return; }
            int size;
            try { size = Integer.parseInt(zoom.getText().toString()); }
            catch (NumberFormatException ex) { size = 0; }
            if (size < 80 || size > 160) { zoom.setError("أدخل رقمًا بين 80 و160"); return; }
            saveAndOpen(value, name.getText().toString(), size);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void saveAndOpen(String url, String name, int zoom) {
        String label = limit(name.trim(), 35);
        if (label.isEmpty()) label = "مختبر التطبيقات";
        settings.edit().putString("url", url).putString("name", label).putInt("zoom", zoom).apply();
        web.getSettings().setTextZoom(zoom);
        open(url);
    }

    private void pinShortcut() {
        ShortcutManager manager = getSystemService(ShortcutManager.class);
        if (!manager.isRequestPinShortcutSupported()) { toast("الشاشة الرئيسية لا تدعم إضافة اختصار من هنا."); return; }
        Bitmap icon = shortcutImage;
        if (icon == null) icon = android.graphics.BitmapFactory.decodeFile(new java.io.File(getFilesDir(), "shortcut.png").getPath());
        Icon shortcutIcon = icon == null ? Icon.createWithResource(this, R.drawable.lab_icon) : Icon.createWithBitmap(icon);
        String url = settings.getString("url", SITES[0]);
        String id = "web-" + java.util.UUID.nameUUIDFromBytes(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        settings.edit().putString("shortcut:" + id, url).apply();
        Intent launch = new Intent(Intent.ACTION_VIEW, null, this, MainActivity.class).putExtra("shortcut_id", id);
        ShortcutInfo shortcut = new ShortcutInfo.Builder(this, id)
                .setShortLabel(settings.getString("name", NAMES[0])).setIcon(shortcutIcon).setIntent(launch).build();
        manager.requestPinShortcut(shortcut, null);
    }

    @Override protected void onStart() {
        super.onStart();
        String id = getIntent().getStringExtra("shortcut_id");
        if (id == null) return;
        getIntent().removeExtra("shortcut_id");
        String target = settings.getString("shortcut:" + id, null);
        if (target != null) {
            settings.edit().putString("url", target).apply();
            open(target);
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 21 && files != null) {
            files.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            files = null;
        }
        if (request == 24 && result == RESULT_OK && data != null && data.getData() != null) {
            try {
                Uri uri = data.getData();
                if (Build.VERSION.SDK_INT >= 28) {
                    shortcutImage = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri),
                        (decoder, info, source) -> {
                            int max = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                            decoder.setTargetSampleSize(Math.max(1, (max + 255) / 256));
                            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        });
                } else {
                    toast("اختيار صورة الاختصار يتطلب Android 9 أو أحدث.");
                    return;
                }
                try (java.io.OutputStream output = openFileOutput("shortcut.png", MODE_PRIVATE)) {
                    shortcutImage.compress(Bitmap.CompressFormat.PNG, 100, output);
                }
                toast("تم حفظ صورة الاختصار.");
            } catch (Exception ex) { toast("تعذر قراءة الصورة."); }
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 22 && camera != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && trustedCurrentOrigin(camera.getOrigin()))
                camera.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            else camera.deny();
            camera = null;
        }
    }

    private static String origin(Uri uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getHost() == null) return null;
        int port = uri.getPort();
        return "https://" + uri.getHost().toLowerCase(java.util.Locale.ROOT) + (port == -1 || port == 443 ? "" : ":" + port);
    }

    private void open(String url) { if (origin(Uri.parse(url)) != null) web.loadUrl(url); }
    private void openExternally(Uri uri) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (ActivityNotFoundException ex) { toast("تعذر فتح الرابط."); }
    }
    private void closeBridge() { if (bridge != null) { bridge.close(); bridge = null; } }
    private static String limit(String text, int size) { return text.substring(0, Math.min(text.length(), size)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() { closeBridge(); web.destroy(); super.onDestroy(); }
}
