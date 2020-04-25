package net.vonforst.evmap.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.app.SharedElementCallback
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.transition.TransitionInflater
import androidx.viewpager2.widget.ViewPager2
import com.ortiz.touchview.TouchImageView
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.GalleryAdapter
import net.vonforst.evmap.api.goingelectric.ChargerPhoto
import net.vonforst.evmap.databinding.FragmentGalleryBinding
import net.vonforst.evmap.viewmodel.GalleryViewModel


class GalleryFragment : Fragment() {
    companion object {
        private const val EXTRA_POSITION = "position"
        private const val EXTRA_PHOTOS = "photos"
        private const val SAVED_CURRENT_PAGE_POSITION = "current_page_position"

        fun buildArgs(photos: List<ChargerPhoto>, position: Int): Bundle {
            return Bundle().apply {
                putParcelableArrayList(EXTRA_PHOTOS, ArrayList(photos))
                putInt(EXTRA_POSITION, position)
            }
        }
    }

    private lateinit var binding: FragmentGalleryBinding
    private var isReturning: Boolean = false
    private var startingPosition: Int = 0
    private var currentPosition: Int = 0
    private lateinit var galleryAdapter: GalleryAdapter
    private var currentPage: TouchImageView? = null
    private val galleryVm: GalleryViewModel by activityViewModels()

    private val backPressedCallback = object :
        OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val image = currentPage
            if (image != null && image.currentZoom !in 0.95f..1.05f) {
                image.setZoomAnimated(1f, 0.5f, 0.5f)
            } else {
                isReturning = true
                galleryVm.galleryPosition.value = currentPosition
                findNavController().popBackStack()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_gallery, container, false
        )
        binding.lifecycleOwner = this

        val args = requireArguments()
        startingPosition = args.getInt(EXTRA_POSITION, 0)
        currentPosition =
            savedInstanceState?.getInt(SAVED_CURRENT_PAGE_POSITION) ?: startingPosition

        galleryAdapter =
            GalleryAdapter(requireContext(), detailView = true, pageToLoad = currentPosition) {
                startPostponedEnterTransition()
            }
        binding.gallery.setPageTransformer { page, position ->
            val v = page as TouchImageView
            currentPage = v
        }
        binding.gallery.adapter = galleryAdapter
        binding.photos = args.getParcelableArrayList(EXTRA_PHOTOS)

        binding.gallery.post {
            binding.gallery.setCurrentItem(currentPosition, false)
            binding.gallery.registerOnPageChangeCallback(object :
                ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    currentPosition = position
                }
            })
        }

        sharedElementEnterTransition = TransitionInflater.from(context)
            .inflateTransition(R.transition.image_shared_element_transition)
        sharedElementReturnTransition = TransitionInflater.from(context)
            .inflateTransition(R.transition.image_shared_element_transition)
        setEnterSharedElementCallback(enterElementCallback)
        if (savedInstanceState == null) {
            postponeEnterTransition();
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backPressedCallback
        )

        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(SAVED_CURRENT_PAGE_POSITION, currentPosition)
    }

    private val enterElementCallback: SharedElementCallback = object : SharedElementCallback() {
        override fun onMapSharedElements(
            names: MutableList<String>,
            sharedElements: MutableMap<String, View>
        ) {
            if (isReturning) {
                val currentPage = currentPage ?: return
                sharedElements[names[0]] = currentPage
            }
        }
    }

}