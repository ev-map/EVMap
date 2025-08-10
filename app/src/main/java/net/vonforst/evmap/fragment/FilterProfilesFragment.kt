package net.vonforst.evmap.fragment

import android.graphics.Canvas
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialSharedAxis
import kotlinx.coroutines.launch
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.DataBindingAdapter
import net.vonforst.evmap.adapter.FilterProfilesAdapter
import net.vonforst.evmap.databinding.FragmentFilterProfilesBinding
import net.vonforst.evmap.databinding.ItemFilterProfileBinding
import net.vonforst.evmap.storage.FilterProfile
import net.vonforst.evmap.ui.showEditTextDialog
import net.vonforst.evmap.viewmodel.FilterProfilesViewModel
import net.vonforst.evmap.viewmodel.viewModelFactory


class FilterProfilesFragment : Fragment() {
    private lateinit var touchHelper: ItemTouchHelper
    private lateinit var adapter: FilterProfilesAdapter
    private lateinit var binding: FragmentFilterProfilesBinding
    private val vm: FilterProfilesViewModel by viewModels(factoryProducer = {
        viewModelFactory {
            FilterProfilesViewModel(requireActivity().application)
        }
    })
    private var deleteSnackbar: Snackbar? = null
    private var toDelete: FilterProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFilterProfilesBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = vm

        ViewCompat.setOnApplyWindowInsetsListener(
            binding.filterProfilesList
        ) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            WindowInsetsCompat.CONSUMED
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.toolbar)

        binding.toolbar.setupWithNavController(
            findNavController(),
            (requireActivity() as MapsActivity).appBarConfiguration
        )

        touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition

                val list = vm.filterProfiles.value?.toMutableList()
                if (list != null) {
                    val item = list[fromPos]
                    list.removeAt(fromPos)
                    list.add(toPos, item)
                    list.forEachIndexed { index, filterProfile ->
                        filterProfile.order = index
                    }
                    vm.reorderProfiles(list)
                }


                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val fp = vm.filterProfiles.value?.find { it.id == viewHolder.itemId }
                fp?.let { delete(it) }
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                if (viewHolder != null && actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val binding =
                        (viewHolder as DataBindingAdapter.ViewHolder<*>).binding as ItemFilterProfileBinding
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
                        (viewHolder as DataBindingAdapter.ViewHolder<*>).binding as ItemFilterProfileBinding
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
                    (viewHolder as DataBindingAdapter.ViewHolder<*>).binding as ItemFilterProfileBinding
                getDefaultUIUtil().clearView(binding.foreground)
            }

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float,
                actionState: Int, isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val binding =
                        (viewHolder as DataBindingAdapter.ViewHolder<*>).binding as ItemFilterProfileBinding
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

        adapter = FilterProfilesAdapter(touchHelper, onDelete = { fp ->
            delete(fp)
        }, onRename = { fp ->
            showEditTextDialog(requireContext(), { dialog, input ->
                input.setText(fp.name)

                dialog.setTitle(R.string.rename)
                    .setMessage(R.string.save_profile_enter_name)
            }, { newName ->
                lifecycleScope.launch {
                    if (vm.filterProfiles.value?.find { it.name == newName } != null) {
                        Snackbar.make(
                            view,
                            R.string.filterprofile_name_not_unique,
                            Snackbar.LENGTH_LONG
                        ).show()
                    } else {
                        vm.update(fp.copy(name = newName))
                    }
                }
            })
        })
        binding.filterProfilesList.apply {
            this.adapter = this@FilterProfilesFragment.adapter
            layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(
                DividerItemDecoration(
                    context, LinearLayoutManager.VERTICAL
                )
            )
        }

        touchHelper.attachToRecyclerView(binding.filterProfilesList)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        // Workaround for AndroidX bug: https://github.com/material-components/material-components-android/issues/1984
        view.setBackgroundColor(MaterialColors.getColor(view, android.R.attr.windowBackground))
    }

    fun delete(fp: FilterProfile) {
        val position = vm.filterProfiles.value?.indexOf(fp) ?: return
        // if there is already a profile to delete, delete it now
        actuallyDelete()
        deleteSnackbar?.dismiss()

        toDelete = fp

        view?.let {
            val snackbar = Snackbar.make(
                it,
                getString(R.string.deleted_item, fp.name),
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
        toDelete?.let { vm.delete(it.id) }
        toDelete = null
    }

    override fun onStop() {
        super.onStop()
        actuallyDelete()
    }
}