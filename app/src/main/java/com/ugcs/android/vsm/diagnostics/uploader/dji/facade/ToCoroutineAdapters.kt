package com.ugcs.android.vsm.diagnostics.uploader.dji.facade

import dji.common.error.DJIError
import dji.common.error.DJIWaypointV2Error
import dji.common.mission.waypointv2.Action.WaypointV2Action
import dji.common.mission.waypointv2.WaypointV2Mission
import dji.common.util.CommonCallbacks
import dji.sdk.mission.waypoint.WaypointV2MissionOperator
import java.lang.Exception
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.reflect.KFunction1
import kotlin.reflect.KFunction2

class DjiErrorException(djiError: DJIError) : Exception(djiError.description) {
    val errorCode: Int = djiError.errorCode
}

internal suspend inline fun <TError: DJIError> suspendCoroutineCompletion(f: KFunction1<CommonCallbacks.CompletionCallback<TError>?, Unit>) {
    return kotlin.coroutines.suspendCoroutine { continuation ->
        f() { djiError ->
            if (djiError == null) {
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(
                        DjiErrorException(djiError)
                )
            }
        }
    }
}

internal suspend inline fun <T1> suspendCoroutine(f: KFunction2<T1, CommonCallbacks.CompletionCallback<DJIWaypointV2Error>?, Unit>, p0: T1) {
    return kotlin.coroutines.suspendCoroutine { continuation ->
        f(p0) { djiError ->
            if (djiError == null) {
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(
                        DjiErrorException(djiError)
                )
            }
        }
    }
}

suspend fun WaypointV2MissionOperator.loadMission(m: WaypointV2Mission) {
    suspendCoroutine(this::loadMission, m)
}

suspend fun WaypointV2MissionOperator.uploadMission() {
    suspendCoroutineCompletion(this::uploadMission)
}

suspend fun WaypointV2MissionOperator.uploadWaypointActions(actions: List<WaypointV2Action>) {
    suspendCoroutine(this::uploadWaypointActions, actions)
}