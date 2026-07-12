package ani.dantotsu.settings

import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import java.io.Serializable

data class CurrentReaderSettings(
    var direction: Directions = Directions[PrefManager.getVal(PrefName.Direction)]
        ?: Directions.TOP_TO_BOTTOM,
    var layout: Layouts = Layouts[PrefManager.getVal(PrefName.LayoutReader)]
        ?: Layouts.CONTINUOUS,
    var dualPageMode: DualPageModes = DualPageModes[PrefManager.getVal(PrefName.DualPageModeReader)]
        ?: DualPageModes.Automatic,
    var overScrollMode: Boolean = PrefManager.getVal(PrefName.OverScrollMode),
    var trueColors: Boolean = PrefManager.getVal(PrefName.TrueColors),
    var rotation: Boolean = PrefManager.getVal(PrefName.Rotation),
    var padding: Boolean = PrefManager.getVal(PrefName.Padding),
    var hideScrollBar: Boolean = PrefManager.getVal(PrefName.HideScrollBar),
    var hidePageNumbers: Boolean = PrefManager.getVal(PrefName.HidePageNumbers),
    var horizontalScrollBar: Boolean = PrefManager.getVal(PrefName.HorizontalScrollBar),
    var keepScreenOn: Boolean = PrefManager.getVal(PrefName.KeepScreenOn),
    var volumeButtons: Boolean = PrefManager.getVal(PrefName.VolumeButtonsReader),
    var wrapImages: Boolean = PrefManager.getVal(PrefName.WrapImages),
    var longClickImage: Boolean = PrefManager.getVal(PrefName.LongClickImage),
    var cropBorders: Boolean = PrefManager.getVal(PrefName.CropBorders),
    var cropBorderThreshold: Int = PrefManager.getVal(PrefName.CropBorderThreshold),
    var dataSaverMode: Int = PrefManager.getVal(PrefName.DataSaverMode),
    var dataSaverImageQuality: Int = PrefManager.getVal(PrefName.DataSaverImageQuality),
    var dataSaverImageFormatJpeg: Boolean = PrefManager.getVal(PrefName.DataSaverImageFormatJpeg),
    var dataSaverIgnoreJpeg: Boolean = PrefManager.getVal(PrefName.DataSaverIgnoreJpeg),
    var dataSaverIgnoreGif: Boolean = PrefManager.getVal(PrefName.DataSaverIgnoreGif),
    var dataSaverServer: String = PrefManager.getVal(PrefName.DataSaverServer),
    var dataSaverColorBW: Boolean = PrefManager.getVal(PrefName.DataSaverColorBW),
    var doubleTapAnimationSpeed: Int = PrefManager.getVal(PrefName.DoubleTapAnimationSpeed),
    var showReadingModeToggle: Boolean = PrefManager.getVal(PrefName.ShowReadingModeToggle),
    var showTapZonesOverlay: Boolean = PrefManager.getVal(PrefName.ShowTapZonesOverlay),
    var smallerTapZones: Boolean = PrefManager.getVal(PrefName.SmallerTapZones),
    var forcedHorizontalSeekbar: Boolean = PrefManager.getVal(PrefName.ForcedHorizontalSeekbar),
    var showVerticalSeekbarInLandscape: Boolean = PrefManager.getVal(PrefName.ShowVerticalSeekbarInLandscape),
    var leftHandedVerticalSeekbar: Boolean = PrefManager.getVal(PrefName.LeftHandedVerticalSeekbar),
    var defaultRotation: Int = PrefManager.getVal(PrefName.DefaultRotation),
    var readerBackgroundColor: Int = PrefManager.getVal(PrefName.ReaderBackgroundColor),
    var eInkFlashPageChange: Boolean = PrefManager.getVal(PrefName.EInkFlashPageChange),
    var skipChaptersMarkedRead: Boolean = PrefManager.getVal(PrefName.SkipChaptersMarkedRead),
    var skipFilteredChapters: Boolean = PrefManager.getVal(PrefName.SkipFilteredChapters),
    var alwaysShowChapterTransition: Boolean = PrefManager.getVal(PrefName.AlwaysShowChapterTransition),
    var pagedTapZones: Int = PrefManager.getVal(PrefName.PagedTapZones),
    var invertTapZones: Int = PrefManager.getVal(PrefName.InvertTapZones),
    var pagedScaleType: Int = PrefManager.getVal(PrefName.PagedScaleType),
    var zoomStartPosition: Int = PrefManager.getVal(PrefName.ZoomStartPosition),
    var splitWidePages: Boolean = PrefManager.getVal(PrefName.SplitWidePages),
    var rotateWidePagesToFit: Boolean = PrefManager.getVal(PrefName.RotateWidePagesToFit),
    var smartScaleWideScreen: Int = PrefManager.getVal(PrefName.SmartScaleWideScreen),
    var continuousSidePadding: Int = PrefManager.getVal(PrefName.ContinuousSidePadding),
    var menuHidingSensitivity: Int = PrefManager.getVal(PrefName.MenuHidingSensitivity),
    var pagePreloadAmount: Int = PrefManager.getVal(PrefName.PagePreloadAmount),
    var downloadThreads: Int = PrefManager.getVal(PrefName.DownloadThreads),
    var readerCacheSize: Int = PrefManager.getVal(PrefName.ReaderCacheSize),
    var lanczosUpscale: Boolean = PrefManager.getVal(PrefName.LanczosUpscale),
    var sharpenStrength: Float = PrefManager.getVal(PrefName.SharpenStrength)
) : Serializable {

    enum class Directions {
        TOP_TO_BOTTOM,
        RIGHT_TO_LEFT,
        BOTTOM_TO_TOP,
        LEFT_TO_RIGHT;

        companion object {
            operator fun get(value: Int) = values().firstOrNull { it.ordinal == value }
        }
    }

    enum class Layouts {
        PAGED,
        CONTINUOUS_PAGED,
        CONTINUOUS;

        companion object {
            operator fun get(value: Int) = values().firstOrNull { it.ordinal == value }
        }
    }

    enum class DualPageModes {
        No, Automatic, Force;

        companion object {
            operator fun get(value: Int) = values().firstOrNull { it.ordinal == value }
        }
    }

    companion object {
        fun applyWebtoon(settings: CurrentReaderSettings) {
            settings.apply {
                layout = Layouts.CONTINUOUS
                direction = Directions.TOP_TO_BOTTOM
                dualPageMode = DualPageModes.No
                padding = false
            }
        }
    }
}
