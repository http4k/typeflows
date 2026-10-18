package org.http4k.typeflows

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.lessThan
import org.gradle.testkit.runner.GradleRunner
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.ACCEPTED
import org.http4k.core.then
import org.http4k.filter.ServerFilters
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.io.path.writeText

class BuildTelemetryPluginTest {

    @Test
    fun `posts telemetry about the build when running in github actions`(@TempDir dir: Path) {
        val endpoint = FakeEndpoint()

        endpoint.use { buildIn(dir, it, ciEnv()).build() }

        val received = endpoint.awaitOne()
        assertThat(received.uri.path, equalTo("/build/telemetry"))
        assertThat(received.header("Content-Type")!!, containsSubstring("application/json"))
        assertThat(received.bodyString(), containsSubstring(""""repository":"http4k/typeflows""""))
        assertThat(received.bodyString(), containsSubstring(""""workflow":"build""""))
        assertThat(received.bodyString(), containsSubstring(""""gradleVersion":""""))
        assertThat(received.bodyString(), containsSubstring(""""os":""""))
        assertThat(received.bodyString(), containsSubstring(""""cores":"""))
        assertThat(received.bodyString(), containsSubstring(""""timestamp":""""))
    }

    @Test
    fun `reports every task that the build actually executed`(@TempDir dir: Path) {
        val endpoint = FakeEndpoint()

        endpoint.use { buildIn(dir, it, ciEnv()).build() }

        assertThat(
            endpoint.awaitOne().bodyString(),
            containsSubstring(""""path":":work","outcome":"success"""")
        )
    }

    @Test
    fun `stays silent when not running in github actions`(@TempDir dir: Path) {
        val endpoint = FakeEndpoint()

        endpoint.use { buildIn(dir, it, emptyMap()).build() }

        assertThat(endpoint.drain().size, equalTo(0))
    }

    @Test
    fun `times this build rather than the one that seeded the configuration cache`(@TempDir dir: Path) {
        val endpoint = FakeEndpoint()
        val gap = 2000L

        endpoint.use {
            buildIn(dir, it, ciEnv(), "--configuration-cache").build()
            Thread.sleep(gap)
            buildIn(dir, it, ciEnv(), "--configuration-cache").build()
        }

        endpoint.awaitOne()
        val cacheHit = endpoint.awaitOne()
        assertThat(durationMsIn(cacheHit.bodyString()), lessThan(gap))
    }

    private fun durationMsIn(body: String) =
        Regex(""""durationMs":(\d+)""").find(body)!!.groupValues[1].toLong()

    private val GITHUB_MARKERS = setOf("GITHUB_ACTIONS", "GITHUB_REPOSITORY", "GITHUB_WORKFLOW")

    private fun ciEnv() = mapOf(
        "GITHUB_ACTIONS" to "true",
        "GITHUB_REPOSITORY" to "http4k/typeflows",
        "GITHUB_WORKFLOW" to "build"
    )

    private fun buildIn(
        dir: Path,
        endpoint: FakeEndpoint,
        env: Map<String, String>,
        vararg extraArgs: String
    ): GradleRunner {
        dir.resolve("settings.gradle.kts").writeText("""rootProject.name = "example"""")
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins { id("org.http4k.build") }
            tasks.register("work") { doLast { println("working") } }
            """.trimIndent()
        )
        return GradleRunner.create()
            .withProjectDir(dir.toFile())
            .withPluginClasspath()
            .withEnvironment(System.getenv() - GITHUB_MARKERS + env)
            .withArguments("work", "-Phttp4k.telemetry.url=${endpoint.url}", *extraArgs)
    }
}

class FakeEndpoint(status: Status = ACCEPTED) : AutoCloseable {
    private val received = LinkedBlockingQueue<Request>()

    private val server = ServerFilters.GZip()
        // the request body is a stream that closes when the handler returns - read it now
        .then { request: Request -> received.add(request.body(request.bodyString())); Response(status) }
        .asServer(SunHttp(0))
        .start()

    val url = "http://localhost:${server.port()}/build/telemetry"

    fun awaitOne(): Request = received.poll(10, SECONDS) ?: error("no telemetry received")

    fun drain(): List<Request> = received.toList()

    override fun close() {
        server.stop()
    }
}
