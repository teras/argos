# Argos Comparison Tests

This module contains size comparison tests between Argos and other popular CLI parsing libraries.

## Structure

- **test-argos**: Test using Argos library
- **test-clikt**: Test using Clikt library
- **test-kotlin**: Control test (pure Kotlin, minimal CLI parsing)
- **test-picocli**: Test using picocli library (JVM only)

## Purpose

These tests measure the overhead introduced by each CLI parsing library by comparing against a minimal baseline (pure Kotlin).

## Building

From the root project:

```bash
# Build all native executables (release, Linux x64)
./gradlew :argos-compare:test-argos:linkReleaseExecutableLinuxX64 \
          :argos-compare:test-clikt:linkReleaseExecutableLinuxX64 \
          :argos-compare:test-kotlin:linkReleaseExecutableLinuxX64

# Build all fat JARs
./gradlew :argos-compare:test-argos:shadowJar \
          :argos-compare:test-clikt:shadowJar \
          :argos-compare:test-kotlin:shadowJar \
          :argos-compare:test-picocli:shadowJar
```

## Size Comparison Results

### Fat JAR Overhead (vs 1,744 KB baseline)

| Library | Overhead |
|---------|----------|
| Argos | +281 KB |
| Picocli | +404 KB |
| Clikt | +10,140 KB |

### Native Binary Overhead (Linux x64, stripped, vs 352 KB baseline)

| Library | Overhead |
|---------|----------|
| Argos | +414 KB |
| Picocli | N/A (JVM only) |
| Clikt | +2,440 KB |

## Locations

- **Native executables**: `*/build/bin/linuxX64/releaseExecutable/*.kexe`
- **Fat JARs**: `*/build/libs/*-all.jar`
