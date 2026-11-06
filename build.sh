#!/usr/bin/env bash
# Builds wow-addons.apk without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
#
# Needs JDK 17+ and the Android SDK (build-tools + platforms;android-35):
#   ANDROID_HOME=~/Android/Sdk ./build.sh
# Each tool can be overridden on its own: AAPT2, ZIPALIGN, D8_JAR, D8_LIB, APKSIGNER_JAR, ANDROID_JAR.
set -euo pipefail
cd "$(dirname "$0")"

PACKAGE=app.wowforever.addons
VERSION_CODE=9
VERSION_NAME=0.0.9

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BT="${BUILD_TOOLS:-$( (ls -d "$SDK"/build-tools/* 2>/dev/null || true) | sort -V | tail -1)}"
AAPT2="${AAPT2:-$BT/aapt2}"
ZIPALIGN="${ZIPALIGN:-$BT/zipalign}"
D8_JAR="${D8_JAR:-$BT/lib/d8.jar}"
APKSIGNER_JAR="${APKSIGNER_JAR:-$BT/lib/apksigner.jar}"
ANDROID_JAR="${ANDROID_JAR:-$SDK/platforms/android-35/android.jar}"
D8_LIB="${D8_LIB:-$ANDROID_JAR}"   # an old d8 may need an older android.jar here

for f in "$AAPT2" "$ZIPALIGN" "$D8_JAR" "$APKSIGNER_JAR" "$ANDROID_JAR"; do
  [ -e "$f" ] || { echo "Not found: $f (set ANDROID_HOME or the variable for this tool)"; exit 1; }
done

OUT=build/cli
rm -rf $OUT && mkdir -p $OUT/res $OUT/gen $OUT/classes $OUT/dex $OUT/stubs

echo "[1/6] compile resources"
"$AAPT2" compile --dir res -o $OUT/res/res.zip

echo "[2/6] link"
# Gradle takes the package from build.gradle.kts; aapt2 needs it in the manifest.
sed "s#<manifest #<manifest package=\"$PACKAGE\" #" AndroidManifest.xml > $OUT/AndroidManifest.xml
"$AAPT2" link -I "$ANDROID_JAR" --manifest $OUT/AndroidManifest.xml -o $OUT/base.apk \
  --java $OUT/gen --min-sdk-version 29 --target-sdk-version 34 \
  --version-code $VERSION_CODE --version-name $VERSION_NAME --auto-add-overlay $OUT/res/res.zip

echo "[3/6] javac"
BOOT="$ANDROID_JAR"
# Some trimmed android.jar builds lack the lambda bootstrap classes; d8 desugars lambdas anyway.
if ! unzip -l "$ANDROID_JAR" | grep -q "java/lang/invoke/LambdaMetafactory.class"; then
  javac -nowarn -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -d $OUT/stubs stubs/java/lang/invoke/*.java
  BOOT="$ANDROID_JAR:$OUT/stubs"
fi
find src $OUT/gen -name '*.java' > $OUT/sources.txt
javac -nowarn -parameters -Xlint:-options -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$BOOT" -classpath $OUT/stubs -d $OUT/classes @$OUT/sources.txt

echo "[4/6] d8"
java -cp "$D8_JAR" com.android.tools.r8.D8 --release --min-api 29 --lib "$D8_LIB" \
  --output $OUT/dex $(find $OUT/classes -name '*.class')

echo "[5/6] package"
cp $OUT/base.apk $OUT/unaligned.apk
(cd $OUT/dex && zip -q -X ../unaligned.apk classes.dex)
"$ZIPALIGN" -f -p 4 $OUT/unaligned.apk $OUT/aligned.apk

echo "[6/6] sign"
# The release key is not in the repository: set SIGNING_KEYSTORE, SIGNING_STORE_PASSWORD,
# SIGNING_KEY_ALIAS and SIGNING_KEY_PASSWORD. Without it, a throwaway key in build/ signs
# the APK (fine for testing, not for releases).
KS="${SIGNING_KEYSTORE:-}"
if [ -z "$KS" ] || [ ! -e "$KS" ]; then
  KS=build/debug.keystore
  SIGNING_KEY_ALIAS=debug SIGNING_STORE_PASSWORD=android SIGNING_KEY_PASSWORD=android
  [ -e "$KS" ] || keytool -genkeypair -keystore "$KS" -alias debug -storepass android -keypass android \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug" > /dev/null
fi
# ApkSignerTool is the SDK's apksigner (also bundled inside uber-apk-signer).
SIGN="java -cp $APKSIGNER_JAR com.android.apksigner.ApkSignerTool"
$SIGN sign --ks "$KS" --ks-key-alias "$SIGNING_KEY_ALIAS" \
  --ks-pass "pass:$SIGNING_STORE_PASSWORD" --key-pass "pass:$SIGNING_KEY_PASSWORD" \
  --out wow-addons.apk $OUT/aligned.apk
$SIGN verify --print-certs wow-addons.apk | head -1
ls -la wow-addons.apk
