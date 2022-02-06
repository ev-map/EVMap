package net.vonforst.evmap.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import net.vonforst.evmap.R
import net.vonforst.evmap.storage.PreferenceDataSource

class SettingsScreen(ctx: CarContext) : Screen(ctx) {
    val prefs = PreferenceDataSource(carContext)
    val dataSourceNames = carContext.resources.getStringArray(R.array.pref_data_source_names)
    val dataSourceValues = carContext.resources.getStringArray(R.array.pref_data_source_values)

    override fun onGetTemplate(): Template {
        return ListTemplate.Builder().apply {
            setTitle(carContext.getString(R.string.auto_settings))
            setHeaderAction(Action.BACK)
            setSingleList(ItemList.Builder().apply {
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.pref_data_source))
                    val dataSourceId = prefs.dataSource
                    val dataSourceDesc = dataSourceNames[dataSourceValues.indexOf(dataSourceId)]
                    addText(dataSourceDesc)
                    setBrowsable(true)
                    setOnClickListener {
                        screenManager.push(ChooseDataSourceScreen(carContext))
                    }
                }.build())
            }.build())
        }.build()
    }
}

class ChooseDataSourceScreen(ctx: CarContext) : Screen(ctx) {
    val prefs = PreferenceDataSource(carContext)
    val dataSourceNames = carContext.resources.getStringArray(R.array.pref_data_source_names)
    val dataSourceValues = carContext.resources.getStringArray(R.array.pref_data_source_values)
    val dataSourceDescriptions = listOf(
        carContext.getString(R.string.data_source_goingelectric_desc),
        carContext.getString(R.string.data_source_openchargemap_desc)
    )

    override fun onGetTemplate(): Template {
        return ListTemplate.Builder().apply {
            setTitle(carContext.getString(R.string.pref_data_source))
            setHeaderAction(Action.BACK)
            setSingleList(ItemList.Builder().apply {
                for (i in dataSourceNames.indices) {
                    addItem(Row.Builder().apply {
                        setTitle(dataSourceNames[i])
                        addText(dataSourceDescriptions[i])
                    }.build())
                }
                setOnSelectedListener {
                    prefs.dataSource = dataSourceValues[it]
                    screenManager.pop()
                }
                setSelectedIndex(dataSourceValues.indexOf(prefs.dataSource))
            }.build())
        }.build()
    }
}