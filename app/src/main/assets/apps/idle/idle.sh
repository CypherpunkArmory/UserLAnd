#! /bin/bash

SCRIPT_PATH=$(realpath ${BASH_SOURCE})
sudo rm -f $SCRIPT_PATH

if [ ! -f /usr/bin/idle ]; then
   sudo DEBIAN_FRONTEND=noninteractive apt -y update
   sudo DEBIAN_FRONTEND=noninteractive apt -y --no-install-recommends install idle3
fi

if [ ! -f /usr/bin/idle3 ]; then
   sudo ln -s /usr/bin/idle /usr/bin/idle3
fi

if [[ $? != 0 ]]; then
   read -rsp $'An error occurred installing packages, please try again and if it persists provide this log to the developer.\nPress any key to close...\n' -n1 key
   exit
fi

/usr/bin/idle3

exit
