# locus4s 0.1 release manifest

The `0.1` release line admits the complete portable locus4s API: core finite
domain algebra, representation-neutral data fields, and reusable laws. All
three modules are supported on the JVM and Scala.js.

| Build project | Maven coordinate | Platform | Status |
|---|---|---|---|
| `locus4s-coreJVM` | `io.github.canardlapin:locus4s-core_3` | JVM | admitted |
| `locus4s-coreJS` | `io.github.canardlapin:locus4s-core_sjs1_3` | Scala.js | admitted |
| `locus4s-dataJVM` | `io.github.canardlapin:locus4s-data_3` | JVM | admitted |
| `locus4s-dataJS` | `io.github.canardlapin:locus4s-data_sjs1_3` | Scala.js | admitted |
| `locus4s-lawsJVM` | `io.github.canardlapin:locus4s-laws_3` | JVM | admitted |
| `locus4s-lawsJS` | `io.github.canardlapin:locus4s-laws_sjs1_3` | Scala.js | admitted |

Each coordinate must contain its POM, binary JAR, sources JAR, Scaladoc JAR,
MD5/SHA-1 checksums, and signatures with their checksums. All six use one
tag-derived version. The bundle verifier reads the machine manifest, rejects
missing or additional coordinates, mixed versions, snapshots, filesystem or
repository declarations, incomplete POM metadata, bad checksums, and missing
signatures on the tag path.

The documentation project and root aggregator are excluded and publish-skipped.
The machine-readable copy of this boundary is `project/release-artifacts.tsv`.

Scala 3.7.4 is the producer compiler and the oldest supported consumer compiler
for the 0.1.0 court. JDK 21 is the maintained JVM build/runtime floor, and Node
22 is the maintained Scala.js linking/test runtime. JVM and Scala.js are the
supported platforms; Scala Native and Wasm are not claimed by this release.
