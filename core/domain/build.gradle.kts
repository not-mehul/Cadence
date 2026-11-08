import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    alias(libs.plugins.cadence.jvm.library)
    alias(libs.plugins.kover)
}

kover {
    reports {
        verify {
            // All cycle maths lives here; it must stay fully covered (docs/PLAN.md §5.7).
            rule("Domain logic is fully covered") {
                minBound(100, CoverageUnit.LINE)
                minBound(100, CoverageUnit.BRANCH)
            }
        }
    }
}
