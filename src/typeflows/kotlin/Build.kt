import io.typeflows.github.workflow.Job
import io.typeflows.github.workflow.Permission.Contents
import io.typeflows.github.workflow.PermissionLevel.Write
import io.typeflows.github.workflow.Permissions
import io.typeflows.github.workflow.RunsOn.Companion.UBUNTU_LATEST
import io.typeflows.github.workflow.Secrets
import io.typeflows.github.workflow.StrExp
import io.typeflows.github.workflow.Workflow
import io.typeflows.github.workflow.step.RunCommand
import io.typeflows.github.workflow.step.RunScript
import io.typeflows.github.workflow.step.SendRepositoryDispatch
import io.typeflows.github.workflow.step.marketplace.Checkout
import io.typeflows.github.workflow.step.marketplace.JavaDistribution.Adopt
import io.typeflows.github.workflow.step.marketplace.JavaVersion.V21
import io.typeflows.github.workflow.step.marketplace.SetupGradle
import io.typeflows.github.workflow.step.marketplace.SetupJava
import io.typeflows.github.workflow.trigger.Paths
import io.typeflows.github.workflow.trigger.PullRequest
import io.typeflows.github.workflow.trigger.Push
import io.typeflows.github.workflow.trigger.WorkflowDispatch
import io.typeflows.util.Builder
import org.http4k.typeflows.GithubActionConstants.CHECKOUT
import org.http4k.typeflows.GithubActionConstants.SETUP_GRADLE
import org.http4k.typeflows.GithubActionConstants.SETUP_JAVA

class Build : Builder<Workflow> {
    override fun build() = Workflow("build") {
        displayName = "Build in CI"
        on += WorkflowDispatch()

        permissions = Permissions(Contents to Write)

        on += Push {
            paths = Paths.Ignore("**/.md")
        }

        on += PullRequest {
            paths = Paths.Ignore("**/.md")
        }

        jobs += Job("build", UBUNTU_LATEST) {
            name = "Build and Test"

            steps += Checkout(CHECKOUT)

            steps += SetupJava(Adopt, V21, SETUP_JAVA)

            steps += SetupGradle(SETUP_GRADLE)

            steps += RunCommand("./gradlew check --info") {
                name = "Build"
            }

            val token = Secrets.string("TOOLBOX_REPO_TOKEN")

            steps += RunScript("scripts/tag-if-required.sh") {
                name = "Release (if required)"
                id = "get-version"
                condition = StrExp.of("github.ref").isEqualTo("refs/heads/main")
                env["GH_TOKEN"] = token
            }

            steps += SendRepositoryDispatch(
                "release", token,
                mapOf("tag" to StrExp.of("steps.get-version.outputs.tag").toString())
            ) {
                condition = StrExp.of("github.ref").isEqualTo("refs/heads/main").and(
                    StrExp.of("steps.get-version.outputs.tag-created").isEqualTo("true")
                )
            }
        }
    }
}
