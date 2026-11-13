package dev.hybridlab;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

final class OfflineSite {
    private static final long LIMIT = 256L * 1024 * 1024;
    private static final Pattern HTML = Pattern.compile("(?:src|href)=[\"']([^#\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHUNK = Pattern.compile("[\"']((?:\\./|/assets/|assets/)[^'\"?# ]+\\.(?:js|css|png|svg|webp|woff2?|mp3|ogg|wav|json))[\"']");
    private static final Object DOWNLOAD_LOCK = new Object();
    private final File root;

    OfflineSite(Context context) { root = new File(context.getFilesDir(), "offline-sites"); }

    private static String key(String input) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }

    boolean ready(String host) {
        try { return new File(new File(root, host), key("/") + ".mime").isFile(); }
        catch (Exception ex) { return false; }
    }

    WebResourceResponse response(WebResourceRequest request, String host) {
        Uri uri = request.getUrl();
        if (!"GET".equals(request.getMethod()) || !"https".equals(uri.getScheme()) ||
                !host.equals(uri.getHost()) || (uri.getPort() != -1 && uri.getPort() != 443)) return null;
        String path = uri.getEncodedPath();
        if (path == null || path.isEmpty()) path = "/";
        if (!request.isForMainFrame() && uri.getEncodedQuery() != null) return null;
        try {
            File dir = new File(root, host);
            if (!dir.exists()) dir = new File(root, host + ".previous");
            String id = key(path);
            File typeFile = new File(dir, id + ".mime");
            if (!typeFile.isFile() && request.isForMainFrame() && !path.startsWith("/api/") &&
                    request.getRequestHeaders().getOrDefault("Accept", "").contains("text/html")) {
                id = key("/");
                typeFile = new File(dir, id + ".mime");
            }
            if (!typeFile.isFile()) return null;
            String type;
            try (InputStream input = new FileInputStream(typeFile)) {
                type = new String(ApkFactory.read(input), StandardCharsets.UTF_8);
            }
            Map<String, String> headers = new HashMap<>();
            File policy = new File(dir, id + ".csp");
            if (policy.isFile()) {
                try (InputStream input = new FileInputStream(policy)) {
                    headers.put("Content-Security-Policy", new String(ApkFactory.read(input), StandardCharsets.UTF_8));
                }
            }
            return new WebResourceResponse(type, type.startsWith("text/") || type.contains("json")
                    || type.contains("javascript") ? "UTF-8" : null, 200, "OK", headers, new FileInputStream(new File(dir, id)));
        } catch (Exception ex) { return null; }
    }

    int download(String host) throws Exception {
        synchronized (DOWNLOAD_LOCK) { return save(host); }
    }

    private int save(String host) throws Exception {
        File destination = new File(root, host);
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Storage unavailable");
        File staging = new File(root, host + ".pending");
        File previous = new File(root, host + ".previous");
        if (!destination.exists() && previous.exists() && !previous.renameTo(destination))
            throw new IOException("Cannot recover snapshot");
        remove(staging);
        if (!staging.mkdir()) throw new IOException("Storage unavailable");
        try {
            LinkedHashSet<String> pending = new LinkedHashSet<>();
            pending.add("/");
            URL base = new URL("https://" + host + "/");
            if ("toqna.pages.dev".equals(host)) {
                byte[] manifest = fetch(new URL(base, "/offline-manifest.json"), 2 * 1024 * 1024).bytes;
                org.json.JSONArray urls = new org.json.JSONObject(new String(manifest, StandardCharsets.UTF_8)).getJSONArray("urls");
                for (int i = 0; i < urls.length(); i++) add(base, urls.getString(i), pending);
            } else {
                String worker = new String(fetch(new URL(base, "/sw.js"), 4 * 1024 * 1024).bytes, StandardCharsets.UTF_8);
                Pattern paths = host.equals("toqna-heroes-app.pages.dev")
                        ? Pattern.compile("url:[\"']([^\"']+)[\"']")
                        : Pattern.compile("['\"](/quran/[^'\"]+\\.json)['\"]");
                Matcher match = paths.matcher(worker);
                while (match.find()) add(base, match.group(1), pending);
            }
            Set<String> completed = new HashSet<>();
            long used = 0;
            while (!pending.isEmpty()) {
                if (completed.size() >= 2500) throw new IOException("Too many files");
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                String path = pending.iterator().next();
                pending.remove(path);
                if (!completed.add(path)) continue;
                URL url = new URL(base, path);
                Asset asset = fetch(url, Math.min(32L * 1024 * 1024, LIMIT - used));
                used += asset.bytes.length;
                String id = key(path);
                try (OutputStream output = new FileOutputStream(new File(staging, id))) { output.write(asset.bytes); }
                try (OutputStream output = new FileOutputStream(new File(staging, id + ".mime"))) {
                    output.write(asset.type.getBytes(StandardCharsets.UTF_8));
                }
                if (asset.policy != null) {
                    try (OutputStream output = new FileOutputStream(new File(staging, id + ".csp"))) {
                        output.write(asset.policy.getBytes(StandardCharsets.UTF_8));
                    }
                }
                if (asset.type.equals("text/html") || asset.type.contains("javascript")) {
                    String source = new String(asset.bytes, StandardCharsets.UTF_8);
                    Matcher references = (asset.type.equals("text/html") ? HTML : CHUNK).matcher(source);
                    while (references.find()) {
                        String value = references.group(1);
                        if (value.matches("(?i).*\\.(?:js|css|png|svg|webp|woff2?|mp3|ogg|wav|json|ico)$"))
                            add(url, value, pending);
                    }
                    pending.removeAll(completed);
                }
            }
            remove(previous);
            if (destination.exists() && !destination.renameTo(previous)) throw new IOException("Cannot replace snapshot");
            if (!staging.renameTo(destination)) {
                previous.renameTo(destination);
                throw new IOException("Cannot publish snapshot");
            }
            remove(previous);
            return completed.size();
        } finally { remove(staging); }
    }

    private static void add(URL base, String value, Set<String> paths) throws IOException {
        URL target = new URL(base, value);
        if (!"https".equals(target.getProtocol()) || !base.getHost().equals(target.getHost()) ||
                (target.getPort() != -1 && target.getPort() != 443) || target.getQuery() != null ||
                target.getUserInfo() != null || target.getPath().startsWith("/api/")) return;
        paths.add(target.getPath());
    }

    private static Asset fetch(URL url, long max) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
            String header = connection.getContentType();
            String type = header == null ? "application/octet-stream" : header.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            String path = url.getPath();
            if ((path.endsWith(".js") && !type.contains("javascript")) ||
                    (path.endsWith(".json") && !type.contains("json")) ||
                    (path.endsWith(".css") && !type.equals("text/css")))
                throw new IOException("Unexpected content type");
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if ((long) output.size() + n > max) throw new IOException("Offline storage limit");
                    output.write(buffer, 0, n);
                }
                return new Asset(type, output.toByteArray(), connection.getHeaderField("Content-Security-Policy"));
            }
        } finally { connection.disconnect(); }
    }

    private static void remove(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) remove(child);
        file.delete();
    }

    private static final class Asset {
        final String type;
        final byte[] bytes;
        final String policy;
        Asset(String type, byte[] bytes, String policy) { this.type = type; this.bytes = bytes; this.policy = policy; }
    }
}
