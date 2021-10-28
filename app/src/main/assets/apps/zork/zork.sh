#! /bin/bash

SCRIPT_PATH=$(realpath ${BASH_SOURCE})
sudo rm -f $SCRIPT_PATH

if [ ! -f /usr/games/frotz ]; then
   sudo DEBIAN_FRONTEND=noninteractive apt -y update
   sudo DEBIAN_FRONTEND=noninteractive apt -y install wget frotz
fi
if [[ $? != 0 ]]; then
   read -rsp $'An error occurred installing packages, please try again and if it persists provide this log to the developer.\nPress any key to close...\n' -n1 key
   exit
fi
if [ ! -f ~/zork ]; then
   mkdir ~/zork
fi
cd ~/zork
if [ ! -f zdungeon.z5 ]; then
   wget http://mirror.ifarchive.org/if-archive/games/zcode/zdungeon.z5
fi
if [[ $? != 0 ]]; then
   read -rsp $'An error occurred downloading the game, please try again and if it persists provide this log to the developer.\nPress any key to close...\n' -n1 key
   exit
fi
frotz zdungeon.z5
exit
