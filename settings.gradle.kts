rootProject.name = "argos"

include(":argos")
project(":argos").projectDir = file("argos")
include(":argos-demo")
include(":argos-completion")
include(":argos-compare:test-argos")
project(":argos-compare:test-argos").projectDir = file("argos-compare/argos")
include(":argos-compare:test-clikt")
project(":argos-compare:test-clikt").projectDir = file("argos-compare/clickt")
include(":argos-compare:test-kotlin")
project(":argos-compare:test-kotlin").projectDir = file("argos-compare/kotlin")
include(":argos-compare:test-picocli")
project(":argos-compare:test-picocli").projectDir = file("argos-compare/picocli")
