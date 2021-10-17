EVMap [![Build Status](https://travis-ci.org/johan12345/EVMap.svg?branch=master)](https://travis-ci.org/johan12345/EVMap)
=====

<img src="https://raw.githubusercontent.com/johan12345/EVMap/master/_img/feature_graphic.svg" width=700 alt="Logo"/>

Android app to find electric vehicle charging stations.

<a href="https://play.google.com/store/apps/details?id=net.vonforst.evmap" target="_blank">
<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on Google Play" height="100"/></a>
<a href="https://f-droid.org/repository/browse/?fdid=net.vonforst.evmap" target="_blank">
<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="100"/></a>

Features
--------

- [Material Design](https://material.io/)
- Shows all charging stations from the community-maintained [GoingElectric.de](https://www.goingelectric.de/stromtankstellen/) and [Open Charge Map](https://openchargemap.org) directories
- Realtime availability information (only in Europe)
- Search for places
- Advanced filtering options, including saved filter profiles
- Favorites list, also with availability information
- Integrated price comparison using [Chargeprice.app](https://chargeprice.app) (only in Europe)
- Android Auto integration
- No ads, fully open source
- Compatible with Android 5.0 and above
- Can use Google Maps or Mapbox (OpenStreetMap) as map backends - the version available on F-Droid only uses Mapbox.

Screenshots
-----------

<img src="https://raw.githubusercontent.com/johan12345/EVMap/master/_img/screenshots/phone/en/mapbox/01_map.png" width=250 alt="Screenshot 1"/><img src="https://raw.githubusercontent.com/johan12345/EVMap/master/_img/screenshots/phone/en/mapbox/02_detail.png" width=250 alt="Screenshot 2"/>

Development setup
-----------------

The App is developed using Android Studio and should pretty much work out-of-the-box when you clone
the Git repository and open the project with Android Studio.

The only exception is that you need to obtain some free API keys for the different data sources that
EVMap uses and put them into the app in the form of a resource file called `apikeys.xml` under
`app/src/main/res/values`. You can find more information on which API keys are necessary for which
features and how they can be obtained in our [documentation page](doc/api_keys.md).

There are two different build flavors, `google` and `foss`, where only the `google` variant uses
Google Maps data and provides the Android Auto integration. The `foss` variant only uses Mapbox data
and should run on devices without Google Play Services.

We also have a special [documentation page](doc/android_auto.md) on how to test the Android Auto
app.
