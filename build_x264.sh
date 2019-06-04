#!/bin/bash
#NDK=/usr/local/android/ndk-bundle
NDK=/usr/local/app/android-ndk-r15c
TOOLCHAIN=$NDK/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64
PLATFORM=$NDK/platforms/android-16/arch-arm
PREFIX=./android
echo $NDK
echo $TOOLCHAIN
echo $PLATFORM
echo $PREFIX
function build_one
{
	./configure \
		 --prefix=$PREFIX \
		 --enable-static \
		 --disable-shared \
		 --enable-pic \
		 --disable-asm \
		 --disable-cli \
		 --host=arm-linux \
		 --cross-prefix=$TOOLCHAIN/bin/arm-linux-androideabi- \
		 --sysroot=$PLATFORM \
 
}
build_one
