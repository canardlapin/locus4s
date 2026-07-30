# Fields and aggregation

A `Field[S, A]` is indexed access to values on `S`. The interface does not
prescribe `Vector`, primitive arrays, mapped files, chunks, typed arrays, or
device storage.

## Use arbitrary storage

```scala mdoc:silent
import locus4s.*
import locus4s.data.*

def expectRight[E, A](result: Either[E, A]): A =
  result match
    case Right(value) => value
    case Left(error)  => sys.error(error.toString)

val voxelOwner = expectRight(FiniteDomain.ephemeral("voxels", 6))
val voxels = voxelOwner.value
val raw = Array(1.0, 2.0, 3.0, 10.0, 20.0, 30.0)

val signal: Field[voxelOwner.S, Double] =
  Field.view(voxels)(index => raw(index.ordinal))
```

```scala mdoc
signal.toVector
```

`Field.view` is non-owning. A storage library can instead implement `Field`
directly and override ordered traversal when it has a more efficient path.
`VectorField` is the supplied immutable reference implementation.

## Gather an ordered selection

```scala mdoc:silent
val mask =
  expectRight(Selection.fromOrdinals(voxels, Vector(5, 1, 3)))
val compact = signal.gather(mask)
```

```scala mdoc
compact.toVector
```

The result is indexed by `mask.I`, so it retains the identity and order needed
by matrices, packed arrays, and downstream groupings.

## Pull a field back through a map

Suppose parcel values should be expanded to every voxel assigned to that
parcel:

```scala mdoc:silent
val parcelOwner = expectRight(FiniteDomain.ephemeral("parcels", 2))
val parcels = parcelOwner.value

val voxelToParcel =
  expectRight(
    Surjection.fromTargetOrdinals(
      voxels,
      parcels,
      Vector(0, 0, 0, 1, 1, 1)
    )
  )

val parcelMeans =
  expectRight(VectorField.fromValues(parcels, Vector(2.0, 20.0)))

val expanded = parcelMeans.pullback(voxelToParcel.toTotalMap)
```

```scala mdoc
expanded.toVector
```

Pullback is lazy: constructing the result creates a view, and lookup composes
the field with the map.

## Push values forward with a reducer

Aggregation moves in the opposite direction. The reducer and destination
builder are explicit, and sources are visited in increasing ordinal order:

```scala mdoc:silent
val parcelSums =
  Aggregation.pushForward(voxelToParcel.toTotalMap, signal)(0.0)(
    identity
  )(_ + _)
```

```scala mdoc
parcelSums.toVector
```

Use `foldMapByWith` with a custom `FieldBuilder` when the destination should be
a primitive, mapped, chunked, or otherwise specialized field. The algorithm
does not clone its map before folding.

## Restrict without inventing partial storage

`SectionView[S, A]` pairs a full field with a support region. Lookup returns an
error outside that support, while mapping remains a lazy view:

```scala mdoc:silent
val support =
  expectRight(Region.fromOrdinals(voxels, Vector(1, 3, 5)))
val section = signal.restrict(support)
val inside = expectRight(voxels.index(3))
val outside = expectRight(voxels.index(2))
```

```scala mdoc
(section(inside), section(outside).isLeft)
```

When values are stored only for selected positions, use the selection's
position domain and a `Field[selection.I, A]` instead of pretending a complete
source field exists.
