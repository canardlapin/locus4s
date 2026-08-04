# Maps and relations

Maps and relations describe how finite domains interact without introducing
coordinates or domain-specific policy.

## Total and certified maps

```scala mdoc:silent
import locus4s.*

def expectRight[E, A](result: Either[E, A]): A =
  result match
    case Right(value) => value
    case Left(error)  => sys.error(error.toString)

val voxelOwner = expectRight(FiniteDomain.ephemeral("voxels", 6))
val parcelOwner = expectRight(FiniteDomain.ephemeral("parcels", 2))
val voxels = voxelOwner.value
val parcels = parcelOwner.value

val grouping =
  expectRight(
    Surjection.fromTargetOrdinals(
      voxels,
      parcels,
      Vector(0, 0, 0, 1, 1, 1)
    )
  )
```

The surjection certifies that every parcel has at least one source:

```scala mdoc
grouping.toTotalMap
  .image(Region.whole(voxels))
  .isWhole
```

The small certified family records useful guarantees:

| Structure | Guarantee | Typical use |
|---|---|---|
| `Injection[X, Y]` | distinct sources have distinct targets | subset or crop embedding |
| `Surjection[X, Y]` | every target has a source | grouping or complete parcellation |
| `Bijection[X, Y]` | one-to-one and onto | exact reordering |
| `PartialSurjection[X, Y]` | defined targets cover `Y` | grouping with background |

Each wrapper reuses the underlying primitive map representation.

## Pull regions back and push them forward

`TotalMap[X, Y]` acts in both directions on regions:

```scala mdoc:silent
val firstParcel =
  expectRight(Region.fromOrdinals(parcels, Vector(0)))
val selectedVoxels =
  grouping.toTotalMap.pullback(firstParcel)
```

```scala mdoc
selectedVoxels.ordinalsInDomainOrder.toVector
```

Image moves a source region forward; pullback asks which sources land in a
target region. The two operations satisfy the image-pullback adjunction
documented by the reusable laws.

## Partial maps keep background explicit

```scala mdoc:silent
val partial =
  expectRight(
    PartialMap.fromOptionalTargetOrdinals(
      voxels,
      parcels,
      Vector(None, Some(0), Some(0), Some(1), Some(1), None)
    )
  )
```

```scala mdoc
partial.definedRegion.ordinalsInDomainOrder.toVector
```

There is no invented background parcel: undefined source indices remain
undefined.

`preimage(target)` scans the source and materializes one fiber. `fibers`
materializes all fibers together as `Relation[Y, X]`, with target rows and
source members. It scans the partial map once, takes O(|X| + |Y|) time, and
allocates fresh CSR storage. `toRelation` instead returns the defined graph as
`Relation[X, Y]`. Both materializers return `Either[RelationError, ...]`
because a non-empty relation cannot represent `Int.MaxValue + 1` row offsets.

`PartialSurjection` exposes the same operations and names its defined source
region `support`. Its `fiber(target)` is non-empty by construction, although it
still returns the ordinary `Region[X]` representation. Constructors report
`PartialMapError` for a wrong count or out-of-range ordinal and
`CertifiedMapError` when the defined image does not cover the target.

Use `fromOptionalTargetsChecked` at an existential boundary. It rejects a
foreign target live owner with `SpaceMismatch`, even when sizes or persistent
keys match. Use `fromOptionalTargetsAligned` only after constructing explicit
target alignment evidence.

Composition with `Surjection[Y, Z]` preserves the source support. Composition
with `PartialSurjection[Y, Z]` preserves target coverage but retains only
sources whose intermediate target belongs to the second map's support. The two
supports are equal when the second support is all of `Y`.

`equivalentUpToTargetRelabelingChecked` compares partitions only when the
source live owners are identical. `equivalentUpToTargetRelabelingAligned`
accepts explicit source alignment. Both methods ignore target names and
ordinals by deriving a one-to-one relabeling between fibers; target owners may
therefore differ without being confused.

## Relations model sparse many-to-many structure

```scala mdoc:silent
val centerOwner = expectRight(FiniteDomain.ephemeral("centers", 2))
val centers = centerOwner.value

val searchlights =
  expectRight(
    Relation.fromOrdinalRows(
      centers,
      voxels,
      Vector(
        Vector(0, 1, 2),
        Vector(3, 4, 5)
      )
    )
  )

val firstCenter = expectRight(centers.index(0))
```

```scala mdoc
searchlights.row(firstCenter).ordinalsInDomainOrder.toVector
```

Relations use compressed sparse rows. Row traversal is proportional to row
degree, and sparse composition visits reachable edges rather than allocating a
target-sized marker for every source.

For a compact ordered collection of relation rows embedded in a larger ambient
domain, use [NeighborhoodSystem](neighborhood-systems.md).

Continue with [Fields and aggregation](../guides/fields-and-aggregation.md) to
move values along these maps.
