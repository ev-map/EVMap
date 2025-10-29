package net.vonforst.evmap.auto

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import androidx.annotation.StringRes
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.EXTRA_DONATE
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.addDebugInterceptors
import net.vonforst.evmap.api.availability.tesla.TeslaAuthenticationApi
import net.vonforst.evmap.api.availability.tesla.TeslaOwnerApi
import net.vonforst.evmap.fragment.oauth.OAuthLoginFragmentArgs
import net.vonforst.evmap.getPackageInfoCompat
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.EncryptedPreferenceDataStore
import net.vonforst.evmap.storage.PreferenceDataSource
import okhttp3.OkHttpClient
import java.io.IOException
import java.time.Instant

@ExperimentalCarApi
class SettingsScreen(ctx: CarContext, val session: EVMapSession) : Screen(ctx), DefaultLifecycleObserver {
    val prefs = PreferenceDataSource(ctx)
    val newMapScreenEnabledPrevious = prefs.androidAutoNewMapScreenEnabled

    init {
        lifecycle.addObserver(this)
    }

    override fun onGetTemplate(): Template {
        return ListTemplate.Builder().apply {
            setTitle(carContext.getString(R.string.auto_settings))
            setHeaderAction(Action.BACK)
            setSingleList(ItemList.Builder().apply {
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.settings_data_sources))
                    setImage(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                R.drawable.ic_settings_data_source
                            )
                        ).setTint(
                            CarColor.DEFAULT
                        ).build()
                    )
                    setBrowsable(true)
                    setOnClickListener {
                        screenManager.push(DataSettingsScreen(carContext, session))
                    }
                }.build())
                if (supportsCarApiLevel3(carContext)) {
                    addItem(
                        Row.Builder()
                            .setTitle(carContext.getString(R.string.auto_vehicle_data))
                            .setImage(
                                CarIcon.Builder(
                                    IconCompat.createWithResource(carContext, R.drawable.ic_car)
                                ).setTint(CarColor.DEFAULT).build()
                            )
                            .setBrowsable(true)
                            .setOnClickListener {
                                screenManager.push(VehicleDataScreen(carContext, session))
                            }
                            .build()
                    )
                    if (supportsNewMapScreen(carContext)) {
                        addItem(
                            Row.Builder()
                                .setTitle(carContext.getString(R.string.auto_use_new_map_screen))
                                .setToggle(Toggle.Builder {
                                    prefs.androidAutoNewMapScreenEnabled = it
                                    invalidate()
                                }.setChecked(prefs.androidAutoNewMapScreenEnabled).build())
                                .setImage(
                                    CarIcon.Builder(
                                        IconCompat.createWithResource(
                                            carContext,
                                            R.drawable.ic_developer
                                        )
                                    ).setTint(CarColor.DEFAULT).build()
                                )
                                .build()
                        )
                    }
                    if (!supportsNewMapScreen(carContext) || !prefs.androidAutoNewMapScreenEnabled) {
                        // this option is only supported in LegacyMapScreen
                        addItem(
                            Row.Builder()
                                .setTitle(carContext.getString(R.string.auto_chargers_ahead))
                                .setToggle(Toggle.Builder {
                                    prefs.showChargersAheadAndroidAuto = it
                                }.setChecked(prefs.showChargersAheadAndroidAuto).build())
                                .setImage(
                                    CarIcon.Builder(
                                        IconCompat.createWithResource(
                                            carContext,
                                            R.drawable.ic_navigation
                                        )
                                    ).setTint(CarColor.DEFAULT).build()
                                )
                                .build()
                        )
                    }
                }
                addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.about))
                        .setImage(
                            CarIcon.Builder(
                                IconCompat.createWithResource(
                                    carContext,
                                    R.drawable.ic_about
                                )
                            ).setTint(CarColor.DEFAULT).build()
                        )
                        .setBrowsable(true)
                        .setOnClickListener {
                            screenManager.push(AboutScreen(carContext, session))
                        }
                        .build()
                )
            }.build())
        }.build()
    }

    override fun onStop(owner: LifecycleOwner) {
        if (newMapScreenEnabledPrevious != prefs.androidAutoNewMapScreenEnabled) {
            val newMapScreen = session.onCreateScreen(session.intent)
            val oldMapScreen = screenManager.screenStack.last()
            screenManager.push(newMapScreen)
            screenManager.remove(oldMapScreen)
        }
    }
}

