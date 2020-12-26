package net.vonforst.evmap.fragment

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.FiltersAdapter
import net.vonforst.evmap.databinding.FragmentFilterBinding
import net.vonforst.evmap.viewmodel.FilterViewModel
import net.vonforst.evmap.viewmodel.viewModelFactory


class FilterFragment : Fragment() {
    private lateinit var binding: FragmentFilterBinding
    private val vm: FilterViewModel by viewModels(factoryProducer = {
        viewModelFactory {
            FilterViewModel(
                requireActivity().application,
                getString(R.string.goingelectric_key)
            )
        }
    })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_filter, container, false)
        binding.lifecycleOwner = this
        binding.vm = vm

        setHasOptionsMenu(true)

        vm.filterProfile.observe(viewLifecycleOwner) {}

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

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.filter, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_apply -> {
                lifecycleScope.launch {
                    vm.saveFilterValues()
                    findNavController().popBackStack()
                }
                true
            }
            R.id.menu_save_profile -> {
                val container = FrameLayout(requireContext())
                container.setPadding(
                    (16 * resources.displayMetrics.density).toInt(), 0,
                    (16 * resources.displayMetrics.density).toInt(), 0
                )
                val input = EditText(requireContext())
                input.isSingleLine = true
                vm.filterProfile.value?.let { profile ->
                    input.setText(profile.name)
                }
                container.addView(input)

                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle(R.string.save_as_profile)
                    .setMessage(R.string.save_profile_enter_name)
                    .setView(container)
                    .setPositiveButton(R.string.ok) { di, button ->
                        lifecycleScope.launch {
                            vm.saveAsProfile(input.text.toString())
                            findNavController().popBackStack()
                        }
                    }
                    .setNegativeButton(R.string.cancel) { di, button ->

                    }.show()

                // move dialog to top
                val attrs = dialog.window?.attributes?.apply {
                    gravity = Gravity.TOP
                }
                dialog.window?.attributes = attrs

                // focus and show keyboard
                input.requestFocus()
                input.postDelayed({
                    val imm =
                        requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                }, 100)
                input.setOnEditorActionListener { _, actionId, event ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        val text = input.text
                        if (text != null) {
                            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                            return@setOnEditorActionListener true
                        }
                    }
                    false
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}