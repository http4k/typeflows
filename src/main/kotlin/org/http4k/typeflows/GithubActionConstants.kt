package org.http4k.typeflows

import io.typeflows.github.workflow.step.marketplace.JavaDistribution.Temurin
import io.typeflows.github.workflow.step.marketplace.JavaVersion.V21
import io.typeflows.github.workflow.step.marketplace.Version

object GithubActionConstants {
    val JDK = Temurin
    val JAVA_VERSION = V21

    const val CREATE_RELEASE = "actions/create-release@0cb9c9b65d5d1901c1f53e5e66eaf4afd303e70e" // v1.1.4
    const val CREATE_PULL_REQUEST = "peter-evans/create-pull-request@5f6978faf089d4d20b00c7766989d076bb2fc7f1" // v8.1.1

    val CHECKOUT = Version.sha("3d3c42e5aac5ba805825da76410c181273ba90b1") // actions/checkout v7.0.1
    val SETUP_JAVA = Version.sha("de7274f081f381c8f8158605e0321c36c376e2e6") // actions/setup-java v6.0.1
    val SETUP_GRADLE = Version.sha("3f5f9adaf7d9fecd50b5935e54106014257a94e6") // gradle/actions/setup-gradle v6.4.0
}
