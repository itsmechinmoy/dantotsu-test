package ani.dantotsu.torrent

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.BottomSheetDialogFragment
import ani.dantotsu.FileUrl
import ani.dantotsu.R
import ani.dantotsu.currActivity
import ani.dantotsu.databinding.BottomSheetDirectTorrentBinding
import ani.dantotsu.databinding.ItemTorrentFileBinding
import ani.dantotsu.databinding.ItemTorrentFolderBinding
import ani.dantotsu.media.Anime
import ani.dantotsu.media.Media
import ani.dantotsu.media.MediaNameAdapter
import ani.dantotsu.media.Selected
import ani.dantotsu.media.anime.Episode
import ani.dantotsu.media.anime.ExoplayerView
import ani.dantotsu.parsers.Video
import ani.dantotsu.parsers.VideoExtractor
import ani.dantotsu.parsers.VideoServer
import ani.dantotsu.parsers.VideoType
import ani.dantotsu.snackString
import ani.dantotsu.toast
import ani.dantotsu.util.Logger
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.data.torrentServer.model.FileStat
import eu.kanade.tachiyomi.data.torrentServer.model.Torrent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import kotlin.math.abs

class DirectTorrentBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetDirectTorrentBinding? = null
    private val binding get() = _binding!!

    private var initialUrl: String? = null
    private var loadedTorrent: Torrent? = null
    private val treeItems = mutableListOf<TreeItem>()
    private var torrentAdapter: TorrentTreeAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialUrl = arguments?.getString(ARG_URL)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDirectTorrentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        torrentAdapter = TorrentTreeAdapter(treeItems) { fileStat ->
            onFileClicked(fileStat)
        }

        binding.torrentFilesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.torrentFilesRecyclerView.adapter = torrentAdapter

        binding.torrentLoadButton.setOnClickListener {
            val url = binding.torrentInputEditText.text.toString().trim()
            if (url.isNotBlank()) {
                loadTorrent(url)
            }
        }

        initialUrl?.takeIf { it.isNotBlank() }?.let { url ->
            binding.torrentInputEditText.setText(url)
            loadTorrent(url)
        }
    }

    private fun loadTorrent(url: String) {
        val torrentManager = Injekt.get<TorrentServerManager>()
        binding.torrentProgressBar.isVisible = true
        binding.torrentStatusText.isVisible = true
        binding.torrentStatusText.text = getString(R.string.loading)
        binding.torrentInfoContainer.isVisible = false

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                torrentManager.start()
                val torrent = torrentManager.addTorrent(
                    url = url,
                    title = "Torrent Stream",
                    poster = "",
                    data = "",
                    save = false
                )
                loadedTorrent = torrent
                buildTreeItems(torrent)

                withContext(Dispatchers.Main) {
                    val b = _binding ?: return@withContext
                    b.torrentProgressBar.isVisible = false
                    b.torrentStatusText.isVisible = false
                    b.torrentInfoContainer.isVisible = true
                    b.torrentNameText.text = torrent.name ?: torrent.title ?: "Torrent Stream"
                    b.torrentSizeText.text = "Total Size: ${formatFileSize(torrent.torrent_size ?: 0L)} • ${torrent.file_stats?.size ?: 0} Files"
                    torrentAdapter?.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Logger.log("DirectTorrentBottomSheet error: ${e.message}")
                withContext(Dispatchers.Main) {
                    val b = _binding ?: return@withContext
                    b.torrentProgressBar.isVisible = false
                    b.torrentStatusText.isVisible = true
                    b.torrentStatusText.text = "Error: ${e.message}"
                }
            }
        }
    }

    private fun buildTreeItems(torrent: Torrent) {
        treeItems.clear()
        val stats = torrent.file_stats ?: return

        val videoExtensions = listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".flv", ".m4v", ".ts")
        val videoFiles = stats.filter { stat ->
            val p = stat.path.lowercase()
            videoExtensions.any { p.endsWith(it) }
        }.takeIf { it.isNotEmpty() } ?: stats

        val grouped = videoFiles.groupBy { stat ->
            val norm = stat.path.replace('\\', '/')
            if (norm.contains('/')) norm.substringBeforeLast('/') else ""
        }

        grouped.keys.sorted().forEach { folder ->
            if (folder.isNotBlank()) {
                treeItems.add(TreeItem.Folder(folder))
            }
            val filesInFolder = grouped[folder] ?: emptyList()
            filesInFolder.sortedBy { it.path }.forEach { stat ->
                val norm = stat.path.replace('\\', '/')
                val fileName = if (norm.contains('/')) norm.substringAfterLast('/') else norm
                treeItems.add(TreeItem.File(stat, fileName))
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun onFileClicked(fileStat: FileStat) {
        val torrent = loadedTorrent ?: return
        val torrentManager = Injekt.get<TorrentServerManager>()
        val activity = activity ?: currActivity() ?: return

        binding.torrentProgressBar.isVisible = true
        binding.torrentStatusText.isVisible = true
        binding.torrentStatusText.text = "Pre-buffering ${fileStat.path.substringAfterLast('/')}..."

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                torrentManager.activeTorrentHash = torrent.hash
                val fileId = fileStat.id ?: 0
                torrentManager.prebuffer(torrent.hash!!, fileId)
                val streamUrl = torrentManager.getLink(torrent, fileId)

                val cleanTitle = fileStat.path.replace('\\', '/').substringAfterLast('/')
                val detectedEp = MediaNameAdapter.findEpisodeNumber(cleanTitle)?.toInt()?.toString() ?: "1"

                val media = Media().apply {
                    id = abs((torrent.hash ?: cleanTitle).hashCode())
                    name = torrent.name ?: torrent.title ?: cleanTitle
                    userPreferredName = name
                    selected = Selected().apply {
                        source = "Torrent"
                    }
                    anime = Anime().apply {
                        val sAnime = SAnime.create().apply {
                            title = media.name ?: "Torrent Stream"
                            url = torrent.hash ?: ""
                        }
                        val sEpisode = SEpisode.create().apply {
                            name = cleanTitle
                            url = streamUrl
                            episode_number = detectedEp.toFloatOrNull() ?: 1f
                        }
                        val ep = Episode(
                            number = detectedEp,
                            link = streamUrl,
                            title = cleanTitle,
                            sEpisode = sEpisode
                        )
                        val video = Video(
                            quality = 1080,
                            format = VideoType.CONTAINER,
                            file = FileUrl(streamUrl),
                            extra = null
                        )
                        val extractor = object : VideoExtractor() {
                            override val server = VideoServer(name = "Torrent Stream", embed = FileUrl(streamUrl))
                            init {
                                videos = listOf(video)
                            }
                        }
                        ep.extractors = listOf(extractor)
                        ep.selectedExtractor = "Torrent Stream"
                        ep.selectedVideo = 0

                        episodes = mutableMapOf(detectedEp to ep)
                        selectedEpisode = detectedEp
                    }
                }

                withContext(Dispatchers.Main) {
                    val b = _binding ?: return@withContext
                    b.torrentProgressBar.isVisible = false
                    b.torrentStatusText.isVisible = false
                    ExoplayerView.media = media
                    ExoplayerView.initialized = true
                    startActivity(Intent(activity, ExoplayerView::class.java))
                    dismissAllowingStateLoss()
                }
            } catch (e: Exception) {
                Logger.log("Failed to start torrent stream: ${e.message}")
                withContext(Dispatchers.Main) {
                    val b = _binding ?: return@withContext
                    b.torrentProgressBar.isVisible = false
                    b.torrentStatusText.isVisible = true
                    b.torrentStatusText.text = "Error streaming file: ${e.message}"
                    toast("Error streaming file: ${e.message}")
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.torrentFilesRecyclerView.adapter = null
        torrentAdapter = null
        _binding = null
    }

    companion object {
        private const val ARG_URL = "arg_torrent_url"

        fun newInstance(url: String? = null): DirectTorrentBottomSheet {
            return DirectTorrentBottomSheet().apply {
                arguments = Bundle().apply {
                    if (url != null) putString(ARG_URL, url)
                }
            }
        }

        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }
    }

    sealed class TreeItem {
        data class Folder(val name: String) : TreeItem()
        data class File(val stat: FileStat, val fileName: String) : TreeItem()
    }

    class TorrentTreeAdapter(
        private val items: List<TreeItem>,
        private val onItemClick: (FileStat) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is TreeItem.Folder -> 0
                is TreeItem.File -> 1
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                val binding = ItemTorrentFolderBinding.inflate(inflater, parent, false)
                FolderViewHolder(binding)
            } else {
                val binding = ItemTorrentFileBinding.inflate(inflater, parent, false)
                FileViewHolder(binding)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is TreeItem.Folder -> (holder as FolderViewHolder).bind(item)
                is TreeItem.File -> (holder as FileViewHolder).bind(item, onItemClick)
            }
        }

        override fun getItemCount(): Int = items.size

        class FolderViewHolder(private val binding: ItemTorrentFolderBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(folder: TreeItem.Folder) {
                binding.folderNameText.text = folder.name
            }
        }

        class FileViewHolder(private val binding: ItemTorrentFileBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(fileItem: TreeItem.File, onClick: (FileStat) -> Unit) {
                binding.fileNameText.text = fileItem.fileName
                binding.fileSizeText.text = "(${formatFileSize(fileItem.stat.length)})"
                binding.root.setOnClickListener {
                    onClick(fileItem.stat)
                }
            }
        }
    }
}
