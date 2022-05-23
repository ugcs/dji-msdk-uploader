package com.ugcs.android.vsm.diagnostics.uploader

import dji.common.mission.waypoint.WaypointMission
import dji.common.mission.waypointv2.Action.WaypointV2Action
import dji.common.mission.waypointv2.WaypointV2Mission

data class RouteModelV1(
        val actions: List<Any>?,
        val autopilotModel: Any?,
        val mission: WaypointMission?)

data class RouteModelV2(val autopilotModel: Any,
                        val mission: WaypointV2Mission,
                        val actions: List<WaypointV2Action>?)