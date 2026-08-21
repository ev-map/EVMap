import com.android.build.api.dsl.ApplicationBaseFlavor
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64

plugins {
    alias(libs.plugins.test.logger)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.moshix)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.aboutlibraries)
}


android {
    useLibrary("android.car")

    defaultConfig {
        applicationId = "net.vonforst.evmap"
        compileSdk = 36
        minSdk = 23
        targetSdk = 36
        // NOTE: always increase versionCode by 2 since automotive flavor uses versionCode + 1
        versionCode = 280
        versionName = "2.1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        addApiKeys()
    }

    val isRunningOnCI = System.getenv("CI") == "true"
    val isCIKeystoreAvailable = System.getenv("KEYSTORE_PASSWORD") != null

    signingConfigs {
        create("release") {
            if (isRunningOnCI && isCIKeystoreAvailable) {
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
            signingConfig = if (isRunningOnCI && !isCIKeystoreAvailable) {
                null
            } else {
                signingConfigs.getByName("release")
            }
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
            addGoogleApiKey()
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
        targetCompatibility = JavaVersion.VERSION_17
        sourceCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        resValues = true
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

/**
 * add API keys from environment variable if not set in apikeys.xml
 */
fun ApplicationBaseFlavor.addApiKeys() {
    var evmapKey =
        System.getenv("EVMAP_API_KEY") ?: project.findProperty("EVMAP_API_KEY")?.toString()
    if (evmapKey == null && project.hasProperty("EVMAP_API_KEY_ENCRYPTED")) {
        evmapKey = decode(
            project.findProperty("EVMAP_API_KEY_ENCRYPTED").toString(),
            "FmK.d,-f*p+rD+WK!eds"
        )
    }
    if (evmapKey != null) {
        resValue("string", "evmap_key", evmapKey)
    }
    val goingelectricKey =
        System.getenv("GOINGELECTRIC_API_KEY") ?: project.findProperty("GOINGELECTRIC_API_KEY")
            ?.toString()
    if (goingelectricKey != null) {
        resValue("string", "goingelectric_key", goingelectricKey)
    }
    var nobilKey =
        System.getenv("NOBIL_API_KEY") ?: project.findProperty("NOBIL_API_KEY")?.toString()
    if (nobilKey == null && project.hasProperty("NOBIL_API_KEY_ENCRYPTED")) {
        nobilKey = decode(
            project.findProperty("NOBIL_API_KEY_ENCRYPTED").toString(),
            "FmK.d,-f*p+rD+WK!eds"
        )
    }
    if (nobilKey != null) {
        resValue("string", "nobil_key", nobilKey)
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
        resValue("string", "arcgis_key", arcgisKey)
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

fun ApplicationBaseFlavor.addGoogleApiKey() {
    val googleMapsKey =
        System.getenv("GOOGLE_MAPS_API_KEY") ?: project.findProperty("GOOGLE_MAPS_API_KEY")
            ?.toString()
    if (googleMapsKey != null) {
        resValue("string", "google_maps_key", googleMapsKey)
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("17")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
    license {
        allowedLicenses = setOf(
            "Apache-2.0", "mit", "BSD-2-Clause", "BSD-3-Clause", "EPL-1.0",
            "asdkl",  // Android SDK
            "Dual OpenSSL and SSLeay License",  // Android NDK OpenSSL
            "Google Maps Platform Terms of Service",  // Google Maps SDK
            "Unicode/ICU License", "Unicode-3.0",  // icu4j
            "Bouncy Castle Licence",  // bcprov
            "CDDL + GPLv2 with classpath exception",  // javax.annotation-api
        )
        strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.FAIL
    }
    export {
        excludeFields = setOf("generated")
    }
}

dependencies {
    val normalImplementation by configurations
    val googleImplementation by configurations
    val automotiveImplementation by configurations

    implementation(libs.kotlin.stdlib.jdk7)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.custom.bottom.sheet.behavior)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.urlconnection)
    implementation(libs.moshi.kotlin)
    implementation(libs.moshi.adapters)
    implementation(libs.coil)
    implementation(libs.stfalcon.image.viewer)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries)
    implementation(libs.lottie)
    implementation(libs.bimap)
    implementation(libs.mapscaleview)
    implementation(libs.page.indicator.view)
    implementation(libs.locale.config.x)

    // Android Auto
    implementation(libs.androidx.car.app)
    normalImplementation(libs.androidx.car.app.projected)
    automotiveImplementation(libs.androidx.car.app.automotive)

    // AnyMaps
    implementation(libs.anymaps.base)
    googleImplementation(libs.anymaps.google)
    googleImplementation(libs.play.services.maps)
    implementation(libs.anymaps.maplibre) {
        // exclude default (Vulkan) version and use Vulkan + OpenGL ES 3.0 version for better compatibility
        exclude("org.maplibre.gl", "android-sdk")
    }
    implementation(libs.maplibre.android.sdk.vulkan.opengl)

    // Google Places
    googleImplementation(libs.places)
    googleImplementation(libs.kotlinx.coroutines.play.services)

    // Mapbox Geocoding
    implementation(libs.mapbox.sdk.services)

    // navigation library
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // viewmodel library
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // room library
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.spatia.room) {
        exclude("com.github.dalgarins", "android-spatialite")
    }
    // forked version with upgraded libxml 2.15.2 & APP_STL := c++_static
    implementation(libs.android.spatialite)

    // billing library
    googleImplementation(libs.billing)
    googleImplementation(libs.billing.ktx)

    // ACRA (crash reporting)
    implementation(libs.acra.http)
    implementation(libs.acra.dialog)
    implementation(libs.acra.limiter)

    // debug tools
    debugImplementation(libs.timber)
    debugImplementation(libs.leakcanary.android)

    // testing
    testImplementation(libs.junit4)
    testImplementation(libs.okhttp.mockwebserver)
    //noinspection GradleDependency
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.car.app.testing)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.arch.core.testing)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
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
