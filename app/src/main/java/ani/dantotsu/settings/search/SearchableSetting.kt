package ani.dantotsu.settings.search

import androidx.appcompat.app.AppCompatActivity
import ani.dantotsu.R

data class SearchableSetting(
    val title: String,
    val desc: String? = null,
    val icon: Int = R.drawable.ic_round_settings_24,
    val category: String,
    val breadcrumbs: String,
    val targetActivity: Class<out AppCompatActivity>,
    val highlightKey: String = title,
    val isVisible: Boolean = true
)
