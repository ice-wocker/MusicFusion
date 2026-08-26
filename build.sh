#!/data/data/com.termux/files/usr/bin/bash
# MusicFusion 构建链 (复用卫士工具链)
set -e
B=~/musicfusion
cd $B
rm -rf classes gen && mkdir -p classes gen
aapt package -f -M AndroidManifest.xml -S res -I ~/apkbuild/android.jar -J gen
javac -source 1.8 -target 1.8 -bootclasspath ~/apkbuild/android.jar \
  -classpath ~/apkbuild/android.jar -d classes \
  gen/R.java src/com/musicfusion/app/*.java > javac.log 2>&1 \
  || { echo 编译失败; tail -15 javac.log; exit 1; }
dx --dex --output=classes.dex classes
aapt package -f -M AndroidManifest.xml -S res -I ~/apkbuild/android.jar -F base.apk
aapt add base.apk classes.dex
[ -f assets/stations.json ] && aapt add base.apk assets/stations.json
[ -f assets/ic_launcher.png ] && aapt add base.apk assets/ic_launcher.png
zipalign -f 4 base.apk aligned.apk 2>/dev/null || cp base.apk aligned.apk
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -alias mf \
  -keyalg RSA -keysize 2048 -validity 10000 -storepass mf123456 -keypass mf123456 \
  -dname "CN=MusicFusion,O=OpenSource" 2>/dev/null
apksigner sign --ks debug.keystore --ks-pass pass:mf123456 --out musicfusion.apk aligned.apk
apksigner verify musicfusion.apk && echo 签名OK
ls -la musicfusion.apk
