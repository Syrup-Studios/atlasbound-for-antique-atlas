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
    maven("https://raw.githubusercontent.com/Rasa-Novum/Rosetta_Library/maven/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    modImplementation("folk.sisby:surveyor:${property("deps.surveyor")}")
    modImplementation("folk.sisby:antique-atlas:${property("deps.antique_atlas")}")
    val rosetta = "com.rasanovum.rosetta:rosetta-1.21.1-fabric:${property("deps.rosetta")}"
    modImplementation(rosetta)
    include(rosetta)
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
    runConfigs.configureEach {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run")
        jvmArguments.add("-Dmixin.debug.export=true")
    }
    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1")
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
}

tasks.processResources {
    val values = mapOf(
        "version" to project.property("mod.version"),
        "modId" to project.property("mod.id"),
        "modName" to project.property("mod.name"),
        "modDescription" to project.property("mod.description"),
        "license" to project.property("mod.license"),
        "mc" to project.property("mod.fabric_mc_range"),
        "loader" to project.property("deps.fabric_loader"),
        "surveyor" to project.property("deps.surveyor"),
        "antiqueAtlas" to project.property("deps.antique_atlas"),
        "rosetta" to project.property("deps.rosetta"),
        "authors" to project.property("mod.authors"),
        "homepage" to project.property("mod.homepage"),
        "issues" to project.property("mod.issues"),
        "sources" to project.property("mod.sources"),
    )
    inputs.properties(values)
    filesMatching("fabric.mod.json") { expand(values) }
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    description = "Builds mod jars and copies results to `build/libs/{mod version}/`"
    inputs.property("version", project.property("mod.version"))
    from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    dependsOn("build")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
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
val uploadVersion = "${property("mod.version")}+$minecraftVersion-$loader"
val compatibleVersions = stonecutter.properties.rawOrNull("mod.mc_releases")
    ?.asList().orEmpty().map { it.toString() }

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
    displayName = "${property("mod.name")} ${property("mod.version")} - ${if (loader == "neoforge") "NeoForge" else "Fabric"} $minecraftVersion"
    changelog = providers.fileContents(rootProject.layout.projectDirectory.file("CHANGELOG.md")).asText
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
        compatibleVersions.forEach { minecraftVersions.add(it) }
        environment = CLIENT_AND_SERVER
        requires("surveyor")
        requires("antique-atlas-4")
        if (loader == "fabric") requires("fabric-api")
        else {
            requires("connector")
            requires("forgified-fabric-api")
        }
    }
    curseforge {
        projectId = curseForgeId.ifBlank { "0" }
        accessToken = curseForgeToken
        compatibleVersions.forEach { minecraftVersions.add(it) }
        client = true
        server = true
        requires("surveyor-map-framework")
        requires("antique-atlas-4")
        if (loader == "fabric") requires("fabric-api")
        else {
            requires("sinytra-connector")
            requires("forgified-fabric-api")
        }
    }
}
