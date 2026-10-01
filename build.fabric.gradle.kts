plugins {
    id("dev.kikugie.loom-back-compat")
    id("me.modmuss50.mod-publish-plugin")
}

val minecraftVersion = stonecutter.current.version
val loader = stonecutter.current.project.substringAfter('-')
version = "${property("mod.version")}+$minecraftVersion-$loader"
group = property("mod.group") as String
base.archivesName = property("mod.id") as String

repositories {
    maven("https://repo.sleeping.town/")
    maven("https://api.modrinth.com/maven")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    modImplementation("folk.sisby:surveyor:${property("deps.surveyor")}")
    modImplementation("folk.sisby:antique-atlas:${property("deps.antique_atlas")}")
    modImplementation("maven.modrinth:aR9FhY20:${property("deps.aa4_atlas_artifact")}")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
    runConfigs.configureEach {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run/$loader")
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE.md")) { into("META-INF") }
    from(rootProject.file("NOTICE.md")) { into("META-INF") }
    from(rootProject.file("LICENSES")) { into("META-INF/LICENSES") }
}

tasks.processResources {
    val values = mapOf(
        "version" to project.property("mod.version"),
        "modId" to project.property("mod.id"),
        "modName" to project.property("mod.name"),
        "modDescription" to project.property("mod.description"),
        "license" to project.property("mod.license"),
        "mc" to project.property("mod.mc_range"),
        "loader" to project.property("deps.fabric_loader"),
        "surveyor" to project.property("deps.surveyor"),
        "antiqueAtlas" to project.property("deps.antique_atlas"),
        "aa4Atlas" to project.property("deps.aa4_atlas"),
    )
    inputs.properties(values)
    filesMatching("fabric.mod.json") { expand(values) }
}

tasks.register<Copy>("buildAndCollect") {
    from(loomx.modJar, loomx.modSourcesJar)
    into(rootProject.layout.buildDirectory.dir("libs/${project.version}"))
}

val modrinthToken = providers.gradleProperty("publish.modrinth_token")
    .orElse(providers.gradleProperty("MODRINTH_TOKEN"))
    .orElse(providers.environmentVariable("MODRINTH_TOKEN"))
val curseForgeToken = providers.gradleProperty("publish.curseforge_token")
    .orElse(providers.gradleProperty("CURSEFORGE_TOKEN"))
    .orElse(providers.environmentVariable("CURSEFORGE_TOKEN"))
val modrinthId = property("publish.modrinth").toString().trim()
val curseForgeId = property("publish.curseforge").toString().trim()
val publishDryRun = providers.gradleProperty("publish.dry_run").map {
    when (it) {
        "true" -> true
        "false" -> false
        else -> throw GradleException("publish.dry_run must be true or false")
    }
}.orElse(!modrinthToken.isPresent || !curseForgeToken.isPresent)
val loaderName = if (loader == "neoforge") "NeoForge (Connector)" else "Fabric"
val uploadVersion = "${property("mod.version")}+$minecraftVersion-$loader"
val requiredDependencies = buildList {
    add("Surveyor ${property("deps.surveyor")}")
    add("Antique Atlas 4 ${property("deps.antique_atlas")} (client only)")
    add("AA4 Atlas ${property("deps.aa4_atlas")} (1.1.2+1.21): https://modrinth.com/mod/aa4-atlas/version/${property("deps.aa4_atlas_artifact")}")
    if (loader == "fabric") add("Fabric API ${property("deps.fabric_api")}")
    else add("Sinytra Connector and Forgified Fabric API (NeoForge only)")
}

if (!publishDryRun.get()) {
    if (modrinthId.isBlank() || curseForgeId.isBlank()) {
        throw GradleException("Set publish.modrinth and publish.curseforge in stonecutter.properties.yaml before live publishing")
    }
    if (!modrinthToken.isPresent || !curseForgeToken.isPresent) {
        throw GradleException("Live publishing needs MODRINTH_TOKEN and CURSEFORGE_TOKEN (or Gradle publish.*_token properties)")
    }
}

publishMods {
    file = loomx.modJar.flatMap { it.archiveFile }
    dryRun.set(publishDryRun)
    version = uploadVersion
    displayName = "${property("mod.name")} ${property("mod.version")} - ${if (loader == "neoforge") "Neoforge" else "Fabric"} $minecraftVersion"
    changelog = "${providers.fileContents(rootProject.layout.projectDirectory.file("CHANGELOG.md")).asText.get()}\n\n## Required dependencies for Minecraft $minecraftVersion ($loaderName)\n\n${requiredDependencies.joinToString("\n") { "- $it" }}"
    type = when (property("publish.release_type").toString().lowercase()) {
        "stable" -> STABLE
        "beta" -> BETA
        "alpha" -> ALPHA
        else -> throw GradleException("publish.release_type must be stable, beta, or alpha")
    }
    modLoaders.add(loader)

    modrinth {
        projectId = modrinthId.ifBlank { "00000000" }
        accessToken = modrinthToken
        minecraftVersions.add(minecraftVersion)
        environment = CLIENT_AND_SERVER
        requires("surveyor")
        requires("antique-atlas-4")
        requires("aa4-atlas")
        if (loader == "fabric") requires("fabric-api")
        else {
            requires("connector")
            requires("forgified-fabric-api")
        }
        additionalFile(tasks.named("sourcesJar")) {
            type = SOURCES_JAR
        }
    }
    curseforge {
        projectId = curseForgeId.ifBlank { "0" }
        accessToken = curseForgeToken
        minecraftVersions.add(minecraftVersion)
        client = true
        server = true
        requires("surveyor-map-framework")
        requires("antique-atlas-4")
        if (loader == "fabric") requires("fabric-api")
        else {
            requires("sinytra-connector")
            requires("forgified-fabric-api")
        }
        additionalFile(tasks.named("sourcesJar").get()) {
            name = "${property("mod.id")}-${uploadVersion}-sources.jar"
        }
    }
}
