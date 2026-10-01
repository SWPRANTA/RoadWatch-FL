package com.roadwatch.fl

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.roadwatch.fl.fragment.HomeFragment
import com.roadwatch.fl.service.SensorService

class MainActivity : AppCompatActivity() {

    private var sensorService: SensorService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SensorService.LocalBinder
            val s = binder.getService()
            sensorService = s
            isBound = true

            // Connect service callbacks to HomeFragment
            setupServiceCallbacks()

            // Synchronize immediate state with currently active HomeFragment
            runOnUiThread {
                getVisibleHomeFragment()?.onRecordingStateChanged(s.isRecording(), s.isPaused())
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sensorService = null
            isBound = false
        }
    }

    private fun getVisibleHomeFragment(): HomeFragment? {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        return navHostFragment?.childFragmentManager?.fragments?.firstOrNull { it is HomeFragment && it.isVisible } as? HomeFragment
            ?: navHostFragment?.childFragmentManager?.fragments?.firstOrNull() as? HomeFragment
    }

    private fun setupServiceCallbacks() {
        sensorService?.onDataUpdate = { data ->
            getVisibleHomeFragment()?.updateSensorData(data)
        }

        sensorService?.onStateChange = { recording, paused ->
            runOnUiThread {
                getVisibleHomeFragment()?.onRecordingStateChanged(recording, paused)
            }
        }

        sensorService?.onRecordingStopped = { file ->
            runOnUiThread {
                android.widget.Toast.makeText(
                    this,
                    "Data saved to ${file.name}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                getVisibleHomeFragment()?.onRecordingStateChanged(false, false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)

        // Bind to SensorService
        Intent(this, SensorService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun getSensorService(): SensorService? = sensorService

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
