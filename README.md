![UserLAnd Feature Graphic](https://github.com/CypherpunkArmory/UserLAnd/raw/master/play_store/featureGraphic.png)

[UserLAnd Homepage](https://userland.tech)

# Welcome to UserLAnd

The easiest way to run a Linux distribution or application on Android.   
Features: 
* Run full linux distros or specific applications on top of Android.
* Install and uninstall like a regular app.
* No root required.

How to get started:

There are two ways to use UserLAnd: single-click apps and user-defined custom sessions.

### Using single-click apps:
1. Click an app.
2. Fill out the required information.
3. You're good to go!

### Using user-defined custom sessions:
1. Define a session - This describes what filesystem you are going to use, and what kind of service you want to use when connecting to it (ssh or vnc).
2. Define a filesystem - This describes what distribution of Linux you want to install.
3. Once defined, just tap on the session to start up. This will download necessary assets, setup the filesystem, start the server, and connect to it.  This will take several minutes for the first start up, but will be quicker afterwards.

### Using your Linux distribution

A normal first session might look like this:
* sudo apt update <- update package information
* sudo apt install wget <- install whatever you want to use
* wget http://google.com <- use it  

But you can do so much more than that. Your phone isn't just a play thing any more!

This app is fully open source.  You can find our code and file issues [here](https://github.com/CypherpunkArmory/UserLAnd/).

The assets that UserLAnd depends on and the scripts that build them are contained in other repositories.  

The common assets that are used for all distros and application are found at [CypherpunkArmory/UserLAnd-Assets-Support](https://github.com/CypherpunkArmory/UserLAnd-Assets-Support).  

Distribution or application specific assets are found under CypherpunkArmory/UserLAnd-Assets-(__Distribution/App__). For example, our Debian specific assets can be found at [CypherpunkArmory/UserLAnd-Assets-Debian](https://github.com/CypherpunkArmory/UserLAnd-Assets-Debian)
