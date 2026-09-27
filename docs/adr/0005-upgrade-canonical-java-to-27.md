# ADR 0005: Upgrade the canonical Java version to 27

## Status

Accepted. Supersedes the Java version in [ADR 0001](0001-java-first.md); the Java-first decision itself is unchanged.

## Context

[ADR 0001](0001-java-first.md) made Java the canonical implementation language and set the version to Java 21.

The project has since reached its first public learning arc: seven labs, a composition capstone, and a readiness assessment. Java 27 is now the current Java SE feature release. Java 25 is the current long-term support (LTS) release. Java 27 is not an LTS release.

## Decision

The canonical implementation moves from Java 21 to Java 27.

The code keeps to ordinary, final Java language features and APIs. It does not use preview or incubator features unless a future teaching goal explicitly requires one, and that would need its own ADR.

## Rationale

- The examples stay aligned with the current Java platform that readers will install today.
- This is an educational reference project, not a conservative enterprise baseline. Following the current release costs little and keeps the code current.
- No concept in the handbook depends on an older language level, so nothing is lost by moving forward.

## Consequences

- Contributors need JDK 27 to build and test the repository.
- CI builds with JDK 27 (Temurin).
- Maven compiles every module with `--release 27`, set once through the `maven.compiler.release` property in the root POM.
- Documentation and the README badge show Java 27.
- Readers who need an LTS-only baseline may prefer Java 25. The handbook's concepts carry over, but the canonical code targets 27.
