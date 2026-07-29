# locus4s

locus4s provides identity-safe finite domains for Scala on the JVM and
Scala.js. It is independent of imaging, spatial geometry, and workflow
libraries.

The repository publishes three granular artifacts:

| Artifact | Owns |
|---|---|
| `locus4s-core` | persistent domain identity, runtime ownership, ordinals, regions, selections, total maps, and relations |
| `locus4s-data` | indexed fields and genuinely domain-neutral aggregation |
| `locus4s-laws` | reusable laws for core and data operations |

The dependency direction is:

```text
locus4s-laws ──> locus4s-data ──> locus4s-core
       └────────────────────────> locus4s-core
```

`locus4s-core` has no dependency on image4s, reframe4s, or ScalaFIM.
Parcellation, searchlight, imaging, and neuroimaging workflow policies belong
in downstream libraries.

## Build

```text
sbt compileAll
sbt testAll
```

The build uses Scala 3.7.4 and cross-publishes the three artifacts for JVM and
Scala.js.
