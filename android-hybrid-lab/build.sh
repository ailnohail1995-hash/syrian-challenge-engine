#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
sdk="${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
tools="$sdk/build-tools/36.0.0"
android="$sdk/platforms/android-36/android.jar"
out="${BUILD_DIR:-$PWD/build}"
mkdir -p "$out"/{generated,classes,dex}
rm -rf "$out/res"
mkdir -p "$out/res/raw"
cp -a res/. "$out/res/"
case "${APP_VARIANT:-hub}" in
  hub) name='تطبيقات سلوم'; preset=-1; package=dev.hybridlab; icon=salloum_icon ;;
  dulja) name='دلجة العام'; preset=0; package=dev.hybridlab.dulja; icon=dulja_card ;;
  lovable) name='دلجة Lovable'; preset=1; package=dev.hybridlab.lovable; icon=dulja_card ;;
  toqna) name='TOQNA'; preset=2; package=dev.hybridlab.toqna; icon=toqna_card ;;
  heroes) name='أبطال TOQNA'; preset=3; package=dev.hybridlab.heroes; icon=toqna_card ;;
  *) echo "Unknown APP_VARIANT" >&2; exit 2 ;;
esac
if [[ "$preset" == -1 ]]; then
  sed 's|<!-- LAB_INSTRUMENTATION -->|<instrumentation android:name=".LabSmoke" android:targetPackage="dev.hybridlab" />|' AndroidManifest.xml > "$out/AndroidManifest.xml"
else
  cp AndroidManifest.xml "$out/AndroidManifest.xml"
fi
mkdir -p "$out/res/values"
cat > "$out/res/values/launcher.xml" <<RES
<resources>
  <string name="launcher_name">$name</string>
  <integer name="launch_preset">$preset</integer>
  <item name="launcher_icon" type="drawable">@drawable/$icon</item>
</resources>
RES
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
"$tools/aapt2" link -I "$android" --manifest "$out/AndroidManifest.xml" --java "$out/generated" \
  --rename-manifest-package "$package" --version-code 5 --version-name 0.5 -o "$out/unsigned.apk" "$out/resources.zip"
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
