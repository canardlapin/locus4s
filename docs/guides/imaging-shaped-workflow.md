# An imaging-shaped workflow

This worked example models a compact mask, a parcellation, and sparse
searchlights. It uses imaging-shaped names without defining coordinates,
affines, file formats, or neighborhood policy.

## Establish finite owners

```scala mdoc:silent
import locus4s.*
import locus4s.data.*

def expectRight[E, A](result: Either[E, A]): A =
  result match
    case Right(value) => value
    case Left(error)  => sys.error(error.toString)

val voxelOwner = expectRight(FiniteDomain.ephemeral("voxels", 6))
val parcelOwner = expectRight(FiniteDomain.ephemeral("parcels", 2))
val centerOwner = expectRight(FiniteDomain.ephemeral("centers", 2))

val voxels = voxelOwner.value
val parcels = parcelOwner.value
val centers = centerOwner.value
```

In a real reader, persisted grid or topology records would usually establish
these owners. Ephemeral domains keep the example focused on the algebra.

## Gather compact masked data

```scala mdoc:silent
val signal =
  expectRight(
    VectorField.fromValues(
      voxels,
      Vector(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
    )
  )

val mask =
  expectRight(Selection.fromOrdinals(voxels, Vector(0, 2, 3, 5)))

val maskedSignal = signal.gather(mask)
```

```scala mdoc
maskedSignal.toVector
```

The compact field is owned by `mask.I`, and `mask.embedding` retains its exact
correspondence to the voxel domain.

## Group masked positions into parcels

```scala mdoc:silent
val parcellation =
  expectRight(
    Surjection.fromTargetOrdinals(
      mask.positions,
      parcels,
      Vector(0, 0, 1, 1)
    )
  )

val parcelSums =
  Aggregation.pushForward(
    parcellation.toTotalMap,
    maskedSignal
  )(0.0)(identity)(_ + _)
```

```scala mdoc
parcelSums.toVector
```

The surjection proves that every parcel is inhabited. If background were
present on the full voxel domain, a `PartialSurjection[Voxel, Parcel]` would
retain it as undefined instead of inventing a background parcel.

## Represent sparse searchlights

The downstream library decides which neighbors belong to each center, then
constructs a relation:

```scala mdoc:silent
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

val secondCenter = expectRight(centers.index(1))
```

```scala mdoc
searchlights.row(secondCenter).ordinalsInDomainOrder.toVector
```

Row retrieval is proportional to the three stored neighbors, not the complete
voxel-domain size.

## What remains downstream

The workflow has not defined:

- voxel coordinates, grid shape, strides, or orientation;
- affine or nonlinear transforms;
- interpolation weights;
- NIfTI, DICOM, or BIDS I/O;
- how masks, parcels, or searchlights are generated; or
- numerical tensor operations.

locus4s owns finite identity and composition. Imaging libraries retain imaging
meaning and policy.
