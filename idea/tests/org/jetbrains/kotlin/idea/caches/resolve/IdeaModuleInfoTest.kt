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

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.testFramework.ModuleTestCase
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.DependencyScope
import org.junit.Assert
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.UsefulTestCase
import java.util.*

class IdeaModuleInfoTest : ModuleTestCase() {

    fun testSimpleModuleDependency() {
        val (a, b) = modules()
        b.addDependency(a)

        b.production.assertDependenciesEqual(b.production, a.production)
        UsefulTestCase.assertDoesntContain(a.production.dependencies(), b.production)
    }

    fun testCircularDependency() {
        val (a, b) = modules()

        b.addDependency(a)
        a.addDependency(b)

        a.production.assertDependenciesEqual(a.production, b.production)
        b.production.assertDependenciesEqual(b.production, a.production)
    }

    fun testExportedDependency() {
        val (a, b, c) = modules()

        b.addDependency(a, exported = true)
        c.addDependency(b)

        a.production.assertDependenciesEqual(a.production)
        b.production.assertDependenciesEqual(b.production, a.production)
        c.production.assertDependenciesEqual(c.production, b.production, a.production)
    }

    fun testRedundantExportedDependency() {
        val (a, b, c) = modules()

        b.addDependency(a, exported = true)
        c.addDependency(a)
        c.addDependency(b)

        a.production.assertDependenciesEqual(a.production)
        b.production.assertDependenciesEqual(b.production, a.production)
        c.production.assertDependenciesEqual(c.production, a.production, b.production)
    }

    fun testCircularExportedDependency() {
        val (a, b, c) = modules()

        b.addDependency(a, exported = true)
        c.addDependency(b, exported = true)
        a.addDependency(c, exported = true)

        a.production.assertDependenciesEqual(a.production, c.production, b.production)
        b.production.assertDependenciesEqual(b.production, a.production, c.production)
        c.production.assertDependenciesEqual(c.production, b.production, a.production)
    }

    fun testSimpleLibDependency() {
        val a = module("a")
        val lib = projectLibrary()
        a.addDependency(lib)

        a.production.assertDependenciesEqual(a.production, lib.classes)
    }

    fun testCircularExportedDependencyWithLib() {
        val (a, b, c) = modules()

        val lib = projectLibrary()

        a.addDependency(lib)

        b.addDependency(a, exported = true)
        c.addDependency(b, exported = true)
        a.addDependency(c, exported = true)

        b.addDependency(lib)
        c.addDependency(lib)

        a.production.assertDependenciesEqual(a.production, lib.classes, c.production, b.production)
        b.production.assertDependenciesEqual(b.production, a.production, c.production, lib.classes)
        c.production.assertDependenciesEqual(c.production, b.production, a.production, lib.classes)
    }

    fun testSeveralModulesExportLibs() {
        val (a, b, c) = modules()

        val lib1 = projectLibrary("lib1")
        val lib2 = projectLibrary("lib2")

        a.addDependency(lib1, exported = true)
        b.addDependency(lib2, exported = true)
        c.addDependency(a)
        c.addDependency(b)

        c.production.assertDependenciesEqual(c.production, a.production, lib1.classes, b.production, lib2.classes)
    }

    fun testSeveralModulesExportSameLib() {
        val (a, b, c) = modules()

        val lib = projectLibrary()

        a.addDependency(lib, exported = true)
        b.addDependency(lib, exported = true)
        c.addDependency(a)
        c.addDependency(b)

        c.production.assertDependenciesEqual(c.production, a.production, lib.classes, b.production)
    }

    fun testRuntimeDependency() {
        val (a, b) = modules()

        b.addDependency(a, dependencyScope = DependencyScope.RUNTIME)
        b.addDependency(projectLibrary(), dependencyScope = DependencyScope.RUNTIME)

        b.production.assertDependenciesEqual(b.production)
    }

    fun testProvidedDependency() {
        val (a, b) = modules()
        val lib = projectLibrary()

        b.addDependency(a, dependencyScope = DependencyScope.PROVIDED)
        b.addDependency(lib, dependencyScope = DependencyScope.PROVIDED)

        b.production.assertDependenciesEqual(b.production, a.production, lib.classes)
    }

    fun testSimpleTestDependency() {
        val (a, b) = modules()
        b.addDependency(a, dependencyScope = DependencyScope.TEST)

        a.production.assertDependenciesEqual(a.production)
        a.test.assertDependenciesEqual(a.test, a.production)
        b.production.assertDependenciesEqual(b.production)
        b.test.assertDependenciesEqual(b.test, b.production, a.test, a.production)
    }

    fun testLibTestDependency() {
        val a = module("a")
        val lib = projectLibrary()
        a.addDependency(lib, dependencyScope = DependencyScope.TEST)

        a.production.assertDependenciesEqual(a.production)
        a.test.assertDependenciesEqual(a.test, a.production, lib.classes)
    }

    fun testExportedTestDependency() {
        val (a, b, c) = modules()
        b.addDependency(a, exported = true)
        c.addDependency(b, dependencyScope = DependencyScope.TEST)

        c.production.assertDependenciesEqual(c.production)
        c.test.assertDependenciesEqual(c.test, c.production, b.test, b.production, a.test, a.production)
    }

