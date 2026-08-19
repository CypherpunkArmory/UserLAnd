SCRIPT_PATH=$(realpath ${BASH_SOURCE})

sudo rm -f $SCRIPT_PATH

if [ -d /sdcard ]; then
  if [ ! -d ~/sdcard ]; then
    ln -s /sdcard ~/sdcard
  fi
  # See kali.sh's identical block for why, and for why this is now gated on /sdcard actually
  # being a 9p mount (AVF/QEMU only -- this used to run unconditionally, but under proot it
  # fires a real ACTION_OPEN_DOCUMENT_TREE consent prompt per subdirectory instead of the
  # harmless no-op it was assumed to be).
  if grep -qE '^[^ ]+ [^ ]+ 9p ' /proc/mounts 2>/dev/null; then
    (nohup sh -c 'sudo chmod a+rX /sdcard; for f in /sdcard/*; do case "$f" in */Android) continue;; esac; sudo chmod -R a+rX "$f"; done' >/dev/null 2>&1 &) 2>/dev/null
  fi
fi
if [ -d /storage ]; then
  if [ ! -d ~/scopedStorage ]; then
    ln -s /storage/internal ~/scopedStorage
  fi
fi

echo "Welcome to Ubuntu in UserLAnd!"
