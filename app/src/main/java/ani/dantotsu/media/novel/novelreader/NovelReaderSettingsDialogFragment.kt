package ani.dantotsu.media.novel.novelreader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import ani.dantotsu.BottomSheetDialogFragment
import ani.dantotsu.NoPaddingArrayAdapter
import ani.dantotsu.R
import ani.dantotsu.databinding.BottomSheetCurrentNovelReaderSettingsBinding
import ani.dantotsu.settings.CurrentNovelReaderSettings
import ani.dantotsu.settings.CurrentReaderSettings
import ani.dantotsu.settings.saving.PrefManager

class NovelReaderSettingsDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetCurrentNovelReaderSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCurrentNovelReaderSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as NovelReaderActivity
        val settings = activity.defaultSettings
        val themeLabels = activity.themes.map { it.name }
        binding.themeSelect.adapter =
            NoPaddingArrayAdapter(activity, R.layout.item_dropdown, themeLabels)
        binding.themeSelect.setSelection(themeLabels.indexOfFirst { it == settings.currentThemeName })
        binding.themeSelect.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                settings.currentThemeName = themeLabels[position]
                activity.applySettings()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.useOledTheme.isChecked = settings.useOledTheme
        binding.useOledTheme.setOnCheckedChangeListener { _, isChecked ->
            settings.useOledTheme = isChecked
            activity.applySettings()
        }
        val layoutList = listOf(
            binding.paged,
            binding.continuous
        )

        binding.layoutText.text = settings.layout.string
        var selected = layoutList[settings.layout.ordinal]
        selected.alpha = 1f

        layoutList.forEachIndexed { index, imageButton ->
            imageButton.setOnClickListener {
                selected.alpha = 0.33f
                selected = imageButton
                selected.alpha = 1f
                settings.layout = CurrentNovelReaderSettings.Layouts[index]
                    ?: CurrentNovelReaderSettings.Layouts.PAGED
                binding.layoutText.text = settings.layout.string
                activity.applySettings()
            }
        }

        val dualList = listOf(
            binding.dualNo,
            binding.dualAuto,
            binding.dualForce
        )

        binding.dualPageText.text = settings.dualPageMode.toString()
        var selectedDual = dualList[settings.dualPageMode.ordinal]
        selectedDual.alpha = 1f

        dualList.forEachIndexed { index, imageButton ->
            imageButton.setOnClickListener {
                selectedDual.alpha = 0.33f
                selectedDual = imageButton
                selectedDual.alpha = 1f
                settings.dualPageMode = CurrentReaderSettings.DualPageModes[index]
                    ?: CurrentReaderSettings.DualPageModes.Automatic
                binding.dualPageText.text = settings.dualPageMode.toString()
                activity.applySettings()
            }
        }

        binding.lineHeight.setText(settings.lineHeight.toString())
        binding.lineHeight.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.lineHeight.text.toString().toFloatOrNull() ?: 1.4f
                settings.lineHeight = value
                binding.lineHeight.setText(value.toString())
                activity.applySettings()
            }
        }

        binding.incrementLineHeight.setOnClickListener {
            val value = binding.lineHeight.text.toString().toFloatOrNull() ?: 1.4f
            settings.lineHeight = value + 0.1f
            binding.lineHeight.setText(settings.lineHeight.toString())
            activity.applySettings()
        }

        binding.decrementLineHeight.setOnClickListener {
            val value = binding.lineHeight.text.toString().toFloatOrNull() ?: 1.4f
            settings.lineHeight = value - 0.1f
            binding.lineHeight.setText(settings.lineHeight.toString())
            activity.applySettings()
        }

        binding.margin.setText(settings.margin.toString())
        binding.margin.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.margin.text.toString().toFloatOrNull() ?: 0.06f
                settings.margin = value
                binding.margin.setText(value.toString())
                activity.applySettings()
            }
        }

        binding.incrementMargin.setOnClickListener {
            val value = binding.margin.text.toString().toFloatOrNull() ?: 0.06f
            settings.margin = value + 0.01f
            binding.margin.setText(settings.margin.toString())
            activity.applySettings()
        }

        binding.decrementMargin.setOnClickListener {
            val value = binding.margin.text.toString().toFloatOrNull() ?: 0.06f
            settings.margin = value - 0.01f
            binding.margin.setText(settings.margin.toString())
            activity.applySettings()
        }

        binding.maxInlineSize.setText(settings.maxInlineSize.toString())
        binding.maxInlineSize.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.maxInlineSize.text.toString().toIntOrNull() ?: 720
                settings.maxInlineSize = value
                binding.maxInlineSize.setText(value.toString())
                activity.applySettings()
            }
        }

        binding.incrementMaxInlineSize.setOnClickListener {
            val value = binding.maxInlineSize.text.toString().toIntOrNull() ?: 720
            settings.maxInlineSize = value + 10
            binding.maxInlineSize.setText(settings.maxInlineSize.toString())
            activity.applySettings()
        }

        binding.decrementMaxInlineSize.setOnClickListener {
            val value = binding.maxInlineSize.text.toString().toIntOrNull() ?: 720
            settings.maxInlineSize = value - 10
            binding.maxInlineSize.setText(settings.maxInlineSize.toString())
            activity.applySettings()
        }

        binding.maxBlockSize.setText(settings.maxBlockSize.toString())
        binding.maxBlockSize.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.maxBlockSize.text.toString().toIntOrNull() ?: 720
                settings.maxBlockSize = value
                binding.maxBlockSize.setText(value.toString())
                activity.applySettings()
            }
        }
        binding.incrementMaxBlockSize.setOnClickListener {
            val value = binding.maxBlockSize.text.toString().toIntOrNull() ?: 720
            settings.maxBlockSize = value + 10
            binding.maxBlockSize.setText(settings.maxBlockSize.toString())
            activity.applySettings()
        }

        binding.decrementMaxBlockSize.setOnClickListener {
            val value = binding.maxBlockSize.text.toString().toIntOrNull() ?: 720
            settings.maxBlockSize = value - 10
            binding.maxBlockSize.setText(settings.maxBlockSize.toString())
            activity.applySettings()
        }

        binding.useDarkTheme.isChecked = settings.useDarkTheme
        binding.useDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            settings.useDarkTheme = isChecked
            activity.applySettings()
        }

        binding.keepScreenOn.isChecked = settings.keepScreenOn
        binding.keepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            settings.keepScreenOn = isChecked
            activity.applySettings()
        }

        binding.volumeButton.isChecked = settings.volumeButtons
        binding.volumeButton.setOnCheckedChangeListener { _, isChecked ->
            settings.volumeButtons = isChecked
            activity.applySettings()
        }

        val storedFontSize = PrefManager.getCustomVal(ExtraNovelReaderPrefs.PREF_FONT_SIZE_PX, 0)
        binding.novelFontSize.max = 32
        binding.novelFontSize.progress = if (storedFontSize == 0) 8 else (storedFontSize - 8).coerceIn(0, 32)
        val fontSizeDisplay = if (storedFontSize == 0) "Inherit" else "${storedFontSize}px"
        binding.novelFontSizeText.text = fontSizeDisplay
        binding.novelFontSize.setOnSeekBarChangeListener(simpleSeekListener { value ->
            val px = if (value == 0) 0 else value + 8
            PrefManager.setCustomVal(ExtraNovelReaderPrefs.PREF_FONT_SIZE_PX, px)
            binding.novelFontSizeText.text = if (px == 0) "Inherit" else "${px}px"
            activity.applyExtraSettings()
        })
        
        val storedLs = PrefManager.getCustomVal(ExtraNovelReaderPrefs.PREF_LETTER_SPACING, 0f)
        binding.novelLetterSpacing.max = 20
        binding.novelLetterSpacing.progress = (storedLs * 100).toInt().coerceIn(0, 20)
        binding.novelLetterSpacingText.text = "%.2fem".format(storedLs)
        binding.novelLetterSpacing.setOnSeekBarChangeListener(simpleSeekListener { value ->
            val em = value / 100f
            PrefManager.setCustomVal(ExtraNovelReaderPrefs.PREF_LETTER_SPACING, em)
            binding.novelLetterSpacingText.text = "%.2fem".format(em)
            activity.applyExtraSettings()
        })

        val storedWs = PrefManager.getCustomVal(ExtraNovelReaderPrefs.PREF_WORD_SPACING_PX, 0)
        binding.novelWordSpacing.max = 20
        binding.novelWordSpacing.progress = storedWs.coerceIn(0, 20)
        binding.novelWordSpacingText.text = "${storedWs}px"
        binding.novelWordSpacing.setOnSeekBarChangeListener(simpleSeekListener { value ->
            PrefManager.setCustomVal(ExtraNovelReaderPrefs.PREF_WORD_SPACING_PX, value)
            binding.novelWordSpacingText.text = "${value}px"
            activity.applyExtraSettings()
        })

        val storedPs = PrefManager.getCustomVal(ExtraNovelReaderPrefs.PREF_PARAGRAPH_SPACING_PX, 0)
        binding.novelParagraphSpacing.max = 32
        binding.novelParagraphSpacing.progress = storedPs.coerceIn(0, 32)
        binding.novelParagraphSpacingText.text = if (storedPs == 0) "Inherit" else "${storedPs}px"
        binding.novelParagraphSpacing.setOnSeekBarChangeListener(simpleSeekListener { value ->
            PrefManager.setCustomVal(ExtraNovelReaderPrefs.PREF_PARAGRAPH_SPACING_PX, value)
            binding.novelParagraphSpacingText.text = if (value == 0) "Inherit" else "${value}px"
            activity.applyExtraSettings()
        })

        val storedPad = PrefManager.getCustomVal(ExtraNovelReaderPrefs.PREF_HORIZONTAL_PADDING_PX, 0)
        binding.novelHorizontalPadding.max = 48
        binding.novelHorizontalPadding.progress = storedPad.coerceIn(0, 48)
        binding.novelHorizontalPaddingText.text = if (storedPad == 0) "Inherit" else "${storedPad}px"
        binding.novelHorizontalPadding.setOnSeekBarChangeListener(simpleSeekListener { value ->
            PrefManager.setCustomVal(ExtraNovelReaderPrefs.PREF_HORIZONTAL_PADDING_PX, value)
            binding.novelHorizontalPaddingText.text = if (value == 0) "Inherit" else "${value}px"
            activity.applyExtraSettings()
        })

        val alignLabels = listOf("Inherit", "Left", "Center", "Justify")
        val storedAlign = PrefManager.getCustomVal(ExtraNovelReaderPrefs.PREF_TEXT_ALIGN, 0)
        binding.novelTextAlign.adapter =
            ArrayAdapter(requireContext(), R.layout.item_dropdown, alignLabels)
        binding.novelTextAlign.setSelection(storedAlign.coerceIn(0, alignLabels.size - 1))
        binding.novelTextAlign.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) {
                PrefManager.setCustomVal(ExtraNovelReaderPrefs.PREF_TEXT_ALIGN, position)
                activity.applyExtraSettings()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        
        binding.novelAutoScroll.isChecked =
            PrefManager.getCustomVal(ExtraNovelReaderPrefs.PREF_AUTO_SCROLL, false)
        binding.novelAutoScroll.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setCustomVal(ExtraNovelReaderPrefs.PREF_AUTO_SCROLL, isChecked)
            if (isChecked) activity.autoScroll.start() else activity.autoScroll.stop()
        }

        val storedSpeed = PrefManager.getCustomVal(ExtraNovelReaderPrefs.PREF_AUTO_SCROLL_SPEED, 3)
        binding.novelAutoScrollSpeed.max = 29
        binding.novelAutoScrollSpeed.progress = (storedSpeed - 1).coerceIn(0, 29)
        binding.novelAutoScrollSpeedText.text = "${storedSpeed}s"
        binding.novelAutoScrollSpeed.setOnSeekBarChangeListener(simpleSeekListener { value ->
            val v = value + 1
            PrefManager.setCustomVal(ExtraNovelReaderPrefs.PREF_AUTO_SCROLL_SPEED, v)
            binding.novelAutoScrollSpeedText.text = "${v}s"
            activity.autoScroll.speedSeconds = v.toFloat()
        })

        binding.novelShowStatusBar.isChecked =
            PrefManager.getCustomVal(ExtraNovelReaderPrefs.PREF_SHOW_STATUS_BAR, false)
        binding.novelShowStatusBar.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setCustomVal(ExtraNovelReaderPrefs.PREF_SHOW_STATUS_BAR, isChecked)
            activity.readerOverlay.showStatusBar = isChecked
        }

        binding.novelShowProgress.isChecked =
            PrefManager.getCustomVal(ExtraNovelReaderPrefs.PREF_SHOW_PROGRESS, false)
        binding.novelShowProgress.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setCustomVal(ExtraNovelReaderPrefs.PREF_SHOW_PROGRESS, isChecked)
            activity.readerOverlay.showReadingProgress = isChecked
        }

        val langCodes = NovelTextTranslator.languages.keys.toList()
        val langNames = NovelTextTranslator.languages.values.toList()
        val storedLang = PrefManager.getCustomVal(ExtraNovelReaderPrefs.PREF_TRANSLATE_LANG, "none")
        binding.novelTranslateLang.adapter =
            ArrayAdapter(requireContext(), R.layout.item_dropdown, langNames)
        binding.novelTranslateLang.setSelection(langCodes.indexOf(storedLang).coerceAtLeast(0))
        binding.novelTranslateLang.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) {
                val code = langCodes[position]
                PrefManager.setCustomVal(ExtraNovelReaderPrefs.PREF_TRANSLATE_LANG, code)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun simpleSeekListener(onChanged: (Int) -> Unit) =
        object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) onChanged(p)
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar) {}
        }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    companion object {
        fun newInstance() = NovelReaderSettingsDialogFragment()
        const val TAG = "NovelReaderSettingsDialogFragment"
    }
}
