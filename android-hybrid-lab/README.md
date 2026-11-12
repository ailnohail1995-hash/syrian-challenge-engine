# Android Hybrid Lab

An independent Android test shell with presets for the four official websites.
The app settings accept an HTTPS URL, label and text scale. A pinned launcher
shortcut can use that label and a locally selected image. The installed APK
keeps the lab's launcher label and package ID. Custom URLs run as ordinary web
pages. Only the four configured HTTPS origins receive the native message port.

The Android bridge supports local notifications and exact alarms, cancelling an
alarm, same-origin downloads and text sharing. The WebView also supports file
selection and camera capture from an approved origin, with runtime permission.
Websites must load `hybrid-lab-client.js` and call the bridge to use it. The
built-in 30-second reminder tests native scheduling without changing any website.
Scheduled alarms must be scheduled again after a device reboot in this prototype.

Push notification registration and authenticated background data sync require
backend integration. Offline content depends on the site's existing offline
support. No site source, credentials or student records are included here.

To build, install JDK 21 and Android SDK Platform 36 / Build Tools 36.0.0, then
run `ANDROID_HOME=/path/to/sdk bash android-hybrid-lab/build.sh`. The result
is a debug-signed APK. Set `HYBRID_LAB_DEBUG_KEYSTORE` to reuse a lab signing
key between builds. The public build workflow uploads the APK as an artifact.
