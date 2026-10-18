package org.http4k.typeflows

import com.squareup.moshi.JsonAdapter
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationResult
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status.Companion.UNPROCESSABLE_ENTITY
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.format.ConfigurableMoshi
import org.http4k.format.standardConfig
import se.ansman.kotshi.JsonSerializable
import se.ansman.kotshi.KotshiJsonAdapterFactory
import java.lang.System.getProperty
import java.lang.System.getenv
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration
import java.time.Duration.ofSeconds
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

const val TELEMETRY_ENDPOINT = "https://toolbox.http4k.org/build/telemetry"

object TelemetryMoshi : ConfigurableMoshi(standardConfig(TelemetryJsonAdapterFactory).done())

@KotshiJsonAdapterFactory
object TelemetryJsonAdapterFactory : JsonAdapter.Factory by KotshiTelemetryJsonAdapterFactory

@JsonSerializable
data class BuildTelemetryReport(
    val timestamp: String,
    val durationMs: Long,
    val successful: Boolean,
    val build: BuildDetails?,
    val github: GithubDetails,
    val machine: MachineDetails,
    val tasks: List<ExecutedTask>
)

@JsonSerializable
data class BuildDetails(
    val project: String,
    val version: String,
    val gradleVersion: String,
    val requestedTasks: String
)

@JsonSerializable
data class GithubDetails(
    val repository: String?,
    val workflow: String?,
    val ref: String?,
    val sha: String?,
    val actor: String?
) {
    companion object {
        fun detect(env: Map<String, String>) = GithubDetails(
            env["GITHUB_REPOSITORY"],
            env["GITHUB_WORKFLOW"],
            env["GITHUB_REF"],
            env["GITHUB_SHA"],
            env["GITHUB_ACTOR"]
        )
    }
}

@JsonSerializable
data class MachineDetails(
    val os: String,
    val osVersion: String,
    val arch: String,
    val cores: Int,
    val maxMemoryMb: Long,
    val java: String,
    val jvm: String,
) {
    companion object {
        fun detect() = MachineDetails(
            getProperty("os.name"),
            getProperty("os.version"),
            getProperty("os.arch"),
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().maxMemory() / 1024 / 1024,
            getProperty("java.version"),
            getProperty("java.vm.name")
        )
    }
}

@JsonSerializable
data class ExecutedTask(val path: String, val outcome: String, val durationMs: Long)

fun buildTelemetryReport(
    clock: Clock,
    startedAt: Instant,
    build: BuildDetails? = null,
    github: GithubDetails = GithubDetails.detect(getenv()),
    machine: MachineDetails = MachineDetails.detect(),
    tasks: List<ExecutedTask> = emptyList()
) = clock.instant().let { now ->
    BuildTelemetryReport(
        timestamp = now.toString(),
        durationMs = Duration.between(startedAt, now).toMillis(),
        successful = tasks.none { it.outcome == "failed" },
        build = build,
        github = github,
        machine = machine,
        tasks = tasks
    )
}

abstract class BuildTelemetryPlugin @Inject constructor(
    private val events: BuildEventsListenerRegistry
) : Plugin<Project> {

    override fun apply(target: Project) {
        if (getenv("GITHUB_ACTIONS") == null) return
        if (target != target.rootProject) return

        val service =
            target.gradle.sharedServices.registerIfAbsent("http4kBuildTelemetry", BuildTelemetry::class.java) { spec ->
                spec.parameters.endpoint.set(target.telemetryUrl())
                spec.parameters.project.set(target.name)
                spec.parameters.version.set(target.version.toString())
                spec.parameters.gradleVersion.set(target.gradle.gradleVersion)
                spec.parameters.requestedTasks.set(target.gradle.startParameter.taskNames.joinToString(" "))
            }

        events.onTaskCompletion(service)
    }
}

private fun Project.telemetryUrl() =
    findProperty("http4k.telemetry.url")?.toString() ?: TELEMETRY_ENDPOINT

abstract class BuildTelemetry : BuildService<BuildTelemetry.Params>, OperationCompletionListener, AutoCloseable {

    interface Params : BuildServiceParameters {
        val endpoint: Property<String>
        val project: Property<String>
        val version: Property<String>
        val gradleVersion: Property<String>
        val requestedTasks: Property<String>
    }

    private val http: HttpHandler = JavaHttpClient(
        HttpClient.newBuilder().connectTimeout(ofSeconds(5)).build()
    ) { it.timeout(ofSeconds(30)) }

    private val clock: Clock = Clock.systemUTC()

    private val startedAt = clock.instant()

    private val tasks = ConcurrentLinkedQueue<ExecutedTask>()

    override fun onFinish(event: FinishEvent) {
        if (event !is TaskFinishEvent) return
        tasks += ExecutedTask(
            event.descriptor.taskPath,
            event.result.outcome(),
            event.result.endTime - event.result.startTime
        )
    }

    override fun close() = postTelemetry(http, parameters.endpoint.get(), report())

    private fun report() = buildTelemetryReport(
        clock = clock,
        startedAt = startedAt,
        build = BuildDetails(
            parameters.project.get(),
            parameters.version.get(),
            parameters.gradleVersion.get(),
            parameters.requestedTasks.get()
        ),
        tasks = tasks.toList()
    )
}

fun postTelemetry(http: HttpHandler, endpoint: String, report: BuildTelemetryReport) {
    val response = runCatching {
        ClientFilters.GZip().then(http)(with(TelemetryMoshi) { Request(POST, endpoint).json(report) })
    }.getOrNull() ?: return

    if (response.status == UNPROCESSABLE_ENTITY) {
        throw GradleException("$endpoint rejected the build telemetry: ${response.bodyString()}")
    }
}

private fun TaskOperationResult.outcome() = when (this) {
    is TaskFailureResult -> "failed"
    is TaskSkippedResult -> "skipped"
    is TaskSuccessResult if isFromCache -> "from-cache"
    is TaskSuccessResult if isUpToDate -> "up-to-date"
    else -> "success"
}

