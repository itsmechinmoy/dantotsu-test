package ani.dantotsu.media.novel

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.R
import ani.dantotsu.databinding.DialogLayoutBinding
import ani.dantotsu.databinding.ItemNovelHeaderBinding
import ani.dantotsu.media.Media
import ani.dantotsu.parsers.ShowResponse
import ani.dantotsu.loadImage
import ani.dantotsu.parsers.NovelReadSources
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.util.customAlertDialog

class NovelReadAdapter(
    private val media: Media,
    private val fragment: NovelReadFragment,
    private val novelReadSources: NovelReadSources
) : RecyclerView.Adapter<NovelReadAdapter.ViewHolder>() {

    var progress: View? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NovelReadAdapter.ViewHolder {
        val binding =
            ItemNovelHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        progress = binding.progress.root
        return ViewHolder(binding)
    }

    private val imm = fragment.requireContext()
        .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val binding = holder.binding
        progress = binding.progress.root

        val isLocal = media.format == "LOCAL" || media.format == "LOCAL_NOVEL"

        if (isLocal) {

            binding.mediaSource.visibility = View.GONE
            (binding.mediaSource.parent as? View)?.visibility = View.GONE
            binding.divider.visibility = View.GONE
            binding.searchBar.visibility = View.GONE


            binding.novelLocalHeader.visibility = View.VISIBLE

            binding.novelLocalFilter.setOnClickListener {
                showFilterDialog()
            }
        } else {
            fun search(): Boolean {
                val query = binding.searchBarText.text.toString()
                val source =
                    media.selected!!.sourceIndex.let { if (it >= novelReadSources.names.size) 0 else it }
                fragment.source = source

                binding.searchBarText.clearFocus()
                imm.hideSoftInputFromWindow(binding.searchBarText.windowToken, 0)
                fragment.search(query, source, true)
                return true
            }

            val source =
                media.selected!!.sourceIndex.let { if (it >= novelReadSources.names.size) 0 else it }
            if (novelReadSources.names.isNotEmpty() && source in 0 until novelReadSources.names.size) {
                binding.mediaSource.setText(novelReadSources.names[source], false)
            }
            val displayNames = novelReadSources.names.filter { it != "Local" }
            binding.mediaSource.setAdapter(
                ArrayAdapter(
                    fragment.requireContext(),
                    R.layout.item_dropdown,
                    displayNames
                )
            )
            binding.mediaSource.setOnItemClickListener { _, _, i, _ ->
                val actualIndex = novelReadSources.names.indexOf(displayNames[i])
                fragment.onSourceChange(actualIndex)
                search()
            }

            binding.searchBarText.setText(fragment.searchQuery)
            binding.searchBarText.setOnEditorActionListener { _, actionId, _ ->
                return@setOnEditorActionListener when (actionId) {
                    IME_ACTION_SEARCH -> search()
                    else -> false
                }
            }
            binding.searchBar.setEndIconOnClickListener { search() }
        }
    }

    private fun showFilterDialog() {
        val dialogBinding = DialogLayoutBinding.inflate(fragment.layoutInflater)
        var run = false
        var reversed = fragment.reverse
        var style = fragment.style

        dialogBinding.apply {

            mediaSourceTop.rotation = if (reversed) -90f else 90f
            sortText.text = if (reversed) "Down to Up" else "Up to Down"
            mediaSourceTop.setOnClickListener {
                reversed = !reversed
                mediaSourceTop.rotation = if (reversed) -90f else 90f
                sortText.text = if (reversed) "Down to Up" else "Up to Down"
                run = true
            }


            mediaSourceGrid.visibility = View.VISIBLE
            var selected = when (style) {
                0 -> mediaSourceList
                1 -> mediaSourceCompact
                2 -> mediaSourceGrid
                else -> mediaSourceGrid
            }
            when (style) {
                0 -> layoutText.setText(R.string.list)
                1 -> layoutText.setText(R.string.compact)
                2 -> layoutText.text = "Cover"
                else -> layoutText.text = "Cover"
            }
            selected.alpha = 1f
            fun selected(it: ImageButton) {
                selected.alpha = 0.33f
                selected = it
                selected.alpha = 1f
            }
            mediaSourceList.setOnClickListener {
                selected(it as ImageButton)
                style = 0
                layoutText.setText(R.string.list)
                run = true
            }
            mediaSourceCompact.setOnClickListener {
                selected(it as ImageButton)
                style = 1
                layoutText.setText(R.string.compact)
                run = true
            }
            mediaSourceGrid.setOnClickListener {
                selected(it as ImageButton)
                style = 2
                layoutText.text = "Cover"
                run = true
            }


            animeDownloadContainer.visibility = View.GONE
            mangaScanlatorContainer.visibility = View.GONE
            mediaWebviewContainer.visibility = View.GONE


            resetProgressDef.text = "Clear stored chapter details"
            resetProgress.setOnClickListener {
                fragment.requireContext().customAlertDialog().apply {
                    setTitle("Delete Progress for all chapters of ${fragment.media.nameRomaji}")
                    setMessage("This will delete all the locally stored progress for chapters")
                    setPosButton(R.string.ok) {
                        val prefix = "${fragment.media.id}_"
                        val regex = Regex("^${prefix}\\d+$")
                        PrefManager.getAllCustomValsForMedia(prefix)
                            .keys
                            .filter { it.matches(regex) }
                            .onEach { key -> PrefManager.removeCustomVal(key) }
                        ani.dantotsu.snackString("Deleted the progress of Chapters for ${fragment.media.nameRomaji}")
                    }
                    setNegButton(R.string.no)
                    show()
                }
            }
        }

        fragment.requireContext().customAlertDialog().apply {
            setTitle("Options")
            setCustomView(dialogBinding.root)
            setPosButton(R.string.ok) {
                if (run) {
                    fragment.onLayoutChanged(style, reversed)
                }
            }
            setNegButton(R.string.cancel)
            show()
        }
    }

    fun updateContinue(responses: List<ShowResponse>) {
        if (progress == null) return
        val lastReadName = PrefManager.getCustomVal("${media.id}_last_read_volume", "")
        
        var continueNovel: ShowResponse? = null
        if (lastReadName.isNotBlank() && responses.isNotEmpty()) {
            continueNovel = responses.firstOrNull { it.name == lastReadName } ?: responses.first()
        } else if (responses.isNotEmpty()) {
            continueNovel = responses.first()
        }

        val binding = ItemNovelHeaderBinding.bind(progress!!.parent as View)
        if (continueNovel != null) {
            binding.sourceContinue.visibility = View.VISIBLE
            binding.itemMediaImage.loadImage(media.banner ?: media.cover)
            binding.mediaSourceContinueText.text = fragment.getString(R.string.continue_reading) + "\n" + continueNovel.name
            binding.sourceContinue.setOnClickListener {
                fragment.onNovelClick(continueNovel)
            }
        } else {
            binding.sourceContinue.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = 1

    inner class ViewHolder(val binding: ItemNovelHeaderBinding) :
        RecyclerView.ViewHolder(binding.root)
}