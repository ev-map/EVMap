package net.vonforst.evmap.fragment

import android.graphics.Canvas
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.car2go.maps.model.LatLng
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialFadeThrough
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.DataBindingAdapter
import net.vonforst.evmap.adapter.FavoritesAdapter
import net.vonforst.evmap.databinding.FragmentFavoritesBinding
import net.vonforst.evmap.databinding.ItemFavoriteBinding
import net.vonforst.evmap.location.FusionEngine
import net.vonforst.evmap.location.LocationEngine
import net.vonforst.evmap.model.Favorite
import net.vonforst.evmap.model.FavoriteWithDetail
import net.vonforst.evmap.utils.checkAnyLocationPermission
import net.vonforst.evmap.viewmodel.FavoritesViewModel
import net.vonforst.evmap.viewmodel.viewModelFactory

class FavoritesFragment : Fragment() {
    private lateinit var binding: FragmentFavoritesBinding
    private lateinit var locationEngine: LocationEngine
    private var toDelete: Favorite? = null
    private var deleteSnackbar: Snackbar? = null
    private lateinit var adapter: FavoritesAdapter

    private val vm: FavoritesViewModel by viewModels(factoryProducer = {
        viewModelFactory {
            FavoritesViewModel(
                requireActivity().application
            )
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationEngine = FusionEngine(requireContext())

        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_favorites, container, false
        )
        binding.lifecycleOwner = this
        binding.vm = vm

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Workaround for AndroidX bug: https://github.com/material-components/material-components-android/issues/1984
        view.setBackgroundColor(MaterialColors.getColor(view, android.R.attr.windowBackground))

        binding.toolbar.setupWithNavController(
            findNavController(),
            (requireActivity() as MapsActivity).appBarConfiguration
        )

        adapter = FavoritesAdapter(onDelete = {
            delete(it.fav)
        }).apply {
            onClickListener = {
                findNavController().navigate(
                    R.id.action_favs_to_map,
                    MapFragmentArgs(
                        chargerId = it.charger.id,
                        latLng = LatLng(it.charger.coordinates.lat, it.charger.coordinates.lng)
                    ).toBundle()
                )
            }
        }
        binding.favsList.apply {
            adapter = this@FavoritesFragment.adapter
            layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(
                DividerItemDecoration(
                    context, LinearLayoutManager.VERTICAL
                )
            )
        }
        createTouchHelper().attachToRecyclerView(binding.favsList)

        binding.swipeRefresh.setOnRefreshListener {
            vm.reloadAvailability() {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    override fun onStart() {
        super.onStart()

        if (requireContext().checkAnyLocationPermission()) {
            val location = locationEngine.getLastKnownLocation()
            location?.let {
                vm.location.value = LatLng(it.latitude, it.longitude)
            }
        }
    }

    fun delete(fav: FavoriteWithDetail) {
        val position =
            vm.listData.value?.indexOfFirst { it.fav.favorite.favoriteId == fav.favorite.favoriteId }
                ?: return
        // if there is already a profile to delete, delete it now
        actuallyDelete()
        deleteSnackbar?.dismiss()

        toDelete = fav.favorite

        view?.let {
            val snackbar = Snackbar.make(
                it,
                getString(R.string.deleted_filterprofile, fav.charger.name),
                Snackbar.LENGTH_LONG
            ).setAction(R.string.undo) {
                toDelete = null
                adapter.notifyItemChanged(position)
            }.addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    // if undo was not clicked, actually delete
                    if (event == DISMISS_EVENT_TIMEOUT || event == DISMISS_EVENT_SWIPE) {
                        actuallyDelete()
                    }
                }
            })
            deleteSnackbar = snackbar
            snackbar.show()
        } ?: run {
            actuallyDelete()
        }
    }

    private fun actuallyDelete() {
        toDelete?.let { vm.deleteFavorite(it) }
        toDelete = null
    }

    private fun createTouchHelper(): ItemTouchHelper {
        return ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val fav = vm.favorites.value?.find { it.favorite.favoriteId == viewHolder.itemId }
                fav?.let { delete(it) }
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                if (viewHolder != null && actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val binding =
                        (viewHolder as DataBindingAdapter.ViewHolder<*>).binding as ItemFavoriteBinding
                    getDefaultUIUtil().onSelected(binding.foreground)
                } else {
                    super.onSelectedChanged(viewHolder, actionState)
                }
            }

            override fun onChildDrawOver(
                c: Canvas, recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float,
                actionState: Int, isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val binding =
                        (viewHolder as DataBindingAdapter.ViewHolder<*>).binding as ItemFavoriteBinding
                    getDefaultUIUtil().onDrawOver(
                        c, recyclerView, binding.foreground, dX, dY,
                        actionState, isCurrentlyActive
                    )
                    val lp = (binding.deleteIcon.layoutParams as FrameLayout.LayoutParams)
                    lp.gravity = Gravity.CENTER_VERTICAL or if (dX > 0) {
                        Gravity.START
                    } else {
                        Gravity.END
                    }
                    binding.deleteIcon.layoutParams = lp
                } else {
                    super.onChildDrawOver(
                        c,
                        recyclerView,
                        viewHolder,
                        dX,
                        dY,
                        actionState,
                        isCurrentlyActive
                    )
                }
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                val binding =
                    (viewHolder as DataBindingAdapter.ViewHolder<*>).binding as ItemFavoriteBinding
                getDefaultUIUtil().clearView(binding.foreground)
            }

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float,
                actionState: Int, isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val binding =
                        (viewHolder as DataBindingAdapter.ViewHolder<*>).binding as ItemFavoriteBinding
                    getDefaultUIUtil().onDraw(
                        c, recyclerView, binding.foreground, dX, dY,
                        actionState, isCurrentlyActive
                    )
                } else {
                    super.onChildDraw(
                        c,
                        recyclerView,
                        viewHolder,
                        dX,
                        dY,
                        actionState,
                        isCurrentlyActive
                    )
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
    }
}