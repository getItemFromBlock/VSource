package net.spaceeye.vsource.rendering

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.spaceeye.vsource.ELOG
import net.spaceeye.vsource.rendering.types.PositionDependentRenderingData
import net.spaceeye.vsource.rendering.types.TimedRenderingData
import net.spaceeye.vsource.utils.Vector3d
import net.spaceeye.vsource.utils.getNow_ms
import org.valkyrienskies.mod.common.shipObjectWorld
import java.util.concurrent.ConcurrentSkipListSet

object ReservedRenderingPages {
    const val TimedRenderingObjects = -1L
}

fun renderInWorld(poseStack: PoseStack, camera: Camera, minecraft: Minecraft) {
    minecraft.profiler.push("vsource_rendering_ship_objects")
    renderShipObjects(poseStack, camera)
    minecraft.profiler.pop()

    minecraft.profiler.push("vsource_rendering_timed_objects")
    renderTimedObjects(poseStack, camera)
    minecraft.profiler.pop()
}

private inline fun renderShipObjects(poseStack: PoseStack, camera: Camera) {
    val level = Minecraft.getInstance().level!!
    SynchronisedRenderingData.clientSynchronisedData.mergeData()

    try {
    for (ship in level.shipObjectWorld.loadedShips) {
        SynchronisedRenderingData.clientSynchronisedData.tryPoolDataUpdate(ship.id)
        for ((idx, render) in SynchronisedRenderingData.clientSynchronisedData.cachedData[ship.id] ?: continue) {
            render.renderData(poseStack, camera)
        }
    }
    // let's hope that it never happens, but if it does, then do nothing
    } catch (e: ConcurrentModificationException) { ELOG("GOT ConcurrentModificationException WHILE RENDERING.\n${e.stackTraceToString()}"); }
}

private inline fun renderTimedObjects(poseStack: PoseStack, camera: Camera) {
    SynchronisedRenderingData.clientSynchronisedData.tryPoolDataUpdate(ReservedRenderingPages.TimedRenderingObjects)
    val cpos = Vector3d(Minecraft.getInstance().player!!.position())
    val now = getNow_ms()
    val toDelete = mutableListOf<Int>()
    val page = SynchronisedRenderingData.clientSynchronisedData.cachedData[ReservedRenderingPages.TimedRenderingObjects] ?: return
    for ((idx, render) in page) {
        if (render !is TimedRenderingData || render !is PositionDependentRenderingData) { toDelete.add(idx); ELOG("FOUND RENDERING DATA ${render.javaClass.simpleName} IN renderTimedObjects THAT DIDN'T IMPLEMENT INTERFACE TimedRenderingData OR PositionDependentRenderingData."); continue }
        if (!render.wasActivated && render.activeFor_ms == -1L) { render.timestampOfBeginning = now }
        if (render.activeFor_ms + render.timestampOfBeginning < now) { toDelete.add(idx); continue }
        if ((render.renderingPosition - cpos).sqrDist() > render.renderingArea*render.renderingArea) { continue }

        render.wasActivated = true
        render.renderData(poseStack, camera)
    }

    SynchronisedRenderingData.clientSynchronisedData.pageIndicesToRemove.getOrPut(ReservedRenderingPages.TimedRenderingObjects) { ConcurrentSkipListSet(toDelete) }
}