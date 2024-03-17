package net.vonforst.evmap.fragment

import androidx.fragment.app.Fragment
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.databinding.FragmentDonateReferralBinding

abstract class DonateFragmentBase : Fragment() {
    fun setupReferrals(referrals: FragmentDonateReferralBinding) {
        referrals.referralTesla.setOnClickListener {
            (requireActivity() as MapsActivity).openUrl(getString(R.string.tesla_referral_link))
        }
        referrals.referralJuicify.setOnClickListener {
            (requireActivity() as MapsActivity).openUrl(getString(R.string.juicify_referral_link))
        }
        referrals.referralGeldfuereauto.setOnClickListener {
            (requireActivity() as MapsActivity).openUrl(getString(R.string.geldfuereauto_referral_link))
        }
        referrals.referralMaingau.setOnClickListener {
            (requireActivity() as MapsActivity).openUrl(getString(R.string.maingau_referral_link))
        }
        referrals.referralEwieeinfach.setOnClickListener {
            (requireActivity() as MapsActivity).openUrl(getString(R.string.ewieeinfach_referral_link))
        }
        referrals.referralEprimo.setOnClickListener {
            (requireActivity() as MapsActivity).openUrl(getString(R.string.eprimo_referral_link))
        }
    }
}