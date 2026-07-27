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
        setup3DDrawerEffect()
    }

    private fun setup3DDrawerEffect() {
        // Transparent scrim allows the premium 3D layered sliding card to shine
        binding.drawerLayout.setScrimColor(android.graphics.Color.TRANSPARENT)
        binding.drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                // Scale the main content view card down (from 1.0f to 0.85f) as slideOffset goes from 0.0 to 1.0
                val scaleFactor = 1f - (slideOffset * 0.15f)
                binding.mainContent.scaleX = scaleFactor
                binding.mainContent.scaleY = scaleFactor

                // Translate the card horizontally
                val xOffset = drawerView.width * slideOffset
                val xTranslation = xOffset * 0.75f // overlap factor for beautiful 3D layering
                binding.mainContent.translationX = xTranslation

                // Dynamically apply rounded corner radius (target: 28dp)
                val density = resources.displayMetrics.density
                val targetRadius = 28f * density
                binding.mainContent.radius = slideOffset * targetRadius

                // Dynamically apply card elevation shadow depth (target: 16dp)
                val targetElevation = 16f * density
                binding.mainContent.cardElevation = slideOffset * targetElevation
            }
        })
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
                        // Since login/guest is removed, navigate directly to homeFragment on logout
                        navController.navigate(R.id.homeFragment)
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