    fun testDependents() {
        //NOTE: we do not differ between dependency kinds
        val (a, b, c) = modules(name1 = "a", name2 = "b", name3 = "c")
        val (d, e, f) = modules(name1 = "d", name2 = "e", name3 = "f")

        b.addDependency(a, exported = true)

        c.addDependency(a)

        d.addDependency(c, exported = true)

        e.addDependency(b)

        f.addDependency(d)
        f.addDependency(e)


        a.test.assertDependentsEqual(a.test, b.test, c.test, e.test)
        a.production.assertDependentsEqual(a.production, a.test, b.production, b.test, c.production, c.test, e.production, e.test)

        b.test.assertDependentsEqual(b.test, e.test)
        b.production.assertDependentsEqual(b.production, b.test, e.production, e.test)


        c.test.assertDependentsEqual(c.test, d.test, f.test)
        c.production.assertDependentsEqual(c.production, c.test, d.production, d.test, f.production, f.test)

        d.test.assertDependentsEqual(d.test, f.test)
        d.production.assertDependentsEqual(d.production, d.test, f.production, f.test)

        e.test.assertDependentsEqual(e.test, f.test)
        e.production.assertDependentsEqual(e.production, e.test, f.production, f.test)

        f.test.assertDependentsEqual(f.test)
        f.production.assertDependentsEqual(f.production, f.test)
    }

    fun testLibraryDependency1() {
        val lib1 = projectLibrary("lib1")
        val lib2 = projectLibrary("lib2")

        val module = module("module")
        module.addDependency(lib1)
        module.addDependency(lib2)

        lib1.classes.assertAdditionalLibraryDependencies(lib2.classes)
        lib2.classes.assertAdditionalLibraryDependencies(lib1.classes)
    }

    fun testLibraryDependency2() {
        val lib1 = projectLibrary("lib1")
        val lib2 = projectLibrary("lib2")
        val lib3 = projectLibrary("lib3")

        val (a, b, c) = modules()
        a.addDependency(lib1)
        b.addDependency(lib2)
        c.addDependency(lib3)

        c.addDependency(a)
        c.addDependency(b)

        lib1.classes.assertAdditionalLibraryDependencies()
        lib2.classes.assertAdditionalLibraryDependencies()
        lib3.classes.assertAdditionalLibraryDependencies(lib1.classes, lib2.classes)
    }

    fun testLibraryDependency3() {
        val lib1 = projectLibrary("lib1")
        val lib2 = projectLibrary("lib2")
        val lib3 = projectLibrary("lib3")

        val (a, b) = modules()
        a.addDependency(lib1)
        b.addDependency(lib2)

        a.addDependency(lib3)
        b.addDependency(lib3)

        lib1.classes.assertAdditionalLibraryDependencies(lib3.classes)
        lib2.classes.assertAdditionalLibraryDependencies(lib3.classes)
        lib3.classes.assertAdditionalLibraryDependencies(lib1.classes, lib2.classes)
    }

    private fun Module.addDependency(
            other: Module,
            dependencyScope: DependencyScope = DependencyScope.COMPILE,
            exported: Boolean = false
    ) = ModuleRootModificationUtil.addDependency(this, other, dependencyScope, exported)

    private val Module.production: ModuleProductionSourceInfo
        get() = productionSourceInfo()

    private val Module.test: ModuleTestSourceInfo
        get() = testSourceInfo()

    private val Library.classes: LibraryInfo
        get() = LibraryInfo(project!!, this)

    private fun Module.addDependency(
            lib: Library,
            dependencyScope: DependencyScope = DependencyScope.COMPILE,
            exported: Boolean = false
    ) = ModuleRootModificationUtil.addDependency(this, lib, dependencyScope, exported)

    private fun module(name: String): Module {
        return createModuleFromTestData(createTempDirectory()!!.absolutePath, name, StdModuleTypes.JAVA, false)!!
    }

    private fun modules(name1: String = "a", name2: String = "b", name3: String = "c") = Triple(module(name1), module(name2), module(name3))

    private fun IdeaModuleInfo.assertDependenciesEqual(vararg expected: IdeaModuleInfo) {
        Assert.assertEquals(expected.toList(), this.dependencies())
    }

    private fun LibraryInfo.assertAdditionalLibraryDependencies(vararg expected: IdeaModuleInfo) {
        Assert.assertEquals(this, dependencies().first())
        val dependenciesWithoutSelf = this.dependencies().drop(1)
        UsefulTestCase.assertSameElements(dependenciesWithoutSelf, expected.toList())
    }

    private fun ModuleSourceInfo.assertDependentsEqual(vararg expected: ModuleSourceInfo) {
        UsefulTestCase.assertSameElements(this.getDependentModules(), expected.toList())
    }

    private fun projectLibrary(name: String = "lib"): Library {
        val libraryTable = ProjectLibraryTable.getInstance(myProject)!!
        return WriteCommandAction.runWriteCommandAction<Library>(myProject) {
            libraryTable.createLibrary(name)
        }!!
    }
}
