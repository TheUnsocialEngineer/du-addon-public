import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.kotlin.dsl.loom

plugins {
    // Must be >= Loom used by any jar-in-jar mod (e.g. ui-utils-advanced built with 1.15.3).
    id("fabric-loom") version "1.15.3"
}

base {
    archivesName = "${properties["archives_base_name"]}-${properties["minecraft_version"]}"
    version = properties["mod_version"] as String
    group = properties["maven_group"] as String
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

dependencies {
    // Fabric
    minecraft("com.mojang:minecraft:${properties["minecraft_version"] as String}")
    mappings("net.fabricmc:yarn:${properties["yarn_mappings"] as String}:v2")
    modImplementation("net.fabricmc:fabric-loader:${properties["loader_version"] as String}")

    // Meteor
    modImplementation("meteordevelopment:meteor-client:${properties["minecraft_version"] as String}-SNAPSHOT")
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    // Optional UI-Utils bundle: do NOT use Loom `include(files(...))` — raw file JARs are not Gradle "module"
    // components and fail :processIncludeJars. We nest manually (META-INF/jars + fabric.mod.json "jars"), see processResources.
}

tasks {
    processResources {
        val uiUtilsJar = file("libs/ui-utils-advanced.jar")

        if (uiUtilsJar.exists()) {
            inputs.file(uiUtilsJar)
            from(uiUtilsJar) {
                into("META-INF/jars")
            }
        }

        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to project.property("minecraft_version"),
        )

        inputs.properties(propertyMap)

        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }

        // Source fabric.mod.json keeps valid `"jars": []` so Loom/IDE can parse it; patch after expand.
        doLast {
            val out = layout.buildDirectory.get().dir("resources/main").file("fabric.mod.json").asFile
            if (!out.exists()) return@doLast
            @Suppress("UNCHECKED_CAST")
            val root = JsonSlurper().parse(out) as MutableMap<String, Any>
            root["jars"] = if (uiUtilsJar.exists()) {
                listOf(mapOf("file" to "META-INF/jars/ui-utils-advanced.jar"))
            } else {
                emptyList<Any>()
            }
            out.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(root)))
        }
    }

    jar {
        val licenseSuffix = project.base.archivesName.get()
        from("LICENSE") {
            rename { "${it}_${licenseSuffix}" }
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
    }
}
