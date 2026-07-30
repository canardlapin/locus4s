package locus4s.data

import locus4s.Relation
import locus4s.SpaceMismatch
import locus4s.TotalMap
import scala.collection.mutable.ArrayBuffer

/** Deterministic finite-domain pushforward in increasing source order.
  *
  * Algorithms access private immutable map/relation storage directly and never clone
  * the grouping before folding.
  */
object Aggregation:
  def foldMapBy[X, Y, A, M](
      grouping: TotalMap[X, Y],
      field: Field[X, A]
  )(
      empty: => M
  )(
      contribution: A => M
  )(
      combine: (M, M) => M
  ): Field[Y, M] =
    foldMapByWith(grouping, field, FieldBuilder.vector)(empty)(
      contribution
    )(combine)

  def foldMapByWith[X, Y, A, M](
      grouping: TotalMap[X, Y],
      field: Field[X, A],
      builder: FieldBuilder
  )(
      empty: => M
  )(
      contribution: A => M
  )(
      combine: (M, M) => M
  ): Field[Y, M] =
    val accumulated =
      freshAccumulators(grouping.to.size, empty)
    grouping.foreachMapping: (source, target) =>
      accumulated(target.ordinal) = combine(
        accumulated(target.ordinal),
        contribution(field(source))
      )
    builder.tabulate(grouping.to)(index => accumulated(index.ordinal))

  def foldMapByChecked[X, FX, Y, A, M](
      grouping: TotalMap[X, Y],
      field: Field[FX, A]
  )(
      empty: => M
  )(
      contribution: A => M
  )(
      combine: (M, M) => M
  ): Either[SpaceMismatch, Field[Y, M]] =
    if grouping.from.sameRuntimeOwnerAs(field.space) then
      grouping.from.align(field.space) match
        case Right(alignment) =>
          Right(
            foldMapBy(
              grouping,
              field.rebind(alignment.reverse)
            )(empty)(contribution)(combine)
          )
        case Left(_) =>
          Left(grouping.from.mismatch(field.space))
    else Left(grouping.from.mismatch(field.space))

  def foldMapBy[X, Y, A, M](
      grouping: Relation[X, Y],
      field: Field[X, A]
  )(
      empty: => M
  )(
      contribution: A => M
  )(
      combine: (M, M) => M
  ): Field[Y, M] =
    foldMapByWith(grouping, field, FieldBuilder.vector)(empty)(
      contribution
    )(combine)

  def foldMapByWith[X, Y, A, M](
      grouping: Relation[X, Y],
      field: Field[X, A],
      builder: FieldBuilder
  )(
      empty: => M
  )(
      contribution: A => M
  )(
      combine: (M, M) => M
  ): Field[Y, M] =
    val accumulated =
      freshAccumulators(grouping.to.size, empty)
    grouping.from.foreachIndex: source =>
      if grouping.hasTargets(source) then
        val contributionValue = contribution(field(source))
        grouping.foreachTarget(source): target =>
          accumulated(target.ordinal) = combine(
            accumulated(target.ordinal),
            contributionValue
          )
    builder.tabulate(grouping.to)(index => accumulated(index.ordinal))

  def foldMapByChecked[X, FX, Y, A, M](
      grouping: Relation[X, Y],
      field: Field[FX, A]
  )(
      empty: => M
  )(
      contribution: A => M
  )(
      combine: (M, M) => M
  ): Either[SpaceMismatch, Field[Y, M]] =
    if grouping.from.sameRuntimeOwnerAs(field.space) then
      grouping.from.align(field.space) match
        case Right(alignment) =>
          Right(
            foldMapBy(
              grouping,
              field.rebind(alignment.reverse)
            )(empty)(contribution)(combine)
          )
        case Left(_) =>
          Left(grouping.from.mismatch(field.space))
    else Left(grouping.from.mismatch(field.space))

  /** User-facing spelling for total-map pushforward. */
  def pushForward[X, Y, A, M](
      grouping: TotalMap[X, Y],
      field: Field[X, A]
  )(
      empty: => M
  )(
      contribution: A => M
  )(
      combine: (M, M) => M
  ): Field[Y, M] =
    foldMapBy(grouping, field)(empty)(contribution)(combine)

  /** User-facing spelling for relational pushforward. */
  def pushForward[X, Y, A, M](
      grouping: Relation[X, Y],
      field: Field[X, A]
  )(
      empty: => M
  )(
      contribution: A => M
  )(
      combine: (M, M) => M
  ): Field[Y, M] =
    foldMapBy(grouping, field)(empty)(contribution)(combine)

  private def freshAccumulators[M](
      size: Int,
      empty: => M
  ): ArrayBuffer[M] =
    val result = ArrayBuffer.empty[M]
    result.sizeHint(size)
    var index = 0
    while index < size do
      result += empty
      index += 1
    result
