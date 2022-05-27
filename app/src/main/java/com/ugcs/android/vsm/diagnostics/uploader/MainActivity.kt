package com.ugcs.android.vsm.diagnostics.uploader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ugcs.android.vsm.diagnostics.uploader.nativeroute.DjiMissionContainer
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.time.ExperimentalTime


class MainActivity : AppCompatActivity() {
    companion object {
        private const val NATIVE_ROUTE_TO_UPLOAD_PATH = "2022_05_23_10_29_32.770.json"
        private val EVENT_FILTER = IntentFilter()
        const val REQUEST_PERMISSION_CODE = 2358

        init {
            DroneBridge.DroneActions.values().forEach { EVENT_FILTER.addAction(it.name) }

        }
    }

    private lateinit var droneBridge: DroneBridge
    private lateinit var broadcastManager: LocalBroadcastManager
    private var log = LinkedList<String>()
    private val eventReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            when (DroneBridge.DroneActions.valueOf(action)) {
                DroneBridge.DroneActions.ON_DRONE_CONNECTED -> {
                    tv_status?.text = ("Drone ready. Press 'Upload'")
                    btn_upload?.isEnabled = true
                    droneBridge.startModelSimulator()
                }
                DroneBridge.DroneActions.ON_DRONE_DISCONNECTED -> {
                    tv_status?.text = ("Drone disconnected. Connect drone")
                    btn_upload?.isEnabled = false
                }
                DroneBridge.DroneActions.STATE_UPDATE -> {
                    val text = intent.getStringExtra("text") ?: return
                    log(text)
                }

            }
        }
    }

    private fun log(e: Throwable) {
        log("Error: $e")
    }

    private fun log(msg: String) {
        if (log.size > 15) {
            log.removeAt(0)
        }
        log.add(msg)
        tv_log?.text = (log.joinToString("\n"))
    }

    @ExperimentalTime
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        droneBridge = DroneBridge(this)
        broadcastManager = LocalBroadcastManager.getInstance(this)
        broadcastManager.registerReceiver(eventReceiver, EVENT_FILTER)
        checkAndRequestAndroidPermissions()
        btn_upload.setOnClickListener(this::btn_upload_onClick)
    }

    @ExperimentalTime
    private fun btn_upload_onClick(view: View) {
        try {
            val json: String = assets.open(NATIVE_ROUTE_TO_UPLOAD_PATH)
                    .bufferedReader(charset("UTF-8"))
                    .use { it.readText() }

            val data = DjiMissionContainer.deserialize(json)

            GlobalScope.launch(Dispatchers.Main) {
                try {
                    data.uploadToVehicle { progress ->
                        log("${progress.stageName}: ${progress.stageProgress}")
                    }
                } catch (e: Throwable) {
                    log(e)
                }
            }
        } catch (e: Throwable) {
            log(e)
        }
    }


    private fun checkAndRequestAndroidPermissions() {
        val missing = PermissionUtils.checkForMissingPermission(applicationContext)
        if (missing.isNotEmpty()) {
            val missingPermissions = missing.toTypedArray()
            ActivityCompat.requestPermissions(this, missingPermissions, REQUEST_PERMISSION_CODE)
        } else {
            onAndroidPermissionsValid()
        }
    }

    private fun onAndroidPermissionsValid() {
        droneBridge.sdkInit()
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            Log.d("MainActivity", "onRequestPermissionsResult...")
            checkAndRequestAndroidPermissions()
        }
    }
}