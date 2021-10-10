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
import kotlin.math.roundToInt

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

        val density = resources.displayMetrics.density
        val width = resources.displayMetrics.widthPixels
        val maxWidth = (500 * density).roundToInt()

        dialog?.window?.setLayout(
            if (width < maxWidth) WindowManager.LayoutParams.MATCH_PARENT else maxWidth,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }
}