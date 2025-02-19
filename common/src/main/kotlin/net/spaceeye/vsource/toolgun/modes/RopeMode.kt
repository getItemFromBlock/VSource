package net.spaceeye.vsource.toolgun.modes

import dev.architectury.event.EventResult
import dev.architectury.networking.NetworkManager
import gg.essential.elementa.components.UIBlock
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.spaceeye.vsource.ILOG
import net.spaceeye.vsource.gui.makeTextEntry
import net.spaceeye.vsource.networking.C2SConnection
import net.spaceeye.vsource.rendering.SynchronisedRenderingData
import net.spaceeye.vsource.rendering.types.RopeRenderer
import net.spaceeye.vsource.utils.RaycastFunctions
import net.spaceeye.vsource.constraintsSaving.makeManagedConstraint
import net.spaceeye.vsource.gui.DItem
import net.spaceeye.vsource.gui.makeDropDown
import net.spaceeye.vsource.translate.GUIComponents
import net.spaceeye.vsource.translate.GUIComponents.COMPLIANCE
import net.spaceeye.vsource.translate.GUIComponents.FIXED_DISTANCE
import net.spaceeye.vsource.translate.GUIComponents.MAX_FORCE
import net.spaceeye.vsource.translate.GUIComponents.ROPE
import net.spaceeye.vsource.translate.GUIComponents.SEGMENTS
import net.spaceeye.vsource.translate.GUIComponents.WIDTH
import net.spaceeye.vsource.translate.get
import org.lwjgl.glfw.GLFW
import org.valkyrienskies.core.apigame.constraints.VSRopeConstraint

class RopeMode : BaseMode {
    var compliance = 1e-10
    var maxForce = 1e10
    var fixedDistance = -1.0

    var posMode = PositionModes.NORMAL

    var width: Double = .2
    var segments: Int = 16

    override fun handleKeyEvent(key: Int, scancode: Int, action: Int, mods: Int): EventResult {
        return EventResult.pass()
    }

    override fun handleMouseButtonEvent(button: Int, action: Int, mods: Int): EventResult {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
            conn_primary.sendToServer(this)
        }

        return EventResult.interruptTrue()
    }

    override fun serialize(): FriendlyByteBuf {
        val buf = getBuffer()

        buf.writeDouble(compliance)
        buf.writeDouble(maxForce)
        buf.writeDouble(fixedDistance)
        buf.writeEnum(posMode)
        buf.writeDouble(width)
        buf.writeInt(segments)

        return buf
    }

    override fun deserialize(buf: FriendlyByteBuf) {
        compliance = buf.readDouble()
        maxForce = buf.readDouble()
        fixedDistance = buf.readDouble()
        posMode = buf.readEnum(posMode.javaClass)
        width = buf.readDouble()
        segments = buf.readInt()
    }

    override val itemName = ROPE
    override fun makeGUISettings(parentWindow: UIBlock) {
        val offset = 2.0f

        makeTextEntry(COMPLIANCE.get(),     ::compliance,    offset, offset, parentWindow, 0.0)
        makeTextEntry(MAX_FORCE.get(),      ::maxForce,      offset, offset, parentWindow, 0.0)
        makeTextEntry(FIXED_DISTANCE.get(), ::fixedDistance, offset, offset, parentWindow)
        makeTextEntry(WIDTH.get(),          ::width,         offset, offset, parentWindow, 0.0, 1.0)
        makeTextEntry(SEGMENTS.get(),       ::segments,      offset, offset, parentWindow, 1, 100)
        makeDropDown(GUIComponents.HITPOS_MODES.get(), parentWindow, offset, offset, listOf(
            DItem(GUIComponents.NORMAL.get(),            posMode == PositionModes.NORMAL)            { posMode = PositionModes.NORMAL },
            DItem(GUIComponents.CENTERED_ON_SIDE.get(),  posMode == PositionModes.CENTERED_ON_SIDE)  { posMode = PositionModes.CENTERED_ON_SIDE },
            DItem(GUIComponents.CENTERED_IN_BLOCK.get(), posMode == PositionModes.CENTERED_IN_BLOCK) { posMode = PositionModes.CENTERED_IN_BLOCK },
        ))
    }

    val conn_primary = register { object : C2SConnection<RopeMode>("rope_mode_primary", "toolgun_command") { override fun serverHandler(buf: FriendlyByteBuf, context: NetworkManager.PacketContext) = serverRaycastAndActivate<RopeMode>(context.player, buf, ::RopeMode, ::activatePrimaryFunction) } }

    var previousResult: RaycastFunctions.RaycastResult? = null

    fun activatePrimaryFunction(level: Level, player: Player, raycastResult: RaycastFunctions.RaycastResult) = serverTryActivateFunction(posMode, level, raycastResult, ::previousResult, ::resetState) {
        level, shipId1, shipId2, ship1, ship2, spoint1, spoint2, rpoint1, rpoint2 ->

        val dist = if (fixedDistance > 0) {fixedDistance} else {(rpoint1 - rpoint2).dist()}

        val constraint = VSRopeConstraint(
            shipId1, shipId2,
            1e-10,
            spoint1.toJomlVector3d(), spoint2.toJomlVector3d(),
            1e10,
            dist
        )

        val id = level.makeManagedConstraint(constraint)

        SynchronisedRenderingData.serverSynchronisedData
            .addConstraintRenderer(ship1, shipId1, shipId2, id!!.id,
                RopeRenderer(
                ship1 != null,
                ship2 != null,
                spoint1, spoint2,
                dist, width, segments
            ))

        resetState()
    }

    fun resetState() {
        ILOG("RESETTING STATE")
        previousResult = null
    }
}