package net.vonforst.evmap.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialSharedAxis
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.DonationAdapter
import net.vonforst.evmap.adapter.SingleViewAdapter
import net.vonforst.evmap.databinding.FragmentDonateBinding
import net.vonforst.evmap.databinding.FragmentDonateHeaderBinding
import net.vonforst.evmap.databinding.FragmentDonateReferralBinding
import net.vonforst.evmap.viewmodel.DonateViewModel
import net.vonforst.evmap.viewmodel.Status

class DonateFragment : DonateFragmentBase() {
    private lateinit var binding: FragmentDonateBinding
    private val vm: DonateViewModel by viewModels()
    private lateinit var header: FragmentDonateHeaderBinding
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

        header = FragmentDonateHeaderBinding.inflate(inflater, container, false)
        referrals = FragmentDonateReferralBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.toolbar)

        binding.toolbar.setupWithNavController(
            findNavController(),
            (requireActivity() as MapsActivity).appBarConfiguration
        )

        val donationAdapter = DonationAdapter().apply {
            onClickListener = {
                vm.startPurchase(it, requireActivity())
            }
        }
        binding.productsList.apply {
            val joinedAdapter = ConcatAdapter(
                SingleViewAdapter(header.root),
                donationAdapter,
                SingleViewAdapter(referrals.root)
            )
            adapter = joinedAdapter
            layoutManager = LinearLayoutManager(context)
        }

        vm.products.observe(viewLifecycleOwner) {
            donationAdapter.submitList(it.data)
            binding.progressBar3.visibility = if (it.status == Status.LOADING) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        vm.purchaseSuccessful.observe(viewLifecycleOwner) {
            Snackbar.make(view, R.string.donation_successful, Snackbar.LENGTH_LONG).show()
        }
        vm.purchaseFailed.observe(viewLifecycleOwner) {
            Snackbar.make(view, R.string.donation_failed, Snackbar.LENGTH_LONG).show()
        }

        setupReferrals(referrals)

        // Workaround for AndroidX bug: https://github.com/material-components/material-components-android/issues/1984
        view.setBackgroundColor(MaterialColors.getColor(view, android.R.attr.windowBackground))
    }
}