@ExperimentalCarApi
class DataSettingsScreen(ctx: CarContext, val session: EVMapSession) : Screen(ctx), DefaultLifecycleObserver {
    val prefs = PreferenceDataSource(ctx)
    val encryptedPrefs = EncryptedPreferenceDataStore(ctx)
    val db = AppDatabase.getInstance(ctx)

    val dataSourceNames = carContext.resources.getStringArray(R.array.pref_data_source_names)
    val dataSourceValues = carContext.resources.getStringArray(R.array.pref_data_source_values)
    val searchProviderNames =
        carContext.resources.getStringArray(R.array.pref_search_provider_names)
    val searchProviderValues =
        carContext.resources.getStringArray(R.array.pref_search_provider_values)
    val mapProviderNames =
        carContext.resources.getStringArray(R.array.pref_map_provider_names)
    val mapProviderValues =
        carContext.resources.getStringArray(R.array.pref_map_provider_values)

    var teslaLoggingIn = false

    init {
        lifecycle.addObserver(this)
    }

    override fun onGetTemplate(): Template {
        return ListTemplate.Builder().apply {
            setTitle(carContext.getString(R.string.settings_data_sources))
            setHeaderAction(Action.BACK)
            addSectionedList(SectionedItemList.create(ItemList.Builder().apply {
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.pref_data_source))
                    setBrowsable(true)
                    val dataSourceId = prefs.dataSource
                    val dataSourceDesc = dataSourceNames[dataSourceValues.indexOf(dataSourceId)]
                    addText(dataSourceDesc)
                    setOnClickListener {
                        screenManager.push(
                            ChooseDataSourceScreen(
                                carContext,
                                ChooseDataSourceScreen.Type.CHARGER_DATA_SOURCE
                            )
                        )
                    }
                }.build())
                /*addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.pref_prediction_enabled))
                        .addText(carContext.getString(R.string.pref_prediction_enabled_summary))
                        .setToggle(Toggle.Builder {
                            prefs.predictionEnabled = it
                        }.setChecked(prefs.predictionEnabled).build())
                        .build()
                )*/
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.pref_tesla_account))
                    addText(
                        if (encryptedPrefs.teslaRefreshToken != null) {
                            carContext.getString(
                                R.string.pref_tesla_account_enabled,
                                encryptedPrefs.teslaEmail
                            )
                        } else if (teslaLoggingIn) {
                            carContext.getString(R.string.logging_in)
                        } else {
                            carContext.getString(R.string.pref_tesla_account_disabled)
                        }
                    )
                    if (encryptedPrefs.teslaRefreshToken != null) {
                        setOnClickListener {
                            teslaLogout()
                        }
                    } else {
                        setOnClickListener(ParkedOnlyOnClickListener.create {
                            teslaLogin()
                        })
                    }
                }.build())
            }.build(), carContext.getString(R.string.settings_charger_data)))
            addSectionedList(SectionedItemList.create(ItemList.Builder().apply {
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.pref_search_provider))
                    setBrowsable(true)
                    val searchProviderId = prefs.searchProvider
                    val searchProviderDesc =
                        searchProviderNames[searchProviderValues.indexOf(searchProviderId)]
                    addText(searchProviderDesc)
                    setOnClickListener {
                        screenManager.push(
                            ChooseDataSourceScreen(
                                carContext,
                                ChooseDataSourceScreen.Type.SEARCH_PROVIDER
                            )
                        )
                    }
                }.build())
                if (supportsNewMapScreen(carContext) && BuildConfig.FLAVOR_automotive != "automotive") {
                    // Google Maps SDK is not available on AAOS (not even AAOS with GAS, so far)
                    addItem(Row.Builder().apply {
                        setTitle(carContext.getString(R.string.pref_map_provider))
                        setBrowsable(true)
                        val mapProviderId = prefs.mapProvider
                        val mapProviderDesc =
                            mapProviderNames[mapProviderValues.indexOf(mapProviderId)]
                        addText(mapProviderDesc)
                        setOnClickListener {
                            screenManager.push(
                                ChooseDataSourceScreen(
                                    carContext,
                                    ChooseDataSourceScreen.Type.MAP_PROVIDER
                                )
                            )
                        }
                    }.build())
                }
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.pref_search_delete_recent))
                    setOnClickListener {
                        lifecycleScope.launch {
                            db.recentAutocompletePlaceDao().deleteAll()
                            CarToast.makeText(
                                carContext,
                                R.string.deleted_recent_search_results,
                                CarToast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }.build())
            }.build(), carContext.getString(R.string.settings_map)))
            addSectionedList(SectionedItemList.create(ItemList.Builder().apply {
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.settings_cache_count))
                    cacheCount?.let { count ->
                        cacheSize?.let { size ->
                            val sizeMb = size.toFloat() / 1024 / 1024
                            addText(
                                carContext.getString(
                                    R.string.settings_cache_count_summary,
                                    count,
                                    sizeMb
                                )
                            )
                        }
                    }
                }.build())
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.settings_cache_clear))
                    addText(carContext.getString(R.string.settings_cache_clear_summary))
                    setOnClickListener {
                        lifecycleScope.launch {
                            db.savedRegionDao().deleteAll()
                            db.chargeLocationsDao().deleteAllIfNotFavorite()
                            loadCacheSize()
                        }
                    }
                }.build())
            }.build(), carContext.getString(R.string.settings_caching)))
        }.build()
    }

    var cacheCount: Long? = null
    var cacheSize: Long? = null

    private suspend fun loadCacheSize() {
        cacheCount = db.chargeLocationsDao().getCountAsync()
        cacheSize = db.chargeLocationsDao().getSize()
        invalidate()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        lifecycleScope.launch {
            loadCacheSize()
        }
    }

    private fun teslaLogin() {
        val codeVerifier = TeslaAuthenticationApi.generateCodeVerifier()
        val codeChallenge = TeslaAuthenticationApi.generateCodeChallenge(codeVerifier)
        val uri = TeslaAuthenticationApi.buildSignInUri(codeChallenge)

        val args = OAuthLoginFragmentArgs(
            uri.toString(),
            TeslaAuthenticationApi.resultUrlPrefix,
            "#FFFFFF"
        ).toBundle()
        val intent = Intent(carContext, OAuthLoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtras(args)

        val resultFlow = OAuthLoginActivity.registerForResult(uri.toString())
        lifecycleScope.launch {
            resultFlow.collect { resultUrl ->
                teslaGetAccessToken(resultUrl.toUri(), codeVerifier)
            }
        }

        session.cas.startActivity(intent)

        if (BuildConfig.FLAVOR_automotive != "automotive") {
            CarToast.makeText(
                carContext,
                R.string.opened_on_phone,
                CarToast.LENGTH_LONG
            ).show()
        }
    }

    private fun teslaGetAccessToken(url: Uri, codeVerifier: String) {
        teslaLoggingIn = true
        invalidate()

        val code = url.getQueryParameter("code") ?: return
        val okhttp = OkHttpClient.Builder().addDebugInterceptors().build()
        val request = TeslaAuthenticationApi.AuthCodeRequest(code, codeVerifier)
        lifecycleScope.launch {
            try {
                val time = Instant.now().epochSecond
                val response =
                    TeslaAuthenticationApi.create(okhttp).getToken(request)
                val userResponse =
                    TeslaOwnerApi.create(okhttp, response.accessToken).getUserInfo()

                encryptedPrefs.teslaEmail = userResponse.response.email
                encryptedPrefs.teslaAccessToken = response.accessToken
                encryptedPrefs.teslaAccessTokenExpiry = time + response.expiresIn
                encryptedPrefs.teslaRefreshToken = response.refreshToken
            } catch (e: IOException) {
                CarToast.makeText(
                    carContext,
                    R.string.generic_connection_error,
                    CarToast.LENGTH_SHORT
                ).show()
            } finally {
                teslaLoggingIn = false
            }
            invalidate()
        }
    }

    private fun teslaLogout() {
        // sign out
        encryptedPrefs.teslaRefreshToken = null
        encryptedPrefs.teslaAccessToken = null
        encryptedPrefs.teslaAccessTokenExpiry = -1
        encryptedPrefs.teslaEmail = null
        CarToast.makeText(carContext, R.string.logged_out, CarToast.LENGTH_SHORT).show()

        invalidate()
    }
}

