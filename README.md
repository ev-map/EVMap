EVMap [![Build Status](https://github.com/ev-map/EVMap/actions/workflows/tests.yml/badge.svg)](https://github.com/ev-map/EVMap/actions)
=====

<a href="https://ev-map.app" target="_blank">
<img src="https://raw.githubusercontent.com/ev-map/EVMap/master/_img/feature_graphic.svg" width=700 alt="Logo"/></a>

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
- Can use Google Maps or OpenStreetMap as map backends - the version available on F-Droid only uses
  OSM.

Screenshots
-----------

<img src="https://raw.githubusercontent.com/ev-map/EVMap/master/_img/screenshots/phone/en/mapbox/01_map.png" width=250 alt="Screenshot 1"/><img src="https://raw.githubusercontent.com/ev-map/EVMap/master/_img/screenshots/phone/en/mapbox/02_detail.png" width=250 alt="Screenshot 2"/>

Development setup
-----------------

The App is developed using Android Studio and should pretty much work out-of-the-box when you clone
the Git repository and open the project with Android Studio.

The only exception is that you need to obtain some API keys for the different data sources that
EVMap uses and put them into the app in the form of a resource file called `apikeys.xml` under
`app/src/main/res/values`. You can find more information on which API keys are necessary for which
features and how they can be obtained in our [documentation page](doc/api_keys.md).

There are four different build flavors, `googleNormal`, `fossNormal`, `googleAutomotive`, and
`fossAutomotive`.

- The `foss` variants only use OSM data and should run on most Android devices, even without
  Google Play Services.
    - `fossNormal` is intended to run on smartphones and tablets, and also includes the Android
      Auto app for use on the car display (however Android Auto may not work if the app is not
      installed from Google Play, see https://github.com/ev-map/EVMap/issues/319).
    - `fossAutomotive` can be installed directly on
      [Android Automotive OS (AAOS)](https://source.android.com/docs/automotive/start/what_automotive)
      headunits without Google services.
      It does not provide the usual smartphone UI, and requires an implementation of the
      [AOSP template app host](https://source.android.com/docs/automotive/hmi/aosp_host)
      to be installed. If you are an OEM and would like to distribute EVMap to your AAOS vehicles,
      please [get in touch](mailto:evmap@vonforst.net).
- The `google` variants also include access to Google Maps data.
    - `googleNormal` is intended to run on smartphones and tablets, and also includes the Android
      Auto app for use on the car display.
    - `googleAutomotive` can be installed directly on car infotainment systems running the
      Google-flavored Android Automotive OS (Google Automotive Services /
      ["Google built-in"](https://built-in.google/cars/)).
      It does not provide the usual smartphone UI, and requires the
      [Google Automotive App Host](https://play.google.com/store/apps/details?id=com.google.android.apps.automotive.templates.host)
      to run, which should be preinstalled on those cars and can be updated through the Play Store.

We also have a special [documentation page](doc/android_auto.md) on how to test the Android Auto
app.

Translations
------------

You can use our [Weblate page](https://hosted.weblate.org/projects/evmap/) to help translate EVMap
into new languages.

<a href="https://hosted.weblate.org/engage/evmap/">
<img src="https://hosted.weblate.org/widgets/evmap/-/open-graph.png" width="400" alt="Translation status" />
</a>

Sponsors
--------

Many users currently support the development EVMap with their donations. You can find more
information on the [Donate page](https://ev-map.app/donate/) on the EVMap website.

<a href="https://www.jawg.io"><img src="https://www.jawg.io/static/Blue@10x-9cdc4596e4e59acbd9ead55e9c28613e.png" alt="JawgMaps" height="58"/></a><br>
Since May 2024, **JawgMaps** provides their OpenStreetMap vector map tiles service to EVMap for
free, i.e. the background map displayed in the app if OpenStreetMap is selected as the data source.

<a href="https://chargeprice.app"><img src="https://raw.githubusercontent.com/ev-map/EVMap/master/_img/powered_by_chargeprice.svg" alt="Powered by Chargeprice" height="58"/></a><br>
Since April 2021, **Chargeprice.app** provides their price comparison API at a greatly reduced
price for EVMap. This data is used in EVMap's price comparison feature.

<a href="https://fronyx.io/"><img src="https://github.com/ev-map/EVMap/blob/master/_img/powered_by_fronyx.svg" alt="Powered by Fronyx" height="68"/></a><br>
Since September 2022, for certain charging stations, **Fronyx** provide us free access to their API
for availability predictions.