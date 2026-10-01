package com.roadwatch.fl.fragment

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.roadwatch.fl.MainActivity
import com.roadwatch.fl.R

class SettingsFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var hardwareInterval: Int = 20
    private var samplingInterval: Int = 20

    private lateinit var etHwInterval: TextInputEditText
    private lateinit var etSwInterval: TextInputEditText
    private lateinit var tvHwFreq: TextView
    private lateinit var tvSwFreq: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var tvTestResult: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var testCount = 0
    private var testResults = mutableListOf<Int>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sensorManager = requireContext().getSystemService(SensorManager::class.java)

        etHwInterval = view.findViewById(R.id.et_hw_interval)
        etSwInterval = view.findViewById(R.id.et_sw_interval)
        tvHwFreq = view.findViewById(R.id.tv_hw_freq)
        tvSwFreq = view.findViewById(R.id.tv_sw_freq)
        chipGroup = view.findViewById(R.id.chip_group)
        tvTestResult = view.findViewById(R.id.tv_test_result)

        val service = (activity as? MainActivity)?.getSensorService()
        if (service != null) {
            val hw = service.getHardwareInterval()
            if (hw > 0) hardwareInterval = hw
            val sw = service.getSamplingInterval().toInt()
            if (sw > 0) samplingInterval = sw
        }

        etHwInterval.setText(hardwareInterval.toString())
        etSwInterval.setText(samplingInterval.toString())

        updateFrequencyDisplay()
        setupQuickSelect()
        setupButtons()
    }

    private fun setupQuickSelect() {
        val frequencies = listOf(
            "50 Hz" to 20,
            "100 Hz" to 10,
            "200 Hz" to 5,
            "500 Hz" to 2
        )

        frequencies.forEach { (label, interval) ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                isChecked = hardwareInterval == interval
                setChipBackgroundColorResource(R.color.chip_bg_selector)
                setChipStrokeColorResource(R.color.chip_stroke_selector)
                chipStrokeWidth = resources.displayMetrics.density * 1f
                setTextColor(androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.chip_text_selector))
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        hardwareInterval = interval
                        samplingInterval = interval
                        etHwInterval.setText(interval.toString())
                        etSwInterval.setText(interval.toString())
                        updateFrequencyDisplay()
                        // Uncheck other chips
                        for (i in 0 until chipGroup.childCount) {
                            val other = chipGroup.getChildAt(i) as? Chip
                            if (other != this && other?.isChecked == true) {
                                other.isChecked = false
                            }
                        }
                    }
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun setupButtons() {
        view?.findViewById<MaterialButton>(R.id.btn_apply)?.setOnClickListener {
            val hw = etHwInterval.text.toString().toIntOrNull()
            val sw = etSwInterval.text.toString().toIntOrNull()

            if (hw != null && sw != null && hw in 1..1000 && sw in 1..1000) {
                hardwareInterval = hw
                samplingInterval = sw
                (activity as? MainActivity)?.getSensorService()?.setHardwareInterval(hw)
                (activity as? MainActivity)?.getSensorService()?.setSamplingInterval(sw.toLong())
                updateFrequencyDisplay()
            }
        }

        view?.findViewById<MaterialButton>(R.id.btn_reset)?.setOnClickListener {
            hardwareInterval = 20
            samplingInterval = 20
            etHwInterval.setText("20")
            etSwInterval.setText("20")
            updateFrequencyDisplay()
        }

        view?.findViewById<MaterialButton>(R.id.btn_test)?.setOnClickListener {
            runMaxFrequencyTest()
        }
    }

    private fun updateFrequencyDisplay() {
        val hw = etHwInterval.text.toString().toIntOrNull() ?: hardwareInterval
        val sw = etSwInterval.text.toString().toIntOrNull() ?: samplingInterval
        tvHwFreq.text = "${(1000.0 / hw).format(1)} Hz"
        tvSwFreq.text = "${(1000.0 / sw).format(1)} Hz"
    }

    private fun runMaxFrequencyTest() {
        tvTestResult.text = "Testing..."
        testResults.clear()

        val acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: run {
            tvTestResult.text = "Accelerometer not available"
            return
        }

        // Run 3 tests
        runTest(acc, 0)
    }

    private fun runTest(sensor: Sensor, runIndex: Int) {
        testCount = 0
        sensorManager.registerListener(this, sensor, 1000) // 1ms

        handler.postDelayed({
            sensorManager.unregisterListener(this)
            testResults.add(testCount)

            if (runIndex < 2) {
                // Next run
                handler.postDelayed({
                    runTest(sensor, runIndex + 1)
                }, 200)
            } else {
                // Show results
                val best = testResults.maxOrNull() ?: 0
                val worst = testResults.minOrNull() ?: 0
                val avg = testResults.average().toInt()
                val maxInterval = if (best > 0) Math.max(1, 1000 / best) else 20

                tvTestResult.text = "Best: $best Hz | Avg: $avg Hz | Worst: $worst Hz\nRecommended interval: ${maxInterval}ms ($best Hz)"
            }
        }, 3500) // 3s test + 0.5s warmup
    }

    override fun onSensorChanged(event: SensorEvent) {
        testCount++
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)
}
