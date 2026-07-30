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

Continue with [Fields and aggregation](../guides/fields-and-aggregation.md) to
move values along these maps.
