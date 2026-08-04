# locus4s

[![CI](https://github.com/canardlapin/locus4s/actions/workflows/ci.yml/badge.svg)](https://github.com/canardlapin/locus4s/actions/workflows/ci.yml)

Identity-safe finite domains for Scala 3 on the JVM and Scala.js.

**[Read the rendered Scala guide](https://canardlapin.github.io/locus4s/)**

## Why locus4s?

Scientific programs often have several integer-indexed collections in memory at
once: voxels in a full image, voxels in a mask, surface vertices, parcels, graph
nodes, or rows in a compact matrix. All of them may use `Int` for storage, but
the integers do not mean the same thing. If voxel `42` is accidentally used as
surface vertex `42`, ordinary bounds checking may succeed and return a
plausible—but wrong—value.

locus4s associates each index with the finite domain that owns it. A raw integer
is checked when it enters a domain. After that, Scala tracks the relationship
between the index and the regions, maps, and data that use it. Operations within
one domain are total. If the same persisted domain is loaded independently,
explicit alignment reconnects the two live owners.

This is useful when a library needs to:

- prevent indices from different grids, masks, meshes, or tables from being
  mixed accidentally;
- retain the relationship between compact selected data and its source domain;
- describe reindexing, grouping, partial assignment, or sparse neighborhoods;
  or
- restore persisted data in another process without pretending that the new
  in-memory owner is the original object.

## What does it provide?

- `FiniteDomain[S]` establishes a finite owner and its size.
- `Index[S]` is a bounded ordinal belonging to that owner.
- `Region[S]` represents an unordered subset, while `Selection[S]` gives an
  ordered subset its own compact position domain.
- Total, partial, injective, surjective, and bijective maps describe structured
  relationships between domains. `Relation` represents sparse many-to-many
  relationships.
- `NeighborhoodSystem[C, S]` stores compact ordered neighborhood rows for
  centers embedded in an ambient domain; `CenteredNeighborhoodSystem` also
  proves that each center belongs to its own row.
- `Field[S, A]` associates a value with every index and supports gathering,
  pullback, views, and deterministic aggregation.
- `DomainRecord`, `DomainRegistry`, and `DomainAlignment` separate persisted
  identity from the particular live owner reconstructed by one program.

The canonical public vocabulary is `Index` for a typed ordinal and `Field` for
representation-neutral indexed values. The older `Point` and `IndexedField`
spellings remain source-compatible during the pre-1.0 series, but new code
should not use them. See the
[compatibility policy](docs/reference/compatibility.md) for the removal plan.

Runtime ownership and persisted identity answer different questions.
`sameRuntimeOwnerAs` checks whether two values refer to the exact live owner.
`samePersistentIdentityAs` compares caller-supplied structural keys.
`align` turns matching persisted identities into explicit evidence that lets
code transport immutable values between independently restored live owners.

locus4s supplies these identity and indexing rules, not an imaging data model.
Geometry, coordinates, image formats, interpolation, neighborhood policy, and
numerical arrays remain downstream.

## The mistake locus4s prevents

This example creates two process-local domains with the same size:

```scala
import locus4s.*
import locus4s.data.*

def expectRight[E, A](result: Either[E, A]): A =
  result match
    case Right(value) => value
    case Left(error)  => throw new IllegalArgumentException(error.toString)

val voxelOwner = expectRight(FiniteDomain.ephemeral("voxels", size = 5))
val vertexOwner =
  expectRight(FiniteDomain.ephemeral("surface vertices", size = 5))

val voxels = voxelOwner.value
val vertices = vertexOwner.value

val signal = VectorField.tabulate(voxels)(_.ordinal * 10.0)
val voxel3 = expectRight(voxels.index(3))
val vertex3 = expectRight(vertices.index(3))

signal(voxel3)  // 30.0
// signal(vertex3) does not compile: vertex3 belongs to a different domain
```

Both domains contain ordinal `3`, so bounds checking alone cannot detect the
mistake. Their owner types differ, however, so `signal(vertex3)` is rejected at
compile time. Once an index and a field share an owner, `signal(voxel3)` is a
total lookup and returns the value directly.

The helper turns constructor errors into exceptions to keep this first example
short. Applications can instead keep those errors in `Either`, `IO`, or their
existing error type.

The executable
[Scala guide](https://canardlapin.github.io/locus4s/) continues with
installation, ownership and alignment, regions and selections, maps and
relations, fields and aggregation, persistence, and an imaging-shaped workflow.

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

- [Rendered Scala guide](https://canardlapin.github.io/locus4s/)
- [Guide source](docs/README.md)
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
