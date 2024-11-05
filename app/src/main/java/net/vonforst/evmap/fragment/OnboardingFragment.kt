package net.vonforst.evmap.fragment

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.text.method.LinkMovementMethodCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import net.vonforst.evmap.R
import net.vonforst.evmap.databinding.FragmentOnboardingAndroidAutoBinding
import net.vonforst.evmap.databinding.FragmentOnboardingBinding
import net.vonforst.evmap.databinding.FragmentOnboardingDataSourceBinding
import net.vonforst.evmap.databinding.FragmentOnboardingIconsBinding
import net.vonforst.evmap.databinding.FragmentOnboardingWelcomeBinding
import net.vonforst.evmap.model.FILTERS_DISABLED
import net.vonforst.evmap.navigation.safeNavigate
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.waitForLayout

class OnboardingFragment : Fragment() {
    private lateinit var binding: FragmentOnboardingBinding
    private lateinit var adapter: OnboardingViewPagerAdapter
    private lateinit var prefs: PreferenceDataSource

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        prefs = PreferenceDataSource(requireContext())
        binding = FragmentOnboardingBinding.inflate(inflater)

        adapter = OnboardingViewPagerAdapter(this)
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
                binding.forward?.visibility =
                    if (position == adapter.itemCount - 1) View.INVISIBLE else View.VISIBLE
                binding.backward?.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
            }
        })
        binding.forward?.setOnClickListener {
            binding.viewPager.setCurrentItem(binding.viewPager.currentItem + 1, true)
        }
        binding.backward?.setOnClickListener {
            binding.viewPager.setCurrentItem(binding.viewPager.currentItem - 1, true)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.waitForLayout {
            binding.viewPager.currentItem = if (prefs.welcomeDialogShown) {
                // skip to last page for selecting data source or accepting the privacy policy
                adapter.itemCount - 1
            } else {
                0
            }
        }
    }

    fun goToNext() {
        if (binding.viewPager.currentItem == adapter.itemCount - 1) {
            findNavController().safeNavigate(OnboardingFragmentDirections.actionOnboardingToMap())
        } else {
            binding.viewPager.setCurrentItem(binding.viewPager.currentItem + 1, true)
        }
    }
}

class OnboardingViewPagerAdapter(fragment: Fragment) :
    FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> WelcomeFragment()
        1 -> IconsFragment()
        2 -> AndroidAutoFragment()
        3 -> DataSourceSelectFragment()
        else -> throw IllegalArgumentException()
    }
}

abstract class OnboardingPageFragment : Fragment() {
    lateinit var parent: OnboardingFragment

    override fun onAttach(context: Context) {
        super.onAttach(context)
        parent = parentFragment as OnboardingFragment
    }
}

class WelcomeFragment : OnboardingPageFragment() {
    private lateinit var binding: FragmentOnboardingWelcomeBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOnboardingWelcomeBinding.inflate(inflater, container, false)

        binding.btnGetStarted.setOnClickListener {
            parent.goToNext()
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        val drawable = (binding.animationView as ImageView).drawable
        if (drawable is AnimatedVectorDrawable) {
            drawable.start()
        }
    }

    override fun onPause() {
        super.onPause()
        val drawable = (binding.animationView as ImageView).drawable
        if (drawable is AnimatedVectorDrawable) {
            drawable.stop()
        }
    }
}

class IconsFragment : OnboardingPageFragment() {
    private lateinit var binding: FragmentOnboardingIconsBinding

    val labels
        get() = listOf(
            binding.iconLabel1,
            binding.iconLabel2,
            binding.iconLabel3,
            binding.iconLabel4,
            binding.iconLabel5
        )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOnboardingIconsBinding.inflate(inflater, container, false)

        binding.btnGetStarted.setOnClickListener {
            parent.goToNext()
        }
        labels.forEach { it.alpha = 0f }

        return binding.root
    }

    @SuppressLint("Recycle")
    override fun onResume() {
        super.onResume()
        val animators = labels.flatMapIndexed { i, view ->
            listOf(
                ObjectAnimator.ofFloat(view, "translationY", -20f, 0f).apply {
                    startDelay = 40L * i
                    interpolator = DecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
                    startDelay = 40L * i
                    interpolator = DecelerateInterpolator()
                }
            )
        }
        AnimatorSet().apply {
            playTogether(animators)
            start()
        }
    }

    override fun onPause() {
        super.onPause()
        labels.forEach { it.alpha = 0f }
    }
}

class DataSourceSelectFragment : OnboardingPageFragment() {
    private lateinit var prefs: PreferenceDataSource
    private lateinit var binding: FragmentOnboardingDataSourceBinding