class ChooseDataSourceScreen(
    ctx: CarContext,
    val type: Type,
    val initialChoice: Boolean = false,
    @StringRes val extraDesc: Int? = null
) : Screen(ctx) {
    enum class Type {
        CHARGER_DATA_SOURCE, SEARCH_PROVIDER, MAP_PROVIDER
    }

    val prefs = PreferenceDataSource(carContext)
    val title = when (type) {
        Type.CHARGER_DATA_SOURCE -> R.string.pref_data_source
        Type.SEARCH_PROVIDER -> R.string.pref_search_provider
        Type.MAP_PROVIDER -> R.string.pref_map_provider
    }
    val names = carContext.resources.getStringArray(
        when (type) {
            Type.CHARGER_DATA_SOURCE -> R.array.pref_data_source_names
            Type.SEARCH_PROVIDER -> R.array.pref_search_provider_names
            Type.MAP_PROVIDER -> R.array.pref_map_provider_names
        }
    )
    val values = carContext.resources.getStringArray(
        when (type) {
            Type.CHARGER_DATA_SOURCE -> R.array.pref_data_source_values
            Type.SEARCH_PROVIDER -> R.array.pref_search_provider_values
            Type.MAP_PROVIDER -> R.array.pref_map_provider_values
        }
    )
    val currentValue: String = when (type) {
        Type.CHARGER_DATA_SOURCE -> prefs.dataSource
        Type.SEARCH_PROVIDER -> prefs.searchProvider
        Type.MAP_PROVIDER -> prefs.mapProvider
    }
    val descriptions = when (type) {
        Type.CHARGER_DATA_SOURCE -> listOf(
            carContext.getString(R.string.data_source_goingelectric_desc),
            carContext.getString(R.string.data_source_nobil_desc),
            carContext.getString(R.string.data_source_openchargemap_desc),
            carContext.getString(R.string.data_source_openstreetmap_desc)
        )
        Type.SEARCH_PROVIDER -> null
        Type.MAP_PROVIDER -> null
    }
    val callback: (String) -> Unit = when (type) {
        Type.CHARGER_DATA_SOURCE -> { it ->
            prefs.dataSourceSet = true
            prefs.dataSource = it
        }
        Type.SEARCH_PROVIDER -> { it ->
            prefs.searchProvider = it
        }
        Type.MAP_PROVIDER -> { it ->
            prefs.mapProvider = it
        }
    }

    override fun onGetTemplate(): Template {
        return ListTemplate.Builder().apply {
            setTitle(carContext.getString(title))
            setHeaderAction(if (initialChoice) Action.APP_ICON else Action.BACK)

            val list = ItemList.Builder().apply {
                for (i in names.indices) {
                    addItem(Row.Builder().apply {
                        setTitle(names[i])
                        descriptions?.let { addText(it[i]) }
                        if (initialChoice) {
                            setBrowsable(true)
                            setOnClickListener {
                                itemSelected(i)
                            }
                        }
                    }.build())
                }
                if (!initialChoice) {
                    setOnSelectedListener {
                        itemSelected(it)
                    }
                    setSelectedIndex(values.indexOf(currentValue))
                }
            }.build()
            if (extraDesc != null) {
                addSectionedList(SectionedItemList.create(list, carContext.getString(extraDesc)))
            } else {
                setSingleList(list)
            }
        }.build()
    }

    private fun itemSelected(i: Int) {
        callback(values[i])
        screenManager.pop()
    }
}

