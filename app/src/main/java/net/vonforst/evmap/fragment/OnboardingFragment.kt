package net.vonforst.evmap.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import net.vonforst.evmap.R
import net.vonforst.evmap.databinding.*
import net.vonforst.evmap.storage.PreferenceDataSource

class OnboardingFragment : Fragment() {
    private lateinit var binding: FragmentOnboardingBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOnboardingBinding.inflate(inflater)

        val adapter = OnboardingViewPagerAdapter(this) {
            if (binding.viewPager.currentItem == 2) {
                findNavController().navigate(R.id.action_onboarding_to_map)
            } else {
                binding.viewPager.setCurrentItem(binding.viewPager.currentItem + 1, true)
            }
        }
        binding.viewPager.adapter = adapter
        binding.pageIndicatorView.count = adapter.itemCount
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                binding.pageIndicatorView.onPageScrollStateChanged(state)
            }

            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
                binding.pageIndicatorView.onPageScrolled(
                    position,
                    positionOffset,
                    positionOffsetPixels
                )
            }

            override fun onPageSelected(position: Int) {
                binding.pageIndicatorView.selection = position
            }
        })

        return binding.root
    }
}

class OnboardingViewPagerAdapter(fragment: Fragment, val goToNext: () -> Unit) :
    FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> WelcomeFragment(goToNext)
        1 -> IconsFragment(goToNext)
        2 -> DataSourceSelectFragment(goToNext)
        else -> throw IllegalArgumentException()
    }
}

class WelcomeFragment(val goToNext: () -> Unit) : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentOnboardingWelcomeBinding.inflate(inflater, container, false)

        binding.btnGetStarted.setOnClickListener {
            goToNext()
        }

        return binding.root
    }
}

class IconsFragment(val goToNext: () -> Unit) : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentOnboardingIconsBinding.inflate(inflater, container, false)

        binding.btnGetStarted.setOnClickListener {
            goToNext()
        }

        return binding.root
    }
}

class DataSourceSelectFragment(val goToNext: () -> Unit) : Fragment() {
    private lateinit var prefs: PreferenceDataSource
    private lateinit var binding: FragmentOnboardingDataSourceBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOnboardingDataSourceBinding.inflate(inflater, container, false)
        prefs = PreferenceDataSource(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnGetStarted.isEnabled = false

        for (rb in listOf(
            binding.rgDataSource.rbGoingElectric,
            binding.rgDataSource.rbOpenChargeMap
        )) {
            rb.setOnCheckedChangeListener { _, _ ->
                binding.btnGetStarted.isEnabled = true
            }
        }

        binding.btnGetStarted.setOnClickListener {
            val result = if (binding.rgDataSource.rbGoingElectric.isChecked) {
                "goingelectric"
            } else if (binding.rgDataSource.rbOpenChargeMap.isChecked) {
                "openchargemap"
            } else {
                return@setOnClickListener
            }
            prefs.dataSource = result
            prefs.dataSourceSet = true
            prefs.welcomeDialogShown = true
            goToNext()
        }
    }
}