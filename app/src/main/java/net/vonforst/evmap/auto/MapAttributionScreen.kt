package net.vonforst.evmap.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.car2go.maps.AttributionClickListener
import net.vonforst.evmap.R

@ExperimentalCarApi
class MapAttributionScreen(
    ctx: CarContext,
    val session: EVMapSession,
    val attributions: List<AttributionClickListener.Attribution>
) : Screen(ctx) {
    override fun onGetTemplate(): Template {
        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setStartHeaderAction(Action.BACK)
                    .setTitle(carContext.getString(R.string.maplibre_attributionsDialogTitle))
                    .build()
            )
            .setSingleList(ItemList.Builder().apply {
                attributions.forEach { attr ->
                    addItem(
                        Row.Builder()
                            .setTitle(attr.title)
                            .setBrowsable(true)
                            .setOnClickListener(
                                ParkedOnlyOnClickListener.create {
                                    openUrl(carContext, session.cas, attr.url)
                                }).build()
                    )
                }
            }.build())
            .build()
    }

}