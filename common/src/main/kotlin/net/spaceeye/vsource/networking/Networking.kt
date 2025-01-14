package net.spaceeye.vsource.networking

import dev.architectury.networking.NetworkManager
import dev.architectury.networking.NetworkManager.NetworkReceiver
import dev.architectury.networking.NetworkManager.PacketContext
import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.spaceeye.vsource.VS

interface Connection {
    val side: NetworkManager.Side
    val id: ResourceLocation
    fun getHandler(): NetworkReceiver
}

interface Serializable {
    fun serialize(): FriendlyByteBuf
    fun deserialize(buf: FriendlyByteBuf)

    fun getBuffer() = FriendlyByteBuf(Unpooled.buffer(512))
}

abstract class C2SConnection<T : Serializable>(id: String, connectionName: String): Connection {
    override val side: NetworkManager.Side = NetworkManager.Side.C2S
    override val id = ResourceLocation(VS.MOD_ID, "c2s_${connectionName}_$id")

    override fun getHandler(): NetworkReceiver = NetworkReceiver(::serverHandler)
    abstract fun serverHandler(buf: FriendlyByteBuf, context: PacketContext)

    fun sendToServer(packet: T) = NetworkManager.sendToServer(id, packet.serialize())
}

abstract class S2CConnection<T : Serializable>(id: String, connectionName: String): Connection {
    override val side: NetworkManager.Side = NetworkManager.Side.S2C
    override val id = ResourceLocation(VS.MOD_ID, "s2c_${connectionName}_$id")

    override fun getHandler(): NetworkReceiver = NetworkReceiver(::clientHandler)
    abstract fun clientHandler(buf: FriendlyByteBuf, context: PacketContext)

    fun sendToClient(player: ServerPlayer, packet: T) = NetworkManager.sendToPlayer(player, id, packet.serialize())
    fun sendToClients(players: Iterable<ServerPlayer>, packet: T) = NetworkManager.sendToPlayers(players, id, packet.serialize())
}