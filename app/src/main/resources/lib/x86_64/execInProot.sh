#!/data/data/tech.ula/files/support/busybox sh

$LIB_PATH/busybox clear

if [[ -z "${OS_VERSION}" ]]; then
  OS_VERSION="4.0.0"
fi

if [[ ! -r /dev/ashmem ]] ; then
	EXTRA_BINDINGS="$EXTRA_BINDINGS -b $ROOTFS_PATH/tmp:/dev/ashmem" 
fi
if [[ ! -r /dev/shm ]] ; then
	EXTRA_BINDINGS="$EXTRA_BINDINGS -b $ROOTFS_PATH/tmp:/dev/shm" 
fi
if [[ ! -r /proc/stat ]] ; then
	numProc="$($LIB_PATH/busybox grep rocessor /proc/cpuinfo)"
	numProc="${numProc: -1}"
	if [[ "$numProc" -le "3" ]] 2>/dev/null ; then
		EXTRA_BINDINGS="$EXTRA_BINDINGS -b $ROOT_PATH/support/stat4:/proc/stat" 
	else
		EXTRA_BINDINGS="$EXTRA_BINDINGS -b $ROOT_PATH/support/stat8:/proc/stat" 
	fi
fi
if [[ ! -r /proc/uptime ]] ; then
	EXTRA_BINDINGS="$EXTRA_BINDINGS -b $ROOT_PATH/support/uptime:/proc/uptime" 
fi
if [[ ! -r /proc/version ]] ; then
	currDate="$($LIB_PATH/busybox date)"
	echo "Linux version $OS_VERSION (fake@userland) #1 $currDate" > $ROOT_PATH/support/version
	EXTRA_BINDINGS="$EXTRA_BINDINGS -b $ROOT_PATH/support/version:/proc/version" 
fi

#save what proot version we are using, so we cannot mess this up later
#will make future major upgrades easier
if [ -f $ROOTFS_PATH/support/.success_filesystem_extraction ]; then
	if [ ! -f $ROOTFS_PATH/support/.proot_version ]; then
		if [ -d $ROOTFS_PATH/support/meta_db ]; then
			$LIB_PATH/busybox echo "_meta_leveldb" > $ROOTFS_PATH/support/.proot_version
		else
			$LIB_PATH/busybox echo "_meta" > $ROOTFS_PATH/support/.proot_version
		fi
	fi
else
	if [ ! -f $ROOTFS_PATH/support/.proot_version ]; then
		$LIB_PATH/busybox touch $ROOTFS_PATH/support/.proot_version
	fi
fi
PROOT_VER=$($LIB_PATH/busybox cat $ROOTFS_PATH/support/.proot_version)
PROOT="$LIB_PATH/proot$PROOT_VER"

#launch PRoot
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin PROOT_TMP_DIR=$ROOTFS_PATH/support/ $PROOT -r $ROOTFS_PATH -v $PROOT_DEBUG_LEVEL -p -H -0 -l -L -b /sys -b /dev -b /proc -b /data -b /mnt -b /proc/mounts:/etc/mtab -b /:/host-rootfs -b $ROOTFS_PATH/support/:/support -b $ROOTFS_PATH/support/nosudo:/usr/local/bin/sudo -b $ROOTFS_PATH/support/userland_profile.sh:/etc/profile.d/userland_profile.sh -b $ROOTFS_PATH/support/ld.so.preload:/etc/ld.so.preload -b $ROOT_PATH/support:/support/common $EXTRA_BINDINGS $@
