package net.vonforst.evmap.auto

import android.location.Location
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import net.vonforst.evmap.R
import net.vonforst.evmap.auto.screens.VehicleDataScreen

/**
 * Welcome screen with selection between favorites and nearby chargers
 */
class WelcomeScreen(ctx: CarContext, val session: EVMapSession) : Screen(ctx), LocationAwareScreen {
    private var location: Location? = null

    override fun onGetTemplate(): Template {
        session.mapScreen = this
        return PlaceListMapTemplate.Builder().apply {
            setTitle(carContext.getString(R.string.app_name))
            location?.let {
                setAnchor(Place.Builder(CarLocation.create(it)).build())
            }
            setItemList(ItemList.Builder().apply {
                addItem(
                    Row.Builder()
                    .setTitle(carContext.getString(R.string.auto_chargers_closeby))
                    .setImage(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                R.drawable.ic_address
                            )
                        )
                            .setTint(CarColor.DEFAULT).build()
                    )
                    .setBrowsable(true)
                    .setOnClickListener {
                        screenManager.push(MapScreen(carContext, session, favorites = false))
                    }
                    .build())
                addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.auto_favorites))
                        .setImage(
                            CarIcon.Builder(
                                IconCompat.createWithResource(
                                    carContext,
                                    R.drawable.ic_fav
                                )
                            )
                                .setTint(CarColor.DEFAULT).build()
                        )
                        .setBrowsable(true)
                        .setOnClickListener {
                            screenManager.push(MapScreen(carContext, session, favorites = true))
                        }
                        .build())
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
                            session.mapScreen = null
                            screenManager.push(VehicleDataScreen(carContext))
                        }
                        .build()
                )
            }.build())
            setCurrentLocationEnabled(true)
            setHeaderAction(Action.APP_ICON)
            build()
        }.build()
    }

    override fun updateLocation(location: Location) {
        if (location.latitude == this.location?.latitude
            && location.longitude == this.location?.longitude
        ) {
            return
        }
        this.location = location
        invalidate()
    }
}