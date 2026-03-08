package org.http4k.typeflows

import io.typeflows.github.workflow.step.marketplace.JavaDistribution.Temurin
import io.typeflows.github.workflow.step.marketplace.JavaVersion.V21

object Versions {
    const val GRADLE = "9.3.1"
    val JDK = Temurin
    val JAVA_VERSION = V21
}
