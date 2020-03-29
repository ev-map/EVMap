package com.johan.evmap

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.SharedElementCallback
import androidx.databinding.DataBindingUtil
import androidx.viewpager2.widget.ViewPager2
import com.johan.evmap.adapter.GalleryAdapter
import com.johan.evmap.adapter.galleryTransitionName
import com.johan.evmap.databinding.ActivityGalleryBinding
import com.ortiz.touchview.TouchImageView


class GalleryActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_POSITION = "position"
        const val EXTRA_PHOTOS = "photos"
        private const val SAVED_CURRENT_PAGE_POSITION = "current_page_position"
    }

    private lateinit var binding: ActivityGalleryBinding
    private var isReturning: Boolean = false
    private var startingPosition: Int = 0
    private var currentPosition: Int = 0
    private lateinit var galleryAdapter: GalleryAdapter
    private var currentPage: TouchImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_gallery)
        ActivityCompat.postponeEnterTransition(this)
        ActivityCompat.setEnterSharedElementCallback(this, enterElementCallback)

        startingPosition = intent.getIntExtra(EXTRA_POSITION, 0)
        currentPosition =
            savedInstanceState?.getInt(SAVED_CURRENT_PAGE_POSITION) ?: startingPosition

        galleryAdapter = GalleryAdapter(this, detailView = true, pageToLoad = currentPosition) {
            ActivityCompat.startPostponedEnterTransition(this)
        }
        binding.gallery.setPageTransformer { page, position ->
            val v = page as TouchImageView
            currentPage = v
        }
        binding.gallery.adapter = galleryAdapter
        binding.photos = intent.getParcelableArrayListExtra(EXTRA_PHOTOS)

        binding.gallery.post {
            binding.gallery.setCurrentItem(currentPosition, false)
            binding.gallery.registerOnPageChangeCallback(object :
                ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    currentPosition = position
                }
            })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(SAVED_CURRENT_PAGE_POSITION, currentPosition)
    }

    override fun finishAfterTransition() {
        isReturning = true
        val data = Intent()
        data.putExtra(MapsActivity.EXTRA_STARTING_GALLERY_POSITION, startingPosition)
        data.putExtra(MapsActivity.EXTRA_CURRENT_GALLERY_POSITION, currentPosition)
        setResult(Activity.RESULT_OK, data)
        super.finishAfterTransition()
    }

    private val enterElementCallback: SharedElementCallback = object : SharedElementCallback() {
        override fun onMapSharedElements(
            names: MutableList<String>,
            sharedElements: MutableMap<String, View>
        ) {
            if (isReturning) {
                val index = binding.gallery.currentItem
                val currentPage = currentPage ?: return

                if (startingPosition != currentPosition) {
                    names.clear()
                    names.add(galleryTransitionName(index))

                    sharedElements.clear()
                    sharedElements[galleryTransitionName(index)] = currentPage
                }
            }
        }
    }

    override fun onBackPressed() {
        val image = currentPage
        if (image == null || image.currentZoom in 0.95f..1.05f) {
            super.onBackPressed()
        } else {
            image.setZoomAnimated(1f, 0.5f, 0.5f)
        }
    }

}