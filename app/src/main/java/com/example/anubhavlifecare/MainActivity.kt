package com.example.anubhavlifecare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.anubhavlifecare.databinding.ActivityMainBinding
import com.example.anubhavlifecare.ui.login.LoginActivity
import com.example.anubhavlifecare.utils.LanguageManager
import com.example.anubhavlifecare.utils.SessionManager

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var languageManager: LanguageManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SessionManager.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        languageManager = LanguageManager(this)

        setSupportActionBar(binding.appBarMain.toolbar)

        setupLanguageToggle()
        setupFabButtons()

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home,
                R.id.nav_book_test,
                R.id.nav_my_bookings,
                R.id.nav_my_reports,
                R.id.nav_profile
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Sales dashboard is only for roles that can view sales (ADMIN / ACCOUNT).
        navView.menu.findItem(R.id.nav_sales)?.isVisible = SessionManager.canViewSales(this)

        navView.setNavigationItemSelectedListener { item ->
            if (item.itemId == R.id.nav_logout) {
                SessionManager.clear(this)
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                true
            } else if (item.itemId == R.id.nav_sales) {
                startActivity(Intent(this, com.example.anubhavlifecare.ui.sales.SalesDashboardActivity::class.java))
                drawerLayout.closeDrawers()
                true
            } else {
                val handled = androidx.navigation.ui.NavigationUI.onNavDestinationSelected(item, navController)
                if (handled) drawerLayout.closeDrawers()
                handled
            }
        }

        updateNavigationHeader()
    }

    private fun setupFabButtons() {
        // Setup booking FAB
        binding.appBarMain.fab.setOnClickListener { view ->
            val message = if (languageManager.isBengali()) {
                "এখনই আপনার টেস্ট বুক করুন!"
            } else {
                "Book your test now!"
            }

            val actionText = if (languageManager.isBengali()) "বুক" else "Book"

            Snackbar.make(view, message, Snackbar.LENGTH_LONG)
                .setAction(actionText) {
                    // Navigate to booking fragment
                    findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_book_test)
                }
                .setAnchorView(R.id.fab).show()
        }

        // Setup WhatsApp FAB
        binding.appBarMain.whatsappFab.setOnClickListener { view ->
            openWhatsAppChat()
        }
    }

    private fun openWhatsAppChat() {
        val phoneNumber = "919230755876" // WhatsApp number without + sign
        val message = if (languageManager.isBengali()) {
            "হ্যালো, আমি অনুভব লাইফ কেয়ার থেকে টেস্ট বুক করতে চাই।"
        } else {
            "Hello, I want to book a test via AKTIV Admin."
        }

        try {
            // Try to open WhatsApp directly
            val whatsappIntent = Intent(Intent.ACTION_VIEW)
            whatsappIntent.data =
                Uri.parse("https://wa.me/$phoneNumber?text=${Uri.encode(message)}")
            startActivity(whatsappIntent)
        } catch (e: Exception) {
            // WhatsApp not installed, try SMS as fallback
            try {
                val smsIntent = Intent(Intent.ACTION_VIEW)
                smsIntent.data = Uri.parse("sms:+$phoneNumber")
                smsIntent.putExtra("sms_body", message)
                startActivity(smsIntent)
            } catch (ex: Exception) {
                // Fallback to dialer
                try {
                    val dialIntent = Intent(Intent.ACTION_DIAL)
                    dialIntent.data = Uri.parse("tel:+$phoneNumber")
                    startActivity(dialIntent)
                } catch (dialEx: Exception) {
                    val errorMessage = if (languageManager.isBengali()) {
                        "দুঃখিত, WhatsApp খোলা যাচ্ছে না। অনুগ্রহ করে +৯১-৯২৩০৭৫৫৮৭৬ নম্বরে কল করুন।"
                    } else {
                        "Sorry, unable to open WhatsApp. Please call +91-9230755876"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupLanguageToggle() {
        updateLanguageToggleButton()
        updateNavigationHeader()

        binding.appBarMain.languageToggleBtn.setOnClickListener {
            val newLanguage = languageManager.toggleLanguage()
            updateLanguageToggleButton()
            updateNavigationHeader()

            val message = if (newLanguage == LanguageManager.LANGUAGE_BENGALI) {
                "Language switched to Bengali / ভাষা বাংলায় পরিবর্তিত হয়েছে"
            } else {
                "Language switched to English / ভাষা ইংরেজিতে পরিবর্তিত হয়েছে"
            }

            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

            // Optionally refresh the current fragment to update text
            refreshCurrentFragment()
        }
    }

    private fun updateLanguageToggleButton() {
        val button = binding.appBarMain.languageToggleBtn

        if (languageManager.isBengali()) {
            // Show "A" in dark blue to switch to English
            button.text = getString(R.string.language_english)
            button.setTextColor(getColor(R.color.brand_dark_blue))
            button.contentDescription = getString(R.string.switch_to_english)
        } else {
            // Show "অ" in dark blue to switch to Bengali
            button.text = getString(R.string.language_bengali)
            button.setTextColor(getColor(R.color.brand_dark_blue))
            button.contentDescription = getString(R.string.switch_to_bengali)
        }
    }

    private fun updateNavigationHeader() {
        val headerView = binding.navView.getHeaderView(0)
        val username = SessionManager.getUsername(this) ?: SessionManager.getUserid(this).orEmpty()
        headerView.findViewById<android.widget.TextView>(R.id.navHeaderUser)?.text =
            getString(R.string.logged_in_as, username)

        headerView.findViewById<android.widget.TextView>(R.id.testsLabel)?.apply {
            text = if (languageManager.isBengali()) {
                "AKTIV বিল"
            } else {
                "AKTIV Bills"
            }
            setTextColor(getColor(R.color.brand_yellow))
        }
        
        headerView.findViewById<android.widget.TextView>(R.id.reportsLabel)?.apply {
            text = if (languageManager.isBengali()) {
                "টেস্ট মোড"
            } else {
                "Test Mode"
            }
            setTextColor(getColor(R.color.brand_dark_blue))
        }
    }

    private fun refreshCurrentFragment() {
        // Recreate the activity to ensure all UI elements including navigation drawer are refreshed
        recreate()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}