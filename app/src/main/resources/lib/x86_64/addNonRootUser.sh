#! /bin/bash

if [[ -z "${INITIAL_USERNAME}" ]]; then
  INITIAL_USERNAME="user"
fi
if [[ -z "${INITIAL_PASSWORD}" ]]; then
  INITIAL_PASSWORD="userland"
fi
if [[ -z "${INITIAL_VNC_PASSWORD}" ]]; then
  INITIAL_VNC_PASSWORD="userland"
fi

if [ ! -d "/home/$INITIAL_USERNAME" ]; then
  useradd $INITIAL_USERNAME -s /bin/bash -m -u 2000 
  echo $INITIAL_USERNAME:$INITIAL_PASSWORD | chpasswd
  echo "/bin/bash" >> /etc/shells
  chsh -s /bin/bash $INITIAL_USERNAME
fi
