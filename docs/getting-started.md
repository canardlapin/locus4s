# Getting started

This page adds locus4s to an sbt build and constructs a field and mask over one
finite domain.

## Add the modules

For a JVM build:

```scala
libraryDependencies ++= Seq(
  "io.github.canardlapin" %% "locus4s-core" % "@VERSION@",
  "io.github.canardlapin" %% "locus4s-data" % "@VERSION@"
)
```

Use `%%%` instead of `%%` in a Scala.js or cross-project build. Add
`locus4s-laws` only when you want to reuse the law functions in a downstream
test suite.

The current build is a snapshot rather than a published release. From this
checkout, publish the required JVM or Scala.js projection locally before using
the coordinates in another project.

## Register a persistent domain

`DomainRegistry` accepts caller-supplied structural identity. It does not mint
IDs because branching an immutable generator could produce duplicates.

```scala mdoc:silent
import locus4s.*
import locus4s.data.*

def expectRight[E, A](result: Either[E, A]): A =
  result match
    case Right(value) => value
    case Left(error)  => sys.error(error.toString)

val record =
  expectRight(
    DomainRecord.parse(
      id = "subject-17-mask-v1",
      name = "subject 17 mask",
      size = 8,
      fingerprint = Some("sha256:example-grid")
    )
  )

val restored = expectRight(DomainRegistry.empty.restore(record))
val voxels = restored.space
```

The ID, size, and optional fingerprint form `record.key`. The name is
presentation metadata and may change without changing structural identity.

## Create values owned by the domain

```scala mdoc:silent
val signal =
  VectorField.tabulate(voxels)(index => (index.ordinal + 1).toDouble)

val mask =
  expectRight(Region.fromOrdinals(voxels, Vector(1, 2, 5, 7)))
```

```scala mdoc
mask.ordinalsInDomainOrder.toVector
```

```scala mdoc
val fifth = expectRight(voxels.index(5))
signal(fifth)
```

`voxels.index(5)` is checked because a raw integer enters the typed world
there. After construction, `signal(fifth)` is total because both values carry
the same owner type.

## Traverse without point objects

Use `foreachIndex` in allocation-sensitive code:

```scala mdoc
var ordinalSum = 0
voxels.foreachIndex(index => ordinalSum += index.ordinal)
ordinalSum
```

`Index[S]` is represented by an opaque `Int`; traversal does not allocate one
owner-bearing object per ordinal.

Continue with [Ownership and alignment](concepts/ownership.md) before combining
values loaded through independent registries.
