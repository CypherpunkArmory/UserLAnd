SCRIPT_PATH=$(realpath ${BASH_SOURCE})

sudo rm -f $SCRIPT_PATH

if [ -d /sdcard ]; then
  if [ ! -d ~/sdcard ]; then
    ln -s /sdcard ~/sdcard
  fi
  # Shared storage arrives over a 9p mount using the mapped-file security model, which only
  # grants the guest's non-root user access to entries the guest has itself chmod'd/created --
  # pre-existing content (the entire point of sharing storage) otherwise keeps Android's real
  # owner-only permissions forever, since nothing auto-populates metadata for a plain read. Nudge
  # it permissive once, in the background so first login isn't blocked walking a large media
  # library. This only ever touches 9p-side metadata, never the real files (see UserLAnd-QEMU's
  # security_model=mapped-file).
  #
  # Only do this when /sdcard is actually a 9p mount (AVF/QEMU) -- under proot it's a real bind
  # mount, and this used to run unconditionally there too on the assumption that Android's
  # storage FUSE would just reject the chmod as a harmless no-op. It doesn't: this proot fork's
  # --droid_files extension intercepts open/chmod/getdents under /sdcard and routes each one
  # through the same ACTION_OPEN_DOCUMENT_TREE consent flow a real user access would hit, so
  # walking every subdirectory here fired that prompt for each one automatically, in order, on
  # every single login -- confirmed live, and with no real file access involved to justify it.
  # Android/ is typically huge (per-app data/obb across every installed app) and largely
  # inaccessible even to MANAGE_EXTERNAL_STORAGE holders under scoped storage -- skip it so
  # the walk actually reaches the media folders users mean by "sdcard" in a reasonable time.
  if grep -qE '^[^ ]+ [^ ]+ 9p ' /proc/mounts 2>/dev/null; then
    (nohup sh -c 'sudo chmod a+rX /sdcard; for f in /sdcard/*; do case "$f" in */Android) continue;; esac; sudo chmod -R a+rX "$f"; done' >/dev/null 2>&1 &) 2>/dev/null
  fi
fi
if [ -d /storage ]; then
  if [ ! -d ~/scopedStorage ]; then
    ln -s /storage/internal ~/scopedStorage
  fi
fi

echo "Welcome to Kali in UserLAnd!"
