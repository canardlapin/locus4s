# locus4s 0.1 release evidence

This receipt is filled from the exact candidate; configured checks are not
reported as executed until their command, revision, and result are recorded.

## Candidate

- Commit: pending
- Candidate tag: `v0.1.0-RC1`
- Stable tag: pending adoption gate
- Producer: Scala 3.7.4, sbt 1.12.14, JDK 21
- Scala.js CI runtime: Node 22

## Required evidence

| Court | Command or source | Result |
|---|---|---|
| Formatting, compile, JVM/JS tests | `sbt checkAll` | pending exact candidate |
| Optimized Scala.js | `sbt testFullOptJS` | pending exact candidate |
| Scaladoc and executable guide | `sbt docsCheck` | pending exact candidate |
| Compatibility wiring/baseline | `sbt compatibilityCheck` | pending exact candidate |
| Unsigned release bundle | `releaseUnsigned` plus bundle verifier | pending exact candidate |
| Published-coordinate JVM/JS consumer | `tools/verify-release-consumer.sh` | pending exact candidate |
| image4s | exact candidate override/coordinate | pending |
| mesh4s | exact candidate override/coordinate | pending |
| ScalaFIM | exact candidate override/coordinate | pending |
| Hosted CI | GitHub Actions URL | pending exact candidate |
| Central Portal | deployment ID and public coordinates | pending |

The single local/hosted entry point is `tools/release-court.sh VERSION`; the
individual rows remain visible so a failure cannot be hidden behind the final
aggregate result.

Advisory findings and external blockers remain recorded rather than converted
to passes. Stable promotion requires all three direct-consumer rows and public
artifact resolution to be green.
