#!/usr/bin/env bash
# ساخت APK «نگهبان گشت» بدون Gradle — قابل اجرای مجدد در هر محیط لینوکس
# نیازمندی‌ها: java (JDK 11+)، curl، unzip، zip — بقیه خودش دانلود می‌کند
set -euo pipefail

PROJ="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK:-/tmp/asdk}"
BT="$SDK/build-tools/34.0.0"
AJ="$SDK/platforms/android-34/android.jar"
ZX="$PROJ/libs/core-3.5.3.jar"

if [ ! -x "$BT/aapt2" ] || [ ! -f "$AJ" ] || [ ! -f "$ZX" ]; then
  echo "== دانلود اجزای SDK =="
  mkdir -p "$SDK/tmp" "$PROJ/libs"
  curl -sfL -o "$SDK/tmp/bt.zip"   https://dl.google.com/android/repository/build-tools_r34-linux.zip
  curl -sfL -o "$SDK/tmp/plat.zip" https://dl.google.com/android/repository/platform-34-ext7_r03.zip
  curl -sfL -o "$ZX"               https://repo1.maven.org/maven2/com/google/zxing/core/3.5.3/core-3.5.3.jar
  rm -rf "$SDK/tmp/btx" "$SDK/tmp/plx"
  unzip -q -o "$SDK/tmp/bt.zip"   -d "$SDK/tmp/btx"
  unzip -q -o "$SDK/tmp/plat.zip" -d "$SDK/tmp/plx"
  d="$(find "$SDK/tmp/btx" -mindepth 1 -maxdepth 1 -type d | head -1)"
  mkdir -p "$BT"; rm -rf "$BT"; mv "$d" "$BT"
  d="$(find "$SDK/tmp/plx" -mindepth 1 -maxdepth 1 -type d | head -1)"
  mkdir -p "$SDK/platforms/android-34"; rm -rf "$SDK/platforms/android-34"; mv "$d" "$SDK/platforms/android-34"
fi

export PATH="/usr/lib/jvm/jdk-11/bin:$PATH"
command -v java >/dev/null

B="$PROJ/build"
rm -rf "$B"
mkdir -p "$B/gen" "$B/classes"

echo "== aapt2: کامپایل منابع =="
"$BT/aapt2" compile --dir "$PROJ/res" -o "$B/res.zip"
"$BT/aapt2" link -o "$B/base.apk" -I "$AJ" \
  --manifest "$PROJ/AndroidManifest.xml" \
  --java "$B/gen" \
  --min-sdk-version 24 --target-sdk-version 34 \
  --auto-add-overlay "$B/res.zip"

echo "== javac =="
find "$PROJ/src" "$B/gen" -name '*.java' > "$B/sources.txt"
javac -source 8 -target 8 -encoding UTF-8 -nowarn \
  -bootclasspath "$AJ" -classpath "$ZX" \
  -d "$B/classes" @"$B/sources.txt"

echo "== d8: تبدیل به DEX =="
(cd "$B/classes" && jar cf "$B/app.jar" .)
"$BT/d8" --release --min-api 24 --lib "$AJ" --output "$B" "$B/app.jar" "$ZX"

echo "== بسته‌بندی و امضا =="
(cd "$B" && zip -q -u base.apk classes.dex)
"$BT/zipalign" -f 4 "$B/base.apk" "$B/aligned.apk"

KS="$PROJ/release.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias negahban -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass negahban123 -keypass negahban123 \
    -dname "CN=Negahban Patrol" >/dev/null 2>&1
fi

OUT="$PROJ/negahban-v0.8.apk"
"$BT/apksigner" sign --ks "$KS" --ks-key-alias negahban \
  --ks-pass pass:negahban123 --key-pass pass:negahban123 \
  --out "$OUT" "$B/aligned.apk"

echo "== تأیید امضا =="
"$BT/apksigner" verify --print-certs "$OUT" | head -4
echo "OK → $OUT"
