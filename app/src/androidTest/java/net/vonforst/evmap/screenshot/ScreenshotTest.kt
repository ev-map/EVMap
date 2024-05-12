package net.vonforst.evmap.screenshot

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressBack
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.NavigationViewActions
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.rule.GrantPermissionRule.grant
import kotlinx.coroutines.runBlocking
import net.vonforst.evmap.EXTRA_CHARGER_ID
import net.vonforst.evmap.EXTRA_LAT
import net.vonforst.evmap.EXTRA_LON
import net.vonforst.evmap.EspressoIdlingResource
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.api.goingelectric.GEReferenceData
import net.vonforst.evmap.api.goingelectric.GoingElectricApiWrapper
import net.vonforst.evmap.model.Favorite
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.PreferenceDataSource
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.locale.LocaleTestRule
import java.time.Instant


@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun beforeAll() {
            IdlingRegistry.getInstance().register(EspressoIdlingResource.countingIdlingResource)

            Screengrab.setDefaultScreenshotStrategy { screenshotName, screenshotCallback ->
                screenshotCallback.screenshotCaptured(
                    screenshotName,
                    androidx.test.core.app.takeScreenshot()
                )
            }

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val prefs = PreferenceDataSource(context)
            prefs.dataSourceSet = true
            prefs.welcomeDialogShown = true
            prefs.privacyAccepted = true
            prefs.opensourceDonationsDialogLastShown = Instant.now()
            prefs.chargepriceMyVehicles = setOf("b58bc94d-d929-ad71-d95b-08b877bf76ba")
            prefs.appStartCounter = 0
            prefs.mapProvider = "google"

            // insert favorites
            val db = AppDatabase.getInstance(context)
            val api = GoingElectricApiWrapper(
                context.getString(R.string.goingelectric_key),
                context = context
            )
            val ids = listOf(70774L to true, 40315L to true, 65330L to true, 62489L to false)
            runBlocking {
                val refData = api.getReferenceData().data as GEReferenceData
                ids.forEachIndexed { i, (id, favorite) ->
                    val detail = api.getChargepointDetail(refData, id).data!!
                    db.chargeLocationsDao().insert(detail)
                    if (db.favoritesDao().findFavorite(id, "goingelectric") == null && favorite) {
                        db.favoritesDao().insert(
                            Favorite(
                                chargerId = id,
                                chargerDataSource = "goingelectric"
                            )
                        )
                    }
                }
            }
        }
    }

    @get:Rule
    val localeTestRule = LocaleTestRule()

    @get:Rule
    val activityRule: ActivityScenarioRule<MapsActivity> = ActivityScenarioRule(
        Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MapsActivity::class.java
        ).apply {
            putExtra(EXTRA_CHARGER_ID, 62489L)
            putExtra(EXTRA_LAT, 53.099512)
            putExtra(EXTRA_LON, 9.981884)
        })

    @get:Rule
    val permissionRule: GrantPermissionRule = grant(ACCESS_FINE_LOCATION)

    @Before
    fun setUp() {
        IdlingRegistry.getInstance().register(DataBindingIdlingResource(activityRule))
    }

    @Test
    fun testTakeScreenshot() {
        Thread.sleep(15000L)
        Screengrab.screenshot("01_map_google")

        onView(withId(R.id.topPart)).perform(click())

        Thread.sleep(1000L)
        Screengrab.screenshot("02_detail")

        onView(withId(R.id.btnChargeprice)).perform(click())
        Thread.sleep(5000L)
        Screengrab.screenshot("03_prices")

        onView(isRoot()).perform(pressBack())
        Thread.sleep(500L)
        onView(isRoot()).perform(pressBack())

        Thread.sleep(2000L)

        onView(withId(R.id.menu_filter)).perform(click())
        Thread.sleep(1000L)
        onView(withText(R.string.menu_edit_filters)).perform(click())

        Thread.sleep(1000L)

        Screengrab.screenshot("05_filters")
        onView(isRoot()).perform(pressBack())

        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
        onView(withId(R.id.nav_view)).perform(NavigationViewActions.navigateTo(R.id.favs))

        Thread.sleep(10000L)
        Screengrab.screenshot("04_favorites")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PreferenceDataSource(context).mapProvider = "mapbox"
        onView(isRoot()).perform(pressBack())

        Thread.sleep(5000L)
        Screengrab.screenshot("01_map_mapbox")
    }
}