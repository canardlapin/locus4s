package locus4s.laws

import locus4s.TotalMap
import locus4s.data.Aggregation
import locus4s.data.Field

object AggregationLaws:
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
