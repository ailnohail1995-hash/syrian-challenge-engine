#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
sdk="${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
tools="$sdk/build-tools/36.0.0"
android="$sdk/platforms/android-36/android.jar"
out="${BUILD_DIR:-$PWD/build}"
mkdir -p "$out"/{generated,classes,dex}
"$tools/aapt2" compile --dir res -o "$out/resources.zip"
"$tools/aapt2" link -I "$android" --manifest AndroidManifest.xml --java "$out/generated" \
  --version-code 2 --version-name 0.2 -o "$out/unsigned.apk" "$out/resources.zip"
mapfile -t sources < <(find src "$out/generated" -name '*.java' -type f)
javac --release 17 -classpath "$android" -d "$out/classes" "${sources[@]}"
jar cf "$out/classes.jar" -C "$out/classes" .
"$tools/d8" --lib "$android" --min-api 26 --output "$out/dex" "$out/classes.jar"
(cd "$out/dex" && zip -q "$out/unsigned.apk" classes.dex)
"$tools/zipalign" -f -p 4 "$out/unsigned.apk" "$out/aligned.apk"
key="${HYBRID_LAB_DEBUG_KEYSTORE:-$HOME/.android/hybrid-lab-debug.jks}"
mkdir -p "$(dirname "$key")"
if [[ ! -f "$key" ]]; then
  keytool -genkeypair -keystore "$key" -storepass android -keypass android -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
fi
"$tools/apksigner" sign --ks "$key" --ks-pass pass:android --key-pass pass:android \
  --out "$out/hybrid-lab-debug.apk" "$out/aligned.apk"
"$tools/apksigner" verify --verbose "$out/hybrid-lab-debug.apk"
"$tools/aapt" dump badging "$out/hybrid-lab-debug.apk" | sed -n '1p'
