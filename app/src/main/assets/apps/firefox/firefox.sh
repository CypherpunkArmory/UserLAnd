#! /bin/bash

SCRIPT_PATH=$(realpath ${BASH_SOURCE})
sudo rm -f $SCRIPT_PATH

if [ ! -f /support/.firefox_app_install_passed ] || [ ! -f /usr/bin/firefox ]; then
   rm -f /support/.firefox_app_install_passed 
   sudo apt-get update
   sudo DEBIAN_FRONTEND=noninteractive apt-get -y install firefox-esr
   if [[ $? == 0 ]]; then
      touch /support/.firefox_app_install_passed
   else
      read -rsp $'An error occurred installing packages, please try again and if it persists provide this log to the developer.\nPress any key to close...\n' -n1 key
      exit
   fi
fi
/usr/bin/firefox &> /dev/null &
