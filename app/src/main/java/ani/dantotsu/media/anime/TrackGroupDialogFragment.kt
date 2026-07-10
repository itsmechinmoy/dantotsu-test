package ani.dantotsu.media.anime

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.C.TRACK_TYPE_AUDIO
import androidx.media3.common.C.TrackType
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.BottomSheetDialogFragment
import ani.dantotsu.R
import ani.dantotsu.databinding.BottomSheetSubtitlesBinding
import ani.dantotsu.databinding.ItemSubtitleTextBinding
import ani.dantotsu.media.anime.mpv.VideoTrack
import java.util.Locale

@OptIn(UnstableApi::class)
class TrackGroupDialogFragment(
    private var instance: MpvPlayerActivity,
    private var tracks: List<VideoTrack>,
    private var type: @TrackType Int
) : BottomSheetDialogFragment() {
    private var _binding: BottomSheetSubtitlesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSubtitlesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (type == TRACK_TYPE_AUDIO) binding.selectionTitle.text = getString(R.string.audio_tracks)
        binding.subtitlesRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.subtitlesRecycler.adapter = TrackGroupAdapter()
    }

    inner class TrackGroupAdapter : RecyclerView.Adapter<TrackGroupAdapter.StreamViewHolder>() {
        inner class StreamViewHolder(val binding: ItemSubtitleTextBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreamViewHolder =
            StreamViewHolder(
                ItemSubtitleTextBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: StreamViewHolder, position: Int) {
            val binding = holder.binding
            val track = tracks[position]
            
            val trackTitle = track.title
            val trackLang = track.lang
            
            if (trackTitle.lowercase() == "none" || trackLang.lowercase() == "none") {
                binding.subtitleTitle.text = getString(R.string.disabled_track)
            } else {
                val locale = if (trackLang.contains("-")) {
                    val parts = trackLang.split("-")
                    try { Locale(parts[0], parts[1]) } catch (ignored: Exception) { null }
                } else {
                    try { Locale(trackLang) } catch (ignored: Exception) { null }
                }

                binding.subtitleTitle.text = locale?.let {
                    if (trackTitle.isNotEmpty()) {
                        "[${it.language}] $trackTitle"
                    } else {
                        "[${it.language}] ${it.displayName}"
                    }
                } ?: run {
                    if (trackTitle.isNotEmpty()) {
                        if (trackLang.isNotEmpty()) "[$trackLang] $trackTitle" else trackTitle
                    } else {
                        if (trackLang.isNotEmpty()) "[$trackLang] Unknown" else getString(R.string.unknown_track, "Track $position")
                    }
                }
            }

            var isSelected = false
            when (track) {
                is VideoTrack.Internal -> isSelected = track.data.isSelected
                is VideoTrack.External -> isSelected = track.mainSelection == track.index
            }
            if (isSelected) {
                val selected = "✔ ${binding.subtitleTitle.text}"
                binding.subtitleTitle.text = selected
            }

            binding.root.setOnClickListener {
                dismiss()
                instance.onSetTrackOverride(track, type)
            }
        }

        override fun getItemCount(): Int = tracks.size
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }
}
