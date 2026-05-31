import com.gtnewhorizons.retrofuturagradle.MinecraftExtension
import com.gtnewhorizons.retrofuturagradle.mcp.ExtractDependencyATsTask
import net.darkhax.curseforgegradle.TaskPublishCurseForge
import org.gradle.api.GradleException
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.provider.ListProperty
import org.gradle.api.specs.Specs
import org.gradle.jvm.tasks.Jar

val fentMavenName = "Fent Maven"
val fentMavenUrl = uri("https://maven.fentanylsolutions.org/releases")

fun wawelProp(name: String): String? = providers.gradleProperty(name).orNull?.trim()?.takeIf { it.isNotEmpty() }

fun projectVersionString(): String = project.version.toString()

fun nodepVersionSuffix(): String = wawelProp("wawelAuthNodepVersionSuffix") ?: "nodep"

fun nodepVersion(): String = "${projectVersionString()}-${nodepVersionSuffix()}"

fun RepositoryHandler.keepFentMavenFirst() {
    fun currentFentRepo(): MavenArtifactRepository? = withType(MavenArtifactRepository::class.java)
        .firstOrNull { it.url == fentMavenUrl || it.name == fentMavenName }

    fun promoteFentRepo() {
        val fentRepo = currentFentRepo() ?: maven {
            name = fentMavenName
            url = fentMavenUrl
        }
        if (firstOrNull() !== fentRepo) {
            remove(fentRepo)
            addFirst(fentRepo)
        }
    }

    promoteFentRepo()
    whenObjectAdded {
        promoteFentRepo()
    }
}

gradle.allprojects {
    repositories.keepFentMavenFirst()
    buildscript.repositories.keepFentMavenFirst()
}

tasks.withType<JavaExec>().configureEach {
    if (name.startsWith("runServer")) {
        // WawelAuth GUI stack is client-only. Strip these from dedicated-server
        // runtime right before launch (GTNH setup appends classpath later).
        doFirst("wawelauthStripClientOnlyMods") {
            classpath = classpath.filter { file ->
                val n = file.name
                !n.contains("ModularUI2", ignoreCase = true) && !n.contains("Baubles-Expanded", ignoreCase = true) && !n.contains("angelica", ignoreCase = true)
            }
        }
    }
}

val minecraftExtension = project.extensions.getByType(MinecraftExtension::class.java)
val useDependencyAccessTransformers = minecraftExtension.useDependencyAccessTransformers

tasks.named<ExtractDependencyATsTask>("extractDependencyATs").configure {
    // RFG's default onlyIf captures the whole minecraft extension, which can
    // make config-cache serialization trip over unrelated dependency misses.
    enabled = useDependencyAccessTransformers.getOrElse(false)
    setOnlyIf(Specs.satisfyAll())
}

val sqliteJdbcVersion = wawelProp("sqliteJdbcVersion") ?: "3.53.1.0"
val bouncyCastleVersion = wawelProp("bouncyCastleVersion") ?: "1.84"

val reobfJar = tasks.named<Jar>("reobfJar")
val baseJar = tasks.named<Jar>("jar")

val writeNodepFplibDependencies = tasks.register("writeNodepFplibDependencies") {
    notCompatibleWithConfigurationCache("Writes generated nodep FPLib dependency metadata from addon script values.")

    val output = layout.buildDirectory.file("generated/wawelauth-nodep/wawelauth_fplib_dependencies.json")

    inputs.property("bouncyCastleVersion", bouncyCastleVersion)
    inputs.property("sqliteJdbcVersion", sqliteJdbcVersion)
    outputs.file(output)

    doLast {
        val out = output.get().asFile
        out.parentFile.mkdirs()
        out.writeText(
            """
            {
              "identifier": "falsepatternlib_dependencies",
              "repositories": [
                "https://repo.maven.apache.org/maven2/"
              ],
              "dependencies": {
                "always": {
                  "common": [],
                  "client": [],
                  "server": []
                },
                "obf": {
                  "common": [
                    "org.bouncycastle:bcprov-jdk15to18:$bouncyCastleVersion",
                    "org.bouncycastle:bcutil-jdk15to18:$bouncyCastleVersion",
                    "org.bouncycastle:bcpkix-jdk15to18:$bouncyCastleVersion",
                    "org.xerial:sqlite-jdbc:$sqliteJdbcVersion"
                  ],
                  "client": [],
                  "server": []
                },
                "dev": {
                  "common": [],
                  "client": [],
                  "server": []
                }
              },
              "modDependencies": {
                "always": {
                  "common": [],
                  "client": [],
                  "server": []
                },
                "obf": {
                  "common": [],
                  "client": [],
                  "server": []
                },
                "dev": {
                  "common": [],
                  "client": [],
                  "server": []
                }
              }
            }
            """.trimIndent(),
        )
    }
}

