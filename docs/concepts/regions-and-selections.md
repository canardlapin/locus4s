# Regions and selections

`Region[S]` and `Selection[S]` both describe part of a domain, but they answer
different questions.

- A Region is an unordered subset of `S`.
- A Selection is an ordered injection from a position domain into `S`.

## Region Boolean algebra

```scala mdoc:silent
import locus4s.*
import locus4s.data.*

def expectRight[E, A](result: Either[E, A]): A =
  result match
    case Right(value) => value
    case Left(error)  => sys.error(error.toString)

val owner = expectRight(FiniteDomain.ephemeral("vertices", 8))
val vertices = owner.value

val left = expectRight(Region.fromOrdinals(vertices, Vector(0, 2, 4, 6)))
val right = expectRight(Region.fromOrdinals(vertices, Vector(1, 2, 5, 6)))
```

```scala mdoc
left.union(right).ordinalsInDomainOrder.toVector
```

```scala mdoc
left.intersect(right).ordinalsInDomainOrder.toVector
```

```scala mdoc
left.diff(right).ordinalsInDomainOrder.toVector
```

Typed union, intersection, difference, xor, complement, membership, and subset
tests are total. `Region.empty` and `Region.whole` use constant-size
representations; sparse regions store sorted distinct primitive ordinals.

## Overlap similarity

```scala mdoc
left.dice(right)
left.jaccard(right)
```

Dice is twice the intersection cardinality divided by the sum of the two
cardinalities. Jaccard divides the intersection cardinality by the union
cardinality. Both range from zero to one; two empty regions have similarity
one. Counts are widened before arithmetic, including for compact whole regions.
At dynamic boundaries, `diceChecked` and `jaccardChecked` require the same live
domain owner, even for empty operands. Matching persisted identity alone is
insufficient: explicitly align and rebind independently restored regions first.

## Selection order is data

An ordered extraction cannot be represented faithfully by its support alone:

```scala mdoc:silent
val selection =
  expectRight(Selection.fromOrdinals(vertices, Vector(6, 0, 4)))

val signal =
  VectorField.tabulate(vertices)(index => s"value-${index.ordinal}")

val selected = signal.gather(selection)
```

```scala mdoc
selection.support.ordinalsInDomainOrder.toVector
```

```scala mdoc
selected.toVector
```

The support is `{0, 4, 6}`, while gathered values follow selection order
`6, 0, 4`.

`Selection[S]` has a path-dependent position owner `selection.I`:

```scala mdoc
(
  selected.space.sameRuntimeOwnerAs(selection.positions),
  selection.embedding.support == selection.support
)
```

The result of gathering is `Field[selection.I, A]`, not a raw `Vector[A]`.
That retained identity lets compact data compose with maps whose source is the
same position domain.

Selections created from ordinals use ephemeral position domains. The
[persistence guide](../guides/persistence-and-restoration.md) shows how to
assign an explicit persistent identity when the ordering must cross a process
boundary.
