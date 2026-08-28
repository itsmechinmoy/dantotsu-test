package ani.dantotsu.torrent

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.BottomSheetDialogFragment
import ani.dantotsu.FileUrl
import ani.dantotsu.R
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.currActivity
import ani.dantotsu.databinding.BottomSheetDirectTorrentBinding
import ani.dantotsu.databinding.ItemTorrentFileBinding
import ani.dantotsu.databinding.ItemTorrentFolderBinding
import ani.dantotsu.loadImage
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
import ani.dantotsu.util.customAlertDialog
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.data.torrentServer.model.FileStat
import eu.kanade.tachiyomi.data.torrentServer.model.Torrent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import kotlin.math.abs

/**
 * Bottom sheet for playing a magnet link or .torrent file directly.
 *
 * Can be opened two ways:
 * 1. From Settings -> Torrent Settings (no linked media, user pastes any magnet)
 * 2. From an anime detail page via [newInstanceLinked] (media pre-linked for AniList/MAL tracking)
 *
 * When [linkedMedia] is set, progress is tracked against the real AniList/MAL entry.
 * When not set, the user can search and link any AniList anime from within the sheet.
 */
class DirectTorrentBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetDirectTorrentBinding? = null
    private val binding get() = _binding!!

    private var initialUrl: String? = null

    // AniList media linked to this torrent session (real ID for tracking)
    private var linkedMedia: Media? = null

    private var loadedTorrent: Torrent? = null
    private val folderItemsMap = mutableMapOf<String, MutableList<FileStat>>()
    private val collapsedFolders = mutableSetOf<String>()
    private val displayItems = mutableListOf<TreeItem>()
    private var torrentAdapter: TorrentTreeAdapter? = null
    private var searchJob: Job? = null

    // Activity result launcher for .torrent file picker
    private val torrentFilePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            handleTorrentFileUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialUrl = arguments?.getString(ARG_URL)
        // Restore pre-linked media passed from anime detail page
        val preLinkedId = arguments?.getInt(ARG_MEDIA_ID, 0) ?: 0
        val preLinkedIdMAL = arguments?.getInt(ARG_MEDIA_ID_MAL, 0)?.takeIf { it != 0 }
        val preLinkedTitle = arguments?.getString(ARG_MEDIA_TITLE)
        val preLinkedCover = arguments?.getString(ARG_MEDIA_COVER)
        val preLinkedFormat = arguments?.getString(ARG_MEDIA_FORMAT)
        val preLinkedEps = arguments?.getInt(ARG_MEDIA_EPISODES, 0) ?: 0

        if (preLinkedId != 0 && preLinkedTitle != null) {
            linkedMedia = Media(
                id = preLinkedId,
                idMAL = preLinkedIdMAL,
                name = preLinkedTitle,
                nameRomaji = preLinkedTitle,
                userPreferredName = preLinkedTitle,
                cover = preLinkedCover,
                format = preLinkedFormat,
                isAdult = false,
                anime = Anime(totalEpisodes = preLinkedEps.takeIf { it > 0 })
            )
        }
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
                if (folderName in collapsedFolders) collapsedFolders.remove(folderName)
                else collapsedFolders.add(folderName)
                refreshDisplayList()
            },
            onFileClick = { fileStat -> onFileClicked(fileStat) }
        )

        binding.torrentFilesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.torrentFilesRecyclerView.adapter = torrentAdapter

        binding.torrentLoadButton.setOnClickListener {
            val url = binding.torrentInputEditText.text?.toString()?.trim().orEmpty()
            if (url.isNotBlank()) loadTorrent(url)
        }

        binding.torrentFilePickerButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("application/x-bittorrent", "application/octet-stream")
                )
            }
            torrentFilePicker.launch(intent)
        }

        updateLinkRow()
        binding.torrentLinkAnilistButton.setOnClickListener {
            if (linkedMedia != null) unlinkMedia()
            else showAnilistSearchDialog()
        }

        if (initialUrl?.isNotBlank() == true) {
            val url = initialUrl!!
            binding.torrentInputEditText.setText(url)
            loadTorrent(url)
        } else {
            checkClipboardForTorrentLink()
        }
    }

    private fun checkClipboardForTorrentLink() {
        try {
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
            val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: return
            if (clip.startsWith("magnet:?xt=", ignoreCase = true) ||
                (clip.startsWith("http://", ignoreCase = true) && clip.contains(".torrent", ignoreCase = true)) ||
                (clip.startsWith("https://", ignoreCase = true) && clip.contains(".torrent", ignoreCase = true))
            ) {
                binding.torrentInputEditText.setText(clip)
            }
        } catch (e: Exception) {
            Logger.log("Clipboard check error: ${e.message}")
        }
    }

    // ── AniList / MAL Linking ──────────────────────────────────────────────

    private fun updateLinkRow() {
        val b = _binding ?: return
        val m = linkedMedia
        if (m != null) {
            b.torrentLinkedMediaTitle.text = getString(R.string.torrent_linked_to, m.userPreferredName)
            b.torrentLinkedMediaSubtitle.isVisible = true
            val eps = m.anime?.totalEpisodes?.let { " • $it eps" } ?: ""
            b.torrentLinkedMediaSubtitle.text = "${m.format ?: ""}$eps".trimStart(' ', '•')
            b.torrentLinkedMediaCover.isVisible = m.cover != null
            m.cover?.let { b.torrentLinkedMediaCover.loadImage(it) }
            b.torrentLinkAnilistButton.text = getString(R.string.torrent_unlink_button)
        } else {
            b.torrentLinkedMediaTitle.text = getString(R.string.torrent_link_anilist_hint)
            b.torrentLinkedMediaSubtitle.isVisible = false
            b.torrentLinkedMediaCover.isVisible = false
            b.torrentLinkAnilistButton.text = getString(R.string.torrent_link_button)
        }
    }

    private fun unlinkMedia() {
        linkedMedia = null
        updateLinkRow()
    }

    /**
     * Shows a search dialog that queries AniList by name and lets the user
     * link this torrent session to a real AniList entry so progress tracking works.
     */
    private fun showAnilistSearchDialog() {
        val ctx = context ?: return
        var latestResults: List<Media> = emptyList()
        var dialogSelectedMedia: Media? = null

        val dialogView = buildSearchDialogView(ctx) { query, onResults ->
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(400)
                val results = withContext(Dispatchers.IO) {
                    try {
                        Anilist.query.searchAniManga(
                            type = "ANIME",
                            search = query,
                            perPage = 10
                        )?.results ?: emptyList()
                    } catch (e: Exception) {
                        Logger.log("AniList search error: ${e.message}")
                        emptyList()
                    }
                }
                latestResults = results
                onResults(results)
            }
        }

        // Extract the ListView reference from the view tag so we can attach an item listener
        val listView = dialogView.tag as? android.widget.ListView

        listView?.setOnItemClickListener { _, _, position, _ ->
            dialogSelectedMedia = latestResults.getOrNull(position)
        }

        requireActivity().customAlertDialog().apply {
            setTitle(getString(R.string.torrent_link_search_title))
            setCustomView(dialogView)
            setPosButton(R.string.ok) {
                val picked = dialogSelectedMedia
                if (picked != null) {
                    linkedMedia = picked
                    updateLinkRow()
                    toast(getString(R.string.torrent_link_success))
                }
            }
            setNegButton(R.string.cancel) { }
            show()
        }
    }

    private fun buildSearchDialogView(
        ctx: android.content.Context,
        onQuery: (String, (List<Media>) -> Unit) -> Unit
    ): View {
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 16, 32, 8)
        }

        val searchEdit = android.widget.EditText(ctx).apply {
            hint = getString(R.string.torrent_link_search_hint)
            setSingleLine(true)
        }
        container.addView(searchEdit)

        val resultAdapter = android.widget.ArrayAdapter<String>(
            ctx,
            android.R.layout.simple_list_item_1
        )
        val resultsList = android.widget.ListView(ctx).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(android.R.dimen.app_icon_size) * 6
            )
            adapter = resultAdapter
        }
        container.addView(resultsList)
        // Tag the list so the caller can attach an item listener
        container.tag = resultsList

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim() ?: return
                if (q.length < 2) return
                onQuery(q) { results ->
                    resultAdapter.clear()
                    if (results.isEmpty()) {
                        resultAdapter.add(getString(R.string.torrent_no_results))
                    } else {
                        results.forEach { m ->
                            val eps = m.anime?.totalEpisodes?.let { " (${it} eps)" } ?: ""
                            resultAdapter.add("${m.userPreferredName}$eps")
                        }
                    }
                    resultAdapter.notifyDataSetChanged()
                }
            }
        })

        return container
    }

    // ── .torrent File Handling ────────────────────────────────────────────

    private fun handleTorrentFileUri(uri: Uri) {
        val ctx = context ?: return
        try {
            val tmpFile = java.io.File(ctx.cacheDir, "tmp_picked.torrent")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                tmpFile.outputStream().use { output -> input.copyTo(output) }
            }
            val fileUrl = "file://${tmpFile.absolutePath}"
            binding.torrentInputEditText.setText(fileUrl)
            loadTorrent(fileUrl)
        } catch (e: Exception) {
            Logger.log("File pick error: ${e.message}")
            toast("Could not read .torrent file: ${e.message}")
        }
    }

    // ── Torrent Loading ────────────────────────────────────────────────────

    private fun loadTorrent(url: String) {
        val torrentManager = Injekt.get<TorrentServerManager>()
        val b = _binding ?: return
        b.torrentProgressBar.isVisible = true
        b.torrentStatusText.isVisible = true
        b.torrentStatusText.text = getString(R.string.loading)
        b.torrentInfoContainer.isVisible = false

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
                    val bm = _binding ?: return@withContext
                    bm.torrentProgressBar.isVisible = false
                    bm.torrentStatusText.isVisible = false
                    bm.torrentInfoContainer.isVisible = true
                    bm.torrentNameText.text = torrent.name ?: torrent.title ?: "Torrent Stream"
                    bm.torrentSizeText.text =
                        "Total Size: ${formatFileSize(torrent.torrent_size ?: 0L)} • ${torrent.file_stats?.size ?: 0} Files"
                    refreshDisplayList()
                }
            } catch (e: Exception) {
                Logger.log("DirectTorrentBottomSheet error: ${e.message}")
                withContext(Dispatchers.Main) {
                    val bm = _binding ?: return@withContext
                    bm.torrentProgressBar.isVisible = false
                    bm.torrentStatusText.isVisible = true
                    bm.torrentStatusText.text = "Error: ${e.message}"
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
        val newSize = displayItems.size
        torrentAdapter?.notifyItemRangeChanged(0, newSize)
    }

    // ── File Clicked: build episode map and launch ExoPlayer ───────────────

    @SuppressLint("UnsafeOptInUsageError")
    private fun onFileClicked(clickedFileStat: FileStat) {
        val torrent = loadedTorrent ?: return
        val torrentManager = Injekt.get<TorrentServerManager>()
        val currentAct = activity ?: currActivity() ?: return

        val cleanTitle = clickedFileStat.path.replace('\\', '/').substringAfterLast('/')
        val b = _binding ?: return
        b.torrentProgressBar.isVisible = true
        b.torrentStatusText.isVisible = true
        b.torrentStatusText.text = "Pre-buffering $cleanTitle..."

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

                    if (stat.id == clickedFileId) clickedEpNumber = epNum

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
                        init { videos = listOf(vid) }
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

                // If the user linked a real AniList entry, use that Media so
                // updateProgress() in PlayerProgressManager fires with a valid ID.
                val linked = linkedMedia
                val media = if (linked != null) {
                    linked.copy(
                        anime = (linked.anime ?: Anime()).copy(
                            episodes = allEpisodesMap,
                            selectedEpisode = clickedEpNumber
                        )
                    ).also { it.selected = Selected(server = "Torrent") }
                } else {
                    // No link — synthetic ID. AniList/MAL tracking is a no-op.
                    Media(
                        id = abs((torrent.hash ?: cleanTitle).hashCode()),
                        name = torrent.name ?: torrent.title ?: cleanTitle,
                        nameRomaji = torrent.name ?: torrent.title ?: cleanTitle,
                        userPreferredName = torrent.name ?: torrent.title ?: cleanTitle,
                        isAdult = false,
                        anime = Anime(
                            episodes = allEpisodesMap,
                            selectedEpisode = clickedEpNumber
                        )
                    ).also { it.selected = Selected(server = "Torrent") }
                }

                withContext(Dispatchers.Main) {
                    val bm = _binding ?: return@withContext
                    bm.torrentProgressBar.isVisible = false
                    bm.torrentStatusText.isVisible = false
                    ExoplayerView.media = media
                    ExoplayerView.initialized = true
                    startActivity(Intent(currentAct, ExoplayerView::class.java))
                    dismissAllowingStateLoss()
                }
            } catch (e: Exception) {
                Logger.log("Failed to start torrent stream: ${e.message}")
                withContext(Dispatchers.Main) {
                    val bm = _binding ?: return@withContext
                    bm.torrentProgressBar.isVisible = false
                    bm.torrentStatusText.isVisible = true
                    bm.torrentStatusText.text = "Error streaming file: ${e.message}"
                    toast("Error streaming file: ${e.message}")
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding?.torrentFilesRecyclerView?.adapter = null
        torrentAdapter = null
        _binding = null
    }

    companion object {
        private const val ARG_URL           = "arg_torrent_url"
        private const val ARG_MEDIA_ID      = "arg_media_id"
        private const val ARG_MEDIA_ID_MAL  = "arg_media_id_mal"
        private const val ARG_MEDIA_TITLE   = "arg_media_title"
        private const val ARG_MEDIA_COVER   = "arg_media_cover"
        private const val ARG_MEDIA_FORMAT  = "arg_media_format"
        private const val ARG_MEDIA_EPISODES= "arg_media_episodes"

        /** Open with just a URL (Settings / deep link). No pre-linked media. */
        fun newInstance(url: String? = null): DirectTorrentBottomSheet =
            DirectTorrentBottomSheet().apply {
                arguments = Bundle().apply {
                    if (url != null) putString(ARG_URL, url)
                }
            }

        /**
         * Open pre-linked to a real AniList anime.
         * Progress will sync to AniList + MAL automatically when an episode finishes.
         */
        fun newInstanceLinked(media: Media, url: String? = null): DirectTorrentBottomSheet =
            DirectTorrentBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_MEDIA_ID, media.id)
                    media.idMAL?.let { putInt(ARG_MEDIA_ID_MAL, it) }
                    putString(ARG_MEDIA_TITLE, media.userPreferredName ?: media.name)
                    media.cover?.let { putString(ARG_MEDIA_COVER, it) }
                    media.format?.let { putString(ARG_MEDIA_FORMAT, it) }
                    media.anime?.totalEpisodes?.let { putInt(ARG_MEDIA_EPISODES, it) }
                    if (url != null) putString(ARG_URL, url)
                }
            }

        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
            val digitGroups = (kotlin.math.log10(bytes.toDouble()) / kotlin.math.log10(1024.0))
                .toInt().coerceIn(0, units.size - 1)
            return String.format(
                Locale.US, "%.1f %s",
                bytes / kotlin.math.pow(1024.0, digitGroups.toDouble()),
                units[digitGroups]
            )
        }
    }

    // ── Tree item types ────────────────────────────────────────────────────

    sealed class TreeItem {
        data class FolderItem(val name: String, val count: Int, val totalSize: Long, val isCollapsed: Boolean) : TreeItem()
        data class FileItem(val stat: FileStat, val fileName: String) : TreeItem()
    }

    // ── RecyclerView Adapter ────────────────────────────────────────────────

    class TorrentTreeAdapter(
        private val items: List<TreeItem>,
        private val onFolderClick: (String) -> Unit,
        private val onFileClick: (FileStat) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is TreeItem.FolderItem -> 0
            is TreeItem.FileItem   -> 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0)
                FolderViewHolder(ItemTorrentFolderBinding.inflate(inflater, parent, false))
            else
                FileViewHolder(ItemTorrentFileBinding.inflate(inflater, parent, false))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is TreeItem.FolderItem -> (holder as FolderViewHolder).bind(item, onFolderClick)
                is TreeItem.FileItem   -> (holder as FileViewHolder).bind(item, onFileClick)
            }
        }

        override fun getItemCount(): Int = items.size

        class FolderViewHolder(private val binding: ItemTorrentFolderBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(folder: TreeItem.FolderItem, onClick: (String) -> Unit) {
                binding.folderNameText.text = folder.name
                binding.folderCountText.text = "${folder.count} files • ${formatFileSize(folder.totalSize)}"
                binding.folderChevron.rotation = if (folder.isCollapsed) -90f else 0f
                binding.root.setOnClickListener { onClick(folder.name) }
            }
        }

        class FileViewHolder(private val binding: ItemTorrentFileBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(fileItem: TreeItem.FileItem, onClick: (FileStat) -> Unit) {
                binding.fileNameText.text = fileItem.fileName
                binding.fileSizeText.text = "(${formatFileSize(fileItem.stat.length)})"
                binding.root.setOnClickListener { onClick(fileItem.stat) }
            }
        }
    }
}