    val animatedItems
        get() = listOf(
            binding.rgDataSource.rbGoingElectric,
            binding.rgDataSource.textView27,
            binding.rgDataSource.rbOpenChargeMap,
            binding.rgDataSource.textView28,
            binding.rgDataSource.rbOpenStreetMap,
            binding.rgDataSource.textView29,
            binding.rgDataSource.rbNobil,
            binding.rgDataSource.textView30,
            binding.dataSourceHint,
            binding.cbAcceptPrivacy
        )

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
        binding.cbAcceptPrivacy.text =
            HtmlCompat.fromHtml(
                getString(
                    R.string.accept_privacy,
                    getString(R.string.privacy_link)
                ), HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        binding.cbAcceptPrivacy.linksClickable = true
        binding.cbAcceptPrivacy.movementMethod = LinkMovementMethodCompat.getInstance()
        binding.btnGetStarted.visibility = View.INVISIBLE

        for (rb in listOf(
            binding.rgDataSource.rbGoingElectric,
            binding.rgDataSource.rbNobil,
            binding.rgDataSource.rbOpenChargeMap,
            binding.rgDataSource.rbOpenStreetMap
        )) {
            rb.setOnCheckedChangeListener { _, _ ->
                if (binding.btnGetStarted.visibility == View.INVISIBLE) {
                    binding.btnGetStarted.visibility = View.VISIBLE
                    ObjectAnimator.ofFloat(binding.btnGetStarted, "alpha", 0f, 1f).apply {
                        interpolator = DecelerateInterpolator()
                    }.start()
                }
            }
        }
        if (prefs.dataSourceSet) {
            when (prefs.dataSource) {
                "goingelectric" -> binding.rgDataSource.rbGoingElectric.isChecked = true
                "nobil" -> binding.rgDataSource.rbNobil.isChecked = true
                "openchargemap" -> binding.rgDataSource.rbOpenChargeMap.isChecked = true
                "openstreetmap" -> binding.rgDataSource.rbOpenStreetMap.isChecked = true
            }
        }

        binding.btnGetStarted.setOnClickListener {
            if (!binding.cbAcceptPrivacy.isChecked) {
                binding.cbAcceptPrivacy.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.delete_red
                    )
                )
                return@setOnClickListener
            }

            val result = if (binding.rgDataSource.rbGoingElectric.isChecked) {
                "goingelectric"
            } else if (binding.rgDataSource.rbNobil.isChecked) {
                "nobil"
            } else if (binding.rgDataSource.rbOpenChargeMap.isChecked) {
                "openchargemap"
            } else if (binding.rgDataSource.rbOpenStreetMap.isChecked) {
                "openstreetmap"
            } else {
                return@setOnClickListener
            }
            prefs.dataSource = result
            prefs.privacyAccepted = true
            prefs.filterStatus = FILTERS_DISABLED
            prefs.dataSourceSet = true
            prefs.welcomeDialogShown = true
            parent.goToNext()
        }
        animatedItems.forEach { it.alpha = 0f }
    }

    @SuppressLint("Recycle")
    override fun onResume() {
        super.onResume()
        val animators = animatedItems.flatMapIndexed { i, view ->
            listOf(
                ObjectAnimator.ofFloat(view, "translationY", 20f, 0f).apply {
                    startDelay = 40L * i
                    interpolator = DecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
                    startDelay = 40L * i
                    interpolator = DecelerateInterpolator()
                }
            )
        }
        AnimatorSet().apply {
            playTogether(animators)
            start()
        }
    }

    override fun onPause() {
        super.onPause()
        animatedItems.forEach { it.alpha = 0f }
    }
}

class AndroidAutoFragment : OnboardingPageFragment() {
    private lateinit var binding: FragmentOnboardingAndroidAutoBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOnboardingAndroidAutoBinding.inflate(inflater, container, false)

        binding.btnGetStarted.setOnClickListener {
            parent.goToNext()
        }
        binding.imgAndroidAuto.alpha = 0f

        return binding.root
    }

    @SuppressLint("Recycle")
    override fun onResume() {
        super.onResume()

        val animators =
            listOf(
                ObjectAnimator.ofFloat(binding.imgAndroidAuto, "translationY", -20f, 0f).apply {
                    interpolator = DecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(binding.imgAndroidAuto, "alpha", 0f, 1f).apply {
                    interpolator = DecelerateInterpolator()
                }
            )
        AnimatorSet().apply {
            playTogether(animators)
            start()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.imgAndroidAuto.alpha = 0f
    }
}