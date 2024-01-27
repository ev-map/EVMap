package net.vonforst.evmap.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.transition.MaterialSharedAxis
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.databinding.FragmentDonateBinding
import net.vonforst.evmap.databinding.FragmentDonateReferralBinding

class DonateFragment : DonateFragmentBase() {
    private lateinit var binding: FragmentDonateBinding
    private lateinit var referrals: FragmentDonateReferralBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDonateBinding.inflate(inflater, container, false)
        referrals = binding.referrals
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.toolbar)

        binding.toolbar.setupWithNavController(
            findNavController(),
            (requireActivity() as MapsActivity).appBarConfiguration
        )

        binding.btnDonate.setOnClickListener {
            (activity as? MapsActivity)?.openUrl(getString(R.string.paypal_link))
        }

        setupReferrals(referrals)
    }
}