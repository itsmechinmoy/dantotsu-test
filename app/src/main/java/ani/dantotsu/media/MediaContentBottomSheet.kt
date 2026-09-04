package ani.dantotsu.media

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.BottomSheetDialogFragment
import ani.dantotsu.R
import ani.dantotsu.databinding.BottomSheetRecyclerBinding
import ani.dantotsu.databinding.ItemMediaContentBinding
import org.jsoup.parser.Parser
import java.io.Serializable

class MediaContentBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetRecyclerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetRecyclerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = arguments?.getString(ARG_TITLE) ?: ""
        val mode = arguments?.getString(ARG_MODE) ?: MODE_WATCH_ORDER

        binding.title.text = title
        binding.subscribeButton.visibility = View.GONE
        binding.replyButton.visibility = View.GONE
        binding.repliesRefresh.visibility = View.GONE

        binding.repliesRecyclerView.layoutManager = LinearLayoutManager(context)

        if (mode == MODE_WATCH_ORDER) {
            val items: ArrayList<MediaDetailsViewModel.WatchOrderItem> =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arguments?.getSerializable(ARG_ITEMS, ArrayList::class.java) as? ArrayList<MediaDetailsViewModel.WatchOrderItem>
                } else {
                    @Suppress("DEPRECATION")
                    arguments?.getSerializable(ARG_ITEMS) as? ArrayList<MediaDetailsViewModel.WatchOrderItem>
                } ?: arrayListOf()

            binding.repliesRecyclerView.adapter = WatchOrderAdapter(items)
        } else {
            val items: ArrayList<MediaDetailsViewModel.NewsItem> =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arguments?.getSerializable(ARG_ITEMS, ArrayList::class.java) as? ArrayList<MediaDetailsViewModel.NewsItem>
                } else {
                    @Suppress("DEPRECATION")
                    arguments?.getSerializable(ARG_ITEMS) as? ArrayList<MediaDetailsViewModel.NewsItem>
                } ?: arrayListOf()

            binding.repliesRecyclerView.adapter = NewsAdapter(items)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class WatchOrderAdapter(
        private val items: List<MediaDetailsViewModel.WatchOrderItem>
    ) : RecyclerView.Adapter<WatchOrderAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemMediaContentBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                ItemMediaContentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.contentTitle.text = item.name
            holder.binding.contentSubtitle.text = item.relationType.ifEmpty { "Entry" }
            holder.binding.contentIcon.setImageResource(R.drawable.ic_round_play_arrow_24)

            holder.binding.contentCard.setOnClickListener {
                val anilistId = item.anilistId.toIntOrNull() ?: return@setOnClickListener
                val intent = Intent(requireContext(), MediaDetailsActivity::class.java).apply {
                    putExtra("mediaId", anilistId)
                }
                startActivity(intent)
                dismissAllowingStateLoss()
            }
        }

        override fun getItemCount() = items.size
    }

    private inner class NewsAdapter(
        private val items: List<MediaDetailsViewModel.NewsItem>
    ) : RecyclerView.Adapter<NewsAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemMediaContentBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                ItemMediaContentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val decodedTitle = Parser.unescapeEntities(item.title, false)
            holder.binding.contentTitle.text = decodedTitle

            val timeAgo = item.date?.let { formatTimeAgo(it.time) }
            holder.binding.contentSubtitle.text = if (timeAgo != null) "$timeAgo • Read Article" else "Read Article"
            holder.binding.contentIcon.setImageResource(R.drawable.ic_round_notifications_active_24)

            holder.binding.contentCard.setOnClickListener {
                if (item.url.isNotEmpty()) {
                    runCatching {
                        val uri = Uri.parse(item.url)
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                }
            }
        }

        override fun getItemCount() = items.size

        private fun formatTimeAgo(timeMs: Long): String {
            val diff = System.currentTimeMillis() - timeMs
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24

            return when {
                days > 0 -> "${days}d ago"
                hours > 0 -> "${hours}h ago"
                minutes > 0 -> "${minutes}m ago"
                else -> "Just now"
            }
        }
    }

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_MODE = "arg_mode"
        private const val ARG_ITEMS = "arg_items"

        const val MODE_WATCH_ORDER = "watch_order"
        const val MODE_NEWS = "news"

        fun newWatchOrderInstance(title: String, items: List<MediaDetailsViewModel.WatchOrderItem>): MediaContentBottomSheet {
            return MediaContentBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_MODE, MODE_WATCH_ORDER)
                    putSerializable(ARG_ITEMS, ArrayList(items))
                }
            }
        }

        fun newNewsInstance(title: String, items: List<MediaDetailsViewModel.NewsItem>): MediaContentBottomSheet {
            return MediaContentBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_MODE, MODE_NEWS)
                    putSerializable(ARG_ITEMS, ArrayList(items))
                }
            }
        }
    }
}
