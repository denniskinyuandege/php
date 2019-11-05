/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.script

class ScriptConfigurationLoadingTest : AbstractScriptConfigurationLoadingTest() {
    fun testSimple() {
        loadInitialConfiguration()

        makeChanges("A")
        assertAndDoAllBackgroundTasks()
        assertSingleLoading()
        assertAndApplySuggestedConfiguration()
        assertAppliedConfiguration("A")
    }

    fun testConcurrentLoadingWhileInQueue() {
        loadInitialConfiguration()

        makeChanges("A")
        makeChanges("B")
        assertAndDoAllBackgroundTasks()
        assertSingleLoading()
        assertAndApplySuggestedConfiguration()
        assertAppliedConfiguration("B")
    }

    fun testConcurrentLoadingWhileAnotherLoadInProgress() {
        loadInitialConfiguration()

        makeChanges("A")
        assertDoAllBackgroundTaskAndDoWhileLoading {
            makeChanges("B")
        }
        assertSingleLoading()
        assertAndApplySuggestedConfiguration()
        assertAppliedConfiguration("A")

        assertAndDoAllBackgroundTasks()
        assertSingleLoading()
        assertAndApplySuggestedConfiguration()
        assertAppliedConfiguration("B")
    }

    fun testConcurrentLoadingWhileAnotherLoadInProgress2() {
        loadInitialConfiguration()

        makeChanges("A")
        assertDoAllBackgroundTaskAndDoWhileLoading {
            makeChanges("B")
            makeChanges("A")
        }
        assertSingleLoading()
        assertAndApplySuggestedConfiguration()
        assertAppliedConfiguration("A")

        assertAndDoAllBackgroundTasks()
        assertNoLoading()
        assertNoSuggestedConfiguration()
        assertAppliedConfiguration("A")
    }

    fun testConcurrentLoadingWhileAnotherLoadInProgress3() {
        loadInitialConfiguration()

        makeChanges("A")
        assertDoAllBackgroundTaskAndDoWhileLoading {
            makeChanges("initial")
        }
        assertSingleLoading()
        assertSuggestedConfiguration()

        assertAndDoAllBackgroundTasks()
        assertNoLoading()
        assertNoSuggestedConfiguration()
    }

    fun testConcurrentLoadingWhileNotApplied() {
        loadInitialConfiguration()

        makeChanges("A")

        assertAndDoAllBackgroundTasks()
        assertSingleLoading()
        assertAppliedConfiguration("initial")

        // we have loaded and not applied configuration for A
        // let's invalidate file again and check that loading will occur

        makeChanges("B")
        assertAndDoAllBackgroundTasks()
        assertSingleLoading()
        assertAppliedConfiguration("A")

        assertAndApplySuggestedConfiguration()
        assertAppliedConfiguration("B")
    }

    fun testConcurrentLoadingWhileNotApplied2() {
        loadInitialConfiguration()

        makeChanges("A")

        assertAndDoAllBackgroundTasks()
        assertSingleLoading()
        assertAppliedConfiguration("initial")

        // we have loaded and not applied configuration for A
        // let's invalidate file and change it back
        // and check that loading will NOT occur

        makeChanges("B")
        makeChanges("A")

        assertAndDoAllBackgroundTasks()
        assertNoLoading()
        assertAppliedConfiguration("initial")

        assertAndApplySuggestedConfiguration()
        assertAppliedConfiguration("A")
    }

    fun testConcurrentLoadingWhileNotApplied3() {
        loadInitialConfiguration()

        makeChanges("A")
        assertAndDoAllBackgroundTasks()
        assertSingleLoading()
        assertSuggestedConfiguration()

        makeChanges("initial")
        assertAndDoAllBackgroundTasks()
        assertNoLoading()
        assertNoSuggestedConfiguration()
    }

    // todo: test reports

    // todo: test indexing new roots
    // todo: test fs caching
    // todo: test gradle specific logic

    // todo: test not running loading for usages search
}