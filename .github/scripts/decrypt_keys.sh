#!/bin/sh
cd $GITHUB_WORKSPACE/keys
gpg --quiet --batch --yes --decrypt \
    --passphrase="$STOREPASS" \
    --output $GITHUB_WORKSPACE/buffrapp.jks buffrapp.jks.gpg
