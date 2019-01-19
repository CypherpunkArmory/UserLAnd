![UserLAnd Feature Graphic](https://raw.githubusercontent.com/CypherpunkArmory/UserLAnd/master/fastlane/metadata/android/en-US/images/featureGraphic.png)

# Welcome to UserLAnd

The easiest way to run a Linux distribution or application on Android.   
Features: 
* Run full linux distros or specific applications on top of Android.
* Install and uninstall like a regular app.
* No root required.

[<img src="https://f-droid.org/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/tech.ula/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=tech.ula)
     
## Have a bug report or a feature request?
You can see our templates by visiting our [issue center](https://github.com/CypherpunkArmory/UserLAnd/issues).

You can also chat with us on [slack](https://communityinviter.com/apps/userlandtech/userland)
## Want to contribute?
See our [CONTRIBUTING](https://github.com/CypherpunkArmory/UserLAnd/blob/master/CONTRIBUTING.md) document.

## How to get started using UserLAnd:

There are two ways to use UserLAnd: single-click apps and user-defined custom sessions.

### Using single-click apps:
1. Click an app.
2. Fill out the required information.
3. You're good to go!

### Using user-defined custom sessions:
1. Define a session - This describes what filesystem you are going to use, and what kind of service you want to use when connecting to it (ssh or vnc).
2. Define a filesystem - This describes what distribution of Linux you want to install.
3. Once defined, just tap on the session to start up. This will download necessary assets, setup the filesystem, start the server, and connect to it.  This will take several minutes for the first start up, but will be quicker afterwards.

### Managing Packages 

**Debian, Ubuntu, And Kali**:

-> Update: `sudo apt-get update && sudo apt-get dist-upgrade`

-> Install Packages: `sudo apt-get install <package name>`

-> Remove Packages: `sudo apt-get remove <package name>`

Want to know more?: [Apt-Get Guide](https://help.ubuntu.com/community/AptGet/Howto)

**Archlinux**:

-> Update: `sudo pacman -Syu`

-> Install Packages: `sudo pacman -S <package name>`

-> Remove Packages: `sudo pacman -R <package name>`

Want to know more?: [Pacman Guide](https://wiki.archlinux.org/index.php/pacman)

**Alpine Linux**:

-> Update: `sudo apk update && sudo apk upgrade`

-> Install Packages: `sudo apk add <package name>`

-> Remove Packages: `sudo apk del <package name>`

Want to know more?: [Apk Guide](https://wiki.alpinelinux.org/wiki/Alpine_Linux_package_management)

### Installing A Desktop

**Debian, Ubuntu, And Kali**:

-> Install Lxde: `sudo apt-get install lxde` (default desktop)

-> Install X Server Client: [Download on the Play store](https://play.google.com/store/apps/details?id=x.org.server&hl=en)

-> Launch XSDL

-> In UserLAnd Type: `export DISPLAY=:0 PULSE_SERVER=tcp:127.0.0.1:<PORT NUMBER>`

-> Then Type: `startlxde`

-> Then Go Back To XSDL And The Desktop Will Show Up

**ArchLinux**:

-> Install Lxde: `sudo pacman -S lxde`

-> Install X Server Client: [Download on the Play store](https://play.google.com/store/apps/details?id=x.org.server&hl=en)

-> Launch XSDL

-> In UserLAnd Type: export `DISPLAY=:0 PULSE_SERVER=tcp:127.0.0.1:<PORT NUMBER>`

-> Then Type: `startlxde`

-> Then Go Back To XSDL And The Desktop Will Show Up

**Alpine Linux**:

-> Install Xfce (Default For Alpine) `sudo apk add xfce4`

-> Install X Server Client: [Download on the Play store](https://play.google.com/store/apps/details?id=x.org.server&hl=en)

-> Launch XSDL

-> In UserLAnd Type: export `DISPLAY=:0 PULSE_SERVER=tcp:127.0.0.1:<PORT NUMBER>`

-> Then Type: `startlxde`

-> Then Go Back To XSDL And The Desktop Will Show Up
<br/>
<br/>
But you can do so much more than that. Your phone isn't just a play thing any more!

This app is fully open source.  You can find our code [here](https://github.com/CypherpunkArmory/UserLAnd/).

The assets that UserLAnd depends on and the scripts that build them are contained in other repositories.  

The common assets that are used for all distros and application are found at [CypherpunkArmory/UserLAnd-Assets-Support](https://github.com/CypherpunkArmory/UserLAnd-Assets-Support).  

Distribution or application specific assets are found under CypherpunkArmory/UserLAnd-Assets-(__Distribution/App__). For example, our Debian specific assets can be found at [CypherpunkArmory/UserLAnd-Assets-Debian](https://github.com/CypherpunkArmory/UserLAnd-Assets-Debian)
