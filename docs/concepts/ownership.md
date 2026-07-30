# Ownership and alignment

This page explains why typed operations are total, why dynamic operations still
need checks, and how independently restored owners become composable.

## One owner type, one live owner

Public domain creation returns an existential owner type. Consumers cannot
construct another `FiniteDomain[S]` or forge an `Index[S]` for that `S`.
Consequently, operations whose operands already share `S` do not return
ownership errors:

```scala
field(index)
left.union(right)
first.andThen(second)
field.pullback(mapping)
```

Bounds and ownership are checked when raw or existential data enters the typed
surface, not in every hot lookup.

## Two restorations are deliberately distinct

Two independent registries can restore the same persisted key while creating
different live owners.

```scala mdoc:silent
import locus4s.*

def expectRight[E, A](result: Either[E, A]): A =
  result match
    case Right(value) => value
    case Left(error)  => sys.error(error.toString)

val record =
  expectRight(DomainRecord.parse("shared-grid-v1", "first label", 6))

val leftResolution = expectRight(DomainRegistry.empty.restore(record))
val rightResolution = expectRight(DomainRegistry.empty.restore(record))
val left = leftResolution.space
val right = rightResolution.space

val leftRegion =
  expectRight(Region.fromOrdinals(left, Vector(0, 2, 4)))
val rightRegion =
  expectRight(Region.fromOrdinals(right, Vector(1, 2, 5)))
```

The following expression must not typecheck:

```scala mdoc:fail
leftRegion.union(rightRegion)
```

At a dynamic boundary, a checked operation reports that the live owners differ:

```scala mdoc
leftRegion.unionChecked(rightRegion).isLeft
```

The error does not claim that the persisted domains differ. Its
`persistentIdentityMatches` fact is derived from the two domain descriptors and
cannot be supplied inconsistently by a caller.

## Align once, then use the total surface

Alignment compares structural keys. Its ordinal action is identity, so
transport is O(1) for structures backed by immutable ordinal storage.

```scala mdoc:silent
val alignment = expectRight(left.align(right))
val localRight = alignment.reverse.transport(rightRegion)
```

```scala mdoc
leftRegion.union(localRight).ordinalsInDomainOrder.toVector
```

`DomainAlignment` has `identity`, `reverse`, and associative `andThen`.
It transports regions, selections, maps, partial and certified maps, relations,
fields, and sections without weakening their owner types.

## Registry lifetime and naming

A registry canonicalizes a caller-supplied `DomainKey`. Restoring the same key
through the returned registry reuses the original live owner:

```scala mdoc
val again = expectRight(leftResolution.registry.restore(record))
left.sameRuntimeOwnerAs(again.space)
```

`DomainKey` contains ID, size, and optional opaque fingerprint.
`DomainMetadata` contains the display name. A rename may change metadata but
does not prevent alignment. Reusing an ID with a different size or fingerprint
is a hard restoration conflict.

Ephemeral domains are appropriate for derived in-memory owners such as
selection positions. They align only with themselves and cannot be persisted
until the caller assigns an explicit record.

Continue with [Regions and selections](regions-and-selections.md) to see why an
ordered subset needs its own position domain.
