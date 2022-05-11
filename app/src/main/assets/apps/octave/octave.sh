#! /bin/bash

SCRIPT_PATH=$(realpath ${BASH_SOURCE})
sudo rm -f $SCRIPT_PATH

if [ ! -f /support/.octave_app_install_passed ] || [ ! -f /usr/bin/octave ]; then
   rm -f /support/.octave_app_install_passed 
   sudo apt-get update
   sudo DEBIAN_FRONTEND=noninteractive apt-get -y --no-install-recommends install octave less octave-control octave-financial octave-io octave-missing-functions octave-odepkg octave-optim octave-signal octave-specfun octave-statistics octave-symbolic octave-image gnuplot-x11 pstoedit
   if [[ $? == 0 ]]; then
      echo "graphics_toolkit('gnuplot')" > ~/.octaverc
      touch /support/.octave_app_install_passed
   else
      read -rsp $'An error occurred installing packages, please try again and if it persists provide this log to the developer.\nPress any key to close...\n' -n1 key
   fi
fi
if [ -f /support/.octave_app_install_passed ]; then
   /usr/bin/octave
fi
exit
