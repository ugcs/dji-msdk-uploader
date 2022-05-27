package com.ugcs.android.vsm.diagnostics.uploader.dji.facade

import dji.common.error.DJIWaypointV2Error
import dji.common.mission.waypointv2.*
import dji.common.mission.waypointv2.Action.*
import dji.common.mission.waypointv2.WaypointV2MissionState.READY_TO_EXECUTE
import dji.common.mission.waypointv2.WaypointV2MissionState.READY_TO_UPLOAD
import dji.sdk.mission.MissionControl
import dji.sdk.mission.waypoint.WaypointV2ActionListener
import dji.sdk.mission.waypoint.WaypointV2MissionOperator
import dji.sdk.mission.waypoint.WaypointV2MissionOperatorListener
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.lang.IllegalStateException
import java.lang.RuntimeException
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * Implements mission uploading via WaypointV2 DJI API.
 */
class WaypointV2MissionUploader {
    private class MissionUploadingStateListener(private val publisher: WaypointV2MissionOperator) : Closeable {
        private val stateChangeEvents = Channel<WaypointV2MissionUploadEvent>()
        private var progressHandler: ((Double) -> Unit)? = null


        private val waypointEventListener = object : WaypointV2MissionOperatorListener {
            override fun onDownloadUpdate(p0: WaypointV2MissionDownloadEvent) {
            }

            override fun onUploadUpdate(args: WaypointV2MissionUploadEvent) {
                GlobalScope.launch {
                    stateChangeEvents.send(args)
                }
                progressHandler?.let { handler ->
                    args.progress?.let { djiProgress ->
                        if (djiProgress.totalWaypointCount > 0)
                            handler((djiProgress.lastUploadedWaypointIndex + 1) / djiProgress.totalWaypointCount.toDouble())
                    }
                }
            }

            override fun onExecutionUpdate(p0: WaypointV2MissionExecutionEvent) {
            }

            override fun onExecutionStart() {
            }

            override fun onExecutionFinish(p0: DJIWaypointV2Error?) {
            }

            override fun onExecutionStopped() {
            }
        }

        init {
            publisher.addWaypointEventListener(waypointEventListener)
        }


        /**
         * The operation throws a [kotlinx.coroutines.TimeoutCancellationException] if
         * there is no any mission uploading event during [heartbeatTimeout].
         */
        @ExperimentalTime
        suspend fun waitFor(targetState: WaypointV2MissionState, heartbeatTimeout: Duration = Duration.seconds(30)) {
            if (publisher.currentState == targetState)
                return

            while (true) {
                val eventArgs = withTimeout(heartbeatTimeout) {
                    stateChangeEvents.receive()
                }

                if (eventArgs.currentState == targetState)
                    break

                eventArgs.error?.let { err ->
                    throw RuntimeException("DJI Error ${err.errorCode}: ${err.description}")
                }

                // Wait until state becomes READY_TO_EXECUTE
            }
        }

        override fun close() {
            publisher.removeWaypointListener(waypointEventListener)
        }

        fun whenProgress(handler: (Double) -> Unit) {
            progressHandler = handler
        }
    }


    private class ActionsUploadingStateListener(private val publisher: WaypointV2MissionOperator) : Closeable {
        private var progressHandler: ((Double) -> Unit)? = null
        private val actionStateEvents = Channel<ActionUploadEvent>()
        private val actionStateListener = object : WaypointV2ActionListener {
            override fun onDownloadUpdate(p0: ActionDownloadEvent) {
            }

            override fun onUploadUpdate(args: ActionUploadEvent) {
                GlobalScope.launch {
                    actionStateEvents.send(args)
                }
                progressHandler?.let { handler ->
                    args.progress?.let { djiProgress ->
                        if (djiProgress.totalActionCount > 0)
                            handler((djiProgress.lastUploadedWaypointIndex + 1) / djiProgress.totalActionCount.toDouble())
                    }
                }
            }

            override fun onExecutionUpdate(p0: ActionExecutionEvent) {
            }

            override fun onExecutionStart(p0: Int) {
            }

            override fun onExecutionFinish(p0: Int, p1: DJIWaypointV2Error?) {
            }
        }


        init {
            publisher.addActionListener(actionStateListener)
        }

        @ExperimentalTime
        suspend fun waitFor(target: ActionState, heartbeatTimeout: Duration = Duration.seconds(30)) {
            withTimeout(heartbeatTimeout) {
                while (true) {
                    val eventArgs = actionStateEvents.receive()

                    if (eventArgs.currentState == target)
                        break

                    eventArgs.error?.let { err ->
                        throw RuntimeException("DJI Error ${err.errorCode}: ${err.description}")
                    }

                    // Wait until state becomes READY_TO_EXECUTE
                }
            }
        }

        fun whenProgress(handler: (Double) -> Unit) {
            progressHandler = handler
        }

        override fun close() {
            publisher.removeActionListener(actionStateListener)
        }
    }


    companion object {

        private const val UPLOADING_STAGE_WAYPOINTS = "Uploading waypoints"
        private const val UPLOADING_STAGE_ACTIONS = "Uploading actions"


        @ExperimentalTime
        @JvmStatic
        suspend fun upload(mission: WaypointV2Mission, actions: List<WaypointV2Action>?,
                           onProgress: OperationProgressEvent? = null) {
            val missionOperator: WaypointV2MissionOperator =
                    checkNotNull(MissionControl.getInstance().waypointMissionV2Operator) {
                        "Looks like there is no vehicle connected or the vehicle " +
                                "doesn't support waypoint V2 missions."
                    }

            missionOperator.currentState.let { currentState ->
                if ( currentState !in setOf(READY_TO_UPLOAD, READY_TO_EXECUTE))
                    throw IllegalStateException("The mission can be loaded only when the operator " +
                            "state is READY_TO_UPLOAD or READY_TO_EXECUTE. Current state is ${currentState}.")
            }

            MissionUploadingStateListener(missionOperator).use { waypointsListener ->
                if (onProgress != null) {
                    waypointsListener.whenProgress { progress ->
                        onProgress(
                                OperationProgress(UPLOADING_STAGE_WAYPOINTS, progress))
                    }
                }
                missionOperator.loadMission(mission)
                waypointsListener.waitFor(READY_TO_UPLOAD)

                missionOperator.uploadMission()
                waypointsListener.waitFor(READY_TO_EXECUTE)

                if (actions != null && actions.isNotEmpty()) {
                    ActionsUploadingStateListener(missionOperator).use { actionsListener ->
                        if (onProgress != null) {
                            actionsListener.whenProgress { progress ->
                                onProgress(
                                        OperationProgress(UPLOADING_STAGE_ACTIONS, progress))
                            }
                        }

                        missionOperator.uploadWaypointActions(actions)
                        actionsListener.waitFor(ActionState.READY_TO_EXECUTE)
                    }
                }
            }
        }
    }
}