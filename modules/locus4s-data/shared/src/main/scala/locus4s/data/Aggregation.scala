package locus4s.data

import locus4s.Relation
import locus4s.SpaceMismatch
import locus4s.TotalMap
import scala.collection.mutable.ArrayBuffer

/** Deterministic, domain-neutral aggregation in source-domain order. */
object Aggregation:
  /** Fold one contribution from every source through a total grouping map.
    *
    * `empty` is evaluated independently for every target point.
    */
  def foldMapBy[X, FX, Y, A, M](
      grouping: TotalMap[X, Y],
      field: IndexedField[FX, A]
  )(
      empty: => M
  )(
      contribution: A => M
  )(
      combine: (M, M) => M
  ): Either[SpaceMismatch, IndexedField[Y, M]] =
    if !grouping.from.sameRuntimeOwnerAs(field.space) then
      Left(
        SpaceMismatch(
          grouping.from.record,
          field.space.record,
          grouping.from.samePersistentIdentityAs(field.space)
        )
      )
    else
      val accumulated =
        freshAccumulators(grouping.to.size, empty)
      val targets = grouping.targetOrdinals
      field.valuesInDomainOrder.zip(targets.iterator).foreach:
        (value, target) =>
          val next =
            contribution(value)
          accumulated(target) =
            combine(accumulated(target), next)
      Right(
        IndexedField.tabulate(grouping.to)(point =>
          accumulated(point.value)
        )
      )

  /** Fold one source contribution into every related target.
    *
    * Sources with an empty relation row are not evaluated. For each target,
    * contributions are combined in increasing source-domain order. `empty` is
    * evaluated independently for every target point.
    */
  def foldMapBy[X, FX, Y, A, M](
      grouping: Relation[X, Y],
      field: IndexedField[FX, A]
  )(
      empty: => M
  )(
      contribution: A => M
  )(
      combine: (M, M) => M
  ): Either[SpaceMismatch, IndexedField[Y, M]] =
    if !grouping.from.sameRuntimeOwnerAs(field.space) then
      Left(
        SpaceMismatch(
          grouping.from.record,
          field.space.record,
          grouping.from.samePersistentIdentityAs(field.space)
        )
      )
    else
      val accumulated =
        freshAccumulators(grouping.to.size, empty)
      val rows = grouping.ordinalRows
      field.valuesInDomainOrder.zip(rows.iterator).foreach:
        (value, targets) =>
          if targets.nonEmpty then
            val next =
              contribution(value)
            var targetIndex = 0
            while targetIndex < targets.length do
              val target = targets(targetIndex)
              accumulated(target) =
                combine(accumulated(target), next)
              targetIndex += 1
      Right(
        IndexedField.tabulate(grouping.to)(point =>
          accumulated(point.value)
        )
      )

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
