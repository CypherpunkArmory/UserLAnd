#!/support/common/busybox_static sh

if [ ! -f /support/rootfs.tar.gz ]; then
   /support/common/busybox_static cat /support/rootfs.tar.gz.part* > /support/rootfs.tar.gz 
   /support/common/busybox_static rm -f /support/rootfs.tar.gz.part*
fi

ln -s /support/common/busybox_static /support/common/gzip
export PATH=/support/common/gzip:$PATH

/support/common/busybox_static tar -xzvf /support/rootfs.tar.gz --exclude sys --exclude dev --exclude proc --exclude data --exclude mnt --exclude host-rootfs --exclude support --exclude sdcard --exclude etc/mtab --exclude usr/local/bin/sudo --exclude etc/profile.d/userland_profile.sh --exclude etc/ld.so.preload -C /

if [[ $? == 0 ]]; then
	/support/common/addNonRootUser.sh
	/support/common/busybox_static touch /support/.success_filesystem_extraction
else
	/support/common/busybox_static touch /support/.failure_filesystem_extraction
fi
