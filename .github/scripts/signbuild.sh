#!/bin/sh

cd $GITHUB_WORKSPACE/app/build/outputs/apk/debug
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore $GITHUB_WORKSPACE/buffrapp.jks -storepass $STOREPASS -keypass $STOREPASS app-debug-unsigned.apk buffrapp
jarsigner -verify app-debug-unsigned.apk
${ANDROID_HOME}/build-tools/28.0.3/zipalign -v 4 app-debug-unsigned.apk $HOME/BuffRapp-debug-signed.apk
