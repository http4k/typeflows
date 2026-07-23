import io.typeflows.github.workflow.Job
import io.typeflows.github.workflow.RunsOn
import io.typeflows.github.workflow.Secrets
import io.typeflows.github.workflow.Workflow
import io.typeflows.github.workflow.step.RunScript
import io.typeflows.github.workflow.step.UseAction
import io.typeflows.github.workflow.step.marketplace.Checkout
import io.typeflows.github.workflow.step.marketplace.JavaDistribution.Adopt
import io.typeflows.github.workflow.step.marketplace.JavaVersion.V21
import io.typeflows.github.workflow.step.marketplace.SetupGradle
import io.typeflows.github.workflow.step.marketplace.SetupJava
import io.typeflows.github.workflow.trigger.RepositoryDispatch
import io.typeflows.util.Builder
import org.http4k.typeflows.GithubActionConstants.CHECKOUT
import org.http4k.typeflows.GithubActionConstants.CREATE_RELEASE
import org.http4k.typeflows.GithubActionConstants.SETUP_GRADLE
import org.http4k.typeflows.GithubActionConstants.SETUP_JAVA

class UploadRelease : Builder<Workflow> {
    override fun build() = Workflow("upload-release") {
        displayName = "Publish Artifacts"
        on += RepositoryDispatch("release")

        jobs += Job("release", RunsOn.UBUNTU_LATEST) {

            name = "Release"
            steps += Checkout(CHECKOUT) {
                with["ref"] = $$"${{ github.event.client_payload.tag }}"
            }

            steps += SetupJava(Adopt, V21, SETUP_JAVA)

            steps += SetupGradle(SETUP_GRADLE)

            steps += RunScript("scripts/publish-artifacts.sh") {
                name = "Publish"
                env["RELEASE_VERSION"] = $$"${{ github.event.client_payload.tag }}"
                env["SIGNING_KEY"] = $$"${{ secrets.SIGNING_KEY }}"
                env["SIGNING_PASSWORD"] = $$"${{ secrets.SIGNING_PASSWORD }}"
                env["ORG_GRADLE_PROJECT_mavenCentralUsername"] = $$"${{ secrets.MAVEN_CENTRAL_USERNAME }}"
                env["ORG_GRADLE_PROJECT_mavenCentralPassword"] = $$"${{ secrets.MAVEN_CENTRAL_PASSWORD }}"
                env["ORG_GRADLE_PROJECT_signingInMemoryKey"] = $$"${{ secrets.SIGNING_KEY }}"
                env["ORG_GRADLE_PROJECT_signingInMemoryKeyPassword"] = $$"${{ secrets.SIGNING_PASSWORD }}"
            }

            steps += RunScript("scripts/build-release-note.sh") {
                name = "Build release note"
                env["RELEASE_VERSION"] = $$"${{ github.event.client_payload.tag }}"
            }

            steps += UseAction(CREATE_RELEASE) {
                name = "Create Release"
                env["GITHUB_TOKEN"] = Secrets.GITHUB_TOKEN
                with["tag_name"] = $$"${{ github.event.client_payload.tag }}"
                with["release_name"] = $$"${{ github.event.client_payload.tag }}"
                with["body_path"] = "NOTE.md"
                with["draft"] = "false"
                with["prerelease"] = "false"
            }
        }
    }
}
