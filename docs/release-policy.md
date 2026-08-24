# locus4s 0.1 release and compatibility policy

Untagged source derives a unique `0.1.0+...-SNAPSHOT` version. Only an exact,
clean, annotated GPG- or SSH-signed `v0.1.x`, `v0.1.x-Mn`, or `v0.1.x-RCn` tag
can produce a release bundle. The intended first sequence is `v0.1.0-RC1`,
direct-consumer adoption, and then `v0.1.0` from the same source or from a newly
certified candidate.

## Compatibility

The stable `v0.1.0` tag establishes the first compatibility baseline for all
six admitted artifacts. Within the `0.1` line, compatible fixes and additions
advance the patch component. A binary, source, or documented semantic-contract
break requires `0.2.0`.

Public compatibility includes domain ownership and alignment, typed failure
cases, canonical region/relation behavior, persistence records, deterministic
aggregation order, and documented allocation/complexity boundaries. Internal
and `private[locus4s]` definitions are outside the promise.

The initial supported consumer floor is Scala 3.7.4 on JVM/JDK 21 and Scala.js
1.22 on Node 22. Supporting an older Scala 3 compiler requires a separately
recorded consumer gate; the `_3` suffix alone is not evidence. Scala Native and
Wasm are outside the 0.1.0 platform promise.

Before the first stable release, `compatibilityCheck` proves that MiMa,
sbt-version-policy, and TASTy-MiMa are wired across all admitted artifacts but
has no previous artifact to compare. After publication, run it with
`-Dlocus4s.compatibility.baseline=0.1.0` and make that lane blocking.

## Candidate court

One clean, pushed commit must pass the authoritative court:

```text
tools/release-court.sh 0.1.0-RC1
```

The script composes strict formatting, compile, JVM/Scala.js tests, optimized
Scala.js linking, Scaladoc, the executable guide, compatibility wiring,
dependency and version preflight, all six staged artifacts, POM inspection,
bundle verification, and isolated JVM/Scala.js coordinate consumers.

The staged POMs must contain no snapshot or filesystem dependencies. image4s,
mesh4s, and ScalaFIM must compile and test their relevant JVM and Scala.js
surfaces against the same candidate revision or immutable RC coordinate.

## Publication

A manual workflow dispatch is credential-free and stages an unsigned synthetic
RC bundle. A release tag imports signing credentials only inside the publication
job, stages signed artifacts, verifies the exact manifest, and uploads one
`USER_MANAGED` Central Portal deployment. Portal publication remains deliberate
after validation. A GitHub Release is created only after Central coordinates
resolve from a clean external consumer.

Any source, build, dependency, executable-documentation, or release-script
change invalidates the candidate and requires the affected gates to be rerun.
