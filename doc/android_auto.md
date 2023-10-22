Testing EVMap on Android Auto
=============================

In addition to the Android app on the phone, EVMap is also available as an Android Auto app built
using the [Android for Cars App Library](https://developer.android.com/training/cars/apps). Its code
is located under the `net.vonforst.evmap.auto` package.

This page contains instructions on how to test the Android Auto app using the Desktop Head Unit
(DHU).

Further information about testing Android Auto apps is also available on the
[Android Developers site](https://developer.android.com/training/cars/testing).

Install the Desktop Head Unit
-----------------------------

Refer to the instructions on the
[Android Developers site](https://developer.android.com/training/cars/testing#install)
to install the DHU 2.0 using the SDK manager.

Install Android Auto
--------------------

If you haven't already, install the
[Android Auto](https://play.google.com/store/apps/details?id=com.google.android.projection.gearhead)
app on your test device from the Google Play Store.

If you are using the Android Emulator, the Play Store may show the Android Auto app as incompatible.
In that case, download the APK for the newest version from a site like
[APKMirror](https://www.apkmirror.com/apk/google-inc/android-auto/)
(choosing the correct architecture for your emulator - x86_64, x86 or ARM)
and drag it onto the running emulator window to install.

Starting and connecting to the DHU
----------------------------------
(see also the corresponding section on
the [Android Developers site](https://developer.android.com/training/cars/testing#running-dhu))

1. Go to Android Auto settings (Settings app -> Connected devices -> Connection preferences -> Android Auto)
2. Scroll all the way down to the app version, tap it 10 times
3. Click *OK* in the dialog that appears to enable developer mode
4. In the menu on the top left, tap *Start head unit server*
5. On your computer, run the following command to set up the required port forwarding:
    ```shell
    adb forward tcp:5277 tcp:5277
    ```
6. Start the DHU by running the command `desktop-head-unit.exe` (on Windows) or
   `./desktop-head-unit` (on macOS or Linux) in a console window from the
   `SDK_LOCATION/extras/google/auto/` directory.

The desktop head unit should appear and show the Android Auto interface. If this is the first time
the Android device is connected to the DHU, you may need to open the Android Auto app again on the
phone to accept some permissions before the connection can succeed.

Testing EVMap on the DHU
------------------------

Make sure that you have selected the `googleDebug` variant in the *Build Variants*  tool window in
Android Studio (the `foss` variants do not contain the Android Auto app). Then, install the app on
your phone - if the DHU is connected, the app should also automatically appear in the apps menu on
Android Auto.

For testing features that require car sensors, you need to start the DHU with the option
`-c config/default_sensors.ini` to select a configuration file that enables these sensors. From the
console, you can then type certain commands to update the data of these sensors, such as:

```shell
location 54.0 9.0     # latitude, longitude
fuel 50               # percentage
range 100             # in kilometers
speed 28              # in m/s
```
