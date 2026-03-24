import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

fun Project.applyPlugins(vararg aliases: String) {
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

    aliases.forEach { alias ->
        val pluginId = libs.findPlugin(alias).get().get().pluginId
        pluginManager.apply(pluginId)
    }
}