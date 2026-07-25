package com.example.dynamiclock.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dynamiclock.R
import com.example.dynamiclock.databinding.ActivityMainBinding
import com.example.dynamiclock.locker.LockedAppsActivity
import com.example.dynamiclock.pin.PinRepository

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: PinRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = PinRepository(this)

        binding.btnVault.setOnClickListener {
            startActivity(UnlockActivity.forVault(this))
        }
        binding.btnApps.setOnClickListener {
            startActivity(Intent(this, LockedAppsActivity::class.java))
        }
        binding.btnConfigure.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnPin.setOnClickListener {
            startActivity(Intent(this, CurrentPinActivity::class.java))
        }

        // v5: Show dialer code in a hint
        binding.tvDialerHint.text = getString(R.string.dialer_code)
        binding.tvDialerHint.setOnClickListener {
            Toast.makeText(this, getString(R.string.dialer_code), Toast.LENGTH_LONG).show()
        }

        // Launcher app-shortcut routing
        when (intent?.action) {
            "com.example.dynamiclock.OPEN_VAULT" -> startActivity(UnlockActivity.forVault(this))
            "com.example.dynamiclock.SHOW_PIN" -> startActivity(Intent(this, CurrentPinActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        binding.tvRule.text = repo.load().describe()
    }
}
