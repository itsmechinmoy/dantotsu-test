package ani.dantotsu.media.manga.mangareader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ani.dantotsu.BottomSheetDialogFragment
import ani.dantotsu.R
import ani.dantotsu.databinding.BottomSheetCurrentReaderSettingsBinding
import ani.dantotsu.settings.CurrentReaderSettings
import ani.dantotsu.settings.CurrentReaderSettings.Directions
import ani.dantotsu.settings.saving.PrefManager

class ReaderSettingsDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetCurrentReaderSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCurrentReaderSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as MangaReaderActivity
        val settings = activity.defaultSettings

        binding.readerDirectionText.text =
            resources.getStringArray(R.array.manga_directions)[settings.direction.ordinal]
        binding.readerDirection.rotation = 90f * (settings.direction.ordinal)
        binding.readerDirection.setOnClickListener {
            settings.direction =
                Directions[settings.direction.ordinal + 1] ?: Directions.TOP_TO_BOTTOM
            binding.readerDirectionText.text =
                resources.getStringArray(R.array.manga_directions)[settings.direction.ordinal]
            binding.readerDirection.rotation = 90f * (settings.direction.ordinal)
            activity.applySettings()
        }

        val list = listOf(
            binding.readerPaged,
            binding.readerContinuousPaged,
            binding.readerContinuous
        )

        binding.readerPadding.isEnabled = settings.layout.ordinal != 0
        fun paddingAvailable(enable: Boolean) {
            binding.readerPadding.isEnabled = enable
        }

        binding.readerPadding.isChecked = settings.padding
        binding.readerPadding.setOnCheckedChangeListener { _, isChecked ->
            settings.padding = isChecked
            activity.applySettings()
        }

        binding.readerCropBorders.isChecked = settings.cropBorders
        binding.readerCropBorders.setOnCheckedChangeListener { _, isChecked ->
            settings.cropBorders = isChecked
            activity.applySettings()
        }

        binding.readerLayoutText.text =
            resources.getStringArray(R.array.manga_layouts)[settings.layout.ordinal]
        var selected = list[settings.layout.ordinal]
        selected.alpha = 1f

        list.forEachIndexed { index, imageButton ->
            imageButton.setOnClickListener {
                selected.alpha = 0.33f
                selected = imageButton
                selected.alpha = 1f
                settings.layout =
                    CurrentReaderSettings.Layouts[index] ?: CurrentReaderSettings.Layouts.CONTINUOUS
                binding.readerLayoutText.text =
                    resources.getStringArray(R.array.manga_layouts)[settings.layout.ordinal]
                activity.applySettings()
                paddingAvailable(settings.layout.ordinal != 0)
            }
        }

        val dualList = listOf(
            binding.readerDualNo,
            binding.readerDualAuto,
            binding.readerDualForce
        )

        binding.readerDualPageText.text = settings.dualPageMode.toString()
        var selectedDual = dualList[settings.dualPageMode.ordinal]
        selectedDual.alpha = 1f

        dualList.forEachIndexed { index, imageButton ->
            imageButton.setOnClickListener {
                selectedDual.alpha = 0.33f
                selectedDual = imageButton
                selectedDual.alpha = 1f
                settings.dualPageMode = CurrentReaderSettings.DualPageModes[index]
                    ?: CurrentReaderSettings.DualPageModes.Automatic
                binding.readerDualPageText.text = settings.dualPageMode.toString()
                activity.applySettings()
            }
        }
        binding.readerTrueColors.isChecked = settings.trueColors
        binding.readerTrueColors.setOnCheckedChangeListener { _, isChecked ->
            settings.trueColors = isChecked
            activity.applySettings()
        }

        binding.readerImageRotation.isChecked = settings.rotation
        binding.readerImageRotation.setOnCheckedChangeListener { _, isChecked ->
            settings.rotation = isChecked
            activity.applySettings()
        }

        binding.readerHorizontalScrollBar.isChecked = settings.horizontalScrollBar
        binding.readerHorizontalScrollBar.setOnCheckedChangeListener { _, isChecked ->
            settings.horizontalScrollBar = isChecked
            activity.applySettings()
        }

        binding.readerKeepScreenOn.isChecked = settings.keepScreenOn
        binding.readerKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            settings.keepScreenOn = isChecked
            activity.applySettings()
        }

        binding.readerHideScrollBar.isChecked = settings.hideScrollBar
        binding.readerHideScrollBar.setOnCheckedChangeListener { _, isChecked ->
            settings.hideScrollBar = isChecked
            activity.applySettings()
        }

        binding.readerHidePageNumbers.isChecked = settings.hidePageNumbers
        binding.readerHidePageNumbers.setOnCheckedChangeListener { _, isChecked ->
            settings.hidePageNumbers = isChecked
            activity.applySettings()
        }

        binding.readerOverscroll.isChecked = settings.overScrollMode
        binding.readerOverscroll.setOnCheckedChangeListener { _, isChecked ->
            settings.overScrollMode = isChecked
            activity.applySettings()
        }

        binding.readerVolumeButton.isChecked = settings.volumeButtons
        binding.readerVolumeButton.setOnCheckedChangeListener { _, isChecked ->
            settings.volumeButtons = isChecked
            activity.applySettings()
        }

        binding.readerWrapImage.isChecked = settings.wrapImages
        binding.readerWrapImage.setOnCheckedChangeListener { _, isChecked ->
            settings.wrapImages = isChecked
            activity.applySettings()
        }

        binding.readerLongClickImage.isChecked = settings.longClickImage
        binding.readerLongClickImage.setOnCheckedChangeListener { _, isChecked ->
            settings.longClickImage = isChecked
            activity.applySettings()
        }
        
        binding.readerPageIndicator.isChecked =
            PrefManager.getCustomVal(ExtraReaderPrefs.PREF_PAGE_INDICATOR, false)
        binding.readerPageIndicator.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setCustomVal(ExtraReaderPrefs.PREF_PAGE_INDICATOR, isChecked)
            activity.applyExtraSettings()
        }
        
        binding.readerChapterTransition.isChecked =
            PrefManager.getCustomVal(ExtraReaderPrefs.PREF_CHAPTER_TRANSITION, false)
        binding.readerChapterTransition.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setCustomVal(ExtraReaderPrefs.PREF_CHAPTER_TRANSITION, isChecked)
        }
        
        binding.readerAutoScroll.isChecked =
            PrefManager.getCustomVal(ExtraReaderPrefs.PREF_AUTO_SCROLL, false)
        binding.readerAutoScroll.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setCustomVal(ExtraReaderPrefs.PREF_AUTO_SCROLL, isChecked)
            if (isChecked) activity.autoScrollManager.start()
            else activity.autoScrollManager.stop()
        }
        
        val autoScrollSpeedDefault = 3
        binding.readerAutoScrollSpeed.max = 29
        binding.readerAutoScrollSpeed.progress =
            PrefManager.getCustomVal(ExtraReaderPrefs.PREF_AUTO_SCROLL_SPEED, autoScrollSpeedDefault) - 1
        binding.readerAutoScrollSpeedText.text =
            PrefManager.getCustomVal(ExtraReaderPrefs.PREF_AUTO_SCROLL_SPEED, autoScrollSpeedDefault).toString()
        binding.readerAutoScrollSpeed.setOnSeekBarChangeListener(
            simpleSeekListener { value ->
                val v = value + 1
                PrefManager.setCustomVal(ExtraReaderPrefs.PREF_AUTO_SCROLL_SPEED, v)
                binding.readerAutoScrollSpeedText.text = v.toString()
                activity.autoScrollManager.speedSeconds = v.toFloat()
            }
        )
        
        binding.readerCustomBrightness.isChecked =
            PrefManager.getCustomVal(ExtraReaderPrefs.PREF_BRIGHTNESS_ENABLED, false)
        binding.readerCustomBrightness.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setCustomVal(ExtraReaderPrefs.PREF_BRIGHTNESS_ENABLED, isChecked)
            activity.applyExtraSettings()
        }
        
        binding.readerBrightnessLevel.max = 75
        val storedBrightness = PrefManager.getCustomVal(ExtraReaderPrefs.PREF_BRIGHTNESS_LEVEL, 0)
        binding.readerBrightnessLevel.progress = -storedBrightness.coerceIn(-75, 0)
        binding.readerBrightnessLevelText.text = storedBrightness.toString()
        binding.readerBrightnessLevel.setOnSeekBarChangeListener(
            simpleSeekListener { value ->
                val v = -value
                PrefManager.setCustomVal(ExtraReaderPrefs.PREF_BRIGHTNESS_LEVEL, v)
                binding.readerBrightnessLevelText.text = v.toString()
                activity.applyExtraSettings()
            }
        )
        
        binding.readerGrayscale.isChecked =
            PrefManager.getCustomVal(ExtraReaderPrefs.PREF_GRAYSCALE, false)
        binding.readerGrayscale.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setCustomVal(ExtraReaderPrefs.PREF_GRAYSCALE, isChecked)
            activity.applyExtraSettings()
        }
        
        binding.readerInvertColors.isChecked =
            PrefManager.getCustomVal(ExtraReaderPrefs.PREF_INVERT_COLORS, false)
        binding.readerInvertColors.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setCustomVal(ExtraReaderPrefs.PREF_INVERT_COLORS, isChecked)
            activity.applyExtraSettings()
        }

        binding.readerColorFilter.isChecked =
            PrefManager.getCustomVal(ExtraReaderPrefs.PREF_COLOR_FILTER_ENABLED, false)
        binding.readerColorFilter.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setCustomVal(ExtraReaderPrefs.PREF_COLOR_FILTER_ENABLED, isChecked)
            activity.applyExtraSettings()
        }

        fun colorSlider(seekBar: android.widget.SeekBar, textView: android.widget.TextView,
                        prefKey: String, default: Int) {
            seekBar.max = 255
            seekBar.progress = PrefManager.getCustomVal(prefKey, default)
            textView.text = PrefManager.getCustomVal(prefKey, default).toString()
            seekBar.setOnSeekBarChangeListener(simpleSeekListener { value ->
                PrefManager.setCustomVal(prefKey, value)
                textView.text = value.toString()
                activity.applyExtraSettings()
            })
        }
        colorSlider(binding.readerColorFilterR, binding.readerColorFilterRText,
            ExtraReaderPrefs.PREF_CF_RED,   0)
        colorSlider(binding.readerColorFilterG, binding.readerColorFilterGText,
            ExtraReaderPrefs.PREF_CF_GREEN, 0)
        colorSlider(binding.readerColorFilterB, binding.readerColorFilterBText,
            ExtraReaderPrefs.PREF_CF_BLUE,  0)
        colorSlider(binding.readerColorFilterA, binding.readerColorFilterAText,
            ExtraReaderPrefs.PREF_CF_ALPHA, 128)

        binding.readerEinkRefresh.isChecked =
            PrefManager.getCustomVal(ExtraReaderPrefs.PREF_EINK_ENABLED, false)
        binding.readerEinkRefresh.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setCustomVal(ExtraReaderPrefs.PREF_EINK_ENABLED, isChecked)
            activity.applyExtraSettings()
        }
        
        binding.readerEinkWhite.isChecked =
            PrefManager.getCustomVal(ExtraReaderPrefs.PREF_EINK_WHITE, false)
        binding.readerEinkWhite.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setCustomVal(ExtraReaderPrefs.PREF_EINK_WHITE, isChecked)
            activity.einkManager.flashWhite = isChecked
        }
        
        binding.readerEinkDuration.max = 450
        val storedEinkDuration = PrefManager.getCustomVal(ExtraReaderPrefs.PREF_EINK_DURATION_MS, 200)
        binding.readerEinkDuration.progress = (storedEinkDuration - 50).coerceAtLeast(0)
        binding.readerEinkDurationText.text = "${storedEinkDuration}ms"
        binding.readerEinkDuration.setOnSeekBarChangeListener(simpleSeekListener { value ->
            val v = value + 50
            PrefManager.setCustomVal(ExtraReaderPrefs.PREF_EINK_DURATION_MS, v)
            binding.readerEinkDurationText.text = "${v}ms"
            activity.einkManager.flashDurationMs = v
        })

        binding.readerEinkInterval.max = 9
        val storedEinkInterval = PrefManager.getCustomVal(ExtraReaderPrefs.PREF_EINK_INTERVAL, 1)
        binding.readerEinkInterval.progress = (storedEinkInterval - 1).coerceAtLeast(0)
        binding.readerEinkIntervalText.text = storedEinkInterval.toString()
        binding.readerEinkInterval.setOnSeekBarChangeListener(simpleSeekListener { value ->
            val v = value + 1
            PrefManager.setCustomVal(ExtraReaderPrefs.PREF_EINK_INTERVAL, v)
            binding.readerEinkIntervalText.text = v.toString()
            activity.einkManager.flashEveryNPages = v
        })
    }
    
    private fun simpleSeekListener(
        onChanged: (Int) -> Unit
    ) = object : android.widget.SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) onChanged(progress)
        }
        override fun onStartTrackingTouch(seekBar: android.widget.SeekBar) {}
        override fun onStopTrackingTouch(seekBar: android.widget.SeekBar) {}
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    companion object {
        fun newInstance() = ReaderSettingsDialogFragment()
    }
}
