# locus4s

locus4s provides finite domains whose indices cannot be mixed accidentally.
It is useful when an integer such as `42` is meaningful only within one mask,
parcel set, surface, graph, or other finite collection.

The library separates three facts:

- `Index[S]` is an ordinal owned by one live `FiniteDomain[S]`.
- `DomainKey` identifies a persistable indexed domain across processes.
- `DomainAlignment[A, B]` proves that two independently restored live owners
  represent the same persisted domain.

Geometry, coordinates, image formats, interpolation, parcellation policy, and
numerical tensor operations remain downstream.

## Modules

| Artifact | Provides |
|---|---|
| `locus4s-core` | domains, zero-cost indices, regions, selections, maps, relations, alignment, and neutral persistence records |
| `locus4s-data` | representation-neutral fields, vector fields, views, pullback, and deterministic pushforward |
| `locus4s-laws` | reusable laws for the core and data algebras |

The dependency direction is:

```text
locus4s-laws ──> locus4s-data ──> locus4s-core
       └────────────────────────> locus4s-core
```

All three modules cross-compile for the JVM and Scala.js. `locus4s-core` has no
runtime dependencies.

## Create and use a domain

Applications supply persistent IDs. `DomainRegistry` deliberately does not
mint them: an immutable registry cannot guarantee freshness when callers branch
its state.

```scala
import locus4s.*
import locus4s.data.*

val record =
  DomainRecord.parse(
    id = "subject-17-brain-mask-v1",
    name = "brain mask",
    size = 120_000,
    fingerprint = Some("sha256:...")
  )

val resolution =
  record.flatMap(DomainRegistry.empty.register)

resolution.map: resolved =>
  val voxels = resolved.space
  val field = VectorField.tabulate(voxels)(index => index.ordinal.toDouble)

  voxels.index(42).map: index =>
    field(index) // Double; typed lookup is total
```

`DomainKey` contains the ID, size, and optional opaque fingerprint. All three
participate in structural identity. `DomainMetadata` contains the display
name. Renaming a record does not change its key. A registry that has already
restored that key returns its existing live owner and retains the metadata from
the first registration.

The fingerprint has no built-in interpretation. A downstream grid or topology
library may store a canonical digest there without adding geometry to locus4s.

## Typed operations and dynamic boundaries

One static owner type `S` denotes one live owner. Public constructors return
existential owner types, and consumers cannot construct a second
`FiniteDomain[S]` or forge an `Index[S]`.

Operations between values with the same owner type are total:

```scala
field(index)
left.union(right)
first.andThen(second)
relation.andThen(next)
field.pullback(mapping)
```

Checked variants handle values whose owner types were hidden by loading,
decoding, or other dynamic code:

```scala
left.unionChecked(dynamicallyLoadedRegion)
first.andThenChecked(dynamicallyLoadedMap)
field.zipWithChecked(dynamicallyLoadedField)(_ + _)
```

These methods require the same live owner. If two owners were restored
independently, align them and transport one value before entering the typed
API:

```scala
for
  alignment <- leftSpace.align(rightSpace)
yield
  val localRight = alignment.reverse.transport(rightRegion)
  leftRegion.union(localRight)
```

Alignment has identity, reverse, and associative composition. Its ordinal
action is identity, so transport shares immutable storage.

## Selections retain their position domain

An ordered `Selection[S]` is an injection from a finite position domain `I`
into `S`. Gathering therefore returns `Field[I, A]`, not an identity-free
`Vector[A]`.

```scala
val selected =
  for
    selection <- Selection.fromOrdinals(voxels, Vector(9, 2, 7))
  yield field.gather(selection)
```

The result is indexed in selection order. `selection.support` is the unordered
region embedded in the source domain.

Selection position domains are ephemeral by default. To persist a selection,
the caller supplies a `DomainRecord` for those positions:

```scala
val persisted =
  for
    selection <- Selection.fromOrdinals(voxels, Vector(9, 2, 7))
    positionRecord <- DomainRecord.parse(
      "subject-17-roi-v1",
      "ROI positions",
      selection.size
    )
    record <- Persistence.record(selection, positionRecord)
  yield record
```

This explicit step prevents two independently derived orderings from acquiring
the same persistent identity by accident.

## Fields do not prescribe storage

`Field[S, A]` requires a domain, typed indexed access, and ordered traversal.
It does not require `Vector`. A memory-mapped image, primitive array, chunked
volume, JavaScript typed array, GPU view, or packed mask can implement the
interface directly.

`VectorField` is the immutable reference implementation. `Field.view` creates
a non-owning view. `FieldBuilder` lets materializing algorithms choose their
destination storage. Mapping and pullback create views; aggregation accepts any
`Field` and an explicit destination builder.

## Imaging-shaped use without imaging policy

Suppose a downstream reader has already established voxel and parcel domain
records:

```scala
val signal: Field[Voxel, Float] = readerBackedField
val mask: Region[Voxel] = loadedMask
val packed: Selection[Voxel] = orderedMask
val voxelToParcel: Surjection[packed.I, Parcel] = loadedParcellation
val searchlights: Relation[Center, Voxel] = loadedSearchlights

val maskedSignal: Field[packed.I, Float] =
  signal.gather(packed)

val parcelSums: Field[Parcel, Float] =
  Aggregation.pushForward(voxelToParcel.toTotalMap, maskedSignal)(0.0f)(
    identity
  )(_ + _)
```

locus4s supplies the identities and finite-domain algebra. The downstream
library still decides how a voxel relates to coordinates, how masks are read,
and how parcels or searchlights are defined.

## Performance contracts

`Index[S]` is an opaque `Int`. `foreachIndex` does not allocate one object per
ordinal. Regions use constant-size `Empty` and `Whole` representations plus
sparse sorted storage. Relations use compressed sparse rows. Rebinding shares
immutable storage.

See [Complexity and allocation contracts](docs/complexity.md) for operation
costs and representation limits. The test suite includes:

- JVM per-thread allocated-byte checks over millions of typed visits;
- JVM and Scala.js sparse-relation courts with a billion-element declared
  target domain;
- a separate full-optimization Scala.js test lane.

## Build and release gates

```text
sbt checkAll
sbt testFullOptJS
```

`checkAll` checks formatting, compiles every JVM/Scala.js module, and runs all
tests. Strict warnings include unused code and discarded values, and warnings
fail the build.

The current compatibility policy is documented in
[Compatibility policy](docs/compatibility.md). The downstream migration from
the initial prototype is described in
[Downstream migration](docs/downstream-migration.md).

## Deliberate non-goals

locus4s does not define voxel coordinates, grid shapes or strides, coordinate
frames, affine or nonlinear transforms, interpolation weights, file formats,
image orientation, parcellation labels or hierarchy, searchlight generation,
BIDS entities, or numerical tensor operations. Weighted sparse operators also
remain downstream unless a separate representation-sharing interface is
justified.
