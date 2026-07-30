# locus4s

[![CI](https://github.com/canardlapin/locus4s/actions/workflows/ci.yml/badge.svg)](https://github.com/canardlapin/locus4s/actions/workflows/ci.yml)

Identity-safe finite domains for Scala 3 on the JVM and Scala.js.

A point is not merely an integer. It is a bounded ordinal owned by one finite
domain. locus4s makes that ownership explicit, distinguishes live owners from
persistent identity, and supplies the small algebra needed to move regions,
selections, maps, relations, and fields without exchanging naked indices.

Geometry, coordinates, image formats, interpolation, neighborhood policy, and
numerical arrays remain downstream.

## First look

```scala
import locus4s.*
import locus4s.data.*

val result =
  for
    record <- DomainRecord.parse("mesh-v1", "mesh vertices", size = 5)
    restored <- DomainRegistry.empty.register(record)
    vertex <- restored.space.index(3)
  yield
    val signal =
      VectorField.tabulate(restored.space)(_.ordinal * 10.0)
    signal(vertex)
```

Raw ordinals are checked when they enter a domain. Once `vertex` and `signal`
share the same owner type, lookup is total.

The executable [Scala guide](docs/README.md) continues with installation,
ownership and alignment, regions and selections, maps and relations, fields and
aggregation, persistence, and an imaging-shaped workflow.

## Modules

| Artifact | Purpose |
|---|---|
| `locus4s-core` | zero-cost indices, domains, regions, selections, maps, relations, alignment, persistence records |
| `locus4s-data` | representation-neutral fields, views, pullback, sections, deterministic aggregation |
| `locus4s-laws` | reusable laws for the core and data algebras |

All modules cross-compile for the JVM and Scala.js. `locus4s-core` has no
runtime dependencies.

## Status and installation

locus4s is pre-1.0 and currently built as `0.1.0-SNAPSHOT`; no stable release is
advertised yet. To try it from this checkout, publish the required projection
locally and use the displayed snapshot version:

```text
sbt locus4s-coreJVM/publishLocal locus4s-dataJVM/publishLocal
```

The guide records the eventual sbt coordinates without implying that the
snapshot is available from a public repository.

## Documentation

- [Scala guide](docs/README.md)
- [Ownership and alignment](docs/concepts/ownership.md)
- [Fields and aggregation](docs/guides/fields-and-aggregation.md)
- [Complexity and allocation contracts](docs/reference/complexity.md)
- [Compatibility policy](docs/reference/compatibility.md)
- [API reference](docs/reference/api-reference.md)

Build the executable guide and API documentation with:

```text
sbt docsCheck
```

`docsCheck` compiles the JVM Scaladoc for all three modules, evaluates the mdoc
examples, validates links, and renders the Laika site. Site deployment is not
configured.

## Development gates

```text
sbt checkAll
sbt testFullOptJS
sbt docsCheck
```

These gates cover deterministic formatting, strict compilation, JVM and
Scala.js tests, a full-optimized Scala.js lane, API documentation, and the
executable guide.

## Deliberate non-goals

locus4s does not define voxel coordinates, grid shapes or strides, coordinate
frames, affine or nonlinear transforms, interpolation weights, NIfTI or DICOM
I/O, image orientation, parcellation labels or hierarchy, searchlight
generation, BIDS entities, or tensor operations.
