import io.typeflows.github.workflow.Cron
import io.typeflows.github.workflow.Job
import io.typeflows.github.workflow.Permission.Contents
import io.typeflows.github.workflow.PermissionLevel.Read
import io.typeflows.github.workflow.PermissionLevel.Write
import io.typeflows.github.workflow.Permissions
import io.typeflows.github.workflow.RunsOn.Companion.UBUNTU_LATEST
import io.typeflows.github.workflow.Secrets
import io.typeflows.github.workflow.StrExp
import io.typeflows.github.workflow.Workflow
import io.typeflows.github.workflow.step.RunCommand
import io.typeflows.github.workflow.step.marketplace.Checkout
import io.typeflows.github.workflow.step.marketplace.SetupGradle
import io.typeflows.github.workflow.step.marketplace.SetupJava
import io.typeflows.github.workflow.trigger.Schedule
import io.typeflows.github.workflow.trigger.WorkflowDispatch
import io.typeflows.util.Builder
import org.http4k.typeflows.GithubActionConstants.CHECKOUT
import org.http4k.typeflows.GithubActionConstants.JAVA_VERSION
import org.http4k.typeflows.GithubActionConstants.JDK
import org.http4k.typeflows.GithubActionConstants.SETUP_GRADLE
import org.http4k.typeflows.GithubActionConstants.SETUP_JAVA

class AutoRelease : Builder<Workflow> {
    override fun build() = Workflow("auto-release") {
        displayName = "Auto Release"
        on += Schedule {
            cron += Cron.of("0 12 * * *")
        }
        on += WorkflowDispatch()

        permissions = Permissions(Contents to Read)

        jobs += Job("auto-release", UBUNTU_LATEST) {
            permissions = Permissions(Contents to Write)

            val hasChanges = StrExp.of("steps.changes.outputs.has_changes").isEqualTo("true")

            steps += Checkout(CHECKOUT) {
                name = "Checkout repository"
                token = Secrets.string("TOOLBOX_REPO_TOKEN").toString()
                fetchDepth = 0
            }

            steps += SetupJava(JDK, JAVA_VERSION, SETUP_JAVA) {
                name = "Set up JDK"
            }

            steps += SetupGradle(SETUP_GRADLE) {
                name = "Setup Gradle"
            }

            steps += RunCommand("./gradlew versionCatalogUpdate") {
                name = "Update library versions"
                shell = "bash"
            }

            steps += RunCommand(
                $$"""
                ./gradlew wrapper --gradle-version=latest --distribution-type=bin
                ./gradlew wrapper --gradle-version=latest --distribution-type=bin
                """.trimIndent(),
            ) {
                name = "Update Gradle wrapper"
                shell = "bash"
            }

            steps += RunCommand(
                $$"""
                STANDARDS=src/main/resources/org/http4k/typeflows/Http4kProjectStandards
                cp gradlew "$STANDARDS/gradlew"
                cp gradlew.bat "$STANDARDS/gradlew.bat"
                cp gradle/wrapper/gradle-wrapper.jar "$STANDARDS/gradle/wrapper/gradle-wrapper.jar"
                cp gradle/wrapper/gradle-wrapper.properties "$STANDARDS/gradle/wrapper/gradle-wrapper.properties"
                """.trimIndent(),
            ) {
                name = "Sync wrapper into project standards resources"
                shell = "bash"
            }

            steps += RunCommand(
                $$"""
                if git diff --quiet; then
                  echo "has_changes=false" >> $GITHUB_OUTPUT
                else
                  echo "has_changes=true" >> $GITHUB_OUTPUT
                fi
                """.trimIndent(),
            ) {
                name = "Check for changes"
                id = "changes"
                shell = "bash"
            }

            steps += RunCommand("./gradlew check") {
                name = "Verify build still passes"
                condition = hasChanges
                shell = "bash"
            }

            steps += RunCommand(
                $$"""
                CURRENT=$(jq -r '.http4k.version' version.json)
                IFS='.' read -r MAJOR MINOR _ _ <<< "$CURRENT"
                NEW="$MAJOR.$((MINOR + 1)).0.0"
                echo "Bumping $CURRENT -> $NEW"
                echo "current=$CURRENT" >> $GITHUB_OUTPUT
                echo "new=$NEW" >> $GITHUB_OUTPUT
                """.trimIndent(),
            ) {
                name = "Compute next version"
                id = "version"
                condition = hasChanges
                shell = "bash"
            }

            steps += RunCommand(
                $$"""
                find . -name "*.md" -not -name "CHANGELOG*" -print0 \
                  | xargs -0 perl -i -pe 's/\Q$ENV{CURRENT}\E/$ENV{NEW}/g'
                perl -i -pe 's/\Q$ENV{CURRENT}\E/$ENV{NEW}/g' version.json
                """.trimIndent(),
            ) {
                name = "Rewrite version references"
                condition = hasChanges
                shell = "bash"
                env["CURRENT"] = $$"${{ steps.version.outputs.current }}"
                env["NEW"] = $$"${{ steps.version.outputs.new }}"
            }

            steps += RunCommand(
                $$"""
                perl -i -pe 'BEGIN { $inserted = 0 }
                  if (!$inserted && /^### /) {
                    print "### $ENV{NEW}\n- Upgrade underlying libraries (Gradle etc)\n\n";
                    $inserted = 1;
                  }' CHANGELOG.md
                """.trimIndent(),
            ) {
                name = "Prepend CHANGELOG entry"
                condition = hasChanges
                shell = "bash"
                env["NEW"] = $$"${{ steps.version.outputs.new }}"
            }

            steps += RunCommand(
                $$"""
                git config user.name github-actions
                git config user.email github-actions@github.com
                git commit -am "Release $NEW"
                git push origin HEAD:main
                """.trimIndent(),
            ) {
                name = "Commit and push release"
                condition = hasChanges
                shell = "bash"
                env["NEW"] = $$"${{ steps.version.outputs.new }}"
            }
        }
    }
}
