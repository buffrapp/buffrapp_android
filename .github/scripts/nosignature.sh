#!/bin/sh

cd $GITHUB_WORKSPACE

# Set up dummy data, we won't neither use it nor see it anyways.
git config --global user.name 'Unknown'
git config --global user.email ''

# Apply the patch.
git am patches/0001-By-default-don-t-sign-debugging-builds.patch
