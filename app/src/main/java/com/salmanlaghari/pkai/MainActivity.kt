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
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.repository.AuthRepository
import com.salmanlaghari.pkai.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
                destination.id == R.id.aiHubFragment ||
                destination.id == R.id.historyFragment ||
                destination.id == R.id.profileFragment) {
                binding.bottomNavigation.visibility = View.VISIBLE
            } else {
                binding.bottomNavigation.visibility = View.GONE
            }
        }

        setupDrawerNavigation()
        setupDrawerHeader()
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
                R.id.nav_chat_history, R.id.nav_mgr_shared -> {
                    if (navController.currentDestination?.id != R.id.historyFragment) {
                        navController.navigate(R.id.historyFragment)
                    }
                    true
                }
                R.id.nav_favorites -> {
                    Toast.makeText(this, "Favorites Feature coming soon!", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_sys_settings -> {
                    if (navController.currentDestination?.id != R.id.settingsFragment) {
                        navController.navigate(R.id.settingsFragment)
                    }
                    true
                }
                R.id.nav_sys_about -> {
                    if (navController.currentDestination?.id != R.id.aboutFragment) {
                        navController.navigate(R.id.aboutFragment)
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
}
