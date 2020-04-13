package com.johan.evmap.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class GalleryViewModel : ViewModel() {
    val galleryPosition = MutableLiveData<Int>()
}