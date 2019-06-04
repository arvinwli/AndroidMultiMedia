#!/bin/bash
NDK=/usr/local/app/android-ndk-r15c
SYSROOT=$NDK/platforms/android-16/arch-arm/
PREBUILT=$NDK/toolchains/arm-linux-androideabi-4.9/prebuilt
TOOLCHAIN=$NDK/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64

function build_one
{
./configure  --prefix=$PREFIX \
	--enable-shared \
	--cc=$TOOLCHAIN/bin/arm-linux-androideabi-gcc \
	--disable-static \
	--disable-doc \
	--disable-ffserver \
	--disable-yasm \
	--enable-cross-compile \
	--enable-gpl \
	--enable-libx264 \
	--enable-decoder=h264 \
	--enable-encoder=libx264 \
	--enable-libfdk-aac \
	--enable-encoder=libfdk_aac \
	--enable-decoder=libfdk_aac \
	--enable-nonfree \
	--cross-prefix="$TOOLCHAIN/bin/arm-linux-androideabi-" \
	--target-os=linux \
	--arch=arm \
	--sysroot=$SYSROOT \
	--extra-cflags="-Os -fpic $ADDI_CFLAGS" \
	--extra-ldflags="$ADDI_LDFLAGS" \
	$ADDITIONAL_CONFIGURE_FLAG
}

CPU=arm
PREFIX=$(pwd)/android/$CPU
ADDI_CFLAGS="-marm -I/usr/local/app/x264-snapshot-20140916-2245-stable/android/include -DANDROID -I/usr/local/app/fdk-aac-0.1.4/android/include"
ADDI_LDFLAGS="-L/usr/local/app/x264-snapshot-20140916-2245-stable/android/lib -L/usr/local/app/fdk-aac-0.1.4/android/lib"

build_one

