package net.vonforst.evmap.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.transition.MaterialSharedAxis
import kotlinx.coroutines.launch
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.FiltersAdapter
import net.vonforst.evmap.databinding.FragmentFilterBinding
import net.vonforst.evmap.ui.showEditTextDialog
import net.vonforst.evmap.viewmodel.FilterViewModel


class FilterFragment : Fragment(), MenuProvider {
    private lateinit var binding: FragmentFilterBinding
    private val vm: FilterViewModel by viewModels()

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
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_filter, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = vm
        vm.filterProfile.observe(viewLifecycleOwner) {}

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.toolbar)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding.toolbar.setupWithNavController(
            findNavController(),
            (requireActivity() as MapsActivity).appBarConfiguration
        )

        vm.filterProfile.observe(viewLifecycleOwner) {
            if (it != null) {
                binding.toolbar.title = getString(R.string.edit_filter_profile, it.name)
            }
        }

        binding.filtersList.apply {
            adapter = FiltersAdapter()
            layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(
                DividerItemDecoration(
                    context, LinearLayoutManager.VERTICAL
                )
            )
        }

        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        // Workaround for AndroidX bug: https://github.com/material-components/material-components-android/issues/1984
        view.setBackgroundColor(MaterialColors.getColor(view, android.R.attr.windowBackground))
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.filter, menu)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_apply -> {
                lifecycleScope.launch {
                    vm.saveFilterValues()
                    findNavController().popBackStack()
                }
                true
            }
            R.id.menu_save_profile -> {
                saveProfile()
                true
            }
            R.id.menu_reset -> {
                lifecycleScope.launch {
                    vm.resetValues()
                }
                true
            }
            else -> false
        }
    }

    private fun saveProfile() {
        showEditTextDialog(requireContext(), { dialog, input ->
            vm.filterProfile.value?.let { profile ->
                input.setText(profile.name)
            }

            dialog.setTitle(R.string.save_as_profile)
                .setMessage(R.string.save_profile_enter_name)
        }, {
            lifecycleScope.launch {
                vm.saveAsProfile(it)
                findNavController().popBackStack()
            }
        })
    }
}