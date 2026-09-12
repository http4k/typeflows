package org.http4k.typeflows

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import org.http4k.core.HttpHandler
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.ACCEPTED
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.zip.GZIPInputStream

class TelemetryPostingTest {

    private val endpoint = "https://toolbox.http4k.org/build/telemetry"

    @Test
    fun `posts the telemetry as gzipped json`() {
        val sent = mutableListOf<Request>()

        postTelemetry(recording(sent), endpoint, aReport())

        val request = sent.single()
        assertThat(request.method, equalTo(POST))
        assertThat(request.uri.toString(), equalTo(endpoint))
        assertThat(request.header("Content-Type")!!, containsSubstring("application/json"))
        assertThat(request.header("Content-Encoding"), equalTo("gzip"))
        assertThat(request.gunzipped(), containsSubstring(""""repository":"http4k/typeflows""""))
        assertThat(request.gunzipped(), containsSubstring(""""path":":work""""))
    }

    @Test
    fun `ignores any other response from the endpoint`() {
        val failing: HttpHandler = { Response(INTERNAL_SERVER_ERROR) }

        postTelemetry(failing, endpoint, aReport())
    }

    @Test
    fun `ignores a failure to reach the endpoint`() {
        val unreachable: HttpHandler = { throw java.net.ConnectException("no route to host") }

        postTelemetry(unreachable, endpoint, aReport())
    }

    private fun aReport() = buildTelemetryReport(
        clock = FakeClock(Instant.parse("2026-09-12T09:00:00Z")),
        startedAt = Instant.parse("2026-09-12T09:00:00Z"),
        build = BuildDetails("typeflows", "1.20.0.0", "9.6.1", "build"),
        github = GithubDetails("http4k/typeflows", "build", "refs/heads/main", "abc123", "daviddenton"),
        tasks = listOf(ExecutedTask(":work", "success", 10))
    )

    private fun recording(sent: MutableList<Request>): HttpHandler = { request ->
        sent += request
        Response(ACCEPTED)
    }

    private fun Request.gunzipped() = GZIPInputStream(body.stream).readBytes().decodeToString()
}
