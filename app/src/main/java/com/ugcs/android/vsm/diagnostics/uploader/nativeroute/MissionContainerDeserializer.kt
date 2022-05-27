package com.ugcs.android.vsm.diagnostics.uploader.nativeroute

import com.dji.frame.util.V_JsonUtil
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.IllegalArgumentException
import java.lang.reflect.Type

internal class MissionContainerDeserializer: JsonDeserializer<DjiMissionContainer> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): DjiMissionContainer {
        if (json == null)
            throw IllegalArgumentException("The 'json' parameter must not be null.")

        val actions = json.asJsonObject.get("actions")
        if (actions.isJsonNull) {
            return V_JsonUtil.gson.fromJson(
                    json,
                    DjiWaypointMissionContainer::class.java)
        } else {
            return V_JsonUtil.gson.fromJson(
                    json,
                    DjiWaypointV2MissionContainer::class.java)
        }
    }
}