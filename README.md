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
- Realtime availability information (beta)
- Search places
- Favorites list, also with availability information
- Charging price comparison, powered by [Chargeprice.app](https://chargeprice.app)
- Android Auto integration
- No ads, fully open source
- Compatible with Android 5.0 and above
- Can use Google Maps or Mapbox (OpenStreetMap) as map backends - the version available on F-Droid only uses Mapbox.

Screenshots
-----------

<img src="https://raw.githubusercontent.com/johan12345/EVMap/master/_img/screenshots/phone/01_main.png" width=250 alt="Screenshot 1"/><img src="https://raw.githubusercontent.com/johan12345/EVMap/master/_img/screenshots/phone/02_detail.png" width=250 alt="Screenshot 2"/>

Development setup
-----------------

The App is developed using Android Studio.

For testing the app, you need to obtain free API Keys for the 
[GoingElectric API](https://www.goingelectric.de/stromtankstellen/api/),
the [Chargeprice API](https://github.com/chargeprice/chargeprice-api-docs),
the [OpenChargeMap API](https://openchargemap.org/site/profile/appedit),
as well as for [Google APIs](https://console.developers.google.com/)
("Maps SDK for Android" and "Places API" need to be activated) and/or [Mapbox](https://www.mapbox.com/). These API keys need to be put into the
app in the form of a resource file called `apikeys.xml` under `app/src/main/res/values`, with the
following content:

```xml
<resources>
    <string name="google_maps_key" templateMergeStrategy="preserve" translatable="false">
        insert your Google Maps key here
    </string>
    <string name="mapbox_key" translatable="false">
        insert your Mapbox key here
    </string>
    <string name="goingelectric_key" translatable="false">
        insert your GoingElectric key here
    </string>
    <string name="chargeprice_key" translatable="false">
        insert your Chargeprice key here
    </string>
    <string name="openchargemap_key" translatable="false">
        insert your OpenChargeMap key here
    </string>
</resources>
```
