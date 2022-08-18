EVMap [![Build Status](https://github.com/johan12345/EVMap/actions/workflows/tests.yml/badge.svg)](https://github.com/johan12345/EVMap/actions)
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
- Android Auto & Android Automotive OS integration
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

There are three different build flavors, `googleNormal`, `fossNormal` and `googleAutomotive`.
- The `foss` variant only uses Mapbox data and should run on most Android devices, even without
  Google Play Services.
- The `google` variants also include access to Google Maps data.
    - `googleNormal` is intended to run on smartphones and tablets, and also includes the Android
      Auto app for use on the car display.
    - `googleAutomotive` variant is intended to be installed directly on car infotainment systems
      using the Google-flavored Android Automotive OS. It does not provide the usual smartphone UI.

We also have a special [documentation page](doc/android_auto.md) on how to test the Android Auto
app.

Translations
------------

You can use our [Weblate page](https://hosted.weblate.org/projects/evmap/) to help translate EVMap
into new languages.

<a href="https://hosted.weblate.org/engage/evmap/">
<img src="https://hosted.weblate.org/widgets/evmap/-/open-graph.png" width="500" alt="Translation status" />
</a>
