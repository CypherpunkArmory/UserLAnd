#! /bin/bash

SCRIPT_PATH=$(realpath ${BASH_SOURCE})
sudo rm -f $SCRIPT_PATH

xterm &

if [ ! -f /support/gdk_fix ]; then
  update-mime-database /usr/share/mime
  find /usr/lib -name gdk-pixbuf-query-loaders -exec {} --update-cache \;
  touch /support/gdk_fix
fi

if [ -f /Intents/url.txt ]; then
  url=`cat /Intents/url.txt`
  rm /Intents/url.txt
  MOZ_DISABLE_AUTO_SAFE_MODE=1 /usr/bin/firefox -new-tab "$url" &> /dev/null &
else
  MOZ_DISABLE_AUTO_SAFE_MODE=1 /usr/bin/firefox &> /dev/null &
fi

while true
do
    if [ -f /Intents/url.txt ]; then
      url=`cat /Intents/url.txt`
      rm /Intents/url.txt
      MOZ_DISABLE_AUTO_SAFE_MODE=1 /usr/bin/firefox -new-tab "$url" &> /dev/null &
    fi
    sleep 1
done
