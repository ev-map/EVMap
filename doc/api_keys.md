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
</resources>
```

</details>

Not all API keys are strictly required if you only want to work on certain parts of the app. For
example, you can choose only one of the map providers and one of the charging station databases. The
Chargeprice API key is also only required if you want to test the price comparison feature.

All API keys are available for free. Some APIs require payment above a certain limit, but the free
tier should be plenty for local testing and development.

Below you find a list of all the services and how to obtain the API keys.

Map providers
-------------

The different Map SDKs are wrapped by our [fork](https://github.com/johan12345/AnyMaps) of the
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
   - app website (*Webseite der App*): https://github.com/johan12345/EVMap
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
3. Enter the name of the app (EVMap) and website (https://github.com/johan12345/EVMap), and in the
   description field describe that you would like to contribute to the development of EVMap and
   therefore need access to the OpenChargeMap API. Do not tick the *List App in Public Showcase*
   box. Then, click *save*.
4. Your API key will appear on the
   [My Apps](https://openchargemap.org/site/profile/applications) page.

</details>

Pricing providers
-----------------

### Chargeprice.app

[API documentation](https://github.com/chargeprice/chargeprice-api-docs)

<details>
<summary>How to obtain an API key</summary>

1. Check the
   [Pricing page](https://github.com/chargeprice/chargeprice-api-docs/blob/master/plans.md)
   for information on the current plans at Chargeprice. There should be a free tier up to a certain
   limit of API calls per month.
2. Contact [contact@chargeprice.net](mailto:contact@chargeprice.net), stating that you would like to
   contribute to the development the open source EVMap app and therefore need access to the
   Chargeprice API for testing.
3. When your access to the API is approved, you will receive an API key via email.

</details>

