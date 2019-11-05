package custom.scriptDefinition

import kotlin.script.dependencies.*
import kotlin.script.experimental.dependencies.*
import kotlin.script.templates.*
import java.io.File
import kotlin.script.experimental.location.*

class TestDependenciesResolver : AsyncDependenciesResolver {
    suspend override fun resolveAsync(scriptContents: ScriptContents, environment: Environment): DependenciesResolver.ResolveResult {
        val result = ScriptDependencies(
            classpath = listOf(environment["template-classes"] as File),
            imports = listOf("x_" + scriptContents.text)
        ).asSuccess()

        javaClass.classLoader
            .loadClass("org.jetbrains.kotlin.idea.script.ScriptConfigurationLoadingTest")
            .methods.single { it.name == "loadingScriptConfigurationCallback" }
            .invoke(null)

        return result
    }
}

@ScriptExpectedLocations([ScriptExpectedLocation.Everywhere])
@ScriptTemplateDefinition(TestDependenciesResolver::class, scriptFilePattern = "script.kts")
class Template : Base()

open class Base {
    val i = 3
    val str = ""
}