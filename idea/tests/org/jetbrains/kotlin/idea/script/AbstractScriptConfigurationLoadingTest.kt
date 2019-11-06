/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.script

import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.HashSetQueue
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.applySuggestedScriptConfiguration
import org.jetbrains.kotlin.idea.core.script.configuration.DefaultScriptConfigurationManagerExtensions
import org.jetbrains.kotlin.idea.core.script.configuration.loader.FileContentsDependentConfigurationLoader
import org.jetbrains.kotlin.idea.core.script.configuration.utils.backgroundExecutorNewTaskHook
import org.jetbrains.kotlin.idea.core.script.configuration.utils.rootsIndexerTransaction
import org.jetbrains.kotlin.idea.core.script.configuration.utils.testScriptConfigurationNotification
import org.jetbrains.kotlin.idea.core.script.hasSuggestedScriptConfiguration
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.psi.KtFile

open class AbstractScriptConfigurationLoadingTest : AbstractScriptConfigurationTest() {
    private val ktFile: KtFile get() = myFile as KtFile
    private val virtualFile get() = myFile.virtualFile

    val backgroundQueue = HashSetQueue<BackgroundTask>()
    private lateinit var manager: ScriptConfigurationManager

    class BackgroundTask(val file: VirtualFile, val actions: () -> Unit) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BackgroundTask

            if (file != other.file) return false

            return true
        }

        override fun hashCode(): Int {
            return file.hashCode()
        }
    }

    companion object {
        private var occurredLoadings = 0
        private var currentLoadingScriptConfigurationCallback: (() -> Unit)? = null
        @JvmStatic
        @Suppress("unused")
        fun loadingScriptConfigurationCallback() {
            // this method is called from testData/script/definition/loading/async/template/template.kt
            currentLoadingScriptConfigurationCallback?.invoke()
            occurredLoadings++
        }
    }

    override fun setUp() {
        super.setUp()
        backgroundExecutorNewTaskHook = { file, actions ->
            backgroundQueue.add(BackgroundTask(file, actions))
        }
        testScriptConfigurationNotification = true

        addExtensionPointInTest(
            DefaultScriptConfigurationManagerExtensions.LOADER,
            project,
            FileContentsDependentConfigurationLoader(project),
            testRootDisposable
        )

        configureScriptFile("idea/testData/script/definition/loading/async/")
        manager = ServiceManager.getService(project, ScriptConfigurationManager::class.java)
    }

    override fun tearDown() {
        super.tearDown()
        backgroundExecutorNewTaskHook = null
        testScriptConfigurationNotification = false
        occurredLoadings = 0
        currentLoadingScriptConfigurationCallback = null
    }

    override fun loadScriptConfigurationSynchronously(script: VirtualFile) {
        // do nothings
    }

    protected fun assertAndDoAllBackgroundTasks() {
        assertDoAllBackgroundTaskAndDoWhileLoading { }
    }

    protected fun assertDoAllBackgroundTaskAndDoWhileLoading(actions: () -> Unit) {
        // open loading semaphore
        // wait loading done

        assertTrue(backgroundQueue.isNotEmpty())

        val copy = backgroundQueue.toList()
        backgroundQueue.clear()

        currentLoadingScriptConfigurationCallback = {
            actions()
            currentLoadingScriptConfigurationCallback = null
        }

        rootsIndexerTransaction {
            copy.forEach {
                it.actions()
            }
        }

        LaterInvocator.ensureFlushRequested()
        LaterInvocator.dispatchPendingFlushes()
    }

    protected fun assertAppliedConfiguration(contents: String) {
        val secondConfiguration = manager.getConfiguration(ktFile)!!
        assertEquals(
            contents,
            secondConfiguration.defaultImports.single().let {
                check(it.startsWith("x_"))
                it.removePrefix("x_")
            }
        )
    }

    protected fun makeChanges(contents: String) {
        runWriteAction {
            val fileDocumentManager = FileDocumentManager.getInstance()
            fileDocumentManager.reloadFiles(virtualFile)
            val document = fileDocumentManager.getDocument(virtualFile)!!
            document.setText(contents)
            fileDocumentManager.saveDocument(document)
            psiManager.reloadFromDisk(myFile)
            myFile = psiManager.findFile(virtualFile)
        }

        manager.updater.ensureUpToDatedConfigurationSuggested(ktFile)
    }

    protected fun assertSuggestedConfiguration() {
        assertTrue(virtualFile.hasSuggestedScriptConfiguration(project))
    }

    protected fun assertAndApplySuggestedConfiguration() {
        assertTrue(virtualFile.applySuggestedScriptConfiguration(project))
    }

    protected fun assertNoSuggestedConfiguration() {
        assertFalse(virtualFile.applySuggestedScriptConfiguration(project))
    }

    protected fun assertNoLoading() {
        assertEquals(0, occurredLoadings)
        occurredLoadings = 0
    }

    protected fun assertSingleLoading() {
        assertEquals(1, occurredLoadings)
        occurredLoadings = 0
    }

    protected fun assertAndLoadInitialConfiguration() {
        assertNull(manager.getConfiguration(ktFile))
        assertAndDoAllBackgroundTasks()
        assertSingleLoading()
        assertAppliedConfiguration("initial")
    }
}