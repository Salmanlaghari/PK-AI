package com.salmanlaghari.pkai

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.repository.AuthRepository
import com.salmanlaghari.pkai.databinding.ActivityMainBinding
import com.salmanlaghari.pkai.ui.aihub.FlowMusicOAuth
import com.salmanlaghari.pkai.util.CrashHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply the user-selected theme before inflating the layout
        val themeId = runBlocking { preferencesManager.getAppTheme() }
        setTheme(themeResId(themeId))

        // Install global crash handler after app startup completes.
        // This avoids any startup-time Hilt/DataStore initialization race.
        try {
            CrashHandler.initialize(this)
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Failed to install CrashHandler", e)
        }

        // Observe and apply theme/localization settings as early as possible
        lifecycleScope.launch {
            preferencesManager.isDarkMode.collect { isDark ->
                val mode = if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                if (AppCompatDelegate.getDefaultNightMode() != mode) {
                    AppCompatDelegate.setDefaultNightMode(mode)
                }
            }
        }

        lifecycleScope.launch {
            preferencesManager.appLanguage.collect { langCode ->
                val appLocales = LocaleListCompat.forLanguageTags(langCode)
                if (AppCompatDelegate.getApplicationLocales() != appLocales) {
                    AppCompatDelegate.setApplicationLocales(appLocales)
                }
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.homeFragment ||
                destination.id == R.id.chatsFragment ||
                destination.id == R.id.historyFragment ||
                destination.id == R.id.profileFragment) {
                binding.bottomNavigation.visibility = View.VISIBLE
            } else {
                // Hide bottom navigation for Ultra AI 4 (aiHubFragment) & details to grant full screen access
                binding.bottomNavigation.visibility = View.GONE
            }
        }

        setupDrawerNavigation()
        setupDrawerHeader()

        // Handle the OAuth deep link if the app was cold-started from it.
        handleFlowMusicDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle the OAuth deep link when the app is already running.
        handleFlowMusicDeepLink(intent)
    }

    /**
     * Routes the `pkai://auth-callback?code=...` redirect (returned by the
     * Chrome Custom Tab OAuth flow) to the live Ultra Chat AI engine.
     */
    private fun handleFlowMusicDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "pkai" && data.host == "auth-callback") {
            FlowMusicOAuth.onCallback?.invoke(data)
        }
    }

    private fun setupDrawerHeader() {
        val headerView = binding.navView.getHeaderView(0)
        val tvUserName = headerView.findViewById<android.widget.TextView>(R.id.tv_drawer_user_name)
        val tvMarquee = headerView.findViewById<android.widget.TextView>(R.id.tv_drawer_marquee)

        // Make marquee scroll loop infinitely
        tvMarquee?.isSelected = true

        // Dynamic loaded user name from login session
        lifecycleScope.launch {
            preferencesManager.userSession.collect { session ->
                if (session.isLoggedIn) {
                    tvUserName?.text = if (!session.displayName.isNullOrBlank()) {
                        session.displayName
                    } else if (session.isGuest) {
                        "Guest User"
                    } else {
                        "Prince Laghari"
                    }
                } else {
                    tvUserName?.text = "Prince Laghari"
                }
            }
        }
    }

    private fun setupDrawerNavigation() {
        binding.navView.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.nav_new_chat -> {
                    if (navController.currentDestination?.id != R.id.homeFragment) {
                        navController.navigate(R.id.homeFragment)
                    }
                    true
                }
                R.id.nav_profile -> {
                    if (navController.currentDestination?.id != R.id.profileFragment) {
                        navController.navigate(R.id.profileFragment)
                    }
                    true
                }
                R.id.nav_premium -> {
                    Toast.makeText(this, "PK AI Premium — full access unlocked.", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_ultra_ai -> {
                    if (navController.currentDestination?.id != R.id.aiHubFragment) {
                        navController.navigate(R.id.aiHubFragment)
                    }
                    true
                }
                R.id.nav_super_chat -> {
                    if (navController.currentDestination?.id != R.id.superChatFragment) {
                        navController.navigate(R.id.superChatFragment)
                    }
                    true
                }
                R.id.nav_chat_history -> {
                    if (navController.currentDestination?.id != R.id.historyFragment) {
                        navController.navigate(R.id.historyFragment)
                    }
                    true
                }
                R.id.nav_sys_settings -> {
                    if (navController.currentDestination?.id != R.id.settingsFragment) {
                        navController.navigate(R.id.settingsFragment)
                    }
                    true
                }
                R.id.nav_sys_logout -> {
                    lifecycleScope.launch {
                        authRepository.logout()
                        navController.navigate(R.id.loginFragment)
                    }
                    true
                }
                else -> {
                    // Placeholder navigation notifications for premium generators
                    Toast.makeText(this, "${item.title} placeholder clicked!", Toast.LENGTH_SHORT).show()
                    true
                }
            }
        }
    }

    fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun themeResId(themeId: String): Int {
        return when (themeId) {
            "ocean" -> R.style.Theme_PkAi_Ocean
            "sunset" -> R.style.Theme_PkAi_Sunset
            else -> R.style.Theme_PkAi
        }
    }
}
