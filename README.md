![UserLAnd Feature Graphic](https://github.com/CypherpunkArmory/UserLAnd/raw/master/play_store/featureGraphic.png)

## Welcome to UserLAnd

The easiest way to run a Linux distribution or application on Android. 
* Can run full linux distros or specific applications on top of Android.
* No root required.
* Installs and uninstalls like a regular app.

How to get started:
* First you define a session - This describes what filesystem you are going to use, what server you want to run (ssh or vnc) and how you want to connect to it (ConnectBot or bVNC).  
* As part of defining a session you can define a filesystem - This describes what distro or application you want to install (only supports debian, but more coming soon).
* Once that is defined, you just tap on the session to start it up. It will download necessary assets, setup the filesystem, start the server and connect to it.  This will take several minutes the first time, but will be quick after that.

A normal first session might look like this:
* sudo apt update <- sudo or su because you are not fake root initially, update because you need to do this
* sudo apt install wget <- install whatever you want to use
* wget http://google.com <- use it
But, you can do so much more than that...your phone is not just a play thing anymore.

This app is fully open source.  You can find our code and file issues here:

https://github.com/CypherpunkArmory/UserLAnd/
