package net.vonforst.evmap.fragment

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import net.vonforst.evmap.databinding.FragmentOnboardingAndroidAutoBinding

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