@ExperimentalCarApi
class AboutScreen(ctx: CarContext, val session: EVMapSession) : Screen(ctx) {
    val prefs = PreferenceDataSource(ctx)
    var developerOptionsCounter = 0
    private val maxRows = ctx.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)

    override fun onGetTemplate(): Template {
        return ListTemplate.Builder().apply {
            setTitle(carContext.getString(R.string.about))
            setHeaderAction(Action.BACK)
            addSectionedList(SectionedItemList.create(ItemList.Builder().apply {
                addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.version))
                        .addText(BuildConfig.VERSION_NAME)
                        .addText(
                            carContext.getString(R.string.copyright) + " " + carContext.getString(
                                R.string.copyright_summary
                            )
                        )
                        .setBrowsable(prefs.developerModeEnabled)
                        .setOnClickListener {
                            if (!prefs.developerModeEnabled) {
                                developerOptionsCounter += 1
                                if (developerOptionsCounter >= 7) {
                                    prefs.developerModeEnabled = true
                                    invalidate()
                                    CarToast.makeText(
                                        carContext,
                                        carContext.getString(R.string.developer_mode_enabled),
                                        CarToast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                screenManager.pushForResult(DeveloperOptionsScreen(carContext)) {
                                    developerOptionsCounter = 0
                                    invalidate()
                                }
                            }
                        }.build()
                )
                addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.faq))
                        .setBrowsable(true)
                        .setOnClickListener(ParkedOnlyOnClickListener.create {
                            openUrl(
                                carContext,
                                session.cas,
                                carContext.getString(R.string.faq_link)
                            )
                        }).build()
                )
                addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.donate))
                        .addText(carContext.getString(R.string.donate_desc))
                        .setBrowsable(true)
                        .setOnClickListener(ParkedOnlyOnClickListener.create {
                            if (BuildConfig.FLAVOR_automotive == "automotive") {
                                // we can't open the donation page on the phone in this case
                                openUrl(
                                    carContext,
                                    session.cas,
                                    carContext.getString(R.string.donate_link)
                                )
                            } else {
                                val intent = Intent(carContext, MapsActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .putExtra(EXTRA_DONATE, true)
                                session.cas.startActivity(intent)
                                CarToast.makeText(
                                    carContext,
                                    R.string.opened_on_phone,
                                    CarToast.LENGTH_LONG
                                ).show()
                            }
                        }).build()
                )
            }.build(), carContext.getString(R.string.about)))
            addSectionedList(SectionedItemList.create(ItemList.Builder().apply {
                addItem(Row.Builder()
                    .setTitle(carContext.getString(R.string.mastodon))
                    .addText(carContext.getString(R.string.mastodon_handle))
                    .setBrowsable(true)
                    .setOnClickListener(ParkedOnlyOnClickListener.create {
                        openUrl(
                            carContext,
                            session.cas,
                            carContext.getString(R.string.mastodon_url)
                        )
                    }).build()
                )
                if (maxRows > 8) {
                    addItem(
                        Row.Builder()
                            .setTitle(carContext.getString(R.string.twitter))
                            .addText(carContext.getString(R.string.twitter_handle))
                            .setBrowsable(true)
                            .setOnClickListener(ParkedOnlyOnClickListener.create {
                                openUrl(
                                    carContext,
                                    session.cas,
                                    carContext.getString(R.string.twitter_url)
                                )
                            }).build()
                    )
                }
                if (maxRows > 6) {
                    addItem(Row.Builder()
                        .setTitle(carContext.getString(R.string.goingelectric_forum))
                        .setBrowsable(true)
                        .setOnClickListener(ParkedOnlyOnClickListener.create {
                            openUrl(
                                carContext, session.cas,
                                carContext.getString(R.string.goingelectric_forum_url)
                            )
                        }).build()
                    )
                }
                if (maxRows > 7) {
                    addItem(
                        Row.Builder()
                            .setTitle(carContext.getString(R.string.tff_forum))
                            .setBrowsable(true)
                            .setOnClickListener(ParkedOnlyOnClickListener.create {
                                openUrl(
                                    carContext, session.cas,
                                    carContext.getString(R.string.tff_forum_url)
                                )
                            }).build()
                    )
                }
            }.build(), carContext.getString(R.string.contact)))
            addSectionedList(SectionedItemList.create(ItemList.Builder().apply {
                addItem(Row.Builder()
                    .setTitle(carContext.getString(R.string.github_link_title))
                    .setBrowsable(true)
                    .setOnClickListener(ParkedOnlyOnClickListener.create {
                        openUrl(carContext, session.cas, carContext.getString(R.string.github_link))
                    }).build()
                )
                addItem(Row.Builder()
                    .setTitle(carContext.getString(R.string.privacy))
                    .setBrowsable(true)
                    .setOnClickListener(ParkedOnlyOnClickListener.create {
                        openUrl(
                            carContext,
                            session.cas,
                            carContext.getString(R.string.privacy_link)
                        )
                    }).build()
                )
            }.build(), carContext.getString(R.string.other)))
        }.build()
    }
}

