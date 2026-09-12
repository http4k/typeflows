import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.version.catalog.update)
    alias(libs.plugins.typeflows)
    alias(libs.plugins.vannitech)
    `java-library`
    `java-gradle-plugin`
    signing
    `maven-publish`
}

repositories {
    mavenCentral()
}

configure<MavenPublishBaseExtension> {
    configure<PublishingExtension> {
        val enableSigning = project.findProperty("sign") == "true"

        if (enableSigning) {
            apply(plugin = "signing")
            signing {
                val signingKey = project.findProperty("signingKey")?.toString()
                val signingPassword = project.findProperty("signingPassword")?.toString()
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(project.the<PublishingExtension>().publications)
            }
        }

        publishToMavenCentral(automaticRelease = true)

        System.getenv("GITHUB_TOKEN")?.also { token ->
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/http4k/typeflows")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR")
                        password = token
                    }
                }
            }
        }

        coordinates(
            "org.http4k.standards",
            project.name,
            project.findProperty("releaseVersion")?.toString() ?: "LOCAL"
        )

        pom {
            withXml {
                asNode().appendNode("name", project.name)
                asNode().appendNode("description", project.description)
                asNode().appendNode("url", "https://http4k.org")
                asNode().appendNode("developers").apply {
                    appendNode("developer").appendNode("name", "David Denton").parent()
                        .appendNode("email", "david@http4k.org")
                    appendNode("developer").appendNode("name", "Ivan Sanchez").parent()
                        .appendNode("email", "ivan@http4k.org")
                }
                asNode().appendNode("scm")
                    .appendNode("url", "https://github.com/http4k/typeflows").parent()
                    .appendNode("connection", "scm:git:git@github.com:http4k/typeflows.git").parent()
                    .appendNode("developerConnection", "scm:git:git@github.com:http4k/standards.git")

                asNode().appendNode("licenses").appendNode("license")
                    .appendNode("name", "Apache-2.0").parent()
                    .appendNode("url", "http://http4k.org/commercial-license")
            }
        }
    }
}

gradlePlugin {
    plugins {
        create("buildTelemetry") {
            id = "org.http4k.build"
            implementationClass = "org.http4k.typeflows.BuildTelemetryPlugin"
        }
    }
}

dependencies {
    api(libs.typeflows.github)
    api(libs.typeflows.github.marketplace)

    implementation(libs.kotshi.api)
    ksp(libs.kotshi.compiler)

    typeflowsApi(project(":"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.hamkrest)
    testImplementation(gradleTestKit())
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// the sources jar exists only to satisfy Maven Central validation - it ships no source
tasks.withType<Jar>().configureEach {
    if ("ourcesJar" in name) exclude("**/*.kt", "**/*.java")
}

