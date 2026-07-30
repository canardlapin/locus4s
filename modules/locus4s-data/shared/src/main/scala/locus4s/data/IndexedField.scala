package locus4s.data

import locus4s.DomainAlignment
import locus4s.FiniteDomain
import locus4s.Index
import locus4s.Region
import locus4s.Selection
import locus4s.SpaceMismatch
import locus4s.TotalMap

enum FieldConstructionError:
  case WrongValueCount(expected: Int, actual: Int)

  def message: String =
    this match
      case WrongValueCount(expected, actual) =>
        s"field requires $expected values, found $actual"

/** Compatibility name for the former concrete field error. */
type IndexedFieldError = FieldConstructionError

object IndexedFieldError:
  val WrongValueCount = FieldConstructionError.WrongValueCount

/** Representation-neutral values indexed by one finite domain.
  *
  * Implementations may own vectors, primitive arrays, mapped/chunked storage,
  * JavaScript typed arrays, or non-owning views. Typed lookup is total and O(1)
  * whenever the implementation advertises constant indexed access.
  */
trait Field[S, +A]:
  def space: FiniteDomain[S]

  def apply(index: Index[S]): A

  /** Compatibility spelling for total typed lookup. */
  final def at(index: Index[S]): A =
    apply(index)

  def foreachValue(f: A => Unit): Unit =
    space.foreachIndex(index => f(apply(index)))

  def foreachValueWithOrdinal(f: (Int, A) => Unit): Unit =
    space.foreachIndex(index => f(index.ordinal, apply(index)))

  def valuesInDomainOrder: Iterator[A] =
    space.indices.map(apply)

  def toVector: Vector[A] =
    valuesInDomainOrder.toVector

  /** Lazy representation-neutral map; construction is O(1). */
  def map[B](f: A => B): Field[S, B] =
    Field.view(space)(index => f(apply(index)))

  def zipWith[B, C](
      that: Field[S, B]
  )(combine: (A, B) => C): Field[S, C] =
    Field.view(space): index =>
      combine(apply(index), that(index))

  def zipWithChecked[T, B, C](
      that: Field[T, B]
  )(
      combine: (A, B) => C
  ): Either[SpaceMismatch, Field[S, C]] =
    if space.sameRuntimeOwnerAs(that.space) then
      space.align(that.space) match
        case Right(alignment) =>
          Right(zipWith(that.rebind(alignment.reverse))(combine))
        case Left(_) =>
          Left(space.mismatch(that.space))
    else Left(space.mismatch(that.space))

  /** Contravariant action of a total map. Construction is an O(1) view. */
  def pullback[X](mapping: TotalMap[X, S]): Field[X, A] =
    Field.view(mapping.from)(index => apply(mapping(index)))

  def gather(selection: Selection[S]): Field[selection.I, A] =
    pullback(selection.embedding.toTotalMap)

  def restrict(region: Region[S]): SectionView[S, A] =
    SectionView.create(this, region)

  def restrictChecked[T](
      region: Region[T]
  ): Either[SpaceMismatch, SectionView[S, A]] =
    if space.sameRuntimeOwnerAs(region.space) then
      space.align(region.space) match
        case Right(alignment) =>
          Right(
            SectionView.create(
              this,
              alignment.reverse.transport(region)
            )
          )
        case Left(_) =>
          Left(space.mismatch(region.space))
    else Left(space.mismatch(region.space))

  /** O(1) owner transport. Concrete owners may override to share raw storage. */
  def rebind[T](
      alignment: DomainAlignment[S, T]
  ): Field[T, A] =
    Field.view(alignment.right): index =>
      apply(alignment.toLeft(index))

object Field:
  def view[S, A](
      space: FiniteDomain[S]
  )(valueAt: Index[S] => A): Field[S, A] =
    new FieldView(space, valueAt)

  private final class FieldView[S, A](
      val space: FiniteDomain[S],
      valueAt: Index[S] => A
  ) extends Field[S, A]:
    def apply(index: Index[S]): A =
      valueAt(index)

/** Strict immutable reference implementation backed by `Vector`. */
final class VectorField[S, +A] private (
    val space: FiniteDomain[S],
    private val ownedValues: Vector[A]
) extends Field[S, A]:
  def apply(index: Index[S]): A =
    ownedValues(index.ordinal)

  override def foreachValue(f: A => Unit): Unit =
    ownedValues.foreach(f)

  override def foreachValueWithOrdinal(f: (Int, A) => Unit): Unit =
    var ordinal = 0
    while ordinal < ownedValues.length do
      f(ordinal, ownedValues(ordinal))
      ordinal += 1

  override def valuesInDomainOrder: Iterator[A] =
    ownedValues.iterator

  override def toVector: Vector[A] =
    ownedValues

  override def rebind[T](
      alignment: DomainAlignment[S, T]
  ): VectorField[T, A] =
    new VectorField(alignment.right, ownedValues)

