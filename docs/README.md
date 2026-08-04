# locus4s Scala guide

locus4s gives finite collections an owner type, so an ordinal from one domain
cannot be passed accidentally to data from another. It is a small foundation
for masks, parcels, surfaces, graph nodes, searchlights, storage layouts, and
other places where a naked `Int` is not enough.

The library keeps three ideas separate:

- `Index[S]` is an ordinal owned by one live `FiniteDomain[S]`;
- `DomainKey` is the structural identity used in persisted records; and
- `DomainAlignment[A, B]` witnesses that two independently restored owners
  represent the same persisted domain.

Geometry, coordinates, image formats, interpolation, neighborhood policy, and
numerical arrays remain downstream.

## The mistake locus4s prevents

Two collections can have the same size while their ordinals mean different
things. Bounds checking cannot catch an index taken from the wrong collection.
This example creates two process-local domains:

```scala mdoc:silent
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
```

```scala mdoc
signal(voxel3)
```

The same ordinal from the other domain is rejected at compile time:

```scala mdoc:fail
signal(vertex3)
```

Once an index and a field share an owner type, lookup is total: it returns the
value, not an ownership error. The helper throws only to keep the first example
short; applications can retain constructor errors in their usual error type.

## Follow the guide

1. [Getting started](getting-started.md) adds the modules and builds a first
   region and field.
2. [Ownership and alignment](concepts/ownership.md) explains live owners,
   persistent identity, checked boundaries, and transport.
3. [Regions and selections](concepts/regions-and-selections.md) distinguishes
   unordered support from ordered compact data.
4. [Maps and relations](concepts/maps-and-relations.md) introduces functions,
   partial functions, certified maps, and sparse relations.
5. [Compact neighborhood systems](concepts/neighborhood-systems.md) stores
   sparse rows for an ordered center domain embedded in a larger ambient
   domain.
6. [Fields and aggregation](guides/fields-and-aggregation.md) gathers,
   pulls back, and pushes values across domains.
7. [Persistence and restoration](guides/persistence-and-restoration.md)
   reconstructs validated neutral records.
8. [An imaging-shaped workflow](guides/imaging-shaped-workflow.md) combines the
   abstractions without putting imaging policy in locus4s.

The [Reference](reference/README.md) section records complexity, compatibility,
migration, module boundaries, and API-generation commands.

## Project status

locus4s is pre-1.0 and currently built as `0.1.0-SNAPSHOT`. The guide is
executable, but it does not imply that a release has been published. The JVM
and Scala.js implementations share the same public algebra; ordinary mdoc
examples run on the JVM, while the repository test court verifies both
platforms.
