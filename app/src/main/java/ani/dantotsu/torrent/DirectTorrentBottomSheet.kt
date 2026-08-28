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
import ani.dantotsu.media.Media
import ani.dantotsu.media.MediaNameAdapter
import ani.dantotsu.media.Selected
import ani.dantotsu.media.anime.Anime
import ani.dantotsu.media.anime.Episode
import ani.dantotsu.media.anime.ExoplayerView
import ani.dantotsu.parsers.VideoContainer
import ani.dantotsu.parsers.VideoExtractor
import ani.dantotsu.parsers.VideoServer
import ani.dantotsu.parsers.VideoType
import ani.dantotsu.toast
import ani.dantotsu.util.Logger
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
    private val folderItemsMap = mutableMapOf<String, MutableList<FileStat>>()
    private val collapsedFolders = mutableSetOf<String>()
    private val displayItems = mutableListOf<TreeItem>()
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

        torrentAdapter = TorrentTreeAdapter(
            items = displayItems,
            onFolderClick = { folderName ->
                if (folderName in collapsedFolders) {
                    collapsedFolders.remove(folderName)
                } else {
                    collapsedFolders.add(folderName)
                }
                refreshDisplayList()
            },
            onFileClick = { fileStat ->
                onFileClicked(fileStat)
            }
        )

        binding.torrentFilesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.torrentFilesRecyclerView.adapter = torrentAdapter

        binding.torrentLoadButton.setOnClickListener {
            val url = binding.torrentInputEditText.text?.toString()?.trim().orEmpty()
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
                parseTorrentFolders(torrent)

                withContext(Dispatchers.Main) {
                    val b = _binding ?: return@withContext
                    b.torrentProgressBar.isVisible = false
                    b.torrentStatusText.isVisible = false
                    b.torrentInfoContainer.isVisible = true
                    b.torrentNameText.text = torrent.name ?: torrent.title ?: "Torrent Stream"
                    b.torrentSizeText.text = "Total Size: ${formatFileSize(torrent.torrent_size ?: 0L)} • ${torrent.file_stats?.size ?: 0} Files"
                    refreshDisplayList()
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

    private fun parseTorrentFolders(torrent: Torrent) {
        folderItemsMap.clear()
        collapsedFolders.clear()
        val stats = torrent.file_stats ?: return

        val videoExtensions = listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".flv", ".m4v", ".ts")
        val videoFiles = stats.filter { stat ->
            val p = stat.path.lowercase()
            videoExtensions.any { p.endsWith(it) }
        }.ifEmpty { stats }

        videoFiles.forEach { stat ->
            val norm = stat.path.replace('\\', '/')
            val folder = if (norm.contains('/')) norm.substringBeforeLast('/') else ""
            folderItemsMap.getOrPut(folder) { mutableListOf() }.add(stat)
        }
    }

    private fun refreshDisplayList() {
        displayItems.clear()
        folderItemsMap.keys.sorted().forEach { folder ->
            val files = folderItemsMap[folder] ?: return@forEach
            val folderTotalSize = files.sumOf { it.length }
            val isCollapsed = folder in collapsedFolders

            if (folder.isNotBlank()) {
                displayItems.add(TreeItem.FolderItem(folder, files.size, folderTotalSize, isCollapsed))
            }

            if (!isCollapsed) {
                files.sortedBy { it.path }.forEach { stat ->
                    val norm = stat.path.replace('\\', '/')
                    val fileName = if (norm.contains('/')) norm.substringAfterLast('/') else norm
                    displayItems.add(TreeItem.FileItem(stat, fileName))
                }
            }
        }
        torrentAdapter?.notifyDataSetChanged()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun onFileClicked(clickedFileStat: FileStat) {
        val torrent = loadedTorrent ?: return
        val torrentManager = Injekt.get<TorrentServerManager>()
        val currentAct = activity ?: currActivity() ?: return

        val cleanTitle = clickedFileStat.path.replace('\\', '/').substringAfterLast('/')
        binding.torrentProgressBar.isVisible = true
        binding.torrentStatusText.isVisible = true
        binding.torrentStatusText.text = "Pre-buffering $cleanTitle..."

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                torrentManager.activeTorrentHash = torrent.hash
                val clickedFileId = clickedFileStat.id ?: 0
                torrentManager.prebuffer(torrent.hash ?: "", clickedFileId)

                val stats = torrent.file_stats ?: emptyList()
                val videoExtensions = listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".flv", ".m4v", ".ts")
                val videoFilesList = stats.filter { stat ->
                    val p = stat.path.lowercase()
                    videoExtensions.any { p.endsWith(it) }
                }.ifEmpty { stats }

                val allEpisodesMap = mutableMapOf<String, Episode>()
                var clickedEpNumber = "1"

                videoFilesList.forEachIndexed { index, stat ->
                    val fCleanName = stat.path.replace('\\', '/').substringAfterLast('/')
                    val epNum = MediaNameAdapter.findEpisodeNumber(fCleanName)?.let {
                        if (it % 1 == 0f) it.toInt().toString() else it.toString()
                    } ?: (index + 1).toString()

                    if (stat.id == clickedFileId) {
                        clickedEpNumber = epNum
                    }

                    val fId = stat.id ?: 0
                    val sUrl = torrentManager.getLink(torrent, fId)
                    val sEp = SEpisode.create().apply {
                        name = fCleanName
                        url = sUrl
                        episode_number = epNum.toFloatOrNull() ?: (index + 1).toFloat()
                        if (stat.path.replace('\\', '/').contains('/')) {
                            scanlator = stat.path.replace('\\', '/').substringBeforeLast('/')
                        }
                    }
                    val vid = ani.dantotsu.parsers.Video(
                        quality = 1080,
                        format = VideoType.CONTAINER,
                        file = FileUrl(sUrl),
                        size = (stat.length / (1024.0 * 1024.0))
                    )
                    val ext = object : VideoExtractor() {
                        override val server = VideoServer(name = "Torrent Stream", embed = FileUrl(sUrl))
                        override suspend fun extract(): VideoContainer = VideoContainer(videos = listOf(vid))
                        init {
                            videos = listOf(vid)
                        }
                    }
                    val ep = Episode(
                        number = epNum,
                        link = sUrl,
                        title = fCleanName,
                        sEpisode = sEp
                    ).apply {
                        extractors = mutableListOf(ext)
                        selectedExtractor = "Torrent Stream"
                        selectedVideo = 0
                    }
                    allEpisodesMap[epNum] = ep
                }

                val media = Media(
                    id = abs((torrent.hash ?: cleanTitle).hashCode()),
                    name = torrent.name ?: torrent.title ?: cleanTitle,
                    nameRomaji = torrent.name ?: torrent.title ?: cleanTitle,
                    userPreferredName = torrent.name ?: torrent.title ?: cleanTitle,
                    isAdult = false,
                    anime = Anime(
                        episodes = allEpisodesMap,
                        selectedEpisode = clickedEpNumber
                    )
                ).also {
                    it.selected = Selected(
                        server = "Torrent"
                    )
                }

                withContext(Dispatchers.Main) {
                    val b = _binding ?: return@withContext
                    b.torrentProgressBar.isVisible = false
                    b.torrentStatusText.isVisible = false
                    ExoplayerView.media = media
                    ExoplayerView.initialized = true
                    startActivity(Intent(currentAct, ExoplayerView::class.java))
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
        _binding?.torrentFilesRecyclerView?.adapter = null
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
        data class FolderItem(val name: String, val count: Int, val totalSize: Long, val isCollapsed: Boolean) : TreeItem()
        data class FileItem(val stat: FileStat, val fileName: String) : TreeItem()
    }

    class TorrentTreeAdapter(
        private val items: List<TreeItem>,
        private val onFolderClick: (String) -> Unit,
        private val onFileClick: (FileStat) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is TreeItem.FolderItem -> 0
                is TreeItem.FileItem -> 1
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
                is TreeItem.FolderItem -> (holder as FolderViewHolder).bind(item, onFolderClick)
                is TreeItem.FileItem -> (holder as FileViewHolder).bind(item, onFileClick)
            }
        }

        override fun getItemCount(): Int = items.size

        class FolderViewHolder(private val binding: ItemTorrentFolderBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(folder: TreeItem.FolderItem, onClick: (String) -> Unit) {
                binding.folderNameText.text = folder.name
                binding.folderCountText.text = "${folder.count} files • ${formatFileSize(folder.totalSize)}"
                binding.folderChevron.rotation = if (folder.isCollapsed) -90f else 0f
                binding.root.setOnClickListener {
                    onClick(folder.name)
                }
            }
        }

        class FileViewHolder(private val binding: ItemTorrentFileBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(fileItem: TreeItem.FileItem, onClick: (FileStat) -> Unit) {
                binding.fileNameText.text = fileItem.fileName
                binding.fileSizeText.text = "(${formatFileSize(fileItem.stat.length)})"
                binding.root.setOnClickListener {
                    onClick(fileItem.stat)
                }
            }
        }
    }
}
