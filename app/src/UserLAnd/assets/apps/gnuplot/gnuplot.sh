#! /bin/bash

# Only for a user's own login: an SSH session (SSH_CONNECTION) or the graphical session's
# terminal (DISPLAY). A VM's root control shell is a login shell too and sources this on every
# start; run there, its sudo swallowed the VM app's commands and the instance never started.
if [ -z "$SSH_CONNECTION$DISPLAY" ]; then return 0 2>/dev/null || exit 0; fi

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
