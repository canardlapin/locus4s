package locus4s.laws

import locus4s.PartialMap
import locus4s.TotalMap
import locus4s.data.Aggregation
import locus4s.data.Field

object AggregationLaws:
  /** Partial-map aggregation agrees with a direct immutable reference model.
    *
    * This law permits order-sensitive `combine`: both sides visit defined sources in
    * increasing source-domain order and preserve `empty` for targets without a
    * preimage. `empty`, `contribution`, and `combine` must be pure.
    */
  def partialMapReference[X, Y, A, M](
      grouping: PartialMap[X, Y],
      field: Field[X, A]
  )(
      empty: => M
  )(
      contribution: A => M
  )(
      combine: (M, M) => M
  )(
      equal: (M, M) => Boolean
  ): Boolean =
    val actual =
      Aggregation.foldMapBy(grouping, field)(empty)(contribution)(combine)
    val initial = Vector.fill(grouping.to.size)(empty)
    val expected =
      grouping.from.indices.foldLeft(initial): (accumulated, source) =>
        grouping(source) match
          case Some(target) =>
            accumulated.updated(
              target.ordinal,
              combine(
                accumulated(target.ordinal),
                contribution(field(source))
              )
            )
          case None =>
            accumulated
    actual.space.indices.forall: target =>
      equal(actual(target), expected(target.ordinal))

  /** Direct versus staged pushforward through two total maps.
    *
    * This law requires `empty` and `combine` to form a commutative monoid and
    * `contribution` to be pure.
    */
  def totalMapFusion[X, Y, Z, A, M](
      first: TotalMap[X, Y],
      second: TotalMap[Y, Z],
      field: Field[X, A]
  )(
      empty: => M
  )(
      contribution: A => M
  )(
      combine: (M, M) => M
  )(
      equal: (M, M) => Boolean
  ): Boolean =
    val direct =
      Aggregation.foldMapBy(first.andThen(second), field)(empty)(
        contribution
      )(combine)
    val intermediate =
      Aggregation.foldMapBy(first, field)(empty)(contribution)(combine)
    val staged =
      Aggregation.foldMapBy(second, intermediate)(empty)(identity)(
        combine
      )
    direct.space.indices.forall(index => equal(direct(index), staged(index)))
