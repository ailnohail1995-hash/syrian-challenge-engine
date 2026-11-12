package dev.hybridlab;

import android.content.Context;
import android.graphics.Bitmap;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import com.android.apksig.ApkSigner;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.zip.*;
import org.json.JSONObject;

final class ApkFactory {
    private static final String TEMPLATE_ID = "dev.salloum.template";
    private static final String LABEL = "SalloumGeneratedLabel";

    static File build(Context context, String id, String name, String url, Bitmap icon) throws Exception {
        if (!id.matches("dev\\.salloum\\.p[a-f0-9]{20}")) throw new IllegalArgumentException("Invalid package");
        File directory = new File(context.getFilesDir(), "exports");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create output");
        File unsigned = File.createTempFile("unsigned", ".apk", context.getCacheDir());
        File signed = File.createTempFile("signed", ".apk", directory);
        try {
            try (ZipInputStream input = new ZipInputStream(context.getAssets().open("apk-template.bin"));
                 ZipOutputStream output = new ZipOutputStream(new FileOutputStream(unsigned))) {
                ZipEntry entry;
                while ((entry = input.getNextEntry()) != null) {
                    String path = entry.getName();
                    if (entry.isDirectory()) continue;
                    output.putNextEntry(new ZipEntry(path));
                    if ("AndroidManifest.xml".equals(path)) {
                        int version = Math.toIntExact(System.currentTimeMillis() / 1000);
                        File previous = new File(directory, id + ".apk");
                        if (previous.isFile()) {
                            android.content.pm.PackageInfo info = context.getPackageManager().getPackageArchiveInfo(previous.getPath(), 0);
                            if (info != null) version = Math.max(version, Math.addExact(info.versionCode, 1));
                        }
                        output.write(manifest(read(input), id, name, version));
                    } else if ("res/drawable/salloum_icon.png".equals(path) && icon != null) {
                        if (!icon.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("Icon encoding failed");
                    } else output.write(read(input));
                    output.closeEntry();
                }
                output.putNextEntry(new ZipEntry("assets/project.json"));
                output.write(new JSONObject().put("name", name).put("url", url).toString().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
            KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
            keys.load(null);
            if (!keys.containsAlias(id)) {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
                generator.initialize(new KeyGenParameterSpec.Builder(id, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                        .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                        .setCertificateSubject(new javax.security.auth.x500.X500Principal("CN=Salloum Local APK"))
                        .setCertificateNotAfter(new java.util.Date(4102444800000L)).build());
                generator.generateKeyPair();
            }
            ApkSigner.SignerConfig signer = new ApkSigner.SignerConfig.Builder("Salloum",
                    (PrivateKey) keys.getKey(id, null),
                    Collections.singletonList((X509Certificate) keys.getCertificate(id))).build();
            new ApkSigner.Builder(Collections.singletonList(signer)).setInputApk(unsigned)
                    .setOutputApk(signed).setMinSdkVersion(26).setV1SigningEnabled(false)
                    .setV2SigningEnabled(true).setV3SigningEnabled(false).build().sign();
            File result = new File(directory, id + ".apk");
            android.system.Os.rename(signed.getPath(), result.getPath());
            return result;
        } finally {
            unsigned.delete();
            signed.delete();
        }
    }

    static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int size;
        while ((size = input.read(buffer)) != -1) data.write(buffer, 0, size);
        return data.toByteArray();
    }

    static byte[] manifest(byte[] bytes, String id, String name, int version) throws IOException {
        ByteBuffer source = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int start = 8;
        int oldSize = source.getInt(start + 4);
        int header = source.getShort(start + 2) & 0xffff;
        int count = source.getInt(start + 8);
        int flags = source.getInt(start + 16);
        int strings = source.getInt(start + 20);
        if (source.getShort(start) != 1 || source.getInt(start + 12) != 0)
            throw new IOException("Unsupported template string pool");
        boolean utf8 = (flags & 256) != 0;
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        int[] offsets = new int[count];
        int found = 0;
        for (int i = 0; i < count; i++) {
            source.position(start + strings + source.getInt(start + header + i * 4));
            if (utf8) length(source);
            int units = utf8 ? length(source) : source.getShort() & 0xffff;
            if (!utf8 && (units & 0x8000) != 0) units = ((units & 0x7fff) << 16) | (source.getShort() & 0xffff);
            byte[] value = new byte[utf8 ? units : units * 2];
            source.get(value);
            String text = new String(value, utf8 ? StandardCharsets.UTF_8 : StandardCharsets.UTF_16LE);
            if (TEMPLATE_ID.equals(text)) { text = id; found |= 1; }
            if (LABEL.equals(text)) { text = name; found |= 2; }
            value = text.getBytes(utf8 ? StandardCharsets.UTF_8 : StandardCharsets.UTF_16LE);
            offsets[i] = encoded.size();
            if (utf8) {
                length(encoded, text.length());
                length(encoded, value.length);
            } else {
                encoded.write(text.length() & 255);
                encoded.write(text.length() >> 8);
            }
            encoded.write(value);
            encoded.write(0);
            if (!utf8) encoded.write(0);
        }
        if (found != 3) throw new IOException("Missing template metadata");
        while ((encoded.size() & 3) != 0) encoded.write(0);
        int newSize = strings + encoded.size();
        ByteBuffer result = ByteBuffer.allocate(bytes.length + newSize - oldSize).order(ByteOrder.LITTLE_ENDIAN);
        result.put(bytes, 0, start + header);
        result.putInt(4, result.capacity());
        result.putInt(start + 4, newSize);
        result.putInt(start + 16, flags & ~1);
        for (int offset : offsets) result.putInt(offset);
        result.position(start + strings);
        result.put(encoded.toByteArray());
        result.put(bytes, start + oldSize, bytes.length - start - oldSize);
        int chunk = start + newSize;
        while (chunk < result.capacity()) {
            int size = result.getInt(chunk + 4);
            if (size < 8) throw new IOException("Malformed manifest");
            if (result.getShort(chunk) == 0x102) {
                int attributes = chunk + 16 + (result.getShort(chunk + 24) & 0xffff);
                int stride = result.getShort(chunk + 26) & 0xffff;
                int attributesCount = result.getShort(chunk + 28) & 0xffff;
                for (int i = 0; i < attributesCount; i++) {
                    int attribute = attributes + i * stride;
                    if ((result.get(attribute + 15) & 0xff) == 16 && result.getInt(attribute + 16) == 7) {
                        result.putInt(attribute + 16, version);
                        return result.array();
                    }
                }
                break;
            }
            chunk += size;
        }
        throw new IOException("Missing version code");
    }

    private static int length(ByteBuffer source) {
        int first = source.get() & 255;
        return first < 128 ? first : ((first & 127) << 8) | (source.get() & 255);
    }

    private static void length(ByteArrayOutputStream output, int size) {
        if (size >= 128) output.write((size >> 8) | 128);
        output.write(size & 255);
    }
}
