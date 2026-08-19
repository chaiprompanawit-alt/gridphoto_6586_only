#!/usr/bin/env bash
# build APK — ต้องมี JDK และ Android SDK ที่ ~/dev-tools
set -e
cd "$(dirname "$0")"
SRC="$PWD"

export JAVA_HOME="$HOME/dev-tools/jdk21"
export ANDROID_HOME="$HOME/dev-tools/android-sdk"
export PATH="$JAVA_HOME/bin:$PATH"
# บังคับ locale เป็น en_US — ถ้าใช้ locale ไทย Java จะให้ปี พ.ศ. (2569)
# ซึ่งเกินเพดานปีของรูปแบบวันที่ในไฟล์ zip แล้ว build จะพังที่ mergeReleaseJavaResource
export JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"
export LC_ALL=C

# 1) อัปเดตไฟล์เว็บลง www/ (คงการแก้ฟอนต์+ปิด SW ที่ทำไว้แล้ว)
python3 sync-www.py

# 2) build ในโฟลเดอร์ ASCII (path ภาษาไทยเคยมีปัญหากับเครื่องมือบางตัว)
BUILD="$HOME/gridphoto-build"
mkdir -p "$BUILD"
rsync -a --delete android www capacitor.config.json package.json node_modules gridphoto-release.keystore keystore.properties "$BUILD/"
echo "sdk.dir=$ANDROID_HOME" > "$BUILD/android/local.properties"

cd "$BUILD/android"
./gradlew assembleRelease

cp app/build/outputs/apk/release/app-release.apk "$SRC/ประกอบรูป-ปงสนุก.apk"
echo "เสร็จแล้ว: $SRC/ประกอบรูป-ปงสนุก.apk"
