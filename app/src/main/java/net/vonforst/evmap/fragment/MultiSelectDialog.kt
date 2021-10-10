package net.vonforst.evmap.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.DataBindingAdapter
import net.vonforst.evmap.adapter.Equatable
import net.vonforst.evmap.databinding.DialogMultiSelectBinding
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.roundToInt

class MultiSelectDialog : AppCompatDialogFragment() {
    companion object {
        fun getInstance(
            title: String,
            data: Map<String, String>,
            selected: Set<String>,
            commonChoices: Set<String>?,
            showAllButton: Boolean = true
        ): MultiSelectDialog {
            val dialog = MultiSelectDialog()
            dialog.arguments = Bundle().apply {
                putString("title", title)
                putSerializable("data", HashMap(data))
                putSerializable("selected", HashSet(selected))
                if (commonChoices != null) putSerializable("commonChoices", HashSet(commonChoices))
                putBoolean("showAllButton", showAllButton)
            }
            return dialog
        }
    }

    var okListener: ((Set<String>) -> Unit)? = null
    var cancelListener: (() -> Unit)? = null
    private lateinit var items: List<MultiSelectItem>
    private lateinit var binding: DialogMultiSelectBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogMultiSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()

        val density = resources.displayMetrics.density
        val width = resources.displayMetrics.widthPixels
        val maxWidth = (500 * density).roundToInt()

        // dialog with 95% screen height
        dialog?.window?.setLayout(
            if (width < maxWidth) WindowManager.LayoutParams.MATCH_PARENT else maxWidth,
            (resources.displayMetrics.heightPixels * 0.95).toInt()
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        val data = args.getSerializable("data") as HashMap<String, String>
        val selected = args.getSerializable("selected") as HashSet<String>
        val title = args.getString("title")
        val commonChoices = if (args.containsKey("commonChoices")) {
            args.getSerializable("commonChoices") as HashSet<String>
        } else null
        val showAllButton = args.getBoolean("showAllButton")

        binding.dialogTitle.text = title
        val adapter = Adapter()
        binding.list.adapter = adapter
        binding.list.layoutManager = LinearLayoutManager(view.context)

        binding.btnAll.visibility = if (showAllButton) View.VISIBLE else View.INVISIBLE

        items = data.entries.toList()
            .sortedBy { it.value.toLowerCase(Locale.getDefault()) }
            .sortedBy {
                when {
                    selected.contains(it.key) && commonChoices?.contains(it.key) == true -> 0
                    selected.contains(it.key) -> 1
                    commonChoices?.contains(it.key) == true -> 2
                    else -> 3
                }
            }
            .map { MultiSelectItem(it.key, it.value, it.key in selected) }
        adapter.submitList(items)

        binding.etSearch.doAfterTextChanged { text ->
            adapter.submitList(search(items, text.toString()))
        }

        binding.btnCancel.setOnClickListener {
            cancelListener?.let { listener ->
                listener()
            }
            dismiss()
        }
        binding.btnOK.setOnClickListener {
            okListener?.let { listener ->
                val result = items.filter { it.selected }.map { it.key }.toSet()
                listener(result)
            }
            dismiss()
        }
        binding.btnAll.setOnClickListener {
            items = items.map { MultiSelectItem(it.key, it.name, true) }
            adapter.submitList(search(items, binding.etSearch.text.toString()))
        }
        binding.btnNone.setOnClickListener {
            items = items.map { MultiSelectItem(it.key, it.name, false) }
            adapter.submitList(search(items, binding.etSearch.text.toString()))
        }
    }
}

private fun search(
    items: List<MultiSelectItem>,
    text: String
): List<MultiSelectItem> {
    return items.filter { item ->
        // search for string within name
        text.toLowerCase(Locale.getDefault()) in item.name.toLowerCase(Locale.getDefault())
    }
}

class Adapter() : DataBindingAdapter<MultiSelectItem>({ it.key }) {
    override fun getItemViewType(position: Int) = R.layout.dialog_multi_select_item
}

data class MultiSelectItem(val key: String, val name: String, var selected: Boolean) : Equatable