# Running UserLAnd cloud demo

Clone this branch and run it on device. Click an app to download assets.
Startup will go through the normal steps but hang on 'Starting service'
Kill the app then

NOTE: The key names are expected to be exactly as described in this file. Don't change them.

**Generate SSH keys**
`ssh-keygen -t rsa -f sshkey`

**Move SSH keys to required location on device**
`adb push sshkey /sdcard`
`adb push sshkey.pub /sdcard`
`adb shell`

- All of the following should be executed from within adb shell
`run-as tech.ula`
`mv /sdcard/sshkey files/sshkey`
`mv /sdcard/sshkey.pub files/sshkey.pub`
`chmod 600 files/sshkey`
`chmod 644 files/sshkey.pub`

**Start demo**
- Login in cloud fragment using credentials
- Press connect
