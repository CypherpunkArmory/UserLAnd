SCRIPT_PATH=$(realpath ${BASH_SOURCE})

sudo rm -f $SCRIPT_PATH

if [ -d /sdcard ]; then
  if [ ! -d ~/sdcard ]; then
    ln -s /sdcard ~/sdcard
  fi
  # See kali.sh's identical block for why: mapped-file 9p permissions only cover entries the
  # guest has touched, so pre-existing shared-storage content needs a one-time permissive nudge.
  # Android/ is typically huge (per-app data/obb across every installed app) and largely
  # inaccessible even to MANAGE_EXTERNAL_STORAGE holders under scoped storage -- skip it so
  # the walk actually reaches the media folders users mean by "sdcard" in a reasonable time.
  (nohup sh -c 'sudo chmod a+rX /sdcard; for f in /sdcard/*; do case "$f" in */Android) continue;; esac; sudo chmod -R a+rX "$f"; done' >/dev/null 2>&1 &) 2>/dev/null
fi
if [ -d /storage ]; then
  if [ ! -d ~/scopedStorage ]; then
    ln -s /storage/internal ~/scopedStorage
  fi
fi

echo "Welcome to Arch Linux in UserLAnd!"
