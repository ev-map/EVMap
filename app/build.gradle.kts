import java.util.Base64

plugins {
    id("com.adarshr.test-logger") version "3.1.0"
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("kotlin-kapt")
    id("androidx.navigation.safeargs.kotlin")
    id("com.mikepenz.aboutlibraries.plugin")
}


android {
    useLibrary("android.car")

    defaultConfig {
        applicationId = "net.vonforst.evmap"
        compileSdk = 34
        minSdk = 21
        targetSdk = 34
        // NOTE: always increase versionCode by 2 since automotive flavor uses versionCode + 1
        versionCode = 230
        versionName = "1.9.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val isRunningOnCI = System.getenv("CI") == "true"
            if (isRunningOnCI) {
                // configure keystore
                storeFile = file("../_ci/keystore.jks")
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEYSTORE_ALIAS")
                keyPassword = System.getenv("KEYSTORE_ALIAS_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        create("releaseAutomotivePackageName") {
            // Faurecia Aptoide requires the automotive variant to use a separate package name
            initWith(getByName("release"))
            applicationIdSuffix = ".automotive"
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    sourceSets {
        getByName("releaseAutomotivePackageName").setRoot("src/release")
    }

    flavorDimensions += listOf("dependencies", "automotive")
    productFlavors {
        create("foss") {
            dimension = "dependencies"
            isDefault = true
        }
        create("google") {
            dimension = "dependencies"
            versionNameSuffix = "-google"
        }
        create("normal") {
            dimension = "automotive"
            isDefault = true
        }
        create("automotive") {
            dimension = "automotive"
            versionNameSuffix = "-automotive"
            versionCode = defaultConfig.versionCode!! + 1
            minSdk = 29
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        targetCompatibility = JavaVersion.VERSION_1_8
        sourceCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KaptGenerateStubs>().configureEach {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    buildFeatures {
        dataBinding = true
        viewBinding = true
        buildConfig = true
    }
    lint {
        disable += listOf("NullSafeMutableLiveData")
        warning += listOf("MissingTranslation")
    }
    androidResources {
        generateLocaleConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    namespace = "net.vonforst.evmap"

    // add API keys from environment variable if not set in apikeys.xml
    applicationVariants.all {
        val goingelectricKey =
            System.getenv("GOINGELECTRIC_API_KEY") ?: project.findProperty("GOINGELECTRIC_API_KEY")
                ?.toString()
        if (goingelectricKey != null) {
            resValue("string", "goingelectric_key", goingelectricKey)
        }
        var openchargemapKey =
            System.getenv("OPENCHARGEMAP_API_KEY") ?: project.findProperty("OPENCHARGEMAP_API_KEY")
                ?.toString()
        if (openchargemapKey == null && project.hasProperty("OPENCHARGEMAP_API_KEY_ENCRYPTED")) {
            openchargemapKey = decode(
                project.findProperty("OPENCHARGEMAP_API_KEY_ENCRYPTED").toString(),
                "FmK.d,-f*p+rD+WK!eds"
            )
        }
        if (openchargemapKey != null) {
            resValue("string", "openchargemap_key", openchargemapKey)
        }
        val googleMapsKey =
            System.getenv("GOOGLE_MAPS_API_KEY") ?: project.findProperty("GOOGLE_MAPS_API_KEY")
                ?.toString()
        if (googleMapsKey != null && flavorName.startsWith("google")) {
            resValue("string", "google_maps_key", googleMapsKey)
        }
        var mapboxKey =
            System.getenv("MAPBOX_API_KEY") ?: project.findProperty("MAPBOX_API_KEY")?.toString()
        if (mapboxKey == null && project.hasProperty("MAPBOX_API_KEY_ENCRYPTED")) {
            mapboxKey = decode(
                project.findProperty("MAPBOX_API_KEY_ENCRYPTED").toString(),
                "FmK.d,-f*p+rD+WK!eds"
            )
        }
        if (mapboxKey != null) {
            resValue("string", "mapbox_key", mapboxKey)
        }
        var jawgKey =
            System.getenv("JAWG_API_KEY") ?: project.findProperty("JAWG_API_KEY")?.toString()
        if (jawgKey == null && project.hasProperty("JAWG_API_KEY_ENCRYPTED")) {
            jawgKey = decode(
                project.findProperty("JAWG_API_KEY_ENCRYPTED").toString(),
                "FmK.d,-f*p+rD+WK!eds"
            )
        }
        if (jawgKey != null) {
            resValue("string", "jawg_key", jawgKey)
        }
        var arcgisKey =
            System.getenv("ARCGIS_API_KEY") ?: project.findProperty("ARCGIS_API_KEY")?.toString()
        if (arcgisKey == null && project.hasProperty("ARCGIS_API_KEY_ENCRYPTED")) {
            arcgisKey = decode(
                project.findProperty("ARCGIS_API_KEY_ENCRYPTED").toString(),
                "FmK.d,-f*p+rD+WK!eds"
            )
        }
        if (arcgisKey != null) {
            resValue("string", "arcgis_key", jawgKey)
        }
        var chargepriceKey =
            System.getenv("CHARGEPRICE_API_KEY") ?: project.findProperty("CHARGEPRICE_API_KEY")
                ?.toString()
        if (chargepriceKey == null && project.hasProperty("CHARGEPRICE_API_KEY_ENCRYPTED")) {
            chargepriceKey = decode(
                project.findProperty("CHARGEPRICE_API_KEY_ENCRYPTED").toString(),
                "FmK.d,-f*p+rD+WK!eds"
            )
        }
        if (chargepriceKey != null) {
            resValue("string", "chargeprice_key", chargepriceKey)
        }
        var fronyxKey =
            System.getenv("FRONYX_API_KEY") ?: project.findProperty("FRONYX_API_KEY")?.toString()
        if (fronyxKey == null && project.hasProperty("FRONYX_API_KEY_ENCRYPTED")) {
            fronyxKey = decode(
                project.findProperty("FRONYX_API_KEY_ENCRYPTED").toString(),
                "FmK.d,-f*p+rD+WK!eds"
            )
        }
        if (fronyxKey != null) {
            resValue("string", "fronyx_key", fronyxKey)
        }
        var acraKey = System.getenv("ACRA_CRASHREPORT_CREDENTIALS")
            ?: project.findProperty("ACRA_CRASHREPORT_CREDENTIALS")?.toString()
        if (acraKey == null && project.hasProperty("ACRA_CRASHREPORT_CREDENTIALS_ENCRYPTED")) {
            acraKey = decode(
                project.findProperty("ACRA_CRASHREPORT_CREDENTIALS_ENCRYPTED").toString(),
                "FmK.d,-f*p+rD+WK!eds"
            )
        }
        if (acraKey != null) {
            resValue("string", "acra_credentials", acraKey)
        }
    }

    packaging {
        jniLibs {
            pickFirsts.addAll(
                listOf(
                    "lib/x86/libc++_shared.so",
                    "lib/arm64-v8a/libc++_shared.so",
                    "lib/x86_64/libc++_shared.so",
                    "lib/armeabi-v7a/libc++_shared.so"
                )
            )
        }
    }
}

androidComponents {
    beforeVariants { variantBuilder ->
        if (variantBuilder.buildType == "releaseAutomotivePackageName"
            && !variantBuilder.productFlavors.containsAll(
                listOf(
                    "automotive" to "automotive",
                    "dependencies" to "foss"
                )
            )
        ) {
            // releaseAutomotivePackageName type is only needed for fossAutomotive
            variantBuilder.enable = false
        }
    }
}

configurations {
    create("googleNormalImplementation") {}
    create("googleAutomotiveImplementation") {}
}

aboutLibraries {
    allowedLicenses = arrayOf(
        "Apache-2.0", "mit", "BSD-2-Clause", "BSD-3-Clause", "EPL-1.0",
        "asdkl",  // Android SDK
        "Dual OpenSSL and SSLeay License",  // Android NDK OpenSSL
        "Google Maps Platform Terms of Service",  // Google Maps SDK
        "provided without support or warranty",  // org.json
        "Unicode/ICU License",  // icu4j
        "Bouncy Castle Licence",  // bcprov
        "CDDL + GPLv2 with classpath exception",  // javax.annotation-api
    )
    strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.FAIL
}

dependencies {
    val kotlinVersion: String by rootProject.extra
    val aboutLibsVersion: String by rootProject.extra
    val navVersion: String by rootProject.extra
    val normalImplementation by configurations
    val googleImplementation by configurations
    val automotiveImplementation by configurations
    val fossImplementation by configurations
    val testGoogleImplementation by configurations

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.7.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.github.ev-map:CustomBottomSheetBehavior:e48f73ea7b")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation("com.squareup.moshi:moshi-adapters:1.15.0")
    implementation("com.markomilos.jsonapi:jsonapi-retrofit:1.1.0")
    implementation("io.coil-kt:coil:2.6.0")
    implementation("com.github.ev-map:StfalconImageViewer:5082ebd392")
    implementation("com.mikepenz:aboutlibraries-core:$aboutLibsVersion")
    implementation("com.mikepenz:aboutlibraries:$aboutLibsVersion")
    implementation("com.airbnb.android:lottie:4.1.0")
    implementation("io.michaelrocks.bimap:bimap:1.1.0")
    implementation("com.google.guava:guava:29.0-android")
    implementation("com.github.pengrad:mapscaleview:1.6.0")
    implementation("com.github.romandanylyk:PageIndicatorView:b1bad589b5")
    implementation("com.github.erfansn:locale-config-x:1.0.1")

    // Android Auto
    val carAppVersion = "1.7.0-beta01"
    implementation("androidx.car.app:app:$carAppVersion")
    normalImplementation("androidx.car.app:app-projected:$carAppVersion")
    automotiveImplementation("androidx.car.app:app-automotive:$carAppVersion")

    // AnyMaps
    val anyMapsVersion = "1174ef9375"
    implementation("com.github.ev-map.AnyMaps:anymaps-base:$anyMapsVersion")
    googleImplementation("com.github.ev-map.AnyMaps:anymaps-google:$anyMapsVersion")
    googleImplementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.github.ev-map.AnyMaps:anymaps-maplibre:$anyMapsVersion") {
        // duplicates classes from mapbox-sdk-services
        exclude("org.maplibre.gl", "android-sdk-geojson")
    }
    implementation("org.maplibre.gl:android-sdk:10.3.3") {
        exclude("org.maplibre.gl", "android-sdk-geojson")
    }

    // Google Places
    googleImplementation("com.google.android.libraries.places:places:3.5.0")
    googleImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Mapbox Geocoding
    implementation("com.mapbox.mapboxsdk:mapbox-sdk-services:5.5.0")

    // navigation library
    implementation("androidx.navigation:navigation-fragment-ktx:$navVersion")
    implementation("androidx.navigation:navigation-ui-ktx:$navVersion")

    // viewmodel library
    val lifecycle_version = "2.8.1"
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycle_version")

    // room library
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    kapt("androidx.room:room-compiler:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    implementation("com.github.anboralabs:spatia-room:0.3.0")

    // billing library
    val billing_version = "7.0.0"
    googleImplementation("com.android.billingclient:billing:$billing_version")
    googleImplementation("com.android.billingclient:billing-ktx:$billing_version")

    // ACRA (crash reporting)
    val acraVersion = "5.11.1"
    implementation("ch.acra:acra-http:$acraVersion")
    implementation("ch.acra:acra-dialog:$acraVersion")
    implementation("ch.acra:acra-limiter:$acraVersion")

    // debug tools
    debugImplementation("com.facebook.flipper:flipper:0.238.0")
    debugImplementation("com.facebook.soloader:soloader:0.10.5")
    debugImplementation("com.facebook.flipper:flipper-network-plugin:0.238.0")
    debugImplementation("com.jakewharton.timber:timber:5.0.1")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    // testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    //noinspection GradleDependency
    testImplementation("org.json:json:20080701")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("androidx.car.app:app-testing:$carAppVersion")
    testImplementation("androidx.test:core:1.5.0")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")

    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}

fun decode(s: String, key: String): String {
    return String(xorWithKey(Base64.getDecoder().decode(s), key.toByteArray()), Charsets.UTF_8)
}

fun xorWithKey(a: ByteArray, key: ByteArray): ByteArray {
    val out = ByteArray(a.size)
    for (i in a.indices) {
        out[i] = (a[i].toInt() xor key[i % key.size].toInt()).toByte()
    }
    return out
}
