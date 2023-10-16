API keys required for testing EVMap
===================================

EVMap uses multiple different data sources, most of which require an API key. These API keys need to
be put into the app in the form of a resource file called `apikeys.xml` under
`app/src/main/res/values`, with the following content:

<details>
<summary>apikeys.xml content</summary>

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
   <string name="fronyx_key" translatable="false">
      insert your Fronyx key here
   </string>
   <string name="acra_credentials" translatable="false">
      insert your ACRA crash reporting credentials here
   </string>
</resources>
```

</details>

Not all API keys are strictly required if you only want to work on certain parts of the app. For
example, you can choose only one of the map providers and one of the charging station databases. The
Chargeprice API key is also only required if you want to test the price comparison feature.

All APIs can be used for free, at least for testing. Some APIs require payment above a certain usage
limit or to get access to the full dataset, but the free tiers should be plenty for local testing
and development.

Below you find a list of all the services and how to obtain the API keys.

Map providers
-------------

The different Map SDKs are wrapped by our [fork](https://github.com/ev-map/AnyMaps) of the
[AnyMaps](https://github.com/sharenowTech/AnyMaps) library to provide a common API. The `google`
build flavor of the app includes both Google Maps and Mapbox and allows the user to switch between
the two, while the `foss` flavor only includes the Mapbox SDK.

> ⚠️ When testing the app using the Android Emulator, we recommend using Google Maps and not Mapbox, as the latter has
[issues displaying the markers](https://github.com/mapbox/mapbox-gl-native/issues/10829). It works fine on real Android devices.

### Google Maps

[Maps SDK for Android](https://developers.google.com/maps/documentation/android-sdk/overview),
[Places API](https://developers.google.com/maps/documentation/places/android-sdk/overview)

<details>
<summary>How to obtain an API key</summary>

1. Log in to the [Google API console](https://console.developers.google.com/) with your Google
   account
2. Create a new project, or select an existing one that you want to use
3. Under *APIs & Services → Library*, enable
   the [Maps SDK for Android](https://console.cloud.google.com/apis/library/maps-android-backend.googleapis.com)
   and [Places API](https://console.cloud.google.com/apis/library/places-backend.googleapis.com).
4. Under *APIs & Services → Credentials*, click on *Create credentials → API Key*
5. Copy the displayed key to your `apikeys.xml` file.

</details>

### Mapbox

[Maps SDK for Android](https://docs.mapbox.com/android/maps)

<details>
<summary>How to obtain an API key</summary>

1. [Sign up](https://account.mapbox.com/auth/signup) for a Mapbox account
2. Under [Access Tokens](https://account.mapbox.com/access-tokens/), create a new access token
3. Set a name for the scope and enable only the preselected public scopes. Do not restrict the token
   to a specific URL (this setting is not compatible with Android apps)

</details>


Charging station databases
--------------------------

### **GoingElectric.de**

GoingElectric.de provides an [API](https://www.goingelectric.de/stromtankstellen/api/) for their
community-maintained directory of charging stations. The website and data are mostly only available
in German.

<details>
<summary>How to obtain an API key</summary>

1. [Sign up](https://www.goingelectric.de/forum/ucp.php?mode=register) for an account in the
   GoingElectric.de forum. The registration page can be switched to English using the dropdown menu
   under "Sprache". Then, agree to the registration terms.
2. Fill in your desired username, password and email address and submit the registration form. You
   do not need to fill the information under *GoingElectric Usermap*.
3. Verify your account by clicking on the link in the email you received
4. [Log in](https://www.goingelectric.de/forum/ucp.php?mode=login) to the GoingElectric forum
5. Go to [this link](https://www.goingelectric.de/stromtankstellen/api/new/) to request access to
   the API. This page is only available in German. You need to fill in the following data:
   - name / company (*Name / Firma*)
   - street address (*Straße, Nr.*)
   - postal code, town (*Postleitzahl, Ort*)
   - country (*Land*)
   - email address (*E-Mail Adresse*)
   - website (*Webseite*, optional)
   - phone number (*Telefonnummer*, optional)
   - name of the app (*Name der App*): EVMap
   - app website (*Webseite der App*): https://github.com/ev-map/EVMap
   - description (*kurze Beschreibung der App*): please explain that you would like to contribute to
     the development of EVMap and therefore need access to the GoingElectric.de API.
   - Referrer (*Herkunft*): leave this field blank!
6. When your access to the API is approved, you can access the
   [API console](https://www.goingelectric.de/stromtankstellen/api/ucp/) to retrieve your API key.

</details>

### **OpenChargeMap**

[API documentation](https://openchargemap.org/site/develop/api)

<details>
<summary>How to obtain an API key</summary>

1. [Sign up](https://openchargemap.org/site/loginprovider/register) for an account at OpenChargeMap
2. Go to the [My Apps](https://openchargemap.org/site/profile/applications) page and click
   *Register an application*
3. Enter the name of the app (EVMap) and website (https://github.com/ev-map/EVMap), and in the
   description field describe that you would like to contribute to the development of EVMap and
   therefore need access to the OpenChargeMap API. Do not tick the *List App in Public Showcase*
   box. Then, click *save*.
4. Your API key will appear on the
   [My Apps](https://openchargemap.org/site/profile/applications) page.

</details>

### **Tesla**

[API documentation](https://developer.tesla.com/docs/fleet-api)

<details>
<summary>How to obtain an API key</summary>

1. [Sign up](https://www.tesla.com/teslaaccount) for a Tesla account
2. In the [Tesla Developer Portal](https://developer.tesla.com/), click on "Request app access"
3. Enter the details of your app
4. You will receive a *Client ID* and *Client Secret*. Enter them both into `tesla_credentials`,
   separated by a colon (`:`).

</details>

Pricing providers
-----------------

### Chargeprice.app

[API documentation](https://github.com/chargeprice/chargeprice-api-docs)

<details>
<summary>How to obtain an API key</summary>

Since February 2022, the Chargeprice API is no longer available for free to new customers. However,
you can use their
[staging API](https://github.com/chargeprice/chargeprice-api-docs/blob/master/test_the_api.md)
for free to test the Chargeprice features. This is already
[configured](https://github.com/ev-map/EVMap/blob/master/app/src/debug/res/values/donottranslate.xml)
by default for the debug version of the app, so you can leave the `chargeprice_key` field in your
new `app/src/main/res/values/apikeys.xml` file blank. Note that the staging API contains only a
limited dataset, so it only outputs prices for certain charge point operators and payment plans (see
[here](https://docs.google.com/document/d/14zlFr5IEhhR3uGXO5QePKjNUQANVwA-Ba-cZbOCiOBk/edit) for
details).

In case you want to pay for access to the full Chargeprice API, check out their
[API docs](https://github.com/chargeprice/chargeprice-api-docs) on GitHub and contact them at
[sales@chargeprice.net](mailto:sales@chargeprice.net).
</details>

Availability data providers
---------------------------

### fronyx

[fronyx](https://fronyx.io/) provides us predictions of charging station availability.

<details>
<summary>How to obtain an API key</summary>

The API is not publically available, contact [fronyx](https://fronyx.io/contact-us/) to get an API
key and documentation.

If you don't want to test this functionality, simply leave the API key blank.
</details>

Crash reporting
---------------

Crash reporting for release builds is done using [ACRA](https://github.com/ACRA/acra).
This should not be needed for debugging.
If you still want to try it out, you can host any compatible backend such as
[Acrarium](https://github.com/F43nd1r/Acrarium/) yourself.