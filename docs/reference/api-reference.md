# API reference

locus4s publishes three cross-platform artifacts:

| Artifact | Main packages | Purpose |
|---|---|---|
| `locus4s-core` | `locus4s` | domains, indices, regions, selections, maps, relations, alignment, persistence records |
| `locus4s-data` | `locus4s.data` | fields, vector storage, views, sections, pullback, aggregation |
| `locus4s-laws` | `locus4s.laws` | reusable algebra and persistence laws |

All modules target the JVM and Scala.js. Ordinary guide examples use the JVM
classpath; the repository's normal and full-optimized test lanes verify the
shared implementation on Scala.js.

## Generate Scaladoc

From the repository root:

```text
sbt locus4s-coreJVM/doc
sbt locus4s-dataJVM/doc
sbt locus4s-lawsJVM/doc
```

Run all API and guide documentation gates together with:

```text
sbt docsCheck
```

The project does not currently advertise a published Scaladoc URL. This page
will link to the real published API artifact when one exists; it intentionally
does not point readers at an invented or snapshot location.

## Package responsibilities

`locus4s` owns the dependency-free finite-domain algebra and validated neutral
records. `locus4s.data` depends on core and adds values indexed by those
domains. `locus4s.laws` depends on both and packages reusable equalities for
downstream property suites.

The dependency direction is one-way:

```text
locus4s-laws ──> locus4s-data ──> locus4s-core
       └────────────────────────> locus4s-core
```

Geometry, coordinates, storage engines, codecs, file formats, and imaging
policy are not API responsibilities of these packages.
