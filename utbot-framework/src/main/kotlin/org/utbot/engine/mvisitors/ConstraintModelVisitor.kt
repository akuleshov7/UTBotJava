package org.utbot.engine.mvisitors

import org.utbot.engine.*
import org.utbot.engine.pc.*
import org.utbot.engine.symbolic.asHardConstraint
import org.utbot.framework.plugin.api.*
import soot.ArrayType
import soot.RefType

/**
 * This class builds type and value constraints for equation [symbolicValue] == specific UtModel.
 *
 * Note: may create memory updates in [engine] while visiting.
 */
class ConstraintModelVisitor(
    private var symbolicValue: SymbolicValue,
    private val engine: UtBotSymbolicEngine
) : UtModelVisitor<List<UtBoolExpression>> {

    private inline fun <reified T> withSymbolicValue(
        newSymbolicValue: SymbolicValue,
        block: () -> T
    ): T {
        val old = symbolicValue
        return try {
            symbolicValue = newSymbolicValue
            block()
        } finally {
            symbolicValue = old
        }
    }

    // TODO: must exception be thrown?
    private fun mismatchTypes(): List<UtBoolExpression> =
        emptyList()

    override fun visitArray(model: UtArrayModel): List<UtBoolExpression> =
        when (val value = symbolicValue) {
            is ArrayValue -> {
                val constraints = mutableListOf<UtBoolExpression>()
                constraints += mkNot(mkEq(value.addr, nullObjectAddr))
                val arrayLength = engine.memory.findArrayLength(value.addr)
                constraints += mkEq(arrayLength.align(), model.length.primitiveToSymbolic())

                repeat(model.length) { storeIndex ->
                    val storeModel = model.stores[storeIndex] ?: model.constModel
                    val selectedExpr = value.addr.select(storeIndex.primitiveToLiteral())
                    val storeSymbolicValue = when (val elementType = value.type.elementType) {
                        is RefType -> engine.createObject(
                            UtAddrExpression(selectedExpr),
                            elementType,
                            useConcreteType = false,
                            mockInfoGenerator = null
                        )
                        is ArrayType -> engine.createArray(
                            UtAddrExpression(selectedExpr),
                            elementType,
                            useConcreteType = false
                        )
                        else -> PrimitiveValue(elementType, selectedExpr)
                    }
                    constraints += withSymbolicValue(storeSymbolicValue) {
                        storeModel.visit(this)
                    }
                }
                constraints
            }
            else -> mismatchTypes()
        }

    override fun visitAssemble(model: UtAssembleModel): List<UtBoolExpression> {
        // TODO: not supported
        return mismatchTypes()
    }

    override fun visitComposite(model: UtCompositeModel): List<UtBoolExpression> =
        when (val value = symbolicValue) {
            is ObjectValue -> {
                val constraints = mutableListOf<UtBoolExpression>()
                val type = model.classId.toSoot().type
                val typeStorage = engine.typeResolver.constructTypeStorage(type, true)
                constraints += engine.typeRegistry.typeConstraint(value.addr, typeStorage).isConstraint()
                model.fields.forEach { (field, fieldModel) ->
                    val sootField = field.declaringClass.toSoot().getFieldByName(field.name)
                    val fieldSymbolicValue = engine.createFieldOrMock(
                        type,
                        value.addr,
                        sootField,
                        mockInfoGenerator = null
                    )
                    engine.recordInstanceFieldRead(value.addr, sootField)
                    constraints += withSymbolicValue(fieldSymbolicValue) {
                        fieldModel.visit(this)
                    }
                }
                constraints
            }
            else -> mismatchTypes()
        }

    override fun visitNull(model: UtNullModel): List<UtBoolExpression> =
        when (val value = symbolicValue) {
            is ReferenceValue -> listOf(mkEq(value.addr, nullObjectAddr))
            else -> mismatchTypes()
        }

    override fun visitPrimitive(model: UtPrimitiveModel): List<UtBoolExpression> =
        when(val value = symbolicValue) {
            is PrimitiveValue -> listOf(mkEq(value, model.value.primitiveToSymbolic()))
            else -> mismatchTypes()
        }

    override fun visitVoid(model: UtVoidModel): List<UtBoolExpression> =
        when (val value = symbolicValue) {
            is PrimitiveValue -> listOf(mkEq(voidValue, value))
            else -> mismatchTypes()
        }

    override fun visitEnumConstant(model: UtEnumConstantModel): List<UtBoolExpression> =
        when (val value = symbolicValue) {
            is ObjectValue -> {
                val ordinal = engine.memory.findOrdinal(model.classId.toSoot().type, value.addr)
                listOf(mkEq(ordinal.expr, model.value.ordinal.primitiveToLiteral()))
            }
            else -> mismatchTypes()
        }

    override fun visitClassRef(model: UtClassRefModel): List<UtBoolExpression> =
        when (val value = symbolicValue) {
            is ObjectValue -> {
                val classRef = engine.createClassRef(model.classId.toSoot().type)
                listOf(mkEq(classRef.addr, value.addr))
            }
            else -> mismatchTypes()
        }
}
