#!/bin/sh

cd $HOME
curl https://gist.githubusercontent.com/stefanbuck/ce788fee19ab6eb0b4447a85fc99f447/raw/dbadd7d310ce8446de89c4ffdf1db0b400d0f6c3/upload-github-release-asset.sh > upload-github-release-asset.sh
chmod +x upload-github-release-asset.sh
./upload-github-release-asset.sh github_api_token=$GITHUB_TOKEN owner=$GITHUB_ACTOR repo=$(basename $GITHUB_REPOSITORY) tag='0.1_pre-alpha' filename=$HOME/BuffRapp-debug-signed.apk