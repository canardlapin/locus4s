package locus4s

enum SelectionError:
  case OutOfBounds(position: Int, pointOrdinal: Int, size: Int)
  case DuplicateOrdinal(pointOrdinal: Int)
  case ForeignDomain(
      position: Int,
      expected: DomainRecord,
      actual: DomainRecord
  )

  def message: String =
    this match
      case OutOfBounds(position, pointOrdinal, size) =>
        s"selection ordinal at position $position is outside [0, $size): $pointOrdinal"
      case DuplicateOrdinal(pointOrdinal) =>
        s"selection contains duplicate ordinal $pointOrdinal"
      case ForeignDomain(position, expected, actual) =>
        s"selection point at position $position belongs to ${actual.id.value}, " +
          s"expected ${expected.id.value}"

/** An immutable, ordered, duplicate-free sequence of domain points. */
final class Selection[S] private (
    val space: FiniteSpace[S],
    private val ordered: Array[Int],
    val region: Region[S]
):
  def size: Int =
    ordered.length

  def isEmpty: Boolean =
    ordered.isEmpty

  def get(index: Int): Option[Point[S]] =
    if index >= 0 && index < ordered.length then
      space.pointOption(ordered(index))
    else
      None

  def points: Iterator[Point[S]] =
    ordered.iterator.flatMap(space.pointOption)

  def ordinals: Array[Int] =
    ordered.clone()

  /** Rebind this selection through checked persistent-domain evidence. */
  def rebind[B](
      alignment: DomainAlignment[S, B]
  ): Either[SpaceMismatch, Selection[B]] =
    if !space.sameRuntimeOwnerAs(alignment.left) then
      Left(alignment.left.mismatch(space))
    else
      region.rebind(alignment).map: reboundRegion =>
        new Selection(alignment.right, ordered.clone(), reboundRegion)

  override def equals(other: Any): Boolean =
    other match
      case that: Selection[?] =>
        space == that.space && Selection.sameOrdinals(ordered, that.ordered)
      case _ =>
        false

  override def hashCode(): Int =
    var result = space.hashCode()
    var index = 0
    while index < ordered.length do
      result = 31 * result + ordered(index)
      index += 1
    result

  override def toString: String =
    s"Selection(${space.name.value}, size=$size)"

object Selection:
  def empty[S](space: FiniteSpace[S]): Selection[S] =
    new Selection(space, Array.emptyIntArray, Region.empty(space))

  def fromRegion[S](region: Region[S]): Selection[S] =
    new Selection(region.space, region.ordinalsInDomainOrder, region)

  def fromOrdinals[S](
      space: FiniteSpace[S],
      ordinals: IterableOnce[Int]
  ): Either[SelectionError, Selection[S]] =
    val input = ordinals.iterator.toArray
    val seen = scala.collection.mutable.HashSet.empty[Int]
    var index = 0
    var error = Option.empty[SelectionError]
    while index < input.length && error.isEmpty do
      val ordinal = input(index)
      if !space.containsOrdinal(ordinal) then
        error =
          Some(SelectionError.OutOfBounds(index, ordinal, space.size))
      else if seen.contains(ordinal) then
        error = Some(SelectionError.DuplicateOrdinal(ordinal))
      else
        seen += ordinal
      index += 1

    error match
      case Some(value) =>
        Left(value)
      case None =>
        fromValidatedInput(space, input)

  def fromPoints[S](
      space: FiniteSpace[S],
      points: IterableOnce[Point[S]]
  ): Either[SelectionError, Selection[S]] =
    val input = points.iterator.toArray
    val ordinals = Array.ofDim[Int](input.length)
    val seen = scala.collection.mutable.HashSet.empty[Int]
    var index = 0
    var error = Option.empty[SelectionError]
    while index < input.length && error.isEmpty do
      val point = input(index)
      space.validate(point) match
        case Left(PointError.OutOfBounds(ordinal, size)) =>
          error =
            Some(SelectionError.OutOfBounds(index, ordinal, size))
        case Left(PointError.ForeignDomain(expected, actual)) =>
          error =
            Some(SelectionError.ForeignDomain(index, expected, actual))
        case Right(ordinal) =>
          if seen.contains(ordinal) then
            error = Some(SelectionError.DuplicateOrdinal(ordinal))
          else
            ordinals(index) = ordinal
            seen += ordinal
      index += 1

    error match
      case Some(value) =>
        Left(value)
      case None =>
        fromValidatedInput(space, ordinals)

  private def fromValidatedInput[S](
      space: FiniteSpace[S],
      ordered: Array[Int]
  ): Either[SelectionError, Selection[S]] =
    Region.fromOrdinals(space, ordered) match
      case Left(RegionError.OutOfBounds(position, ordinal, size)) =>
        Left(SelectionError.OutOfBounds(position, ordinal, size))
      case Left(RegionError.ForeignDomain(position, expected, actual)) =>
        Left(SelectionError.ForeignDomain(position, expected, actual))
      case Right(region) =>
        Right(new Selection(space, ordered, region))

  private def sameOrdinals(left: Array[Int], right: Array[Int]): Boolean =
    if left.length != right.length then false
    else
      var index = 0
      var same = true
      while index < left.length && same do
        same = left(index) == right(index)
        index += 1
      same
