# Persistence and restoration

locus4s persistence values are codec-neutral. JSON, CBOR, database, or imaging
extension codecs live downstream; locus4s validates identity and finite-domain
structure when reconstructing live values.

## Restore a persistent owner

```scala mdoc:silent
import locus4s.*

def expectRight[E, A](result: Either[E, A]): A =
  result match
    case Right(value) => value
    case Left(error)  => sys.error(error.toString)

val domainRecord =
  expectRight(
    DomainRecord.parse(
      id = "subject-17-grid-v1",
      name = "subject 17 grid",
      size = 6,
      fingerprint = Some("sha256:grid-description")
    )
  )

val resolution =
  expectRight(DomainRegistry.empty.restore(domainRecord))
val voxels = resolution.space
```

Keep the returned registry when restoring more records. It canonicalizes every
known `DomainKey` to one live owner.

## Round-trip a region

```scala mdoc:silent
val region =
  expectRight(Region.fromOrdinals(voxels, Vector(0, 2, 5)))
val regionRecord = expectRight(Persistence.record(region))
val restoredRegion =
  expectRight(Persistence.restore(voxels, regionRecord))
```

```scala mdoc
restoredRegion == region
```

`RegionRecord` uses an explicit Empty, Whole, or sorted-ordinal encoding.
Restoration verifies the domain key, bounds, and strict ordering.

## Give selection positions an identity

Selection positions are ephemeral by default. Persisting an ordering requires
an explicit record for that derived domain:

```scala mdoc:silent
val selection =
  expectRight(Selection.fromOrdinals(voxels, Vector(5, 0, 2)))

val positionRecord =
  expectRight(
    DomainRecord.parse(
      id = "subject-17-roi-order-v1",
      name = "ROI compact positions",
      size = selection.size
    )
  )

val selectionRecord =
  expectRight(Persistence.record(selection, positionRecord))

val selectionRestoration =
  expectRight(
    Persistence.restore(
      voxels,
      selectionRecord,
      resolution.registry
    )
  )
```

```scala mdoc
(
  selectionRestoration.selection.ordinals.toVector,
  selectionRestoration.selection.positions.persistentKey
)
```

This prevents two independently derived orderings from receiving the same
persistent identity accidentally.

## Align independent restorations

Another process or registry reconstructs a distinct live owner even when the
record key is identical:

```scala mdoc:silent
val independent =
  expectRight(DomainRegistry.empty.restore(domainRecord))
val alignment = expectRight(voxels.align(independent.space))
```

```scala mdoc
(
  voxels.sameRuntimeOwnerAs(independent.space),
  voxels.samePersistentIdentityAs(independent.space),
  alignment.reverse.transport(
    expectRight(
      Region.fromOrdinals(independent.space, Vector(1, 4))
    )
  ).ordinalsInDomainOrder.toVector
)
```

Align at the dynamic boundary, transport once, and continue with total typed
operations.

## Persist maps and relations

Neutral records are supplied for:

- `Region`
- `Selection`
- `TotalMap`
- `PartialMap`
- `Relation`

Records include endpoint keys and immutable payloads. Reconstruction validates
target counts, ordinals, CSR offsets, row ordering, uniqueness, and endpoint
identity before exposing a live value. Certified map wrappers can be
revalidated from a restored total or partial map.
