package org.utbot.instrumentation.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object InvokeMethodCommandSerializer : KSerializer<Protocol.InvokeMethodCommand> {
    override fun deserialize(decoder: Decoder): Protocol.InvokeMethodCommand {
        TODO("Not yet implemented")
    }

    override val descriptor: SerialDescriptor
        get() = TODO("Not yet implemented")

    override fun serialize(encoder: Encoder, value: Protocol.InvokeMethodCommand) {
        TODO("Not yet implemented")
    }
}