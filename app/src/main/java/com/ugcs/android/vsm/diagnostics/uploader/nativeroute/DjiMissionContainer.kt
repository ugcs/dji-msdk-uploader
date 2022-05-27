package com.ugcs.android.vsm.diagnostics.uploader.nativeroute

import com.google.gson.GsonBuilder
import com.ugcs.android.vsm.diagnostics.uploader.dji.facade.OperationProgressEvent
import com.ugcs.android.vsm.diagnostics.uploader.dji.facade.WaypointV2MissionUploader
import dji.common.mission.waypoint.WaypointMission
import dji.common.mission.waypointv2.Action.WaypointV2Action
import dji.common.mission.waypointv2.WaypointV2Mission
import kotlin.time.ExperimentalTime


sealed class DjiMissionContainer {
    abstract suspend fun uploadToVehicle(progress: OperationProgressEvent? = null)

    companion object {
        @JvmStatic
        fun deserialize(json: String): DjiMissionContainer {
            val builder = GsonBuilder()
                    .registerTypeAdapter(
                            DjiMissionContainer::class.java,
                            MissionContainerDeserializer())
            return builder.create().fromJson(json, DjiMissionContainer::class.java)
        }
    }
}

class DjiWaypointMissionContainer(
        val autopilotModel: Any,
        val actions: List<Any>,
        val mission: WaypointMission
) : DjiMissionContainer() {
    override suspend fun uploadToVehicle(progress: OperationProgressEvent?) {
        TODO("Waypoint Mission uploading is not yet implemented.")
    }
}

class DjiWaypointV2MissionContainer(
        var mission: WaypointV2Mission,
        val actions: List<WaypointV2Action>
) : DjiMissionContainer() {

    @ExperimentalTime
    override suspend fun uploadToVehicle(progress: OperationProgressEvent?) {
        WaypointV2MissionUploader.upload(
                mission,
                actions,
                progress)
    }
}


