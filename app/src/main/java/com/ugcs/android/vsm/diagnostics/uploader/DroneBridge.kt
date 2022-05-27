package com.ugcs.android.vsm.diagnostics.uploader

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.dji.frame.util.V_JsonUtil
import com.google.gson.reflect.TypeToken
import dji.common.error.DJIError
import dji.common.error.DJISDKError
import dji.common.error.DJIWaypointV2Error
import dji.common.flightcontroller.simulator.InitializationData
import dji.common.mission.waypoint.*
import dji.common.mission.waypointv2.Action.*
import dji.common.mission.waypointv2.WaypointV2Mission
import dji.common.model.LocationCoordinate2D
import dji.common.product.Model
import dji.common.util.CommonCallbacks.CompletionCallbackWith
import dji.keysdk.KeyManager
import dji.keysdk.ProductKey
import dji.keysdk.callback.KeyListener
import dji.sdk.base.BaseComponent
import dji.sdk.base.BaseProduct
import dji.sdk.base.BaseProduct.ComponentKey
import dji.sdk.base.DJIDiagnostics
import dji.sdk.mission.MissionControl
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener
import dji.sdk.mission.waypoint.WaypointV2ActionListener
import dji.sdk.products.Aircraft
import dji.sdk.sdkmanager.DJISDKInitEvent
import dji.sdk.sdkmanager.DJISDKManager
import dji.sdk.sdkmanager.DJISDKManager.SDKManagerCallback
import kotlinx.coroutines.*
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.atomic.AtomicReference


class DroneBridge(private val context: Context) {
    val manager = DJISDKManager.getInstance()
    private val connectionKey = ProductKey.create(ProductKey.CONNECTION)
    private val connectionKeyListener =
        KeyListener { oldValue: Any?, newValue: Any? -> onConnectionChanged(newValue) }
    var permissionCheckResult: PermissionCheckResult? = null
    private var aircraft: Aircraft? = null
    
    class PermissionCheckResult {
        var permissionCheckResult: String? = null
        var permissionCheckResultDesc: String? = null
    }
    val job = SupervisorJob()                               // (1)
    val scope = CoroutineScope(Dispatchers.Default + job)
    fun sdkInit() {
        scope.launch {
            Log.i("DroneBridge","DJISDKManager -> registerApp...")
            sendNotif("DJISDKManager -> registerApp..")
            Log.i("DroneBridge","DJISDKManager -> SDK VERSION = ${DJISDKManager.getInstance().sdkVersion}")
            if (DJISDKManager.getInstance().hasSDKRegistered()) {
                //sendNotif("DJISDKManager -> already registered");
                //Log.i("DroneBridge","DJISDKManager hasSDKRegistered.");
                //return;
            }
            permissionCheckResult = null
            DJISDKManager.getInstance().registerApp(context, object : SDKManagerCallback {
                override fun onRegister(djiError: DJIError) {
                    this@DroneBridge.onRegister(djiError)
                }

                override fun onProductDisconnect() {
                    this@DroneBridge.onProductDisconnect()
                }

                override fun onProductConnect(baseProduct: BaseProduct) {
                    this@DroneBridge.onProductConnect(baseProduct)
                }

                override fun onProductChanged(baseProduct: BaseProduct) {}
                override fun onComponentChange(
                    key: ComponentKey?, oldComponent: BaseComponent?,
                    newComponent: BaseComponent?
                                              ) {
                    onComponentChanged(key, oldComponent, newComponent)
                }

                override fun onInitProcess(djisdkInitEvent: DJISDKInitEvent, i: Int) {}
                override fun onDatabaseDownloadProgress(currentSize: Long, totalSize: Long) {}
            })
        }
    }

    /**
     * Callback method after the application attempts to register.
     *
     *
     * in method initSdkManager (see above) we are passing sdkManagerCallback
     */
    private fun onRegister(registrationResult: DJIError?) {
        scope.launch {
            val pc = PermissionCheckResult()
            var startConnect = false
            if (registrationResult == null || registrationResult === DJISDKError.REGISTRATION_SUCCESS) {
                sendNotif("DJISDKManager -> onRegister OK")
                Log.i("DroneBridge","DJISDKManager -> onRegister OK")
                pc.permissionCheckResult = DJISDKError.REGISTRATION_SUCCESS.toString()
                pc.permissionCheckResultDesc = DJISDKError.REGISTRATION_SUCCESS.description
                startConnect = true
            } else {
                pc.permissionCheckResult = registrationResult.toString()
                pc.permissionCheckResultDesc = registrationResult.description
                sendNotif(
                    String.format(
                        "DJISDKManager -> onRegister ERROR = %s (%s)",
                        pc.permissionCheckResult, pc.permissionCheckResultDesc
                                 )
                                           )
                Log.w(
                    "DroneBridge",
                    "DJISDKManager -> onRegister ERROR = ${pc.permissionCheckResult} (${pc.permissionCheckResultDesc})")
            }
            permissionCheckResult = pc
            if (startConnect) {
                startConnectionToProduct()
            }
        }
        if (registrationResult == null) {
            Log.i("DroneBridge","DJI SDK is initialised")
        } else {
            Log.e("DroneBridge","DJI SDK initialisation failure. Error: ${registrationResult.description}")
        }
    }

