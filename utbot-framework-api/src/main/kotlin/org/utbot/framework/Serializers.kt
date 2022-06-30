package org.utbot.framework

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.serializer
import org.utbot.common.IntRangeSerializer
import org.utbot.framework.plugin.api.*
import org.utbot.framework.plugin.api.util.*

private val classIdSerializer = serializer<ClassId>()

// todo Class<Out> org.utbot.instrumentation.instrumentation.coverage.CollectCoverageCommand
// todo Any? org.utbot.instrumentation.util.Protocol.InvokeMethodCommand

object ThrowableSerializer : KSerializer<Throwable> {
    override val descriptor: SerialDescriptor
        get() = TODO("Not yet implemented")

    override fun deserialize(decoder: Decoder): Throwable {
        TODO("Not yet implemented")
    }

    override fun serialize(encoder: Encoder, value: Throwable) {
        TODO("Not yet implemented")
    }

}

object BuiltinClassIdSerializer : KSerializer<BuiltinClassId> {
    override val descriptor: SerialDescriptor
        get() = TODO("Not yet implemented")

    override fun deserialize(decoder: Decoder): BuiltinClassId {
        TODO("Not yet implemented")
    }

    override fun serialize(encoder: Encoder, value: BuiltinClassId) {
        TODO("Not yet implemented")
    }

}

private fun <T : Enum<T>> safeValueOf(enumType: Class<T>, type: String): T {
    return java.lang.Enum.valueOf(enumType, type)
}

object UtEnumConstantModelSerializer : KSerializer<UtEnumConstantModel> {
    @InternalSerializationApi
    @ExperimentalSerializationApi
    override val descriptor: SerialDescriptor = buildSerialDescriptor("UtEnumConstantModel", StructureKind.CLASS) {
        element("classId", classIdSerializer.descriptor)
        element<String>("enum")
    }

    @InternalSerializationApi
    @ExperimentalSerializationApi
    override fun deserialize(decoder: Decoder): UtEnumConstantModel {
        return decoder.decodeStructure(descriptor) {
            var classId: ClassId? = null
            var enum: String? = null

            while (true) {
                when (val index = decodeElementIndex(IntRangeSerializer.descriptor)) {
                    0 -> classId = decodeSerializableElement(descriptor, 0, classIdSerializer)
                    1 -> enum = decodeStringElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected token $index")
                }
            }


            @Suppress("UNCHECKED_CAST")
            // suppose that UtEnumConstantModel.classId is underlying enum class
            UtEnumConstantModel(classId!!, safeValueOf(classId.jClass as Class<out Enum<*>>, enum!!))
        }
    }

    @InternalSerializationApi
    @ExperimentalSerializationApi
    override fun serialize(encoder: Encoder, value: UtEnumConstantModel) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, classIdSerializer, value.classId)
            encodeStringElement(descriptor, 1, value.value.name)
        }
    }

}

private fun String.parseToPrimitiveTypeJvmName(primitiveType: Char): Any {
    return when (primitiveType) {
        'V' -> Unit
        'Z' -> toBoolean()
        'B' -> toByte()
        'C' -> single()
        'S' -> toShort()
        'I' -> toInt()
        'J' -> toLong()
        'F' -> toFloat()
        'D' -> toDouble()
        else -> error("Primitive expected here, but got: $primitiveType")
    }
}

object UtPrimitiveModelSerializer : KSerializer<UtPrimitiveModel> {
    // todo kononov: possible speed up - char+string instead to remove string concatenation, it is just premilinary version
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UtPrimitiveModel", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UtPrimitiveModel {
        val decodedString = decoder.decodeString()

        return UtPrimitiveModel(decodedString.substring(1).parseToPrimitiveTypeJvmName(decodedString.first()))
    }

    override fun serialize(encoder: Encoder, value: UtPrimitiveModel) {
        // because name `value` comes from KSerializer interface,
        // so that name collision is kept to confront calling this function with named parameters
        @Suppress("UnnecessaryVariable")
        val primitiveModel = value

        if (!primitiveModel.classId.isPrimitive || primitiveModelValueToClassId(primitiveModel.value) != primitiveModel.classId) {
            throw IllegalStateException("PrimitiveModel invariant corrupted: has ${primitiveModel.classId} for ${primitiveModel.value}")
        }

        val jvmTypeName = primitiveModel.classId.primitiveTypeJvmNameOrNull()!!
        val valueString = primitiveModel.value.toString()

        encoder.encodeString(jvmTypeName + valueString)
    }
}

object UtClassRefModelSerializer : KSerializer<UtClassRefModel> {
    @InternalSerializationApi
    @ExperimentalSerializationApi
    override val descriptor: SerialDescriptor = buildSerialDescriptor("UtClassRefModel", StructureKind.CLASS) {
        element("classId", classIdSerializer.descriptor)
    }

    override fun deserialize(decoder: Decoder): UtClassRefModel {
        val classId = decoder.decodeSerializableValue(classIdSerializer)

        // if serialization succeeded - expecting deserialization be succeeded
        return UtClassRefModel(classId, classId.jClass)
    }

    override fun serialize(encoder: Encoder, value: UtClassRefModel) {
        // because name `value` comes from KSerializer interface,
        // so that name collision is kept to confront calling this function with named parameters
        @Suppress("UnnecessaryVariable")
        val classRefModel = value
        val underlyingClassId = classRefModel.value.id

        if (underlyingClassId != classRefModel.classId) {
            throw IllegalStateException("classRefModel invariant corrupted: has ${classRefModel.classId} for $underlyingClassId")
        }

        encoder.encodeSerializableValue(classIdSerializer, classRefModel.classId)
    }
}