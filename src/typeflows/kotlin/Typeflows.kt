import io.typeflows.TypeflowsRepo
import io.typeflows.github.DotGitHub
import io.typeflows.github.TypeflowsGitHubRepo
import io.typeflows.github.visualisation.WorkflowVisualisations
import io.typeflows.github.workflow.Cron
import io.typeflows.github.workflow.step.RunCommand
import io.typeflows.util.Builder
import org.http4k.typeflows.Http4kProjectStandards
import org.http4k.typeflows.UpdateGradleProjectDependencies

class Typeflows : Builder<TypeflowsRepo> {
    override fun build() = TypeflowsGitHubRepo {
        dotGithub = DotGitHub {
            workflows += Build()
            workflows += UploadRelease()
            workflows += AutoRelease()

            workflows += UpdateGradleProjectDependencies(
                "update-dependencies",
                Cron.of("0 12 * * 5"),
                RunCommand("./gradlew check")
            )

            files += WorkflowVisualisations(workflows)
        }
        files += Http4kProjectStandards()
    }
}
