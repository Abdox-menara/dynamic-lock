package com.example.dynamiclock.locker

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.dynamiclock.R

/**
 * v5: Configuration screen for lock scheduling.
 * Allows the user to set a time window per app for auto-unlock.
 */
class ScheduleConfigActivity : AppCompatActivity() {

    private var packageName: String = ""
    private var isNew: Boolean = true
    private lateinit var timePickerStart: TimePicker
    private lateinit var timePickerEnd: TimePicker
    private lateinit var switchEnabled: Switch
    private lateinit var etPackageName: AutoCompleteTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_config)

        packageName = intent.getStringExtra("package") ?: ""
        isNew = packageName.isEmpty()

        etPackageName = findViewById(R.id.etPackageName)
        timePickerStart = findViewById(R.id.timePickerStart)
        timePickerEnd = findViewById(R.id.timePickerEnd)
        switchEnabled = findViewById(R.id.switchEnabled)

        val btnSave = findViewById<Button>(R.id.btnSaveSchedule)
        val btnDelete = findViewById<Button>(R.id.btnDelete)

        if (!isNew) {
            val schedule = LockScheduler.load(this, packageName)
            etPackageName.setText(schedule.packageName)
            etPackageName.isEnabled = false
            timePickerStart.hour = schedule.startHour
            timePickerStart.minute = 0
            timePickerEnd.hour = schedule.endHour
            timePickerEnd.minute = 0
            switchEnabled.isChecked = schedule.enabled
            btnDelete.visibility = android.view.View.VISIBLE
        } else {
            btnDelete.visibility = android.view.View.GONE
        }

        btnSave.setOnClickListener {
            val pkg = etPackageName.text.toString().trim()
            if (pkg.isEmpty()) {
                Toast.makeText(this, "Package name required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val schedule = AppSchedule(
                packageName = pkg,
                enabled = switchEnabled.isChecked,
                startHour = timePickerStart.hour,
                endHour = timePickerEnd.hour
            )
            LockScheduler.save(this, schedule)
            finish()
        }

        btnDelete.setOnClickListener {
            LockScheduler.delete(this, packageName)
            finish()
        }
    }
}
