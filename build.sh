#!/bin/bash
# 纯命令行构建（不依赖 Gradle）：aapt2 -> javac -> d8 -> zipalign -> apksigner
set -euo pipefail
cd "$(dirname "$0")"

SDK=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}}
BT="${ANDROID_BUILD_TOOLS_DIR:-$SDK/build-tools/34.0.0}"
PLATFORM="${ANDROID_PLATFORM_JAR:-$SDK/platforms/android-34/android.jar}"
JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk}
export PATH="$JAVA_HOME/bin:$PATH"

# 版本号 / 签名 / 输出名都可由环境变量注入（CI 用），本地不给就用下面这些默认值，
# 保证 `./build.sh` 的行为和以前完全一致。
VERSION_CODE=${VERSION_CODE:-1}          # manifest versionCode
VERSION_NAME=${VERSION_NAME:-1.0}        # manifest versionName
KEYSTORE_FILE=${KEYSTORE_FILE:-keystore.jks}
KEYSTORE_PASS=${KEYSTORE_PASS:-android}
KEY_ALIAS=${KEY_ALIAS:-androiddebugkey}
KEY_PASS=${KEY_PASS:-android}
OUTPUT_APK=${OUTPUT_APK:-phonecard.apk}   # 产物路径（相对项目根）
export VERSION_CODE VERSION_NAME

rm -rf build
mkdir -p build/gen build/classes build/dex

echo ">> aapt2 compile"
"$BT/aapt2" compile --dir res -o build/res.zip

echo ">> aapt2 link"
"$BT/aapt2" link \
  -o build/base.apk \
  -I "$PLATFORM" \
  --manifest AndroidManifest.xml \
  -R build/res.zip \
  --java build/gen \
  --min-sdk-version 29 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --auto-add-overlay

echo ">> javac"
javac -encoding UTF-8 -source 11 -target 11 -nowarn \
  -cp "$PLATFORM" -d build/classes \
  $(find src build/gen -name '*.java')

echo ">> d8"
"$BT/d8" --lib "$PLATFORM" --min-api 29 --output build/dex \
  $(find build/classes -name '*.class')

echo ">> package"
cp build/base.apk build/unsigned.apk
(cd build && zip -q -j unsigned.apk dex/classes.dex)

echo ">> zipalign"
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

if [ ! -f "$KEYSTORE_FILE" ]; then
  echo ">> keytool (create debug keystore)"
  keytool -genkeypair -keystore "$KEYSTORE_FILE" -storepass "$KEYSTORE_PASS" -keypass "$KEY_PASS" \
    -alias "$KEY_ALIAS" -dname "CN=Android Debug,O=Android,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi

echo ">> apksigner"
"$BT/apksigner" sign --ks "$KEYSTORE_FILE" \
  --ks-pass "pass:$KEYSTORE_PASS" --key-pass "pass:$KEY_PASS" --ks-key-alias "$KEY_ALIAS" \
  --out "$OUTPUT_APK" build/aligned.apk

"$BT/apksigner" verify --print-certs "$OUTPUT_APK" | head -3
ls -lh "$OUTPUT_APK"
echo "BUILD OK"
