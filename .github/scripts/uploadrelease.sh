#!/bin/sh

export TAG='0.1_pre-alpha'
export OWNER=$(printf "$GITHUB_REPOSITORY" | cut -d '/' -f 1)

cd $HOME

echo 'owner='$OWNER
echo 'repo='$(basename $GITHUB_REPOSITORY)
echo 'tag='$TAG
echo 'filename=./BuffRapp-debug-signed.apk' # (path)

curl https://gist.githubusercontent.com/stefanbuck/ce788fee19ab6eb0b4447a85fc99f447/raw/dbadd7d310ce8446de89c4ffdf1db0b400d0f6c3/upload-github-release-asset.sh > upload-github-release-asset.sh
chmod +x upload-github-release-asset.sh
./upload-github-release-asset.sh github_api_token=$GITHUB_TOKEN owner=$OWNER repo=$(basename $GITHUB_REPOSITORY) tag=$TAG filename=./BuffRapp-debug-signed.apk