#!/support/common/busybox_static sh

if [[ -z "${TAR_PATH}" ]]; then
  TAR_PATH="/sdcard/rootfs.tar.gz"
fi

ln -s /support/common/busybox_static /support/common/gzip
export PATH=/support/common/gzip:$PATH

/support/common/busybox_static tar -czvf $TAR_PATH --exclude sys --exclude dev --exclude proc --exclude data --exclude mnt --exclude host-rootfs --exclude support --exclude sdcard --exclude etc/mtab --exclude usr/local/bin/sudo --exclude etc/profile.d/userland_profile.sh --exclude etc/ld.so.preload /
