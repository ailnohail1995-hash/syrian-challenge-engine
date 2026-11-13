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
    private FrameLayout webStage;
    private LinearLayout opening;
    private int openingSequence;
    private OfflineSite offlineSite;
    private android.net.ConnectivityManager.NetworkCallback networkCallback;
    private TextView offlineStatus;
    private final java.util.concurrent.ExecutorService offlineDownloads = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.Set<String> downloading = new java.util.HashSet<>();
    private ScrollView home;
    private LinearLayout homeCards;
    private LinearLayout browser;
    private TextView title;
    private Button imagePicker;
    private Bitmap draftImage;
    private Uri testTone;
    private Button testToneButton;
    private boolean loadingFailed;
    private String loadedUrl;
    private String loadError;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        offlineSite = new OfflineSite(this);
        OfflineSite snapshots = offlineSite;
        android.net.ConnectivityManager network = getSystemService(android.net.ConnectivityManager.class);
        ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!hasInternet(network) && TRUSTED.contains(origin(uri)))
                    return snapshots.response(request, uri.getHost());
                return null;
            }
        });
        settings = getSharedPreferences("lab", MODE_PRIVATE);
        FrameLayout root = new FrameLayout(this);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.setBackgroundColor(0xff081525);
        home = new ScrollView(this);
        home.setFillViewport(true);
        root.addView(home, new FrameLayout.LayoutParams(-1, -1));
        browser = new LinearLayout(this);
        browser.setOrientation(LinearLayout.VERTICAL);
        browser.setBackgroundColor(0xff081525);
        root.addView(browser, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(6), dp(10), dp(6));
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button back = new Button(this);
        back.setText("التطبيقات");
        back.setOnClickListener(view -> showHome());
        back.setTextColor(0xffd2f9ed);
        back.setBackground(background(0xff19334a, 12));
        bar.addView(back);
        title = label("", 17, 0xffeff7ff, true);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine();
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button options = new Button(this);
        options.setText("الإعدادات");
        options.setOnClickListener(view -> showSettings());
        options.setTextColor(0xffd2f9ed);
        options.setBackground(background(0xff19334a, 12));
        bar.addView(options);
        browser.addView(bar);
        offlineStatus = label("", 13, 0xffd2f9ed, false);
        offlineStatus.setGravity(Gravity.CENTER);
        offlineStatus.setPadding(dp(8), dp(5), dp(8), dp(5));
        offlineStatus.setVisibility(View.GONE);
        offlineStatus.setOnClickListener(view -> {
            String url = web.getUrl();
            if (url != null) prepareOffline(Uri.parse(url), true);
        });
        browser.addView(offlineStatus);
        web = new WebView(this);
        webStage = new FrameLayout(this);
        webStage.setBackgroundColor(0xff081525);
        web.setBackgroundColor(0xff081525);
        webStage.addView(web, new FrameLayout.LayoutParams(-1, -1));
        browser.addView(webStage, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        configureWebView();
        networkCallback = new android.net.ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(android.net.Network available) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if (getResources().getInteger(R.integer.launch_preset) < 0 && getPackageName().equals("dev.hybridlab")) {
                        for (String site : SITES) {
                            String host = Uri.parse(site).getHost();
                            if (!offlineSite.ready(host)) queueOffline(host, false);
                        }
                    }
                    if (web.getUrl() != null) prepareOffline(Uri.parse(web.getUrl()), false);
                });
            }
        };
        network.registerDefaultNetworkCallback(networkCallback);
        int preset = getResources().getInteger(R.integer.launch_preset);
        if (preset < 0) {
            showHome();
            if (online()) {
                for (String site : SITES) queueOffline(Uri.parse(site).getHost(), false);
            }
        }
        else {
            back.setVisibility(View.GONE);
            String url = SITES[preset];
            String name = NAMES[preset];
            try (java.io.InputStream config = getAssets().open("project.json")) {
                JSONObject project = new JSONObject(new String(ApkFactory.read(config), java.nio.charset.StandardCharsets.UTF_8));
                url = project.getString("url");
                name = project.getString("name");
            } catch (java.io.IOException | org.json.JSONException ignored) { }
            saveAndOpen(url, name, settings.getInt("zoom", 100));
        }
    }

    private void showHome() {
        clearOpening();
        browser.setVisibility(View.GONE);
        home.setVisibility(View.VISIBLE);
        home.removeAllViews();
        FrameLayout centered = new FrameLayout(this);
        home.addView(centered, new ScrollView.LayoutParams(-1, -2));
        homeCards = new LinearLayout(this);
        homeCards.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        homeCards.setOrientation(LinearLayout.VERTICAL);
        homeCards.setPadding(dp(20), dp(26), dp(20), dp(26));
        int width = Math.min(getResources().getDisplayMetrics().widthPixels, dp(580));
        centered.addView(homeCards, new FrameLayout.LayoutParams(width, -2, Gravity.CENTER_HORIZONTAL));
        ImageView brand = new ImageView(this);
        Bitmap savedBrand = savedHubIcon();
        if (savedBrand == null) brand.setImageResource(R.drawable.salloum_icon);
        else brand.setImageBitmap(savedBrand);
        brand.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams brandSize = new LinearLayout.LayoutParams(dp(66), dp(66));
        brandSize.bottomMargin = dp(12);
        homeCards.addView(brand, brandSize);
        homeCards.addView(label("تطبيقات سلوم", 28, 0xfff2f8ff, true));
        TextView intro = label("اختر تطبيقًا لفتحه مباشرةً", 16, 0xffaabed2, false);
        intro.setPadding(0, dp(6), 0, dp(18));
        homeCards.addView(intro);
        for (int i = 0; i < SITES.length; i++) {
            final int selected = i;
            homeCards.addView(card(NAMES[i], Uri.parse(SITES[i]).getHost(), null, i,
                    () -> saveAndOpen(SITES[selected], NAMES[selected], settings.getInt("zoom", 100))));
        }
        org.json.JSONArray apps = customApps();
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.optJSONObject(i);
            if (app == null) continue;
            String url = app.optString("url");
            String name = app.optString("name");
            String id = app.optString("id");
            View entry = card(name, Uri.parse(url).getHost(), customIcon(id), 4,
                    () -> saveAndOpen(url, name, settings.getInt("zoom", 100)));
            entry.setOnLongClickListener(view -> { editCustom(app); return true; });
            homeCards.addView(entry);
        }
        Button add = new Button(this);
        add.setText("＋ إضافة تطبيق");
        add.setOnClickListener(view -> editCustom(null));
        homeCards.addView(add);
        Button export = new Button(this);
        export.setText("إنشاء APK مستقل");
        export.setOnClickListener(view -> showApkFactory());
        homeCards.addView(export);
        Button settingsButton = new Button(this);
        settingsButton.setText("الإعدادات");
        settingsButton.setOnClickListener(view -> showSettings());
        homeCards.addView(settingsButton);
    }

    private View card(String name, String host, Bitmap image, int index, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(11), dp(14), dp(11));
        row.setBackground(background(0xff162b41, 15));
        row.setFocusable(true);
        row.setContentDescription(name);
        row.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams rowSize = new LinearLayout.LayoutParams(-1, -2);
        rowSize.bottomMargin = dp(9);
        row.setLayoutParams(rowSize);
        ImageView icon = new ImageView(this);
        if (image != null) icon.setImageBitmap(image);
        else icon.setImageResource(index < 2 ? R.drawable.dulja_card : index < 4 ? R.drawable.toqna_card : R.drawable.lab_icon);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setBackground(background(0xff233b51, 11));
        icon.setClipToOutline(true);
        row.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout lines = new LinearLayout(this);
        lines.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lineSize = new LinearLayout.LayoutParams(0, -2, 1);
        lineSize.setMarginStart(dp(12));
        row.addView(lines, lineSize);
        lines.addView(label(name, 17, 0xfff2f8ff, true));
        TextView domain = label(host, 12, 0xffb1c5d8, false);
        domain.setTextDirection(View.TEXT_DIRECTION_LTR);
        lines.addView(domain);
        row.addView(label("‹", 22, 0xff6be5c5, true));
        return row;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(color);
        if (bold) label.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return label;
    }

    private android.graphics.drawable.GradientDrawable background(int color, int radius) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private org.json.JSONArray customApps() {
        try {
            String stored = settings.getString("custom_apps", null);
            if (stored != null) return new org.json.JSONArray(stored);
            org.json.JSONArray apps = new org.json.JSONArray();
            String legacy = settings.getString("custom_url", "");
            if (!legacy.isEmpty()) apps.put(new JSONObject().put("id", "legacy").put("url", legacy)
                    .put("name", settings.getString("custom_name", "تطبيقي")));
            return apps;
        } catch (org.json.JSONException ex) { return new org.json.JSONArray(); }
    }

    private String iconFile(String id) {
        return "legacy".equals(id) ? "custom-icon.png" : "custom-icon-" + id + ".png";
    }

    private Bitmap customIcon(String id) {
        return android.graphics.BitmapFactory.decodeFile(new java.io.File(getFilesDir(), iconFile(id)).getPath());
    }

    private void editCustom(JSONObject existing) {
        String id = existing == null ? java.util.UUID.randomUUID().toString() : existing.optString("id");
        draftImage = customIcon(id);
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        fields.setPadding(dp(20), 0, dp(20), 0);
        EditText name = new EditText(this);
        name.setHint("اسم التطبيق");
        name.setSingleLine();
        name.setText(existing == null ? "" : existing.optString("name"));
        EditText url = new EditText(this);
        url.setHint("https://example.com");
        url.setTextDirection(View.TEXT_DIRECTION_LTR);
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        url.setSingleLine();
        url.setText(existing == null ? "" : existing.optString("url"));
        imagePicker = new Button(this);
        imagePicker.setText(draftImage == null ? "اختر صورة الأيقونة" : "غيّر صورة الأيقونة");
        imagePicker.setOnClickListener(view -> {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
            try { startActivityForResult(picker, 25); }
            catch (ActivityNotFoundException ex) { toast("لا يوجد تطبيق لاختيار الصور."); }
        });
        fields.addView(name);
        fields.addView(url);
        fields.addView(imagePicker);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(existing == null ? "إضافة تطبيق" : "تعديل التطبيق")
                .setView(fields).setNegativeButton("إلغاء", null).setPositiveButton("حفظ", null).create();
        dialog.setOnDismissListener(ignored -> { imagePicker = null; draftImage = null; });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String link = url.getText().toString().trim();
            String appName = limit(name.getText().toString().trim(), 35);
            if (appName.isEmpty()) { name.setError("أدخل اسم التطبيق"); return; }
            if (link.length() > 2048 || origin(Uri.parse(link)) == null) { url.setError("أدخل رابط HTTPS صالحًا"); return; }
            if (draftImage != null) {
                try (java.io.OutputStream output = openFileOutput(iconFile(id), MODE_PRIVATE)) {
                    if (!draftImage.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new java.io.IOException();
                } catch (java.io.IOException ex) { toast("تعذر حفظ صورة الأيقونة."); return; }
            }
            org.json.JSONArray updated = new org.json.JSONArray();
            org.json.JSONArray apps = customApps();
            for (int i = 0; i < apps.length(); i++) {
                JSONObject app = apps.optJSONObject(i);
                if (app != null && !id.equals(app.optString("id"))) updated.put(app);
            }
            try { updated.put(new JSONObject().put("id", id).put("url", link).put("name", appName)); }
            catch (org.json.JSONException ex) { toast("تعذر حفظ التطبيق."); return; }
            settings.edit().putString("custom_apps", updated.toString()).apply();
            dialog.dismiss();
            showHome();
        }));
        dialog.show();
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
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!online() && TRUSTED.contains(origin(uri))) return offlineSite.response(request, uri.getHost());
                return null;
            }
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
                loadingFailed = false;
                loadedUrl = null;
                loadError = null;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) { loadingFailed = true; loadError = error.getErrorCode() + " " + error.getDescription(); }
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame()) { loadingFailed = true; loadError = "HTTP " + response.getStatusCode(); }
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!url.equals(view.getUrl())) return;
                if (!loadingFailed) {
                    loadedUrl = url;
                    prepareOffline(Uri.parse(url), false);
                    connectBridge(Uri.parse(url));
                    int sequence = openingSequence;
                    view.postVisualStateCallback(sequence, new WebView.VisualStateCallback() {
                        @Override public void onComplete(long requestId) {
                            if (requestId == openingSequence && url.equals(loadedUrl)) clearOpening();
                        }
                    });
                } else if (opening != null) {
                    showOpeningError();
                }
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

    private void showApkFactory() {
        org.json.JSONArray custom = customApps();
        java.util.ArrayList<String> names = new java.util.ArrayList<>(java.util.Arrays.asList(NAMES));
        java.util.ArrayList<String> links = new java.util.ArrayList<>(java.util.Arrays.asList(SITES));
        for (int i = 0; i < custom.length(); i++) {
            JSONObject entry = custom.optJSONObject(i);
            if (entry != null) {
                names.add(entry.optString("name"));
                links.add(entry.optString("url"));
            }
        }
        new AlertDialog.Builder(this).setTitle("اختر المشروع").setItems(names.toArray(new String[0]),
                (dialog, index) -> configureApk(names.get(index), links.get(index))).show();
    }

    private void configureApk(String name, String url) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText label = new EditText(this);
        label.setSingleLine(true);
        label.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        label.setText(name);
        form.addView(label);
        draftImage = null;
        imagePicker = new Button(this);
        imagePicker.setText("اختيار صورة الأيقونة");
        imagePicker.setOnClickListener(view -> {
            try {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                        .addCategory(Intent.CATEGORY_OPENABLE), 25);
            } catch (ActivityNotFoundException ex) { toast("اختيار الصور غير متاح."); }
        });
        form.addView(imagePicker);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("إنشاء أو تحديث APK").setView(form)
                .setNegativeButton("إلغاء", null).setPositiveButton("بناء على الهاتف", null).create();
        dialog.setOnDismissListener(ignored -> { imagePicker = null; draftImage = null; });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String selected = label.getText().toString().trim();
            if (selected.isEmpty()) { label.setError("أدخل اسم التطبيق"); return; }
            Bitmap icon = draftImage;
            String id = "dev.salloum.p" + java.util.UUID.nameUUIDFromBytes(
                    url.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-", "").substring(0, 20);
            dialog.dismiss();
            ProgressDialog building = ProgressDialog.show(this, "", "جارٍ بناء وتوقيع التطبيق…", true, false);
            new Thread(() -> {
                try {
                    java.io.File apk = ApkFactory.build(this, id, selected, url, icon);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        building.dismiss();
                        new AlertDialog.Builder(this).setTitle("تم إنشاء APK").setMessage(selected)
                                .setPositiveButton("حفظ الملف", (d, button) -> {
                                    settings.edit().putString("pending_apk_export", apk.getName()).apply();
                                    startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                            .setType("application/vnd.android.package-archive")
                                            .addCategory(Intent.CATEGORY_OPENABLE)
                                            .putExtra(Intent.EXTRA_TITLE, selected + ".apk"), 28);
                                }).setNegativeButton("إغلاق", null).show();
                    });
                } catch (Exception ex) {
                    android.util.Log.e("SalloumFactory", "APK generation failed", ex);
                    runOnUiThread(() -> { if (!isDestroyed()) { building.dismiss(); toast("تعذّر بناء الحزمة."); } });
                }
            }).start();
        }));
        dialog.show();
    }

    private Bitmap savedHubIcon() {
        return android.graphics.BitmapFactory.decodeFile(new java.io.File(getFilesDir(), "hub-icon.png").getPath());
    }

    private void showHubIconSettings() {
        String[] choices = {"اختيار صورة من الهاتف", "استعادة الأيقونة الأصلية", "إضافة اختصار إلى الشاشة الرئيسية"};
        new AlertDialog.Builder(this).setTitle("أيقونة تطبيقات سلوم").setItems(choices, (dialog, item) -> {
            if (item == 0) {
                try {
                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                            .addCategory(Intent.CATEGORY_OPENABLE), 27);
                } catch (ActivityNotFoundException ex) { toast("اختيار الصور غير متاح."); }
            } else if (item == 1) {
                java.io.File icon = new java.io.File(getFilesDir(), "hub-icon.png");
                if (icon.exists() && !deleteFile("hub-icon.png")) { toast("تعذر استعادة الأيقونة."); return; }
                refreshHubIcon();
            } else {
                ShortcutManager manager = getSystemService(ShortcutManager.class);
                if (!manager.isRequestPinShortcutSupported() || !manager.requestPinShortcut(hubShortcut(), null))
                    toast("الشاشة الرئيسية لا تدعم إضافة اختصار من هنا.");
            }
        }).show();
    }

    private ShortcutInfo hubShortcut() {
        Bitmap bitmap = savedHubIcon();
        Icon icon = bitmap == null ? Icon.createWithResource(this, R.drawable.salloum_icon)
                : Icon.createWithBitmap(bitmap);
        return new ShortcutInfo.Builder(this, "salloum-home").setShortLabel("تطبيقات سلوم")
                .setIcon(icon).setIntent(new Intent(Intent.ACTION_MAIN, null, this, MainActivity.class)).build();
    }

    private void refreshHubIcon() {
        if (home.getVisibility() == View.VISIBLE) showHome();
        ShortcutManager manager = getSystemService(ShortcutManager.class);
        for (ShortcutInfo shortcut : manager.getPinnedShortcuts()) {
            if ("salloum-home".equals(shortcut.getId())) {
                manager.updateShortcuts(java.util.Collections.singletonList(hubShortcut()));
                break;
            }
        }
    }

    private void saveHubIcon(Uri uri) throws java.io.IOException {
        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
            android.graphics.BitmapFactory.decodeStream(input, null, options);
        }
        int longest = Math.max(options.outWidth, options.outHeight);
        if (longest <= 0) throw new java.io.IOException("Invalid image");
        options.inJustDecodeBounds = false;
        options.inSampleSize = Math.max(1, Integer.highestOneBit(longest / 256));
        Bitmap image;
        try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
            image = android.graphics.BitmapFactory.decodeStream(input, null, options);
        }
        if (image == null) throw new java.io.IOException("Invalid image");
        android.util.AtomicFile file = new android.util.AtomicFile(new java.io.File(getFilesDir(), "hub-icon.png"));
        java.io.FileOutputStream output = file.startWrite();
        try {
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new java.io.IOException("Image encoding failed");
            file.finishWrite(output);
        } catch (java.io.IOException | RuntimeException ex) {
            file.failWrite(output);
            throw ex;
        } finally {
            image.recycle();
        }
    }

    private void showSettings() {
        String[] actions = {"اختيار أحد التطبيقات الأربعة", "تعديل الرابط والاسم وحجم الخط", "اختيار صورة للاختصار",
                "إضافة اختصار بالاسم والصورة", "تجربة منبّه طويل", "إذن التنبيه الدقيق", "أيقونة تطبيقات سلوم"};
        int preset = getResources().getInteger(R.integer.launch_preset);
        if (preset >= 0) {
            new AlertDialog.Builder(this).setTitle("الإعدادات").setItems(
                    new String[]{"حجم الخط", "تجربة منبّه طويل"}, (dialog, item) -> {
                        if (item == 1) showTestAlarm();
                        else new AlertDialog.Builder(this).setTitle("حجم الخط").setItems(
                            new String[]{"صغير", "عادي", "كبير", "كبير جدًا"}, (d, index) -> {
                                int zoom = new int[]{85, 100, 120, 145}[index];
                                settings.edit().putInt("zoom", zoom).apply();
                                web.getSettings().setTextZoom(zoom);
                            }).show();
                    }).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("إعدادات التطبيق").setItems(actions, (dialog, item) -> {
            switch (item) {
                case 0 -> new AlertDialog.Builder(this).setTitle(actions[0]).setItems(NAMES, (d, i) -> saveAndOpen(SITES[i], NAMES[i], settings.getInt("zoom", 100))).show();
                case 1 -> editSettings();
                case 2 -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), 24);
                case 3 -> pinShortcut();
                case 4 -> showTestAlarm();
                case 6 -> showHubIconSettings();
                case 5 -> {
                    if (Build.VERSION.SDK_INT >= 31)
                        startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
                }
            }
        }).show();
    }

    private void showTestAlarm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(8));
        Spinner event = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, TestAlarm.EVENTS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        event.setAdapter(adapter);
        form.addView(event);
        final long[] at = {System.currentTimeMillis() + 30_000L};
        Button time = new Button(this);
        time.setText("بعد 30 ثانية");
        time.setOnClickListener(view -> {
            java.util.Calendar now = java.util.Calendar.getInstance();
            new TimePickerDialog(this, (picker, hour, minute) -> {
                java.util.Calendar selected = java.util.Calendar.getInstance();
                selected.set(java.util.Calendar.HOUR_OF_DAY, hour);
                selected.set(java.util.Calendar.MINUTE, minute);
                selected.set(java.util.Calendar.SECOND, 0);
                selected.set(java.util.Calendar.MILLISECOND, 0);
                if (selected.getTimeInMillis() <= System.currentTimeMillis()) selected.add(java.util.Calendar.DATE, 1);
                at[0] = selected.getTimeInMillis();
                time.setText(android.text.format.DateFormat.getDateFormat(this).format(selected.getTime()) + " • " +
                        android.text.format.DateFormat.getTimeFormat(this).format(selected.getTime()));
            }, now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE),
                android.text.format.DateFormat.is24HourFormat(this)).show();
        });
        form.addView(time);
        testTone = TestAlarm.fallback(this);
        testToneButton = new Button(this);
        testToneButton.setText("نغمة المختبر • تغيير");
        testToneButton.setOnClickListener(view -> {
            Intent picker = new Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALARM)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            "android.resource".equals(testTone.getScheme()) ? android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM) : testTone);
            try { startActivityForResult(picker, 26); }
            catch (ActivityNotFoundException ex) { toast("منتقي النغمات غير متاح على هذا الجهاز."); }
        });
        form.addView(testToneButton);
        EditText snooze = new EditText(this);
        snooze.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        snooze.setHint("مدة الغفوة بالدقائق");
        snooze.setText("5");
        form.addView(snooze);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("تجربة منبّه تُقنى").setView(scroll)
                .setNegativeButton("إغلاق", null)
                .setNeutralButton("إلغاء المنبّه", (d, which) -> { TestAlarm.cancel(this); toast("تم إلغاء المنبّه التجريبي."); })
                .setPositiveButton("جدولة", null).create();
        dialog.setOnDismissListener(ignored -> testToneButton = null);
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 23);
                return;
            }
            if (!getSystemService(NotificationManager.class).areNotificationsEnabled()) {
                toast("فعّل إشعارات التطبيق أولًا."); return;
            }
            int minutes;
            try { minutes = Integer.parseInt(snooze.getText().toString()); }
            catch (NumberFormatException ex) { minutes = 0; }
            if (minutes < 1 || minutes > 30) { snooze.setError("اختر من دقيقة إلى 30 دقيقة"); return; }
            long selectedTime = "بعد 30 ثانية".contentEquals(time.getText()) ? System.currentTimeMillis() + 30_000L : at[0];
            if (selectedTime <= System.currentTimeMillis()) { toast("اختر وقتًا جديدًا."); return; }
            if (testTone == null) { toast("لم تتوفر نغمة منبّه على هذا الجهاز."); return; }
            try {
                TestAlarm.schedule(this, selectedTime, event.getSelectedItem().toString(), testTone, minutes);
                dialog.dismiss();
                toast("تمت الجدولة. يمكنك الخروج من التطبيق وقفل الشاشة.");
            } catch (SecurityException ex) {
                if (Build.VERSION.SDK_INT >= 31)
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
            }
        }));
        dialog.show();
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
        if (label.isEmpty()) label = "تطبيقات سلوم";
        settings.edit().putString("url", url).putString("name", label).putInt("zoom", zoom).apply();
        web.getSettings().setTextZoom(zoom);
        open(url);
    }

    private void pinShortcut() {
        ShortcutManager manager = getSystemService(ShortcutManager.class);
        if (!manager.isRequestPinShortcutSupported()) { toast("الشاشة الرئيسية لا تدعم إضافة اختصار من هنا."); return; }
        String currentUrl = settings.getString("url", SITES[0]);
        Bitmap icon = null;
        org.json.JSONArray apps = customApps();
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.optJSONObject(i);
            if (app != null && currentUrl.equals(app.optString("url"))) icon = customIcon(app.optString("id"));
        }
        if (icon == null) icon = shortcutImage;
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
        if (request == 28 && result == RESULT_OK && data != null && data.getData() != null) {
            String pending = settings.getString("pending_apk_export", "");
            if (pending.matches("dev\\.salloum\\.p[a-f0-9]{20}\\.apk")) {
                java.io.File apk = new java.io.File(new java.io.File(getFilesDir(), "exports"), pending);
                try (java.io.InputStream input = new java.io.FileInputStream(apk);
                     java.io.OutputStream output = getContentResolver().openOutputStream(data.getData(), "wt")) {
                    if (output == null) throw new java.io.IOException("Export unavailable");
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    toast("تم حفظ APK. افتحه من مدير الملفات للتثبيت.");
                } catch (java.io.IOException ex) { toast("تعذّر حفظ APK."); }
            }
        }
        if (request == 27 && result == RESULT_OK && data != null && data.getData() != null) {
            try {
                saveHubIcon(data.getData());
                refreshHubIcon();
                toast("تم حفظ الأيقونة.");
            } catch (java.io.IOException | RuntimeException ex) { toast("تعذّر حفظ صورة الأيقونة."); }
        }
        if (request == 26 && result == RESULT_OK && data != null && testToneButton != null) {
            Uri selected = data.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (selected != null) {
                testTone = selected;
                android.media.Ringtone ringtone = android.media.RingtoneManager.getRingtone(this, selected);
                testToneButton.setText(ringtone == null ? "تم اختيار النغمة" : ringtone.getTitle(this));
            }
        }
        if (request == 21 && files != null) {
            files.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            files = null;
        }
        if (request == 25 && result == RESULT_OK && data != null && data.getData() != null && imagePicker != null) {
            try {
                Uri uri = data.getData();
                if (Build.VERSION.SDK_INT >= 28) {
                    draftImage = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri),
                        (decoder, info, source) -> {
                            int max = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                            decoder.setTargetSampleSize(Math.max(1, (max + 255) / 256));
                            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        });
                } else {
                    android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
                        android.graphics.BitmapFactory.decodeStream(input, null, bounds);
                    }
                    bounds.inJustDecodeBounds = false;
                    bounds.inSampleSize = Math.max(1, Integer.highestOneBit(Math.max(bounds.outWidth, bounds.outHeight) / 256));
                    try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
                        draftImage = android.graphics.BitmapFactory.decodeStream(input, null, bounds);
                    }
                }
                if (draftImage == null) throw new java.io.IOException();
                imagePicker.setText("تم اختيار الصورة • تغيير");
            } catch (Exception ex) { toast("تعذر قراءة الصورة."); }
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

    private boolean online() {
        android.net.ConnectivityManager network = getSystemService(android.net.ConnectivityManager.class);
        return hasInternet(network);
    }

    private static boolean hasInternet(android.net.ConnectivityManager network) {
        android.net.NetworkCapabilities capabilities = network.getNetworkCapabilities(network.getActiveNetwork());
        return capabilities != null && capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void prepareOffline(Uri uri, boolean retry) {
        if (!TRUSTED.contains(origin(uri))) return;
        String host = uri.getHost();
        boolean ready = offlineSite.ready(host);
        offlineStatus.setVisibility(View.VISIBLE);
        if (!online()) {
            offlineStatus.setText(ready ? "النسخة المحفوظة • دون إنترنت" : "اتصل بالإنترنت لتنزيل نسخة الموقع");
            return;
        }
        if (ready && !retry) {
            offlineStatus.setText("ملفات الواجهة محفوظة • اضغط لتحديثها");
            return;
        }
        offlineStatus.setText("جارٍ حفظ ملفات الموقع…");
        queueOffline(host, retry);
    }

    private void queueOffline(String host, boolean retry) {
        if (!downloading.add(host)) return;
        offlineDownloads.execute(() -> {
            int files = 0;
            try { if (retry || !offlineSite.ready(host)) files = offlineSite.download(host); }
            catch (Exception ex) { android.util.Log.w("SalloumOffline", "Download failed for " + host, ex); }
            final int count = files;
            runOnUiThread(() -> {
                downloading.remove(host);
                if (isDestroyed() || web.getUrl() == null ||
                        !host.equals(Uri.parse(web.getUrl()).getHost())) return;
                if (count == 0 && offlineSite.ready(host)) {
                    offlineStatus.setText("ملفات الواجهة محفوظة • اضغط لتحديثها");
                    return;
                }
                offlineStatus.setVisibility(View.VISIBLE);
                offlineStatus.setText(count > 0 ? "ملفات الواجهة محفوظة • " + count + " ملف • اضغط لتحديثها"
                        : "تعذّر إكمال الحفظ • اضغط لإعادة المحاولة");
            });
        });
    }

    private void open(String url) {
        if (origin(Uri.parse(url)) == null) return;
        home.setVisibility(View.GONE);
        browser.setVisibility(View.VISIBLE);
        title.setText(settings.getString("name", NAMES[0]));
        offlineStatus.setVisibility(View.GONE);
        showOpening(url);
        web.loadUrl(url);
    }

    private void showOpening(String url) {
        clearOpening();
        int sequence = openingSequence;
        int preset = java.util.Arrays.asList(SITES).indexOf(url);
        opening = new LinearLayout(this);
        opening.setOrientation(LinearLayout.VERTICAL);
        opening.setGravity(Gravity.CENTER);
        opening.setBackgroundColor(preset == 0 ? 0xff092820 : preset == 1 ? 0xff15231c :
                preset == 2 ? 0xff102944 : preset == 3 ? 0xff231b3f : 0xff081525);
        opening.setClickable(true);
        ImageView mark = new ImageView(this);
        mark.setImageResource(preset == 0 || preset == 1 ? R.drawable.dulja_card :
                preset == 2 || preset == 3 ? R.drawable.toqna_card : R.drawable.salloum_icon);
        org.json.JSONArray apps = customApps();
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.optJSONObject(i);
            if (app != null && url.equals(app.optString("url"))) {
                Bitmap icon = customIcon(app.optString("id"));
                if (icon != null) mark.setImageBitmap(icon);
                break;
            }
        }
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        opening.addView(mark, new LinearLayout.LayoutParams(dp(92), dp(92)));
        TextView name = label(title.getText().toString(), 24, 0xfff2f8ff, true);
        name.setPadding(dp(12), dp(16), dp(12), dp(12));
        name.setGravity(Gravity.CENTER);
        opening.addView(name);
        ProgressBar progress = new ProgressBar(this);
        opening.addView(progress, new LinearLayout.LayoutParams(dp(26), dp(26)));
        webStage.addView(opening, new FrameLayout.LayoutParams(-1, -1));
        mark.setAlpha(0f);
        mark.setScaleX(.72f);
        mark.setScaleY(.72f);
        mark.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420).start();
        webStage.postDelayed(() -> {
            if (sequence == openingSequence && opening != null) showOpeningError();
        }, 25000);
    }

    private void showOpeningError() {
        if (opening == null) return;
        opening.removeAllViews();
        TextView message = label("تعذّر فتح الصفحة", 20, 0xfff2f8ff, true);
        opening.addView(message);
        Button retry = new Button(this);
        retry.setText("إعادة المحاولة");
        retry.setOnClickListener(view -> open(settings.getString("url", SITES[0])));
        opening.addView(retry);
    }

    private void clearOpening() {
        openingSequence++;
        if (opening != null) {
            webStage.removeView(opening);
            opening = null;
        }
    }
    private void openExternally(Uri uri) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (ActivityNotFoundException ex) { toast("تعذر فتح الرابط."); }
    }
    private void closeBridge() { if (bridge != null) { bridge.close(); bridge = null; } }
    private static String limit(String text, int size) { return text.substring(0, Math.min(text.length(), size)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed() { if (home.getVisibility() == View.VISIBLE) super.onBackPressed();
        else if (web.canGoBack()) web.goBack();
        else if (getResources().getInteger(R.integer.launch_preset) < 0) showHome();
        else super.onBackPressed(); }
    @Override protected void onDestroy() { getSystemService(android.net.ConnectivityManager.class).unregisterNetworkCallback(networkCallback); offlineDownloads.shutdownNow(); closeBridge(); web.destroy(); super.onDestroy(); }
}
