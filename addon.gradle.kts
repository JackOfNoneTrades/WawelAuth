import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin
import com.gtnewhorizons.retrofuturagradle.MinecraftExtension
import com.gtnewhorizons.retrofuturagradle.mcp.ExtractDependencyATsTask
import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar
import net.darkhax.curseforgegradle.CurseForgeGradlePlugin
import net.darkhax.curseforgegradle.TaskPublishCurseForge
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.provider.ListProperty
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

val fentMavenName = "Fent Maven"
val fentMavenUrl = uri("https://maven.fentanylsolutions.org/releases")

fun wawelProp(name: String): String? = providers.gradleProperty(name).orNull?.trim()?.takeIf { it.isNotEmpty() }

fun projectVersionString(): String = project.version.toString()

fun fatVersion(): String = "${projectVersionString()}-fat"

val configuredCurseForgeProjectId = wawelProp("curseForgeProjectId").orEmpty()
val configuredCurseForgeRelations = wawelProp("curseForgeRelations").orEmpty()
val configuredMinecraftVersion = wawelProp("minecraftVersion") ?: "1.7.10"
val configuredUsesMixins = wawelProp("usesMixins")?.equals("true", ignoreCase = true) == true

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

extensions.findByType<GTNHGradlePlugin.GTNHExtension>()
    ?.configuration
    ?.curseForgeProjectId = ""

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
val shadedDevJar = tasks.named<Jar>("shadowJar")
// addon.gradle.kts is evaluated before dependencies.gradle, so create these
// custom configurations here and let dependencies.gradle populate them later.
val depLoaderBootstrap = configurations.maybeCreate("deploader")
val fatImplementation = configurations.maybeCreate("fatImplementation")

val sourceSets = extensions.getByType<SourceSetContainer>()
val mainSourceSet = sourceSets.named("main").get()
val curseForgeReplacementSourceSet = sourceSets.create("curseforgeReplacement") {
    java.srcDir("src/curseforge/java")
    resources.setSrcDirs(emptyList<String>())
    compileClasspath = mainSourceSet.compileClasspath + mainSourceSet.output
    runtimeClasspath = output + compileClasspath
}

tasks.named<JavaCompile>(curseForgeReplacementSourceSet.compileJavaTaskName).configure {
    sourceCompatibility = tasks.named<JavaCompile>("compileJava").get().sourceCompatibility
    targetCompatibility = tasks.named<JavaCompile>("compileJava").get().targetCompatibility
    options.encoding = "UTF-8"
    options.annotationProcessorPath = configurations.named("annotationProcessor").get()
}

val curseForgeReplacementJar = tasks.register<Jar>("curseForgeReplacementJar") {
    dependsOn(tasks.named(curseForgeReplacementSourceSet.classesTaskName))
    archiveClassifier.set("curseforge-replacement-dev")
    from(curseForgeReplacementSourceSet.output.classesDirs) {
        include("org/fentanylsolutions/wawelauth/wawelclient/ClientStartupExtensions.class")
    }
}

val curseForgeLangFile = layout.buildDirectory.file(
    "generated/curseforgeResources/assets/wawelauth/lang/en_US.lang",
)
val generateCurseForgeLang = tasks.register("generateCurseForgeLang") {
    val source = layout.projectDirectory.file("src/main/resources/assets/wawelauth/lang/en_US.lang")

    notCompatibleWithConfigurationCache("Generates a filtered distribution-specific language file.")
    inputs.file(source)
    outputs.file(curseForgeLangFile)

    doLast {
        val output = curseForgeLangFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            source.asFile.readLines()
                .filterNot { it.startsWith("wawelauth.gui.launcher_import.") }
                .joinToString("\n", postfix = "\n"),
        )
    }
}

