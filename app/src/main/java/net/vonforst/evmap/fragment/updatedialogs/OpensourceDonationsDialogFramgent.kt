package net.vonforst.evmap.fragment.updatedialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.navigation.fragment.findNavController
import net.vonforst.evmap.R
import net.vonforst.evmap.databinding.DialogOpensourceDonationsBinding
import net.vonforst.evmap.storage.PreferenceDataSource

class OpensourceDonationsDialogFramgent : AppCompatDialogFragment() {
    private lateinit var binding: DialogOpensourceDonationsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogOpensourceDonationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = PreferenceDataSource(requireContext())
        binding.btnOk.setOnClickListener {
            prefs.opensourceDonationsDialogShown = true
            dismiss()
        }
        binding.btnDonate.setOnClickListener {
            prefs.opensourceDonationsDialogShown = true
            findNavController().navigate(R.id.action_opensource_donations_to_donate)
        }
        binding.btnGithubSponsors.setOnClickListener {
            prefs.opensourceDonationsDialogShown = true
            findNavController().navigate(R.id.action_opensource_donations_to_github_sponsors)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }
}