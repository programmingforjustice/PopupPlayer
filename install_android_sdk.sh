#!/bin/bash

DIR=$(pwd)
ANDROID_SDK="${DIR}/android_sdk"

conda create -n android_sdk -y
source activate android_sdk

conda install -c conda-forge openjdk=20.0.2 -y

mkdir -p ${ANDROID_SDK}
cd ${ANDROID_SDK}

wget https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip
unzip commandlinetools-linux-10406996_latest.zip

cd "${ANDROID_SDK}/cmdline-tools/bin"

yes | ./sdkmanager --licenses --sdk_root=${ANDROID_SDK}

echo "sdk.dir=${ANDROID_SDK}" >> local.properties

conda deactivate