val curseForgeJar = tasks.register<Jar>("curseForgeJar") {
    group = "build"
    description = "Builds the CurseForge dev jar without launcher account import."
    notCompatibleWithConfigurationCache("Repackages the dev jar with a distribution-specific class.")

    dependsOn(shadedDevJar, curseForgeReplacementJar, generateCurseForgeLang)
    archiveBaseName.set(baseJar.flatMap { it.archiveBaseName })
    archiveVersion.set(providers.provider { projectVersionString() })
    archiveClassifier.set("curseforge-dev")
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

    from(shadedDevJar.flatMap { it.archiveFile }.map { zipTree(it) }) {
        exclude(
            "META-INF/MANIFEST.MF",
            "assets/wawelauth/lang/en_US.lang",
            "org/fentanylsolutions/wawelauth/client/gui/LauncherImportPromptHandler.class",
            "org/fentanylsolutions/wawelauth/client/gui/LauncherImportPromptScreen.class",
            "org/fentanylsolutions/wawelauth/client/gui/LauncherImportPromptScreen\$*.class",
            "org/fentanylsolutions/wawelauth/wawelclient/ClientStartupExtensions.class",
            "org/fentanylsolutions/wawelauth/wawelclient/LauncherAccountImport.class",
            "org/fentanylsolutions/wawelauth/wawelclient/LauncherAccountImport\$*.class",
            "org/fentanylsolutions/wawelauth/wawelclient/LauncherImportSuppression.class",
            "org/fentanylsolutions/wawelauth/wawelclient/LauncherImportSuppression\$*.class",
        )
    }
    from(curseForgeReplacementJar.flatMap { it.archiveFile }.map { zipTree(it) }) {
        exclude("META-INF/MANIFEST.MF")
        include("org/fentanylsolutions/wawelauth/wawelclient/ClientStartupExtensions.class")
    }
    from(curseForgeLangFile) {
        into("assets/wawelauth/lang")
    }
}

val reobfCurseForgeJar = tasks.named<ReobfuscatedJar>("reobfCurseForgeJar") {
    archiveClassifier.set("curseforge")
    getExtraSrgFiles().from(layout.buildDirectory.file("tmp/mixins/mixins.srg"))
}

tasks.named<ProcessResources>("processResources").configure {
    notCompatibleWithConfigurationCache("Expands generated dependency versions and embeds the DepLoader bootstrap.")
    inputs.property("bouncyCastleVersion", bouncyCastleVersion)
    inputs.property("sqliteJdbcVersion", sqliteJdbcVersion)
    filesMatching("META-INF/wawelauth_dependencies.json") {
        expand(
            "bouncyCastleVersion" to bouncyCastleVersion,
            "sqliteJdbcVersion" to sqliteJdbcVersion,
        )
    }
    // DepLoader's small bootstrap jar is embedded; FalsePatternLib itself is
    // neither bundled nor required as an installed mod.
    from(depLoaderBootstrap) {
        rename { "fplib_deploader.jar" }
    }
}

fun Jar.configureFatJar(sourceJar: TaskProvider<out Jar>, classifier: String) {
    group = "build"
    description = "Builds a fully bundled $classifier jar."
    notCompatibleWithConfigurationCache("Unpacks runtime dependencies into a distribution jar.")

    dependsOn(sourceJar)

    archiveBaseName.set(baseJar.flatMap { it.archiveBaseName })
    archiveVersion.set(providers.provider { projectVersionString() })
    archiveClassifier.set(classifier)
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceJar.flatMap { it.archiveFile }.map { zipTree(it) }) {
        exclude(
            "META-INF/MANIFEST.MF",
            "META-INF/wawelauth_dependencies.json",
        )
    }
    from(providers.provider { fatImplementation.map(::zipTree) }) {
        exclude(
            "META-INF/BC*.DSA",
            "META-INF/BC*.RSA",
            "META-INF/BC*.SF",
            "META-INF/MANIFEST.MF",
            "META-INF/versions/**/module-info.class",
        )
    }

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
}

val fatJar = tasks.register<Jar>("fatJar") {
    configureFatJar(reobfJar, "fat")
}

val curseForgeFatJar = tasks.register<Jar>("curseForgeFatJar") {
    configureFatJar(reobfCurseForgeJar, "curseforge-fat")
    description = "Builds the fully bundled CurseForge jar without launcher account import."
}

tasks.named("assemble").configure {
    dependsOn(fatJar, reobfCurseForgeJar, curseForgeFatJar)
}

