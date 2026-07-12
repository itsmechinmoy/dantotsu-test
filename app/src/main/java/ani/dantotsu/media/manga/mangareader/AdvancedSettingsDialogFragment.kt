package ani.dantotsu.media.manga.mangareader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ani.dantotsu.BottomSheetDialogFragment
import ani.dantotsu.R
import ani.dantotsu.databinding.BottomSheetAdvancedReaderSettingsBinding
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName

class AdvancedSettingsDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetAdvancedReaderSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAdvancedReaderSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as MangaReaderActivity
        val settings = activity.defaultSettings

        // --- Background Color Override UI ---
        val bgButtons = listOf(
            binding.btnBgAuto,
            binding.btnBgBlack,
            binding.btnBgGray,
            binding.btnBgWhite
        )
        fun updateBgSelection(selectedIdx: Int) {
            bgButtons.forEachIndexed { idx, btn ->
                btn.alpha = if (idx == selectedIdx) 1.0f else 0.4f
            }
        }
        updateBgSelection(settings.readerBackgroundColor)
        bgButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                settings.readerBackgroundColor = index
                PrefManager.setVal(PrefName.ReaderBackgroundColor, index)
                updateBgSelection(index)
                activity.applySettings()
            }
        }

        // --- Rotation Override UI ---
        val rotButtons = listOf(
            binding.btnRotFree,
            binding.btnRotPortrait,
            binding.btnRotLandscape
        )
        fun updateRotSelection(selectedIdx: Int) {
            rotButtons.forEachIndexed { idx, btn ->
                btn.alpha = if (idx == selectedIdx) 1.0f else 0.4f
            }
        }
        val initialRot = when (settings.defaultRotation) {
            1 -> 1
            2 -> 2
            else -> 0
        }
        updateRotSelection(initialRot)
        rotButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                settings.defaultRotation = index
                PrefManager.setVal(PrefName.DefaultRotation, index)
                updateRotSelection(index)
                activity.applySettings()
            }
        }

        // --- Toggles UI ---
        binding.switchEInkFlash.isChecked = settings.eInkFlashPageChange
        binding.switchEInkFlash.setOnCheckedChangeListener { _, isChecked ->
            settings.eInkFlashPageChange = isChecked
            PrefManager.setVal(PrefName.EInkFlashPageChange, isChecked)
            activity.applySettings()
        }

        binding.switchSkipChaptersRead.isChecked = settings.skipChaptersMarkedRead
        binding.switchSkipChaptersRead.setOnCheckedChangeListener { _, isChecked ->
            settings.skipChaptersMarkedRead = isChecked
            PrefManager.setVal(PrefName.SkipChaptersMarkedRead, isChecked)
            activity.applySettings()
        }

        binding.switchSkipFilteredChapters.isChecked = settings.skipFilteredChapters
        binding.switchSkipFilteredChapters.setOnCheckedChangeListener { _, isChecked ->
            settings.skipFilteredChapters = isChecked
            PrefManager.setVal(PrefName.SkipFilteredChapters, isChecked)
            activity.applySettings()
        }

        binding.switchAlwaysShowTransition.isChecked = settings.alwaysShowChapterTransition
        binding.switchAlwaysShowTransition.setOnCheckedChangeListener { _, isChecked ->
            settings.alwaysShowChapterTransition = isChecked
            PrefManager.setVal(PrefName.AlwaysShowChapterTransition, isChecked)
            activity.applySettings()
        }

        binding.switchSplitWidePages.isChecked = settings.splitWidePages
        binding.switchSplitWidePages.setOnCheckedChangeListener { _, isChecked ->
            settings.splitWidePages = isChecked
            PrefManager.setVal(PrefName.SplitWidePages, isChecked)
            activity.applySettings()
        }

        binding.switchRotateWidePages.isChecked = settings.rotateWidePagesToFit
        binding.switchRotateWidePages.setOnCheckedChangeListener { _, isChecked ->
            settings.rotateWidePagesToFit = isChecked
            PrefManager.setVal(PrefName.RotateWidePagesToFit, isChecked)
            activity.applySettings()
        }

        binding.switchShowReadingMode.isChecked = settings.showReadingModeToggle
        binding.switchShowReadingMode.setOnCheckedChangeListener { _, isChecked ->
            settings.showReadingModeToggle = isChecked
            PrefManager.setVal(PrefName.ShowReadingModeToggle, isChecked)
            activity.applySettings()
        }

        binding.switchShowTapZones.isChecked = settings.showTapZonesOverlay
        binding.switchShowTapZones.setOnCheckedChangeListener { _, isChecked ->
            settings.showTapZonesOverlay = isChecked
            PrefManager.setVal(PrefName.ShowTapZonesOverlay, isChecked)
            activity.applySettings()
        }

        // --- Sliders UI ---
        binding.sliderSidePadding.value = settings.continuousSidePadding.toFloat()
        binding.txtSidePadding.text = "Continuous Side Padding: ${settings.continuousSidePadding}%"
        binding.sliderSidePadding.addOnChangeListener { _, value, _ ->
            val paddingVal = value.toInt()
            settings.continuousSidePadding = paddingVal
            PrefManager.setVal(PrefName.ContinuousSidePadding, paddingVal)
            binding.txtSidePadding.text = "Continuous Side Padding: $paddingVal%"
            activity.applySettings()
        }

        binding.sliderPreloadAmount.value = settings.pagePreloadAmount.toFloat()
        binding.txtPreloadAmount.text = "Page Preload Amount: ${settings.pagePreloadAmount}"
        binding.sliderPreloadAmount.addOnChangeListener { _, value, _ ->
            val preloadVal = value.toInt()
            settings.pagePreloadAmount = preloadVal
            PrefManager.setVal(PrefName.PagePreloadAmount, preloadVal)
            binding.txtPreloadAmount.text = "Page Preload Amount: $preloadVal"
            activity.applySettings()
        }

        binding.sliderDownloadThreads.value = settings.downloadThreads.toFloat()
        binding.txtDownloadThreads.text = "Download Threads: ${settings.downloadThreads}"
        binding.sliderDownloadThreads.addOnChangeListener { _, value, _ ->
            val threadsVal = value.toInt()
            settings.downloadThreads = threadsVal
            PrefManager.setVal(PrefName.DownloadThreads, threadsVal)
            binding.txtDownloadThreads.text = "Download Threads: $threadsVal"
            activity.applySettings()
        }

        binding.switchLanczosUpscale.isChecked = settings.lanczosUpscale
        binding.switchLanczosUpscale.setOnCheckedChangeListener { _, isChecked ->
            settings.lanczosUpscale = isChecked
            PrefManager.setVal(PrefName.LanczosUpscale, isChecked)
            activity.applySettings()
        }

        binding.sliderSharpenStrength.value = settings.sharpenStrength
        binding.txtSharpenStrength.text = "Real-time Sharpening Strength: ${String.format("%.1f", settings.sharpenStrength)}"
        binding.sliderSharpenStrength.addOnChangeListener { _, value, _ ->
            settings.sharpenStrength = value
            PrefManager.setVal(PrefName.SharpenStrength, value)
            binding.txtSharpenStrength.text = "Real-time Sharpening Strength: ${String.format("%.1f", value)}"
            activity.applySettings()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = AdvancedSettingsDialogFragment()
    }
}
