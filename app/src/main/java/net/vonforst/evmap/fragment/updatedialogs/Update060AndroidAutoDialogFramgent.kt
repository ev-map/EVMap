package net.vonforst.evmap.fragment.updatedialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDialogFragment
import net.vonforst.evmap.databinding.DialogUpdate060AndroidautoBinding
import net.vonforst.evmap.storage.PreferenceDataSource

class Update060AndroidAutoDialogFramgent : AppCompatDialogFragment() {
    private lateinit var binding: DialogUpdate060AndroidautoBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogUpdate060AndroidautoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnOk.setOnClickListener {
            PreferenceDataSource(requireContext()).update060AndroidAutoDialogShown = true
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }
}