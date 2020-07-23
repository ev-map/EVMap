package net.vonforst.evmap.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.car2go.maps.model.LatLng
import com.mapzen.android.lost.api.LocationServices
import com.mapzen.android.lost.api.LostApiClient
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.FavoritesAdapter
import net.vonforst.evmap.databinding.FragmentFavoritesBinding
import net.vonforst.evmap.viewmodel.FavoritesViewModel
import net.vonforst.evmap.viewmodel.viewModelFactory

class FavoritesFragment : Fragment(), LostApiClient.ConnectionCallbacks {
    private lateinit var binding: FragmentFavoritesBinding
    private lateinit var locationClient: LostApiClient

    private val vm: FavoritesViewModel by viewModels(factoryProducer = {
        viewModelFactory {
            FavoritesViewModel(
                requireActivity().application,
                getString(R.string.goingelectric_key)
            )
        }
    })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_favorites, container, false
        )
        binding.lifecycleOwner = this
        binding.vm = vm

        locationClient = LostApiClient.Builder(requireContext())
            .addConnectionCallbacks(this).build()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = view.findViewById(R.id.toolbar) as Toolbar

        val navController = findNavController()
        toolbar.setupWithNavController(
            navController,
            (requireActivity() as MapsActivity).appBarConfiguration
        )

        val favAdapter = FavoritesAdapter(vm).apply {
            onClickListener = {
                navController.navigate(R.id.action_favs_to_map, MapFragment.showCharger(it.charger))
            }
        }
        binding.favsList.apply {
            adapter = favAdapter
            layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(
                DividerItemDecoration(
                    context, LinearLayoutManager.VERTICAL
                )
            )
        }

        locationClient.connect()
    }

    override fun onConnected() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val location = LocationServices.FusedLocationApi.getLastLocation(locationClient)
            if (location != null) {
                vm.location.value = LatLng(location.latitude, location.longitude)
            }
        }
    }

    override fun onConnectionSuspended() {

    }
}