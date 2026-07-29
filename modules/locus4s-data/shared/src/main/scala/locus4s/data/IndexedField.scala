package locus4s.data

import locus4s.DomainAlignment
import locus4s.FiniteSpace
import locus4s.Point
import locus4s.PointError
import locus4s.Region
import locus4s.Selection
import locus4s.SpaceMismatch

enum IndexedFieldError:
  case WrongValueCount(expected: Int, actual: Int)

  def message: String =
    this match
      case WrongValueCount(expected, actual) =>
        s"indexed field requires $expected values, found $actual"

enum SectionLookupError:
  case InvalidPoint(error: PointError)
  case OutsideSupport(pointOrdinal: Int)

  def message: String =
    this match
      case InvalidPoint(error) =>
        error.message
      case OutsideSupport(pointOrdinal) =>
        s"point $pointOrdinal is outside the section support"

enum SectionSelectionError:
  case WrongSpace(error: SpaceMismatch)
  case OutsideSupport(pointOrdinal: Int)

  def message: String =
    this match
      case WrongSpace(error) =>
        error.message
      case OutsideSupport(pointOrdinal) =>
        s"selection point $pointOrdinal is outside the section support"

/** A strict immutable value at every point of one live finite domain. */
final class IndexedField[S, +A] private (
    val space: FiniteSpace[S],
    private val ownedValues: Vector[A]
):
  def at(point: Point[S]): Either[PointError, A] =
    if space.contains(point) then Right(ownedValues(point.value))
    else
      Left(PointError.ForeignDomain(space.record, point.domain))

  def valuesInDomainOrder: Iterator[A] =
    ownedValues.iterator

  def toVector: Vector[A] =
    ownedValues

  def map[B](f: A => B): IndexedField[S, B] =
    IndexedField.fromOwned(space, ownedValues.map(f))

  def zipWith[T, B, C](
      that: IndexedField[T, B]
  )(
      combine: (A, B) => C
  ): Either[SpaceMismatch, IndexedField[S, C]] =
    if space.sameRuntimeOwnerAs(that.space) then
      Right(
        IndexedField.fromOwned(
          space,
          ownedValues
            .zip(that.ownedValues)
            .map((left, right) => combine(left, right))
        )
      )
    else Left(mismatch(that.space))

  def restrict[T](
      region: Region[T]
  ): Either[SpaceMismatch, Section[S, A]] =
    Section.create(this, region)

  /** Rebind through explicit checked persistent-domain evidence.
    *
    * The immutable values are shared; only their live domain owner changes.
    */
  def rebind[T](
      alignment: DomainAlignment[S, T]
  ): Either[SpaceMismatch, IndexedField[T, A]] =
    if space.sameRuntimeOwnerAs(alignment.left) then
      Right(IndexedField.fromOwned(alignment.right, ownedValues))
    else Left(mismatch(alignment.left))

  private def mismatch[T](
      actual: FiniteSpace[T]
  ): SpaceMismatch =
    SpaceMismatch(
      space.record,
      actual.record,
      space.samePersistentIdentityAs(actual)
    )

object IndexedField:
  def fromValues[S, A](
      space: FiniteSpace[S],
      values: IterableOnce[A]
  ): Either[IndexedFieldError, IndexedField[S, A]] =
    val copied = values.iterator.toVector
    if copied.length == space.size then
      Right(fromOwned(space, copied))
    else
      Left(
        IndexedFieldError.WrongValueCount(space.size, copied.length)
      )

  /** Evaluates `valueAt` exactly once per point, in domain order. */
  def tabulate[S, A](
      space: FiniteSpace[S]
  )(
      valueAt: Point[S] => A
  ): IndexedField[S, A] =
    fromOwned(space, space.points.map(valueAt).toVector)

  private def fromOwned[S, A](
      space: FiniteSpace[S],
      values: Vector[A]
  ): IndexedField[S, A] =
    new IndexedField(space, values)

/** An indexed field restricted to an immutable finite-domain region. */
final class Section[S, +A] private (
    val field: IndexedField[S, A],
    val support: Region[S]
):
  def at(point: Point[S]): Either[SectionLookupError, A] =
    field
      .at(point)
      .left
      .map(SectionLookupError.InvalidPoint.apply)
      .flatMap: value =>
        if support.contains(point) then Right(value)
        else Left(SectionLookupError.OutsideSupport(point.value))

  def map[B](f: A => B): Section[S, B] =
    new Section(field.map(f), support)

  def restrict[T](
      region: Region[T]
  ): Either[SpaceMismatch, Section[S, A]] =
    for
      candidate <- Section.create(field, region)
      intersection <- support.intersect(candidate.support)
    yield new Section(field, intersection)

  def valuesInDomainOrder: Iterator[A] =
    val values = field.toVector
    support.pointsInDomainOrder.map(point => values(point.value))

  def valuesIn[T](
      selection: Selection[T]
  ): Either[SectionSelectionError, Vector[A]] =
    if !field.space.sameRuntimeOwnerAs(selection.space) then
      Left(
        SectionSelectionError.WrongSpace(
          SpaceMismatch(
            field.space.record,
            selection.space.record,
            field.space.samePersistentIdentityAs(selection.space)
          )
        )
      )
    else
      val ordinals = selection.ordinals
      val supportOrdinals = support.ordinalsInDomainOrder.toSet
      ordinals.find(ordinal => !supportOrdinals.contains(ordinal)) match
        case Some(ordinal) =>
          Left(SectionSelectionError.OutsideSupport(ordinal))
        case None =>
          val values = field.toVector
          Right(
            ordinals.iterator
              .map(values)
              .toVector
          )

  def rebind[T](
      alignment: DomainAlignment[S, T]
  ): Either[SpaceMismatch, Section[T, A]] =
    for
      reboundField <- field.rebind(alignment)
      reboundSupport <- support.rebind(alignment)
    yield new Section(reboundField, reboundSupport)

object Section:
  def create[S, T, A](
      field: IndexedField[S, A],
      support: Region[T]
  ): Either[SpaceMismatch, Section[S, A]] =
    if !field.space.sameRuntimeOwnerAs(support.space) then
      Left(
        SpaceMismatch(
          field.space.record,
          support.space.record,
          field.space.samePersistentIdentityAs(support.space)
        )
      )
    else
      field.space
        .align(support.space)
        .left
        .map(error =>
          SpaceMismatch(
            error.left,
            error.right,
            error.left == error.right
          )
        )
        .flatMap(_.regionToLeft(support))
        .map(rebound => new Section(field, rebound))