val nodepJar = tasks.register<Jar>("nodepJar") {
    group = "build"
    description = "Builds the nodep jar with runtime libraries downloaded through FalsePatternLib."
    notCompatibleWithConfigurationCache("Copies and filters the reobfuscated shaded jar through addon script copy specs.")

    dependsOn(reobfJar)
    dependsOn(writeNodepFplibDependencies)

    archiveBaseName.set(baseJar.flatMap { it.archiveBaseName })
    archiveVersion.set(providers.provider { projectVersionString() })
    archiveClassifier.set(nodepVersionSuffix())
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "FMLCorePluginContainsFMLMod" to "true",
            "FMLCorePlugin" to "${wawelProp("modGroup")}.core.EarlyMixinLoader",
            "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
            "MixinConfigs" to "mixins.${wawelProp("modId")}.json",
            "ForceLoadAsMod" to "true",
            "Multi-Release" to "true",
        )
    }

    from(reobfJar.flatMap { it.archiveFile }.map { zipTree(it) }) {
        exclude(
            "META-INF/BC*.DSA",
            "META-INF/BC*.RSA",
            "META-INF/BC*.SF",
            "META-INF/MANIFEST.MF",
            "META-INF/maven/org.bouncycastle/**",
            "META-INF/maven/org.xerial/**",
            "META-INF/native-image/org.xerial/**",
            "META-INF/services/java.security.Provider",
            "META-INF/services/java.sql.Driver",
            "META-INF/versions/**/module-info.class",
            "META-INF/versions/**/org/bouncycastle/**",
            "META-INF/versions/9/org/sqlite/**",
            "org/bouncycastle/**",
            "org/fentanylsolutions/wawelauth/shadow/bouncycastle/**",
            "org/sqlite/**",
            "sqlite-jdbc.properties",
        )

        filesMatching("mcmod.info") {
            filter { line: String ->
                line
                    .replace(Regex("\"version\"\\s*:\\s*\"[^\"]*\""), "\"version\": \"${nodepVersion()}\"")
                    .replace("\"requiredMods\": [\"fentlib\"]", "\"requiredMods\": [\"fentlib\", \"falsepatternlib\"]")
                    .replace("\"dependencies\": [\"fentlib\"]", "\"dependencies\": [\"fentlib\", \"falsepatternlib\"]")
            }
        }
    }

    from(layout.buildDirectory.file("generated/wawelauth-nodep/wawelauth_fplib_dependencies.json")) {
        into("META-INF")
    }
}

tasks.register("nodepJars") {
    group = "build"
    description = "Builds the nodep jar."
    dependsOn(nodepJar)
}

tasks.named("assemble").configure {
    dependsOn("nodepJars")
}

fun Any.callNoArg(methodName: String): Any? = javaClass.methods
    .first { it.name == methodName && it.parameterCount == 0 }
    .invoke(this)

@Suppress("UNCHECKED_CAST")
fun configureModrinthBundledNodep(extension: Any) {
    (extension.callNoArg("getAdditionalFiles") as ListProperty<Any>).add(nodepJar)
}

plugins.withId("com.modrinth.minotaur") {
    afterEvaluate {
        extensions.findByName("modrinth")?.let(::configureModrinthBundledNodep)
    }
    tasks.matching { it.name == "modrinth" }.configureEach {
        dependsOn(nodepJar)
    }
}

tasks.withType<TaskPublishCurseForge>().configureEach {
    if (name.equals("publishCurseforge", ignoreCase = true)) {
        dependsOn(nodepJar)
        doFirst("wawelauthAttachNodepFile") {
            val parentArtifact = uploadArtifacts.firstOrNull()
                ?: throw GradleException("Cannot attach nodep jar: $name has no primary artifact.")
            val nodepArtifact = parentArtifact.withAdditionalFile(nodepJar)
            nodepArtifact.displayName = providers.provider {
                "${wawelProp("modName") ?: project.name} ${nodepVersion()}"
            }
            nodepArtifact.addRequirement("fplib")
        }
    }
}

extensions.extraProperties.set("publishableNodepJar", nodepJar)
afterEvaluate {
    val extras = extensions.extraProperties
    if (!extras.has("publishableApiJar")) {
        // 67minecraft's extra-file hook.
        extras.set("publishableApiJar", nodepJar)
    }
}

tasks.matching { it.name == "publish67Minecraft" }.configureEach {
    dependsOn(nodepJar)
}