    private fun onProductDisconnect() {
        Log.d("DroneBridge","onProductDisconnect")
    }

    private fun onProductConnect(baseProduct: BaseProduct) {
        val af = baseProduct as Aircraft
        af.flightController.getSerialNumber(object : CompletionCallbackWith<String> {
            override fun onSuccess(s: String) {
                scope.launch {
                    delay(2000)
                    initDrone(s)
                }
            }

            override fun onFailure(djiError: DJIError) {
                sendNotif(
                    String.format(
                        "getSerialNumber method - error: %s",
                        djiError.description
                                 )
                                           )
                Log.i("DroneBridge","getSerialNumber method - error: ${djiError.description}")
            }
        })
        Log.d("DroneBridge","onProductConnect")
    }

    /**
     * Starts a connection between the SDK and the DJI product.
     * This method should be called after successful registration of the app and once there is a data connection
     * between the mobile device and DJI product.
     */
    private fun startConnectionToProduct() {
        scope.launch {
            sendNotif("DJISDKManager -> startConnectionToProduct...")
            Log.i("DroneBridge","DJISDKManager -> startConnectionToProduct...")
            val km = KeyManager.getInstance()
            km.addListener(connectionKey, connectionKeyListener)
            DJISDKManager.getInstance().startConnectionToProduct()
        }
    }

    private fun onConnectionChanged(newValue: Any?) {
        if (newValue is Boolean) {
            scope.launch {
                if (newValue) {
                    Log.i("DroneBridge","KeyManager -> onConnectionChanged = true")
                    // We will not do anything, we will wait for serial number update.
                } else {
                    Log.w("DroneBridge","KeyManager -> onConnectionChanged = false")
                    disconnectDrone()
                }
            }
        }
    }


    // ProductKey.COMPONENT_KEY - Not works in SDK 4.3.2, so this method is invoked vis baseProductListener
    private fun onComponentChanged(
        key: ComponentKey?, oldComponent: BaseComponent?,
        newComponent: BaseComponent?
                                  ) {
    }

