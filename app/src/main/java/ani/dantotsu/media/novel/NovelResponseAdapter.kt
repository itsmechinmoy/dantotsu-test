package ani.dantotsu.media.novel

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.R
import ani.dantotsu.databinding.ItemChapterListBinding
import ani.dantotsu.databinding.ItemEpisodeCompactBinding
import ani.dantotsu.databinding.ItemNovelResponseBinding
import ani.dantotsu.getThemeColor
import ani.dantotsu.loadImage
import ani.dantotsu.parsers.ShowResponse
import ani.dantotsu.setAnimation
import ani.dantotsu.snackString
import ani.dantotsu.util.Logger
import ani.dantotsu.util.customAlertDialog

class NovelResponseAdapter(
    val fragment: NovelReadFragment,
    val downloadTriggerCallback: DownloadTriggerCallback,
    val downloadedCheckCallback: DownloadedCheckCallback
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    val list: MutableList<ShowResponse> = mutableListOf()

    // 0 = List, 1 = Compact, 2 = Cover (default)
    private var type: Int = 2

    inner class CoverViewHolder(val binding: ItemNovelResponseBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ListViewHolder(val binding: ItemChapterListBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class CompactViewHolder(val binding: ItemEpisodeCompactBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            0 -> ListViewHolder(
                ItemChapterListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            1 -> CompactViewHolder(
                ItemEpisodeCompactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            else -> CoverViewHolder(
                ItemNovelResponseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
    }

    override fun getItemViewType(position: Int): Int = type

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val novel = list[position]

        when (holder) {
            is CoverViewHolder -> bindCover(holder.binding, novel, position)
            is ListViewHolder -> bindList(holder.binding, novel, position)
            is CompactViewHolder -> bindCompact(holder.binding, novel, position)
        }
    }

    private fun bindCover(binding: ItemNovelResponseBinding, novel: ShowResponse, position: Int) {
        setAnimation(fragment.requireContext(), binding.root)
        binding.itemMediaImage.loadImage(novel.coverUrl, 400, 0)

        val color = fragment.requireContext()
            .getThemeColor(com.google.android.material.R.attr.colorOnBackground)
        binding.itemEpisodeTitle.text = novel.name
        binding.itemEpisodeFiller.text =
            if (fragment.media.format == "LOCAL" || fragment.media.format == "LOCAL_NOVEL") {
                ""
            } else if (downloadedCheckCallback.downloadedCheck(novel)) {
                "Downloaded"
            } else {
                novel.extra?.get("0") ?: ""
            }
        if (binding.itemEpisodeFiller.text.contains("Downloading")) {
            binding.itemEpisodeFiller.setTextColor(
                ContextCompat.getColor(fragment.requireContext(), android.R.color.holo_blue_light)
            )
        } else if (binding.itemEpisodeFiller.text.contains("Downloaded")) {
            binding.itemEpisodeFiller.setTextColor(
                ContextCompat.getColor(fragment.requireContext(), android.R.color.holo_green_light)
            )
        } else {
            binding.itemEpisodeFiller.setTextColor(color)
        }
        binding.itemEpisodeDesc2.text = novel.extra?.get("1") ?: ""
        val desc = novel.extra?.get("2")
        binding.itemEpisodeDesc.isVisible = !desc.isNullOrBlank()
        binding.itemEpisodeDesc.text = desc ?: ""

        setupClickListeners(binding.root, novel)
    }

    private fun bindList(binding: ItemChapterListBinding, novel: ShowResponse, position: Int) {
        binding.itemChapterNumber.text = novel.name

        
        if (fragment.media.format == "LOCAL" || fragment.media.format == "LOCAL_NOVEL") {
            binding.itemDownload.visibility = View.GONE
        }

        // Hide metadata not relevant for novels
        binding.itemChapterDateLayout.visibility = View.GONE
        binding.itemEpisodeViewed.visibility = View.GONE

        setupClickListeners(binding.root, novel)
    }

    private fun bindCompact(binding: ItemEpisodeCompactBinding, novel: ShowResponse, position: Int) {
       
        val label = novel.name.let {
            val numMatch = Regex("""(?:vol(?:ume)?\.?\s*)(\d+)""", RegexOption.IGNORE_CASE).find(it)
            numMatch?.groupValues?.get(1) ?: (position + 1).toString()
        }
        binding.itemEpisodeNumber.text = label

        setupClickListeners(binding.root, novel)
    }

    private fun setupClickListeners(root: View, novel: ShowResponse) {
        root.setOnClickListener {
            fragment.onNovelClick(novel)
        }

        root.setOnLongClickListener {
            it.context.customAlertDialog().apply {
                setTitle("Delete ${novel.name}?")
                setMessage("Are you sure you want to delete ${novel.name}?")
                setPosButton(R.string.yes) {
                    downloadedCheckCallback.deleteDownload(novel)
                    deleteDownload(novel.link)
                    snackString("Deleted ${novel.name}")
                }
                setNegButton(R.string.no)
                show()
            }
            true
        }
    }

    fun updateType(newType: Int) {
        if (type != newType) {
            type = newType
            notifyDataSetChanged()
        }
    }

    private val activeDownloads = mutableSetOf<String>()
    
    fun isDownloading(link: String): Boolean = activeDownloads.contains(link)

    private val downloadedChapters = mutableSetOf<String>()

    fun startDownload(link: String) {
        activeDownloads.add(link)
        val position = list.indexOfFirst { it.link == link }
        if (position != -1) {
            list[position].extra?.remove("0")
            list[position].extra?.set("0", "Downloading: 0%")
            notifyItemChanged(position)
        }

    }

    fun stopDownload(link: String) {
        activeDownloads.remove(link)
        downloadedChapters.add(link)
        val position = list.indexOfFirst { it.link == link }
        if (position != -1) {
            list[position].extra?.remove("0")
            list[position].extra?.set("0", "Downloaded")
            notifyItemChanged(position)
        }
    }

    fun deleteDownload(link: String) {
        downloadedChapters.remove(link)
        val position = list.indexOfFirst { it.link == link }
        if (position != -1) {
            list[position].extra?.remove("0")
            list[position].extra?.set("0", "")
            notifyItemChanged(position)
        }
    }

    fun purgeDownload(link: String) {
        activeDownloads.remove(link)
        downloadedChapters.remove(link)
        val position = list.indexOfFirst { it.link == link }
        if (position != -1) {
            list[position].extra?.remove("0")
            list[position].extra?.set("0", "Failed")
            notifyItemChanged(position)
        }
    }

    fun updateDownloadProgress(link: String, progress: Int) {
        if (!activeDownloads.contains(link)) {
            activeDownloads.add(link)
        }
        val position = list.indexOfFirst { it.link == link }
        if (position != -1) {
            list[position].extra?.remove("0")
            list[position].extra?.set("0", "Downloading: $progress%")
            Logger.log("updateDownloadProgress: $progress, position: $position")
            notifyItemChanged(position)
        }
    }

    fun submitList(it: List<ShowResponse>) {
        val old = list.size
        list.addAll(it)
        notifyItemRangeInserted(old, it.size)
    }

    fun clear() {
        val size = list.size
        list.clear()
        notifyItemRangeRemoved(0, size)
    }
}

data class NovelDownloadPackage(
    val link: String,
    val coverUrl: String,
    val novelName: String,
    val originalLink: String
)