val verifyCurseForgeJars = tasks.register("verifyCurseForgeJars") {
    group = "verification"
    description = "Verifies CurseForge jars contain no launcher account import implementation."
    notCompatibleWithConfigurationCache("Scans distribution jars for forbidden classes and string markers.")
    dependsOn(reobfCurseForgeJar, curseForgeFatJar)
    inputs.files(reobfCurseForgeJar.flatMap { it.archiveFile }, curseForgeFatJar.flatMap { it.archiveFile })

    doLast {
        val forbiddenEntryFragments = listOf(
            "LauncherAccountImport",
            "LauncherImportPrompt",
            "LauncherImportSuppression",
        )
        val forbiddenMarkers = listOf(
            "LauncherAccountImport",
            "LauncherImportPrompt",
            "LauncherImportSuppression",
            "[launcher-import]",
            "Imported launcher session",
            "launcher session",
            "launcherSession",
            "launcher_import_suppression",
            "wawelauth.gui.launcher_import",
        )

        listOf(reobfCurseForgeJar.get().archiveFile.get().asFile, curseForgeFatJar.get().archiveFile.get().asFile)
            .forEach { archive ->
                ZipFile(archive).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (forbiddenEntryFragments.any(entry.name::contains)) {
                            throw IllegalStateException(
                                "CurseForge archive ${archive.name} contains forbidden entry ${entry.name}",
                            )
                        }
                        val shouldScan = entry.name.startsWith("org/fentanylsolutions/wawelauth/") ||
                            entry.name.startsWith("assets/wawelauth/lang/")
                        if (entry.isDirectory || !shouldScan) {
                            continue
                        }
                        val content = String(zip.getInputStream(entry).readBytes(), StandardCharsets.ISO_8859_1)
                        val marker = forbiddenMarkers.firstOrNull(content::contains)
                        if (marker != null) {
                            throw IllegalStateException(
                                "CurseForge archive ${archive.name} contains forbidden marker '$marker' in ${entry.name}",
                            )
                        }
                    }
                }
            }
    }
}

tasks.named("check").configure {
    dependsOn(verifyCurseForgeJars)
}

fun Any.callNoArg(methodName: String): Any? = javaClass.methods
    .first { it.name == methodName && it.parameterCount == 0 }
    .invoke(this)

@Suppress("UNCHECKED_CAST")
fun configureModrinthFatJar(extension: Any) {
    (extension.callNoArg("getAdditionalFiles") as ListProperty<Any>).add(fatJar)
}

plugins.withId("com.modrinth.minotaur") {
    afterEvaluate {
        extensions.findByName("modrinth")?.let(::configureModrinthFatJar)
    }
    tasks.matching { it.name == "modrinth" }.configureEach {
        dependsOn(fatJar)
    }
}

if (configuredCurseForgeProjectId.isNotEmpty()) {
    plugins.apply(CurseForgeGradlePlugin::class.java)

    val changelogFile = file(System.getenv("CHANGELOG_FILE") ?: "CHANGELOG.md")
    val modVersionProvider = providers.provider {
        val extras = extensions.extraProperties
        if (extras.has("modVersion")) {
            extras.get("modVersion").toString()
        } else {
            projectVersionString()
        }
    }

    val publishCurseforge = tasks.register<TaskPublishCurseForge>("publishCurseforge") {
        group = "publishing"
        description = "Publishes the privacy-restricted WawelAuth jars to CurseForge."
        dependsOn(reobfCurseForgeJar, curseForgeFatJar)

        apiToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        disableVersionDetection()
        val artifact = upload(configuredCurseForgeProjectId, reobfCurseForgeJar.flatMap { it.archiveFile })
        if (changelogFile.exists()) {
            artifact.changelogType = "markdown"
            artifact.changelog = changelogFile
        }
        artifact.releaseType = modVersionProvider.map { if (it.endsWith("-pre")) "beta" else "release" }
        artifact.addGameVersion(configuredMinecraftVersion, "Forge")
        artifact.addModLoader("Forge")
        artifact.addEnvironment("Client", "Server")

        configuredCurseForgeRelations.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { relation ->
                val parts = relation.split(":", limit = 2)
                if (parts.size == 2) {
                    artifact.addRelation(parts[1], parts[0])
                }
            }
        if (configuredUsesMixins) {
            artifact.addRelation("unimixins", "requiredDependency")
        }

        val fatArtifact = artifact.withAdditionalFile(curseForgeFatJar)
        fatArtifact.displayName = providers.provider {
            "${wawelProp("modName") ?: project.name} ${fatVersion()}"
        }
    }

    if (providers.environmentVariable("CURSEFORGE_TOKEN").orNull != null) {
        tasks.named("publish").configure {
            dependsOn(publishCurseforge)
        }
    }
}

extensions.extraProperties.set("publishableFatJar", fatJar)
afterEvaluate {
    val extras = extensions.extraProperties
    if (!extras.has("publishableApiJar")) {
        // 67minecraft's extra-file hook.
        extras.set("publishableApiJar", fatJar)
    }
}

tasks.matching { it.name == "publish67Minecraft" }.configureEach {
    dependsOn(fatJar)
}
