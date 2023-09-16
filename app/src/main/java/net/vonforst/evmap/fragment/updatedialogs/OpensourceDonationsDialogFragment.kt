package net.vonforst.evmap.fragment.updatedialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import net.vonforst.evmap.R
import net.vonforst.evmap.databinding.DialogOpensourceDonationsBinding
import net.vonforst.evmap.navigation.safeNavigate
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.MaterialDialogFragment

class OpensourceDonationsDialogFragment : MaterialDialogFragment() {
    private lateinit var binding: DialogOpensourceDonationsBinding

    override fun createView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogOpensourceDonationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun initView(view: View, savedInstanceState: Bundle?) {
        val prefs = PreferenceDataSource(requireContext())
        binding.btnOk.setOnClickListener {
            prefs.opensourceDonationsDialogShown = true
            dismiss()
        }
        binding.btnDonate.setOnClickListener {
            prefs.opensourceDonationsDialogShown = true
            findNavController().safeNavigate(OpensourceDonationsDialogFragmentDirections.actionOpensourceDonationsToDonate())
        }
        binding.btnGithubSponsors.setOnClickListener {
            prefs.opensourceDonationsDialogShown = true
            findNavController().safeNavigate(OpensourceDonationsDialogFragmentDirections.actionOpensourceDonationsToGithubSponsors())
        }
    }

    override fun onStart() {
        super.onStart()
    }
}