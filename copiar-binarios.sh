#!/bin/bash
# Roda no terminal do MT Manager:  bash /sdcard/MBTerm/copiar-binarios.sh
D="$(cd "$(dirname "$0")" && pwd)/app/src/main"
A="$HOME/addon"
mkdir -p "$D/jniLibs/arm64-v8a" "$D/assets"
cp "$A/bin/proot" "$D/jniLibs/arm64-v8a/libproot.so" || exit 1
cp "$A/libexec/proot/loader" "$D/jniLibs/arm64-v8a/libproot-loader.so" || exit 1
cp "$A/lib/libandroid-support.so" "$D/jniLibs/arm64-v8a/" 2>/dev/null
cp "$A/lib/libandroid-glob.so" "$D/jniLibs/arm64-v8a/" 2>/dev/null
R="$(ls /sdcard/Download/alpine-minirootfs-*-aarch64.tar.gz 2>/dev/null | head -1)"
[ -n "$R" ] || { echo "Falta o alpine-minirootfs aarch64 em /sdcard/Download"; exit 1; }
cp "$R" "$D/assets/rootfs.bin"
ls -l "$D/jniLibs/arm64-v8a" "$D/assets"
