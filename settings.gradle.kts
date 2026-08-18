plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "banteng"

include("errorprone-checks")
include("rewrite-recipes")