@ExperimentalCarApi
class AcceptPrivacyScreen(ctx: CarContext, val session: EVMapSession) : Screen(ctx) {
    val prefs = PreferenceDataSource(ctx)
    override fun onGetTemplate(): Template {
        val textWithoutLink = HtmlCompat.fromHtml(
            carContext.getString(R.string.accept_privacy),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString()
        return MessageTemplate.Builder(textWithoutLink).apply {
            setTitle(carContext.getString(R.string.privacy))
            addAction(Action.Builder()
                .setTitle(carContext.getString(R.string.ok))
                .setFlags(Action.FLAG_PRIMARY)
                .setBackgroundColor(CarColor.PRIMARY)
                .setOnClickListener {
                    prefs.privacyAccepted = true
                    screenManager.pop()
                }.build()
            )
            addAction(Action.Builder()
                .setTitle(carContext.getString(R.string.privacy))
                .setOnClickListener(ParkedOnlyOnClickListener.create {
                    openUrl(carContext, session.cas, carContext.getString(R.string.privacy_link))
                }).build()
            )
        }.build()
    }
}

class DeveloperOptionsScreen(ctx: CarContext) : Screen(ctx) {
    val prefs = PreferenceDataSource(ctx)

    override fun onGetTemplate(): Template {
        return ListTemplate.Builder().apply {
            setTitle(carContext.getString(R.string.developer_options))
            setHeaderAction(Action.BACK)
            setSingleList(ItemList.Builder().apply {
                addItem(
                    Row.Builder().apply {
                        setTitle("Car app API Level: ${carContext.carAppApiLevel}")
                        val hostPackage = carContext.hostInfo?.packageName
                        val hostVersion = hostPackage?.let {
                            try {
                                carContext.packageManager.getPackageInfoCompat(it).versionName
                            } catch (e: NameNotFoundException) {
                                null
                            }
                        }
                        addText("$hostPackage $hostVersion")
                        if (BuildConfig.FLAVOR_automotive == "automotive") {
                            addText(
                                "Sensor list: ${
                                    (carContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getSensorList(
                                        Sensor.TYPE_ALL
                                    ).map { it.type }.joinToString(",")
                                }"
                            )
                        }
                    }.build()
                )
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.disable_developer_mode))
                    setOnClickListener {
                        prefs.developerModeEnabled = false
                        CarToast.makeText(
                            carContext,
                            carContext.getString(R.string.developer_mode_disabled),
                            CarToast.LENGTH_SHORT
                        ).show()
                        screenManager.pop()
                    }
                }.build())
            }.build())
        }.build()
    }
}