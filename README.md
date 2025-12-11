![UserLAnd Feature Graphic](https://raw.githubusercontent.com/CypherpunkArmory/UserLAnd/master/fastlane/metadata/android/en-US/images/featureGraphic.png)

# Welcome to UserLAnd

The easiest way to run a Linux distribution or application on Android.   
Features: 
* Run full linux distros or specific applications on top of Android.
* Install and uninstall like a regular app.
* No root required.

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=tech.ula)
     
## Have a bug report or a feature request?
You can see our templates by visiting our [issue center](https://github.com/CypherpunkArmory/UserLAnd/issues).

## Want to contribute?
See our [CONTRIBUTING](https://github.com/CypherpunkArmory/UserLAnd/blob/master/CONTRIBUTING.md) document.

## Start using UserLAnd
See our [Getting Started](https://github.com/CypherpunkArmory/UserLAnd/wiki/Getting-Started-in-UserLAnd) page.

## UserLAnd assets
The assets that UserLAnd depends on and the scripts that build them are contained in other repositories.  

The common assets that are used for all distros and application are found at [CypherpunkArmory/UserLAnd-Assets-Support](https://github.com/CypherpunkArmory/UserLAnd-Assets-Support).  

Distribution or application specific assets are found under CypherpunkArmory/UserLAnd-Assets-(__Distribution/App__). For example, our Debian specific assets can be found at [CypherpunkArmory/UserLAnd-Assets-Debian](https://github.com/CypherpunkArmory/UserLAnd-Assets-Debian)

## Static musl/clang utilities

We ship tiny, static helpers compiled with musl/clang and invoked by thin shell scripts (no Python wrappers).

Build them all in one reproducible step:

```sh
./scripts/build_static.sh
```

Install/copy them so they are reachable on your `$PATH`:

```sh
# Local install for the UserLAnd tree
./scripts/install_static.sh

# Package into the Termux APK assets (used during app builds)
./termux-app/package_static_bins.sh
```

### JSON contracts

All helpers emit stable JSON payloads so monitoring tools can parse the same schema across environments.

* `raf_cpu_core` reads `/proc/stat` from stdin and prints:

  ```json
  {
    "timestamp_ms": 1715890000000,
    "cpus": [
      {
        "id": "cpu0",
        "user": 1,
        "nice": 2,
        "system": 3,
        "idle": 4,
        "iowait": 5,
        "irq": 6,
        "softirq": 7,
        "steal": 8,
        "guest": 9,
        "guest_nice": 10
      }
    ]
  }
  ```

* `raf_mem_core` reads `/proc/meminfo` directly and prints a single object with all keys from `/proc/meminfo`:

  ```json
  {
    "timestamp_ms": 1715890000000,
    "meminfo": {
      "MemTotal": 16303424,
      "MemFree": 1024
    }
  }
  ```

* `raf_disk_core` reads `/proc/diskstats` directly and prints:

  ```json
  {
    "timestamp_ms": 1715890000000,
    "disks": [
      {
        "name": "sda",
        "major": 8,
        "minor": 0,
        "reads_completed": 0,
        "reads_merged": 0,
        "sectors_read": 0,
        "time_reading_ms": 0,
        "writes_completed": 0,
        "writes_merged": 0,
        "sectors_written": 0,
        "time_writing_ms": 0,
        "ios_in_progress": 0,
        "time_doing_io_ms": 0,
        "weighted_time_io_ms": 0
      }
    ]
  }
  ```

Each binary has a matching shell entrypoint (`scripts/raf_cpu_core.sh`, `scripts/raf_mem_core.sh`, `scripts/raf_disk_core.sh`) that prefers the local `build/bin` artifacts but also falls back to whatever is on the `$PATH`, ensuring consistent invocation names everywhere.
