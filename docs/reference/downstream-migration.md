# Downstream migration from the initial prototype

The foundation refactor intentionally changes APIs that downstream libraries
must not freeze.

## Persistent domains

Replace immutable sequential ID generation:

```scala
DomainRegistry.withSequentialIds("image")
registry.fresh("voxels", size)
```

with a caller-supplied record:

```scala
val record =
  DomainRecord.parse(
    id = externallyManagedId,
    name = "voxels",
    size = size,
    fingerprint = canonicalGridFingerprint
  )

val resolution =
  record.flatMap(DomainRegistry.empty.register)
```

The caller may use an atomic UUID factory, a content-derived ID, a database
identity, or a linearly threaded state program. Test fixtures may use
sequential strings when the fixture constructs and supplies each `DomainId`
itself.

`DomainRecord` now separates `key` from `metadata`. A display-name change does
not prevent alignment. An ID reused with a different size or fingerprint is a
hard conflict.

## Points and lookup

`Point[S]` remains a pre-1.0 compatibility alias, but new code uses `Index[S]`,
an opaque integer. Replace `PointError`, `point`, `pointOption`, and `points`
with `IndexError`, `index`, `indexOption`, and `indices`. These aliases are
scheduled for removal at 1.0. A typed index does not carry a runtime `domain`
field because its owner is guaranteed by `S`.

Code that already has `Index[S]` calls a typed operation directly:

```scala
field(index)
region.contains(index)
mapping(index)
```

Do not reconstruct `SpaceMismatch` from records and a caller-supplied Boolean.
Its constructor is private so the error cannot contain contradictory facts.
Use a checked locus4s operation, `SpaceMismatch.between(expected, actual)`, or a
downstream-specific mismatch error.

## Checked and total operations

Remove ownership-error handling around operands that already share the same
owner type:

```scala
left.union(right)
first.andThen(second)
field(index)
```

At loading or existential boundaries, use `unionChecked`,
`andThenChecked`, `zipWithChecked`, or explicit `DomainAlignment`.

## Selections and fields

Selection extraction no longer returns raw compact values. Use:

```scala
val compact: Field[selection.I, A] =
  field.gather(selection)
```

The result retains the position domain and composes with an
`Injection[selection.I, S]`, a `Surjection[selection.I, Parcel]`, or other
finite-domain maps.

Depend on `Field[S, A]` when the caller need not know the storage type.
Use `VectorField` only when immutable generic vector storage is intended.
Replace the compatibility name `IndexedField` with one of those two names;
replace lookup through `at(index)` with `field(index)`.

## Relations and persistence

Construct large relations through `Relation.fromCsr` or a validated builder.
Row traversal uses `foreachTarget`; avoid `ordinalRows`, which exists only as a
defensive compatibility copy.

Neutral records are available for regions, selections, total maps, partial
maps, and relations. Codecs remain downstream. Reconstruction validates domain
keys and structural invariants before creating live values.
