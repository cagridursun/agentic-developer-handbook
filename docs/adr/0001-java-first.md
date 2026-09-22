# ADR 0001: Java-first

## Status

Accepted

## Context

The handbook has to teach concepts and show them running. Readers work in more than one language. Maintaining a full implementation in several languages would split reviews and let the examples drift apart.

The project still needs one language where the code is complete enough to build, test, and extend.

## Decision

Java 21 is the canonical implementation language.

Conceptual documentation stays language-independent where the idea does not depend on Java. A chapter may mention another language when a short comparison helps. The project will not keep parallel implementations.

Java-first means the reference code is Java. It does not mean the explanations are useful only to Java developers.

## Consequences

- Labs, build, and CI target Java 21.
- Contributors write new reference code in Java unless an ADR says otherwise.
- Documents should name Java when they describe this repository's code, and avoid Java-only jargon when they define a concept.
- A reader who does not write Java can still use the mental model and glossary. They cannot expect a second official codebase.
