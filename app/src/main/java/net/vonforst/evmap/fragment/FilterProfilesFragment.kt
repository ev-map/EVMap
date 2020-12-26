package net.vonforst.evmap.fragment

import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.DataBindingAdapter
import net.vonforst.evmap.adapter.FilterProfilesAdapter
import net.vonforst.evmap.databinding.FragmentFilterProfilesBinding
import net.vonforst.evmap.databinding.ItemFilterProfileBinding
import net.vonforst.evmap.viewmodel.FilterProfilesViewModel
import net.vonforst.evmap.viewmodel.viewModelFactory


class FilterProfilesFragment : Fragment() {
    private lateinit var binding: FragmentFilterProfilesBinding
    private val vm: FilterProfilesViewModel by viewModels(factoryProducer = {
        viewModelFactory {
            FilterProfilesViewModel(requireActivity().application)
        }
    })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFilterProfilesBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = this
        binding.vm = vm

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toolbar = view.findViewById(R.id.toolbar) as Toolbar
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)

        val navController = findNavController()
        toolbar.setupWithNavController(
            navController,
            (requireActivity() as MapsActivity).appBarConfiguration
        )


        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition;
                val toPos = target.adapterPosition;
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                vm.delete(viewHolder.itemId)
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                if (viewHolder != null) {
                    val foregroundView: View =
                        ((viewHolder as DataBindingAdapter.ViewHolder<*>).binding as ItemFilterProfileBinding).foreground
                    getDefaultUIUtil().onSelected(foregroundView)
                }
            }

            override fun onChildDrawOver(
                c: Canvas, recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float,
                actionState: Int, isCurrentlyActive: Boolean
            ) {
                val foregroundView: View =
                    ((viewHolder as DataBindingAdapter.ViewHolder<*>).binding as ItemFilterProfileBinding).foreground
                getDefaultUIUtil().onDrawOver(
                    c, recyclerView, foregroundView, dX, dY,
                    actionState, isCurrentlyActive
                )
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                val foregroundView =
                    ((viewHolder as DataBindingAdapter.ViewHolder<*>).binding as ItemFilterProfileBinding).foreground
                getDefaultUIUtil().clearView(foregroundView)
            }

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float,
                actionState: Int, isCurrentlyActive: Boolean
            ) {
                val foregroundView =
                    ((viewHolder as DataBindingAdapter.ViewHolder<*>).binding as ItemFilterProfileBinding).foreground
                getDefaultUIUtil().onDraw(
                    c, recyclerView, foregroundView, dX, dY,
                    actionState, isCurrentlyActive
                )
            }
        })

        val adapter = FilterProfilesAdapter(touchHelper)
        binding.filterProfilesList.apply {
            this.adapter = adapter
            layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(
                DividerItemDecoration(
                    context, LinearLayoutManager.VERTICAL
                )
            )
        }

        touchHelper.attachToRecyclerView(binding.filterProfilesList)

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }
}