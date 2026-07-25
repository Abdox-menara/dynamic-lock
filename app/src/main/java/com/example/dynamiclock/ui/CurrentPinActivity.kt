package com.example.dynamiclock.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.dynamiclock.databinding.ActivityCurrentPinBinding
import com.example.dynamiclock.pin.PinRepository

/** Shows the live PIN. Masked by default; tap to reveal briefly (screenshots blocked). */
class CurrentPinActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCurrentPinBinding
    private lateinit var repo: PinRepository
    private val handler = Handler(Looper.getMainLooper())
    private var revealUntil = 0L

    private val tick = object : Runnable {
        override fun run() {
            val pin = repo.currentPin()
            binding.tvPin.text =
                if (SystemClock.elapsedRealtime() < revealUntil) pin
                else "•".repeat(pin.length.coerceAtLeast(1))
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityCurrentPinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = PinRepository(this)
        binding.tvRule.text = repo.load().describe()
        binding.tvPin.setOnClickListener {
            revealUntil = SystemClock.elapsedRealtime() + 6000
            handler.post(tick)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }
}
