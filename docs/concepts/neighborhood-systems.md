# Compact neighborhood systems

`NeighborhoodSystem[C, S]` stores neighborhoods for an ordered center domain
`C` embedded in an ambient domain `S`. Use it when only some ambient indices are
centers and allocating empty rows for every other ambient index would waste
space.

The value contains two structures:

- `centerEmbedding: Injection[C, S]` maps each compact center position to its
  ambient index;
- `membership: Relation[C, S]` stores one CSR row for each actual center.

The relation therefore has `|C| + 1` row offsets, not `|S| + 1`. Center order is
exactly the order of `C`.

```scala mdoc:silent
import locus4s.*

def expectRight[E, A](result: Either[E, A]): A =
  result match
    case Right(value) => value
    case Left(error)  => sys.error(error.toString)

val ambientOwner = expectRight(FiniteDomain.ephemeral("vertices", 8))
val ambient = ambientOwner.value
val selection =
  expectRight(Selection.fromOrdinals(ambient, Vector(1, 4, 7)))
val rows =
  expectRight(
    Relation.fromOrdinalRows(
      selection.positions,
      ambient,
      Vector(Vector(0, 1, 2), Vector(3, 4, 5), Vector(6, 7))
    )
  )
val neighborhoods = NeighborhoodSystem.fromSelection(selection, rows)
val secondCenter = expectRight(selection.positions.index(1))
```

```scala mdoc
neighborhoods.center(secondCenter).ordinal
neighborhoods.neighborhood(secondCenter).ordinalsInDomainOrder.toVector
```

`CenteredNeighborhoodSystem` adds one invariant: every embedded center occurs
in its own membership row. Its smart constructor checks every center and
reports `NeighborhoodSystemError.MissingEmbeddedCenter` at the first failure.

`NeighborhoodSystem.from` checks exact live owners at a dynamic boundary. It
rejects a membership relation whose source or target merely has the same size.
If the relation was restored under distinct owners with matching persistent
keys, align both endpoints explicitly and call `fromAligned`.

Rebinding a constructed system through `rebindCenters` or `rebindAmbient` is
O(1) and shares immutable injection and CSR payloads. Calling `neighborhood`
materializes one Region in O(row degree).

locus4s does not generate the rows. Radius selection, distance metrics, grid
traversal, mesh geodesics, masks, atlas rules, and searchlight policy belong to
the downstream library that understands the domain. Neutral composite
persistence for a neighborhood system is deferred; persist the injection and
relation through an application record until locus4s defines such a record.
