/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.codegen.intrinsics

import org.jetbrains.kotlin.builtins.KotlinBuiltIns.FQ_NAMES
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter
import org.jetbrains.org.objectweb.asm.tree.*
import kotlin.text.Regex

object TypeIntrinsics {
    @JvmStatic fun instanceOf(v: InstructionAdapter, jetType: KotlinType, boxedAsmType: Type) {
        val functionTypeArity = getFunctionTypeArity(jetType)
        if (functionTypeArity >= 0) {
            v.iconst(functionTypeArity)
            v.typeIntrinsic(IS_FUNCTON_OF_ARITY_METHOD_NAME, IS_FUNCTON_OF_ARITY_DESCRIPTOR)
            return
        }

        val isMutableCollectionMethodName = getIsMutableCollectionMethodName(jetType)
        if (isMutableCollectionMethodName != null) {
            v.typeIntrinsic(isMutableCollectionMethodName, IS_MUTABLE_COLLECTION_METHOD_DESCRIPTOR)
            return
        }

        v.instanceOf(boxedAsmType)
    }

    private fun iconstNode(value: Int): AbstractInsnNode =
            if (value >= -1 && value <= 5) {
                InsnNode(Opcodes.ICONST_0 + value)
            }
            else if (value >= java.lang.Byte.MIN_VALUE && value <= java.lang.Byte.MAX_VALUE) {
                IntInsnNode(Opcodes.BIPUSH, value)
            }
            else if (value >= java.lang.Short.MIN_VALUE && value <= java.lang.Short.MAX_VALUE) {
                IntInsnNode(Opcodes.SIPUSH, value)
            }
            else {
                LdcInsnNode(Integer(value))
            }

    @JvmStatic fun instanceOf(instanceofInsn: TypeInsnNode, instructions: InsnList, jetType: KotlinType, asmType: Type) {
        val functionTypeArity = getFunctionTypeArity(jetType)
        if (functionTypeArity >= 0) {
            instructions.insertBefore(instanceofInsn, iconstNode(functionTypeArity))
            instructions.insertBefore(instanceofInsn,
                                      typeIntrinsicNode(IS_FUNCTON_OF_ARITY_METHOD_NAME, IS_FUNCTON_OF_ARITY_DESCRIPTOR))
            instructions.remove(instanceofInsn)
            return
        }

        val isMutableCollectionMethodName = getIsMutableCollectionMethodName(jetType)
        if (isMutableCollectionMethodName != null) {
            instructions.insertBefore(instanceofInsn,
                                      typeIntrinsicNode(isMutableCollectionMethodName, IS_MUTABLE_COLLECTION_METHOD_DESCRIPTOR))
            instructions.remove(instanceofInsn)
            return
        }

        instanceofInsn.desc = asmType.internalName
    }

    @JvmStatic fun checkcast(
            v: InstructionAdapter,
            kotlinType: KotlinType, asmType: Type,
            // This parameter is just for sake of optimization:
            // when we generate 'as?' we do necessary intrinsic checks
            // when calling TypeIntrinsics.instanceOf, so here we can just make checkcast
            safe: Boolean) {
        if (safe) {
            v.checkcast(asmType)
            return
        }

        val functionTypeArity = getFunctionTypeArity(kotlinType)
        if (functionTypeArity >= 0) {
            v.iconst(functionTypeArity)
            v.typeIntrinsic(BEFORE_CHECKCAST_TO_FUNCTION_OF_ARITY, BEFORE_CHECKCAST_TO_FUNCTION_OF_ARITY_DESCRIPTOR)
            v.checkcast(asmType)
            return
        }

        val asMutableCollectionMethodName = getAsMutableCollectionMethodName(kotlinType)
        if (asMutableCollectionMethodName != null) {
            v.typeIntrinsic(asMutableCollectionMethodName, getAsMutableCollectionDescriptor(asmType))
            return
        }

        v.checkcast(asmType)
    }

    private val INTRINSICS_CLASS = "kotlin/jvm/internal/TypeIntrinsics"

    private val IS_FUNCTON_OF_ARITY_METHOD_NAME = "isFunctionOfArity"

    private val IS_FUNCTON_OF_ARITY_DESCRIPTOR =
            Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getObjectType("java/lang/Object"), Type.INT_TYPE)


    private val MUTABLE_COLLECTION_TYPE_FQ_NAMES = setOf(
            FQ_NAMES.mutableIterator,
            FQ_NAMES.mutableIterable,
            FQ_NAMES.mutableCollection,
            FQ_NAMES.mutableList,
            FQ_NAMES.mutableListIterator,
            FQ_NAMES.mutableMap,
            FQ_NAMES.mutableSet,
            FQ_NAMES.mutableMapEntry
    )

    private fun getMutableCollectionMethodName(prefix: String, jetType: KotlinType): String? {
        val fqName = getClassFqName(jetType)
        if (fqName == null || fqName !in MUTABLE_COLLECTION_TYPE_FQ_NAMES) return null
        val baseName = if (fqName == FQ_NAMES.mutableMapEntry) "MutableMapEntry" else fqName.shortName().asString()
        return prefix + baseName
    }

    private fun getIsMutableCollectionMethodName(jetType: KotlinType): String? = getMutableCollectionMethodName("is", jetType)

    private fun getAsMutableCollectionMethodName(jetType: KotlinType): String? = getMutableCollectionMethodName("as", jetType)

    private val IS_MUTABLE_COLLECTION_METHOD_DESCRIPTOR =
            Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getObjectType("java/lang/Object"))

    private fun getClassFqName(jetType: KotlinType): FqName? {
        val classDescriptor = TypeUtils.getClassDescriptor(jetType) ?: return null
        return DescriptorUtils.getFqName(classDescriptor).toSafe()
    }

    private val KOTLIN_FUNCTION_INTERFACE_REGEX = Regex("^kotlin\\.Function([0-9]+)$")

    /**
     * @return function type arity (non-negative), or -1 if the given type is not a function type
     */
    private fun getFunctionTypeArity(jetType: KotlinType): Int {
        val classFqName = getClassFqName(jetType) ?: return -1
        val match = KOTLIN_FUNCTION_INTERFACE_REGEX.find(classFqName.asString()) ?: return -1
        return Integer.valueOf(match.groups[1]!!.value)
    }

    private fun typeIntrinsicNode(methodName: String, methodDescriptor: String): MethodInsnNode =
            MethodInsnNode(Opcodes.INVOKESTATIC, INTRINSICS_CLASS, methodName, methodDescriptor, false)

    private fun InstructionAdapter.typeIntrinsic(methodName: String, methodDescriptor: String) {
        invokestatic(INTRINSICS_CLASS, methodName, methodDescriptor, false)
    }


    private val OBJECT_TYPE = Type.getObjectType("java/lang/Object")

    private fun getAsMutableCollectionDescriptor(asmType: Type): String =
            Type.getMethodDescriptor(asmType, OBJECT_TYPE);

    private val BEFORE_CHECKCAST_TO_FUNCTION_OF_ARITY = "beforeCheckcastToFunctionOfArity"

    private val BEFORE_CHECKCAST_TO_FUNCTION_OF_ARITY_DESCRIPTOR =
            Type.getMethodDescriptor(OBJECT_TYPE, OBJECT_TYPE, Type.INT_TYPE)
}