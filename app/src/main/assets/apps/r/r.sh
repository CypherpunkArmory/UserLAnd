#! /bin/bash

SCRIPT_PATH=$(realpath ${BASH_SOURCE})
sudo rm -f $SCRIPT_PATH

if [ ! -f /usr/bin/R ]; then
   sudo DEBIAN_FRONTEND=noninteractive apt -y update
   sudo DEBIAN_FRONTEND=noninteractive apt -y install r-base r-base-dev 
   if [[ $? != 0 ]]; then
      read -rsp $'An error occurred installing packages, please try again and if it persists provide this log to the developer.\nPress any key to close...\n' -n1 key
   fi
fi
/usr/bin/R
exit
