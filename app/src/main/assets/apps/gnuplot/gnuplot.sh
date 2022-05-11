#! /bin/bash

SCRIPT_PATH=$(realpath ${BASH_SOURCE})
sudo rm -f $SCRIPT_PATH

if [ ! -f /usr/bin/gnuplot ]; then
   sudo apt-get update
   sudo DEBIAN_FRONTEND=noninteractive apt-get -y install gnuplot-x11 
   if [[ $? != 0 ]]; then
      read -rsp $'An error occurred installing packages, please try again and if it persists provide this log to the developer.\nPress any key to close...\n' -n1 key
   fi
fi
if [[ -z "${DISPLAY}" ]]; then
   GNUTERM=dumb /usr/bin/gnuplot
else
   GNUTERM=x11 /usr/bin/gnuplot
fi
exit