    private fun initDrone(serial: String) {
        Log.i("DroneBridge","OK, starting drone init...")
        val mProduct = DJISDKManager.getInstance().product as? Aircraft
        aircraft = mProduct
        val missionOperator = MissionControl.getInstance().waypointMissionOperator
        val missionOperator2 = MissionControl.getInstance().waypointMissionV2Operator
    
        if (mProduct != null) {
            Log.i("DroneBridge", "aircraft connected ${mProduct.model.displayName}")
            sendNotif(
                String.format(
                    "aircraft connected %s",
                    mProduct.model.displayName
                             )
                     )
            mProduct.setDiagnosticsInformationCallback { list: List<DJIDiagnostics> ->
                synchronized(this) {
                    for (message in list) {
                        Log.i(
                            "DroneBridge",
                            "DJIDiagnostics: ${message.code} - ${message.reason}: ${message.solution}"
                             )
                    }
                }
            }
            val intent = Intent()
            intent.action = DroneActions.ON_DRONE_CONNECTED.toString()
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
            if (mProduct != null) {
                missionOperator.addListener(waypointMissionOperatorListener)
                if (isWp2()) {
                    missionOperator2?.addActionListener(waypointV2ActionListener)
                }
            }
        }
        else {
            missionOperator.removeListener(waypointMissionOperatorListener)
            missionOperator2?.removeActionListener(waypointV2ActionListener)
        }
    }
    private fun sendNotif (text : String) {
        val intent = Intent()
        intent.action = DroneActions.STATE_UPDATE.toString()
        intent.putExtra("text", text)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    private fun disconnectDrone() {
        // Drone is disconnected
        // 1. cancel initialization future if it is scheduled
        val mProduct = DJISDKManager.getInstance().product
        Log.i("DroneBridge","aircraft disconnected")
        Log.d("DroneBridge","Drone connection lost, in DJISDKManager BaseProduct ${if (mProduct == null) "NULL" else "EXISTS"}")
        aircraft = null
        val intent = Intent()
        intent.action = DroneActions.ON_DRONE_DISCONNECTED.toString()
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
    
    fun startModelSimulator() {
        scope.launch {
            aircraft!!.flightController.simulator.start(InitializationData.createInstance(
                LocationCoordinate2D(BASE_LATITUDE, BASE_LONGITUDE),
                REFRESH_FREQ,
                SATELLITE_COUNT
                                                                                         )
                                                       ) { djiError ->
                val result =
                    if (djiError == null) "Simulator started" else djiError.description
                Log.i("DroneBridge",result)
                sendNotif(result)
            }
        }
    }
    fun isWp2() = aircraft?.model == Model.MATRICE_300_RTK
    
    companion object {
        private const val BASE_LATITUDE = 56.8627672
        private const val BASE_LONGITUDE = 24.1133272
        private const val REFRESH_FREQ = 10
        private const val SATELLITE_COUNT = 10
    }
    enum class DroneActions {
        ON_DRONE_CONNECTED,
        ON_DRONE_DISCONNECTED,
        STATE_UPDATE
        
    }
    
    private val waypointMissionOperatorListener: WaypointMissionOperatorListener = object : WaypointMissionOperatorListener {
        override fun onDownloadUpdate(waypointMissionDownloadEvent: WaypointMissionDownloadEvent) {}
        override fun onUploadUpdate(waypointMissionState: WaypointMissionUploadEvent) {
            val up = waypointMissionState.progress
            val currentState = waypointMissionState.currentState
            if (currentState === WaypointMissionState.UPLOADING) {
                if (up != null) {
                    val index = up.uploadedWaypointIndex + 1
                    val size = up.totalWaypointCount
                    val progress = index.toFloat() / size
                    Log.i("DroneBridge",String.format("MissionUploadProgress = %3.3f (%d/%d)", progress, index, size))
                }
            }
        }
        
        override fun onExecutionUpdate(waypointMissionExecutionEvent: WaypointMissionExecutionEvent) {
            val progress = waypointMissionExecutionEvent.progress
            val tp = progress!!.targetWaypointIndex
            val reached = progress.isWaypointReached
            val state = progress.executeState
            val total = progress.totalWaypointCount
            sendNotif(String.format("WP running. Completed  %d / %d", tp, total))
        }
        
        override fun onExecutionStart() {
            sendNotif("WP - Exec start")
        }
        
        override fun onExecutionFinish(djiError: DJIError?) {
            sendNotif("WP - Exec Finish")
        }
    }
    private var missionActionsAwaitingUpload: List<WaypointV2Action>? = null
    private val waypointV2ActionListener: WaypointV2ActionListener = object : WaypointV2ActionListener {
        override fun onDownloadUpdate(@NotNull actionDownloadEvent: ActionDownloadEvent) {}
        override fun onUploadUpdate(@NotNull actionUploadEvent: ActionUploadEvent) {
            val currentState = actionUploadEvent.currentState
            uploadActionsV2(currentState)
        }
        
        override fun onExecutionUpdate(@NotNull actionExecutionEvent: ActionExecutionEvent) {}
        override fun onExecutionStart(i: Int) {}
        override fun onExecutionFinish(i: Int, @Nullable djiWaypointV2Error: DJIWaypointV2Error?) {}
    }
    
    private fun uploadMissionV1(mission: WaypointMission) {
        var state = MissionControl.getInstance().waypointMissionOperator.currentState
        MissionControl.getInstance().waypointMissionOperator.clearMission()
        
        val waypointMissionOperator = MissionControl.getInstance().waypointMissionOperator
        aircraft?.flightController?.getHomeLocation(object : CompletionCallbackWith<LocationCoordinate2D> {
            override fun onSuccess(locationCoordinate2D: LocationCoordinate2D) {
                Log.i("DroneBridge","Drone location $locationCoordinate2D")
            }
            
            override fun onFailure(djiError: DJIError) {
                Log.i("DroneBridge", "Drone location is unavailable due to ${djiError.description}")
            }
        })
        Log.i("DroneBridge", "Mission Upload - Start (state = ${state.name})")
        sendNotif("Mission upload started")
        val e = waypointMissionOperator.loadMission(mission)
        state = waypointMissionOperator.currentState
        if (e != null) {
            Log.i("DroneBridge","Mission Upload - WaypointMissionOperator - loadMission FAILED (state = ${state.name}) as ${e.description}")
            sendNotif("Error. ${e.errorCode}")
        }
        else {
            Log.i("DroneBridge","Mission Upload - WaypointMissionOperator - uploadMission... (state = ${state.name})")
            waypointMissionOperator.uploadMission { djiError: DJIError? ->
                if (djiError == null) {
                    sendNotif("Mission upload success")
                    Log.i("DroneBridge","Mission Upload - OK - error is null")
                }
                else {
                    Log.e("DroneBridge", "Mission Upload - FAILED - djiError: ${djiError.description}")
                    sendNotif("Error. ${djiError.errorCode}")
                }
            }
        }
    }
    private fun uploadActionsV2(currentState: ActionState) {
        if (currentState == ActionState.READY_TO_UPLOAD
            && missionActionsAwaitingUpload != null
            && missionActionsAwaitingUpload!!.isNotEmpty()) {
            val waypointV2MissionOperator = MissionControl.getInstance().waypointMissionV2Operator
            waypointV2MissionOperator!!.uploadWaypointActions(missionActionsAwaitingUpload) { djiWaypointV2ErrorActions: DJIWaypointV2Error? ->
                if (djiWaypointV2ErrorActions != null) {
                    Log.e("DroneBridge", "Action upload failed ${djiWaypointV2ErrorActions.description}")
                    sendNotif("Error. ${djiWaypointV2ErrorActions.errorCode}")
                }
                else {
                    missionActionsAwaitingUpload = null
                    Log.i("DroneBridge", "Action upload success")
                    sendNotif("Success")
                }
            }
        }
    }
    
}