#! /bin/bash

SCRIPT_PATH=$(realpath ${BASH_SOURCE})
sudo rm -f $SCRIPT_PATH

if [ ! -f /usr/bin/startxfce4 ]; then
   sudo apt-get update
   sudo DEBIAN_FRONTEND=noninteractive apt-get -y install xfce4
   if [[ $? != 0 ]]; then
      read -rsp $'An error occurred installing packages, please try again and if it persists provide this log to the developer.\nPress any key to close...\n' -n1 key
      exit
   fi
fi

if grep -q "^/usr/bin/startxfce4" ~/.vnc/xstartup; then
   echo "xstartup already setup"
else
   mkdir -p ~/.vnc
   echo 'xrdb $HOME/.Xresources' > ~/.vnc/xstartup
   echo 'xsetroot -solid grey' > ~/.vnc/xstartup
   echo '/usr/bin/startxfce4' > ~/.vnc/xstartup
   DE_CHANGED=1
fi

if grep -q "^/usr/bin/startxfce4" ~/.xinitrc; then
   echo "xinitrc already setup"
else
   echo 'xrdb $HOME/.Xresources' > ~/.xinitrc
   echo 'xsetroot -solid grey' > ~/.xinitrc
   echo '/usr/bin/startxfce4' > ~/.xinitrc
   DE_CHANGED=1
fi

if [[ -n "$DE_CHANGED" ]]; then
   while true
   do
	   RED='\033[0;31m'
	   BLUE='\033[0;34m'
	   echo -e "${BLUE}You are requesting a new desktop environment a restart is required."
	   echo -e "${RED}Stop and then restart the app in UserLAnd."
	   sleep 5
   done
fi

exit
