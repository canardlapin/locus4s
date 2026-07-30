package locus4s.laws

import locus4s.DomainAlignment
import locus4s.Selection
import locus4s.TotalMap
import locus4s.data.Field

object FieldLaws:
  def mapIdentity[S, A](
      field: Field[S, A]
  )(
      equal: (A, A) => Boolean
  ): Boolean =
    sameField(field, field.map(identity))(equal)

  def mapComposition[S, A, B, C](
      field: Field[S, A],
      first: A => B,
      second: B => C
  )(
      equal: (C, C) => Boolean
  ): Boolean =
    sameField(
      field.map(first).map(second),
      field.map(first.andThen(second))
    )(equal)

  def pullbackIdentity[S, A](
      field: Field[S, A]
  )(
      equal: (A, A) => Boolean
  ): Boolean =
    sameField(
      field.pullback(TotalMap.identity(field.space)),
      field
    )(equal)

  def pullbackComposition[X, Y, S, A](
      field: Field[S, A],
      first: TotalMap[X, Y],
      second: TotalMap[Y, S]
  )(
      equal: (A, A) => Boolean
  ): Boolean =
    sameField(
      field.pullback(second).pullback(first),
      field.pullback(first.andThen(second))
    )(equal)

  def gatherUsesPositionDomain[S, A](
      field: Field[S, A],
      selection: Selection[S]
  )(
      equal: (A, A) => Boolean
  ): Boolean =
    val gathered = field.gather(selection)
    gathered.space.sameRuntimeOwnerAs(selection.positions) &&
    selection.positions.indices.forall: position =>
      equal(gathered(position), field(selection(position)))

  def alignmentRoundTrip[S, T, A](
      field: Field[S, A],
      alignment: DomainAlignment[S, T]
  )(
      equal: (A, A) => Boolean
  ): Boolean =
    val roundTrip =
      field.rebind(alignment).rebind(alignment.reverse)
    roundTrip.space.sameRuntimeOwnerAs(field.space) &&
    sameField(roundTrip, field)(equal)

  def transportPullbackNaturality[X, S, T, A](
      field: Field[S, A],
      mapping: TotalMap[X, S],
      alignment: DomainAlignment[S, T]
  )(
      equal: (A, A) => Boolean
  ): Boolean =
    sameField(
      field.rebind(alignment).pullback(alignment.transportTo(mapping)),
      field.pullback(mapping)
    )(equal)

  private def sameField[S, A](
      left: Field[S, A],
      right: Field[S, A]
  )(
      equal: (A, A) => Boolean
  ): Boolean =
    left.space.indices.forall(index => equal(left(index), right(index)))

/** Compatibility name retained for existing law consumers. */
object IndexedFieldLaws:
  export FieldLaws.*
