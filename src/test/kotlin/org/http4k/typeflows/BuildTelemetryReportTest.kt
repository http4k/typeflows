package org.http4k.typeflows

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Duration.ofMillis
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset.UTC

class BuildTelemetryReportTest {

    @Test
    fun `stamps the report with the time the build finished`() {
        val clock = FakeClock(Instant.parse("2026-09-12T09:00:00Z"))
        clock.tick(ofMillis(1500))

        val report = buildTelemetryReport(clock, Instant.parse("2026-09-12T09:00:00Z"), tasks = listOf(aTask()))

        assertThat(report.timestamp, equalTo("2026-09-12T09:00:01.500Z"))
    }

    @Test
    fun `measures how long the build took using the clock`() {
        val startedAt = Instant.parse("2026-09-12T09:00:00Z")
        val clock = FakeClock(startedAt)
        clock.tick(Duration.ofMinutes(3))

        val report = buildTelemetryReport(clock, startedAt, tasks = listOf(aTask()))

        assertThat(report.durationMs, equalTo(180_000L))
    }

    @Test
    fun `reports the build as failed when any task failed`() {
        val report = buildTelemetryReport(
            aClock(), Instant.EPOCH,
            tasks = listOf(aTask(outcome = "success"), aTask(outcome = "failed"))
        )

        assertThat(report.successful, equalTo(false))
    }

    @Test
    fun `reports the build as successful when tasks were cached up-to-date or skipped`() {
        val report = buildTelemetryReport(
            aClock(), Instant.EPOCH,
            tasks = listOf(aTask(outcome = "from-cache"), aTask(outcome = "up-to-date"), aTask(outcome = "skipped"))
        )

        assertThat(report.successful, equalTo(true))
    }

    private fun aClock() = FakeClock(Instant.parse("2026-09-12T09:00:00Z"))

    private fun aTask(path: String = ":work", outcome: String = "success", durationMs: Long = 10) =
        ExecutedTask(path, outcome, durationMs)
}

class FakeClock(private var now: Instant) : Clock() {
    override fun instant() = now
    override fun getZone(): ZoneId = UTC
    override fun withZone(zone: ZoneId): Clock = this
    fun tick(duration: Duration) {
        now += duration
    }
}
