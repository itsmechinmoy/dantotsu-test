package ani.dantotsu.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ani.dantotsu.R
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.connections.discord.Discord
import ani.dantotsu.connections.mal.MAL
import ani.dantotsu.connections.simkl.Simkl
import ani.dantotsu.connections.simkl.SimklAuth
import ani.dantotsu.databinding.ActivitySettingsAccountsBinding
import ani.dantotsu.initActivity
import ani.dantotsu.loadImage
import ani.dantotsu.navBarHeight
import ani.dantotsu.openLinkInBrowser
import ani.dantotsu.others.CustomBottomDialog
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.snackString
import ani.dantotsu.startMainActivity
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import ani.dantotsu.toast
import ani.dantotsu.util.AlertDialogBuilder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import kotlinx.coroutines.launch

class SettingsAccountActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsAccountsBinding

    // 🔥 ADD THIS: At class level
    private lateinit var settingsAdapter: SettingsAdapter

    private val restartMainActivity = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = startMainActivity(this@SettingsAccountActivity)
    }

    private fun showLogoutConfirmationDialog(
        context: Context,
        serviceName: String,
        onConfirm: () -> Unit
    ) {
        AlertDialogBuilder(context).apply {
            setTitle("Logout $serviceName")
            setMessage("Are you sure you want to logout?")
            setPosButton("Yes") {
                onConfirm.invoke()
            }
            setNegButton("No", null)
            attach { dialog ->
                val width = (context.resources.displayMetrics.widthPixels * 0.85).toInt()
                dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
            }
            show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        val context = this

        binding = ActivitySettingsAccountsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apply {
            settingsAccountsLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
                bottomMargin = navBarHeight
            }
            accountSettingsBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

            settingsAccountHelp.setOnClickListener {
                CustomBottomDialog.newInstance().apply {
                    setTitleText(context.getString(R.string.account_help))
                    addView(
                        TextView(it.context).apply {
                            val markWon = Markwon.builder(it.context)
                                .usePlugin(SoftBreakAddsNewLinePlugin.create()).build()
                            markWon.setMarkdown(this, context.getString(R.string.full_account_help))
                        }
                    )
                }.show(supportFragmentManager, "dialog")
            }
            settingsSimklHelp.setOnClickListener {
                CustomBottomDialog.newInstance().apply {
                    setTitleText(context.getString(R.string.simkl_help))
                    addView(
                        TextView(it.context).apply {
                            val markWon = Markwon.builder(it.context)
                                .usePlugin(SoftBreakAddsNewLinePlugin.create()).build()
                            markWon.setMarkdown(this, context.getString(R.string.full_simkl_help))
                        }
                    )
                }.show(supportFragmentManager, "dialog")
            }

            fun reload() {
                if (Anilist.token != null) {
                    settingsAnilistLogin.setText(R.string.logout)
                    settingsAnilistLogin.setOnClickListener {
                        showLogoutConfirmationDialog(context, "AniList") {
                            Anilist.removeSavedToken()
                            Toast.makeText(context, "Logout successfully", Toast.LENGTH_SHORT).show()
                            startMainActivity(this@SettingsAccountActivity)
                        }
                    }
                    settingsAnilistUsername.visibility = View.VISIBLE
                    settingsAnilistUsername.text = Anilist.username
                    settingsAnilistAvatar.loadImage(Anilist.avatar)
                    settingsAnilistAvatar.setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        val anilistLink = getString(
                            R.string.anilist_link,
                            PrefManager.getVal<String>(PrefName.AnilistUserName)
                        )
                        openLinkInBrowser(anilistLink)
                    }

                    settingsMALLoginRequired.visibility = View.GONE
                    settingsMALLogin.visibility = View.VISIBLE
                    settingsMALUsername.visibility = View.VISIBLE

                    if (MAL.token != null) {
                        settingsMALLogin.setText(R.string.logout)
                        settingsMALLogin.setOnClickListener {
                            showLogoutConfirmationDialog(context, "MAL") {
                                MAL.removeSavedToken()
                                Toast.makeText(context, "Logout successfully", Toast.LENGTH_SHORT)
                                    .show()
                                startMainActivity(this@SettingsAccountActivity)
                            }
                        }
                        settingsMALUsername.visibility = View.VISIBLE
                        settingsMALUsername.text = MAL.username
                        settingsMALAvatar.loadImage(MAL.avatar)
                        settingsMALAvatar.setOnClickListener {
                            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            openLinkInBrowser(getString(R.string.myanilist_link, MAL.username))
                        }
                    } else {
                        settingsMALAvatar.setImageResource(R.drawable.ic_round_person_24)
                        settingsMALUsername.visibility = View.GONE
                        settingsMALLogin.setText(R.string.login)
                        settingsMALLogin.setOnClickListener {
                            MAL.loginIntent(context)
                        }
                    }
                } else {
                    settingsAnilistAvatar.setImageResource(R.drawable.ic_round_person_24)
                    settingsAnilistUsername.visibility = View.GONE
                    settingsRecyclerView.visibility = View.GONE
                    settingsAnilistLogin.setText(R.string.login)
                    settingsAnilistLogin.setOnClickListener {
                        Anilist.loginIntent(context)
                    }
                    settingsMALLoginRequired.visibility = View.VISIBLE
                    settingsMALLogin.visibility = View.GONE
                    settingsMALUsername.visibility = View.GONE
                }

                if (Discord.token != null) {
                    val id = PrefManager.getVal(PrefName.DiscordId, null as String?)
                    val avatar = PrefManager.getVal(PrefName.DiscordAvatar, null as String?)
                    val username = PrefManager.getVal(PrefName.DiscordUserName, null as String?)
                    if (id != null && avatar != null) {
                        settingsDiscordAvatar.loadImage("https://cdn.discordapp.com/avatars/$id/$avatar.png")
                        settingsDiscordAvatar.setOnClickListener {
                            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            val discordLink = getString(R.string.discord_link, id)
                            openLinkInBrowser(discordLink)
                        }
                    }
                    settingsDiscordUsername.visibility = View.VISIBLE
                    settingsDiscordUsername.text =
                        username ?: Discord.token?.replace(Regex("."), "*")
                    settingsDiscordLogin.setText(R.string.logout)
                    settingsDiscordLogin.setOnClickListener {
                        showLogoutConfirmationDialog(context, "Discord") {
                            Discord.removeSavedToken(context)
                            Toast.makeText(context, "Logout successfully", Toast.LENGTH_SHORT)
                                .show()
                            startMainActivity(this@SettingsAccountActivity)
                        }
                    }

                    settingsPresenceSwitcher.visibility = View.VISIBLE
                    var initialStatus =
                        when (PrefManager.getVal<String>(PrefName.DiscordStatus)) {
                            "online" -> R.drawable.discord_status_online
                            "idle" -> R.drawable.discord_status_idle
                            "dnd" -> R.drawable.discord_status_dnd
                            "invisible" -> R.drawable.discord_status_invisible
                            else -> R.drawable.discord_status_online
                        }
                    settingsPresenceSwitcher.setImageResource(initialStatus)

                    val zoomInAnimation =
                        AnimationUtils.loadAnimation(context, R.anim.bounce_zoom)
                    settingsPresenceSwitcher.setOnClickListener {
                        var status = "online"
                        initialStatus = when (initialStatus) {
                            R.drawable.discord_status_online -> {
                                status = "idle"
                                R.drawable.discord_status_idle
                            }

                            R.drawable.discord_status_idle -> {
                                status = "dnd"
                                R.drawable.discord_status_dnd
                            }

                            R.drawable.discord_status_dnd -> {
                                status = "invisible"
                                R.drawable.discord_status_invisible
                            }

                            R.drawable.discord_status_invisible -> {
                                status = "online"
                                R.drawable.discord_status_online
                            }

                            else -> R.drawable.discord_status_online
                        }

                        PrefManager.setVal(PrefName.DiscordStatus, status)
                        settingsPresenceSwitcher.setImageResource(initialStatus)
                        settingsPresenceSwitcher.startAnimation(zoomInAnimation)
                    }
                    settingsPresenceSwitcher.setOnLongClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        DiscordDialogFragment().show(supportFragmentManager, "dialog")
                        true
                    }
                } else {
                    settingsPresenceSwitcher.visibility = View.GONE
                    settingsDiscordAvatar.setImageResource(R.drawable.ic_round_person_24)
                    settingsDiscordUsername.visibility = View.GONE
                    settingsDiscordLogin.setText(R.string.login)
                    settingsDiscordLogin.setOnClickListener {
                        Discord.warning(context)
                            .show(supportFragmentManager, "dialog")
                    }
                }

                // 🔥 ADD THIS: Refresh settings list when reloading
                settingsAdapter = SettingsAdapter(getSettingsList())
                binding.settingsRecyclerView.adapter = settingsAdapter
            }
            reload()
            updateSimklUI()
        }

        // 🔥 REPLACE THIS BLOCK with the new code below
        binding.settingsRecyclerView.adapter = SettingsAdapter(
            arrayListOf(
                Settings(
                    type = 2,
                    name = getString(R.string.enable_rpc),
                    desc = getString(R.string.enable_rpc_desc),
                    icon = R.drawable.interests_24,
                    isChecked = PrefManager.getVal(PrefName.rpcEnabled),
                    switch = { isChecked, _ ->
                        PrefManager.setVal(PrefName.rpcEnabled, isChecked)
                    },
                    isVisible = Discord.token != null
                ),
                // 🔥 ADD THIS - Simkl Sync Toggle
                Settings(
                    type = 2,
                    name = getString(R.string.enable_simkl_sync),
                    desc = getString(R.string.enable_simkl_sync_desc),
                    icon = R.drawable.ic_simkl,
                    isChecked = PrefManager.getVal(PrefName.SimklEnabled),
                    switch = { isChecked, _ ->
                        PrefManager.setVal(PrefName.SimklEnabled, isChecked)
                        Simkl.getInstance().setEnabled(isChecked)
                    },
                    isVisible = Simkl.getInstance().isLoggedIn()  // Only show if logged in
                ),
                Settings(
                    type = 1,
                    name = getString(R.string.anilist_settings),
                    desc = getString(R.string.alsettings_desc),
                    icon = R.drawable.ic_anilist,
                    onClick = {
                        lifecycleScope.launch {
                            Anilist.query.getUserData()
                            startActivity(Intent(context, AnilistSettingsActivity::class.java))
                        }
                    },
                    isActivity = true
                ),
                Settings(
                    type = 2,
                    name = getString(R.string.comments_button),
                    desc = getString(R.string.comments_button_desc),
                    icon = R.drawable.ic_round_comment_24,
                    isChecked = PrefManager.getVal<Int>(PrefName.CommentsEnabled) == 1,
                    switch = { isChecked, _ ->
                        PrefManager.setVal(PrefName.CommentsEnabled, if (isChecked) 1 else 2)
                        reload()
                    },
                    isVisible = Anilist.token != null
                ),
            )
        )

        // 🔥 NEW CODE:
        settingsAdapter = SettingsAdapter(getSettingsList())
        binding.settingsRecyclerView.adapter = settingsAdapter
        binding.settingsRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
    }

    // New function to generate settings list (only includes visible items)
    private fun getSettingsList(): ArrayList<Settings> {
        return arrayListOf(

            Settings(
                type = 1,
                name = getString(R.string.anilist_settings),
                desc = getString(R.string.alsettings_desc),
                icon = R.drawable.ic_anilist,
                onClick = {
                    lifecycleScope.launch {
                        Anilist.query.getUserData()
                        startActivity(Intent(this@SettingsAccountActivity, AnilistSettingsActivity::class.java))
                    }
                },
                isActivity = true
            ),
            Settings(
                type = 2,
                name = getString(R.string.comments_button),
                desc = getString(R.string.comments_button_desc),
                icon = R.drawable.ic_round_comment_24,
                isChecked = PrefManager.getVal<Int>(PrefName.CommentsEnabled) == 1,
                switch = { isChecked, _ ->
                    PrefManager.setVal(PrefName.CommentsEnabled, if (isChecked) 1 else 2)
                },
                isVisible = Anilist.token != null
            ),
            Settings(
                type = 2,
                name = getString(R.string.enable_simkl_sync),
                desc = getString(R.string.enable_simkl_sync_desc),
                icon = R.drawable.ic_simkl,
                isChecked = PrefManager.getVal(PrefName.SimklEnabled),
                switch = { isChecked, _ ->
                    PrefManager.setVal(PrefName.SimklEnabled, isChecked)
                    Simkl.getInstance().setEnabled(isChecked)
                },
                isVisible = Simkl.getInstance().isLoggedIn()
            ),
            Settings(
                type = 2,
                name = getString(R.string.enable_rpc),
                desc = getString(R.string.enable_rpc_desc),
                icon = R.drawable.interests_24,
                isChecked = PrefManager.getVal(PrefName.rpcEnabled),
                switch = { isChecked, _ ->
                    PrefManager.setVal(PrefName.rpcEnabled, isChecked)
                },
                isVisible = Discord.token != null
            ),

        ).filter { it.isVisible } as ArrayList<Settings>
    }

    // 🔥 UPDATE THIS: Modified updateSimklUI()
    private fun updateSimklUI() {
        val simkl = Simkl.getInstance()
        binding.apply {
            if (simkl.isLoggedIn()) {
                // 🔥 REMOVE THE COROUTINE AND fetchAndSaveUser()
                // Just use cached data like AniList/MAL
                val username = simkl.username ?: simkl.user?.name ?: "Simkl User"
                val avatarUrl = simkl.avatar ?: simkl.user?.avatar

                settingsSimklUsername.text = username
                settingsSimklAvatar.loadImage(avatarUrl)

                settingsSimklAvatar.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    val simklLink = getString(R.string.simkl_link, username)
                    openLinkInBrowser(simklLink)
                }

                settingsSimklLogin.setText(R.string.logout)
                settingsSimklLogin.setOnClickListener {
                    showLogoutConfirmationDialog(this@SettingsAccountActivity, "Simkl") {
                        simkl.logout()
                        updateSimklUI()
                        toast("Logged out from Simkl")

                        settingsAdapter = SettingsAdapter(getSettingsList())
                        binding.settingsRecyclerView.adapter = settingsAdapter
                    }
                }
                settingsSimklUsername.isVisible = true
            } else {
                settingsSimklAvatar.setImageResource(R.drawable.ic_round_person_24)
                settingsSimklUsername.isVisible = false
                settingsSimklLogin.setText(R.string.login)
                settingsSimklLogin.setOnClickListener {
                    SimklAuth.startLogin(this@SettingsAccountActivity)
                }

                settingsSimklAvatar.setOnClickListener(null)

                settingsAdapter = SettingsAdapter(getSettingsList())
                binding.settingsRecyclerView.adapter = settingsAdapter
            }
        }
    }

    fun reload() {
        snackString(getString(R.string.restart_app_extra))
    }
}