object VectorField:
  def fromValues[S, A](
      space: FiniteDomain[S],
      values: IterableOnce[A]
  ): Either[FieldConstructionError, VectorField[S, A]] =
    val copied = values.iterator.toVector
    if copied.length == space.size then Right(fromOwned(space, copied))
    else
      Left(
        FieldConstructionError.WrongValueCount(
          space.size,
          copied.length
        )
      )

  def tabulate[S, A](
      space: FiniteDomain[S]
  )(valueAt: Index[S] => A): VectorField[S, A] =
    val builder = Vector.newBuilder[A]
    builder.sizeHint(space.size)
    space.foreachIndex(index => builder += valueAt(index))
    fromOwned(space, builder.result())

  private def fromOwned[S, A](
      space: FiniteDomain[S],
      values: Vector[A]
  ): VectorField[S, A] =
    new VectorField(space, values)

/** Compatibility alias for the former concrete storage contract. */
type IndexedField[S, A] = VectorField[S, A]

object IndexedField:
  def fromValues[S, A](
      space: FiniteDomain[S],
      values: IterableOnce[A]
  ): Either[FieldConstructionError, VectorField[S, A]] =
    VectorField.fromValues(space, values)

  def tabulate[S, A](
      space: FiniteDomain[S]
  )(valueAt: Index[S] => A): VectorField[S, A] =
    VectorField.tabulate(space)(valueAt)

/** Destination policy for algorithms that materialize fields. */
trait FieldBuilder:
  def tabulate[S, A](
      space: FiniteDomain[S]
  )(valueAt: Index[S] => A): Field[S, A]

object FieldBuilder:
  val vector: FieldBuilder =
    new FieldBuilder:
      def tabulate[S, A](
          space: FiniteDomain[S]
      )(valueAt: Index[S] => A): Field[S, A] =
        VectorField.tabulate(space)(valueAt)

enum SectionLookupError:
  case OutsideSupport(pointOrdinal: Int)

  def message: String =
    this match
      case OutsideSupport(pointOrdinal) =>
        s"index $pointOrdinal is outside the section support"

enum SectionSelectionError:
  case OutsideSupport(pointOrdinal: Int)

  def message: String =
    this match
      case OutsideSupport(pointOrdinal) =>
        s"selection index $pointOrdinal is outside the section support"

/** A non-owning field view restricted to a Region support. */
final class SectionView[S, +A] private (
    val field: Field[S, A],
    val support: Region[S]
):
  def apply(index: Index[S]): Either[SectionLookupError, A] =
    if support.contains(index) then Right(field(index))
    else Left(SectionLookupError.OutsideSupport(index.ordinal))

  /** Compatibility spelling for section lookup. */
  def at(index: Index[S]): Either[SectionLookupError, A] =
    apply(index)

  /** O(1) mapped view; the underlying full field is not eagerly mapped. */
  def map[B](f: A => B): SectionView[S, B] =
    new SectionView(field.map(f), support)

  def restrict(region: Region[S]): SectionView[S, A] =
    new SectionView(field, support.intersect(region))

  def valuesInDomainOrder: Iterator[A] =
    support.indicesInDomainOrder.map(field.apply)

  def gather(
      selection: Selection[S]
  ): Either[SectionSelectionError, Field[selection.I, A]] =
    val outside =
      selection.positions.indices
        .map(selection.apply)
        .find(index => !support.contains(index))
    outside match
      case Some(index) =>
        Left(SectionSelectionError.OutsideSupport(index.ordinal))
      case None =>
        Right(field.gather(selection))

  /** Compatibility spelling retaining the selection position domain. */
  def valuesIn(
      selection: Selection[S]
  ): Either[SectionSelectionError, Field[selection.I, A]] =
    gather(selection)

  def rebind[T](
      alignment: DomainAlignment[S, T]
  ): SectionView[T, A] =
    new SectionView(
      field.rebind(alignment),
      support.rebind(alignment)
    )

object SectionView:
  def create[S, A](
      field: Field[S, A],
      support: Region[S]
  ): SectionView[S, A] =
    new SectionView(field, support)

/** Compatibility name for section views. */
type Section[S, A] = SectionView[S, A]

object Section:
  def create[S, A](
      field: Field[S, A],
      support: Region[S]
  ): SectionView[S, A] =
    SectionView.create(field, support)

extension [A, B](alignment: DomainAlignment[A, B])
  def transport[C](field: Field[A, C]): Field[B, C] =
    field.rebind(alignment)

  def transport[C](section: SectionView[A, C]): SectionView[B, C] =
    section.rebind(alignment)
