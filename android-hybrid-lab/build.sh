#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
sdk="${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
tools="$sdk/build-tools/36.0.0"
android="$sdk/platforms/android-36/android.jar"
out="${BUILD_DIR:-$PWD/build}"
mkdir -p "$out"/{generated,classes,dex}
mkdir -p "$out/res/raw"
cp -a res/. "$out/res/"
python3 - "$out/res/raw/alarm_fallback.wav" <<'PYTONE'
import math
import struct
import sys
import wave

rate = 16000
with wave.open(sys.argv[1], "wb") as tone:
    tone.setparams((1, 2, rate, 0, "NONE", "not compressed"))
    frames = bytearray()
    for i in range(rate * 2):
        t = i / rate
        beat = t % 0.5
        envelope = max(0, min(1, beat / 0.025, (0.30 - beat) / 0.035))
        frequency = 660 if t < 0.5 else 880
        frames.extend(struct.pack("<h", round(11500 * envelope * math.sin(2 * math.pi * frequency * t))))
    tone.writeframes(frames)
PYTONE
"$tools/aapt2" compile --dir "$out/res" -o "$out/resources.zip"
"$tools/aapt2" link -I "$android" --manifest AndroidManifest.xml --java "$out/generated" \
  --version-code 3 --version-name 0.3 -o "$out/unsigned.apk" "$out/resources.zip"
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
