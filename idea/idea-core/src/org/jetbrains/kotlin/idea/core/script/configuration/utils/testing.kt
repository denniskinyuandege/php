/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.core.script.configuration.utils

import com.intellij.openapi.vfs.VirtualFile

lateinit var rootsIndexerTransaction: (() -> Unit) -> Unit
var backgroundExecutorNewTaskHook: ((file: VirtualFile, actions: () -> Unit) -> Unit)? = null
var testScriptConfigurationNotification: Boolean = true