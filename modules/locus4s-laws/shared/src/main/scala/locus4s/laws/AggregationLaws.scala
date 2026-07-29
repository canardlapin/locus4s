package locus4s.laws

import locus4s.SpaceMismatch
import locus4s.TotalMap
import locus4s.data.Aggregation
import locus4s.data.IndexedField

object AggregationLaws:
  /** Checks direct versus staged aggregation through two total maps.
    *
    * This is a law only when `empty` and `combine` form a lawful commutative
    * monoid and `contribution` is pure.
    */
  def totalMapFusion[X, FX, Y, Z, A, M](
      first: TotalMap[X, Y],
      second: TotalMap[Y, Z],
      field: IndexedField[FX, A]
  )(
      empty: => M
  )(
      contribution: A => M
  )(
      combine: (M, M) => M
  )(
      equal: (M, M) => Boolean
  ): Either[SpaceMismatch, Boolean] =
    for
      directGrouping <- first.andThen(second)
      direct <-
        Aggregation.foldMapBy(directGrouping, field)(empty)(
          contribution
        )(combine)
      intermediate <-
        Aggregation.foldMapBy(first, field)(empty)(contribution)(
          combine
        )
      staged <-
        Aggregation.foldMapBy(second, intermediate)(empty)(identity)(
          combine
        )
    yield direct.toVector
      .zip(staged.toVector)
      .forall((left, right) => equal(left, right))
