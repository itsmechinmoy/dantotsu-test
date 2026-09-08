package ani.dantotsu.media

import android.content.Intent
import android.net.Uri
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
import java.text.SimpleDateFormat
import java.util.Locale

class MediaInfoBottomDialog : BottomSheetDialogFragment() {
    private var _binding: BottomSheetRecyclerBinding? = null
    private val binding get() = _binding!!

    private var dialogTitle: String = ""
    private var watchOrders: ArrayList<MediaDetailsViewModel.WatchOrderItem>? = null
    private var newsItems: ArrayList<MediaDetailsViewModel.NewsItem>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            dialogTitle = it.getString(ARG_TITLE, "")
            @Suppress("DEPRECATION")
            watchOrders = it.getSerializable(ARG_WATCH_ORDER) as? ArrayList<MediaDetailsViewModel.WatchOrderItem>
            @Suppress("DEPRECATION")
            newsItems = it.getSerializable(ARG_NEWS) as? ArrayList<MediaDetailsViewModel.NewsItem>
        }
    }

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
        binding.title.text = dialogTitle
        binding.subscribeButton.visibility = View.GONE
        binding.replyButton.visibility = View.GONE
        binding.repliesRefresh.visibility = View.GONE

        binding.repliesRecyclerView.layoutManager = LinearLayoutManager(context)

        if (watchOrders != null) {
            binding.repliesRecyclerView.adapter = WatchOrderAdapter(watchOrders!!)
        } else if (newsItems != null) {
            binding.repliesRecyclerView.adapter = NewsAdapter(newsItems!!)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class WatchOrderAdapter(
        private val items: List<MediaDetailsViewModel.WatchOrderItem>
    ) : RecyclerView.Adapter<WatchOrderAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemMediaContentBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemBinding = ItemMediaContentBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.apply {
                contentIcon.setImageResource(R.drawable.ic_round_movie_filter_24)
                contentTitle.text = item.name
                contentSubtitle.text = item.relationType.ifBlank { "Anime" }

                contentCard.setOnClickListener {
                    val anilistId = item.anilistId.toIntOrNull()
                    if (anilistId != null && anilistId > 0) {
                        val intent = Intent(context, MediaDetailsActivity::class.java).apply {
                            putExtra("mediaId", anilistId)
                        }
                        context?.startActivity(intent)
                        dismiss()
                    }
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    inner class NewsAdapter(
        private val items: List<MediaDetailsViewModel.NewsItem>
    ) : RecyclerView.Adapter<NewsAdapter.ViewHolder>() {

        private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        inner class ViewHolder(val binding: ItemMediaContentBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemBinding = ItemMediaContentBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.apply {
                contentIcon.setImageResource(R.drawable.ic_round_menu_book_24)
                contentTitle.text = item.title
                val dateStr = item.date?.let { dateFormat.format(it) } ?: ""
                contentSubtitle.text = if (dateStr.isNotEmpty()) "$dateStr • ${holder.itemView.context.getString(R.string.read_article)}" else holder.itemView.context.getString(R.string.read_article)

                contentCard.setOnClickListener {
                    if (item.url.isNotBlank()) {
                        val uri = Uri.parse(item.url)
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        runCatching { context?.startActivity(intent) }
                    }
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_WATCH_ORDER = "arg_watch_order"
        private const val ARG_NEWS = "arg_news"

        fun newWatchOrderInstance(
            title: String,
            items: List<MediaDetailsViewModel.WatchOrderItem>
        ): MediaInfoBottomDialog {
            return MediaInfoBottomDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putSerializable(ARG_WATCH_ORDER, ArrayList(items))
                }
            }
        }

        fun newNewsInstance(
            title: String,
            items: List<MediaDetailsViewModel.NewsItem>
        ): MediaInfoBottomDialog {
            return MediaInfoBottomDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putSerializable(ARG_NEWS, ArrayList(items))
                }
            }
        }
    }
}
