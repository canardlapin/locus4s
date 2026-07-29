package locus4s

enum RegionError:
  case OutOfBounds(position: Int, pointOrdinal: Int, size: Int)
  case ForeignDomain(
      position: Int,
      expected: DomainRecord,
      actual: DomainRecord
  )

  def message: String =
    this match
      case OutOfBounds(position, pointOrdinal, size) =>
        s"region ordinal at position $position is outside [0, $size): $pointOrdinal"
      case ForeignDomain(position, expected, actual) =>
        s"region point at position $position belongs to ${actual.id.value}, " +
          s"expected ${expected.id.value}"

/** An immutable set of points in one live finite domain.
  *
  * Members are stored canonically in increasing ordinal order.
  */
final class Region[S] private (
    val space: FiniteSpace[S],
    private val members: Array[Int]
):
  def cardinality: Int =
    members.length

  def isEmpty: Boolean =
    members.isEmpty

  def isWhole: Boolean =
    members.length == space.size

  def contains(point: Point[S]): Boolean =
    space.owns(point) && containsOrdinal(point.value)

  def pointsInDomainOrder: Iterator[Point[S]] =
    members.iterator.flatMap(space.pointOption)

  def ordinalsInDomainOrder: Array[Int] =
    members.clone()

  def subsetOf(that: Region[S]): Either[SpaceMismatch, Boolean] =
    compatible(that).map: _ =>
      var left = 0
      var right = 0
      var subset = true
      while left < members.length && subset do
        while right < that.members.length && that.members(right) < members(left) do
          right += 1
        if right >= that.members.length || that.members(right) != members(left) then
          subset = false
        left += 1
      subset

  def union(that: Region[S]): Either[SpaceMismatch, Region[S]] =
    combine(that, Region.Operation.Union)

  def intersect(that: Region[S]): Either[SpaceMismatch, Region[S]] =
    combine(that, Region.Operation.Intersection)

  def diff(that: Region[S]): Either[SpaceMismatch, Region[S]] =
    combine(that, Region.Operation.Difference)

  def xor(that: Region[S]): Either[SpaceMismatch, Region[S]] =
    combine(that, Region.Operation.SymmetricDifference)

  def complement: Region[S] =
    val result = Array.ofDim[Int](space.size - members.length)
    var ordinal = 0
    var member = 0
    var out = 0
    while ordinal < space.size do
      if member < members.length && members(member) == ordinal then
        member += 1
      else
        result(out) = ordinal
        out += 1
      ordinal += 1
    Region.fromSortedOwned(space, result)

  /** Rebind this region through checked persistent-domain evidence. */
  def rebind[B](
      alignment: DomainAlignment[S, B]
  ): Either[SpaceMismatch, Region[B]] =
    if !space.sameRuntimeOwnerAs(alignment.left) then
      Left(alignment.left.mismatch(space))
    else
      Right(new Region(alignment.right, members.clone()))

  private[locus4s] def containsOrdinal(ordinal: Int): Boolean =
    var low = 0
    var high = members.length - 1
    var found = false
    while low <= high && !found do
      val middle = low + (high - low) / 2
      val candidate = members(middle)
      if candidate == ordinal then found = true
      else if candidate < ordinal then low = middle + 1
      else high = middle - 1
    found

  private def compatible(that: Region[S]): Either[SpaceMismatch, Unit] =
    if space.sameRuntimeOwnerAs(that.space) then Right(())
    else Left(space.mismatch(that.space))

  private def combine(
      that: Region[S],
      operation: Region.Operation
  ): Either[SpaceMismatch, Region[S]] =
    compatible(that).map: _ =>
      val result =
        Array.ofDim[Int](
          operation.maximumSize(members.length, that.members.length)
        )
      var left = 0
      var right = 0
      var out = 0

      while left < members.length && right < that.members.length do
        val leftValue = members(left)
        val rightValue = that.members(right)
        if leftValue < rightValue then
          if operation.includeLeftOnly then
            result(out) = leftValue
            out += 1
          left += 1
        else if rightValue < leftValue then
          if operation.includeRightOnly then
            result(out) = rightValue
            out += 1
          right += 1
        else
          if operation.includeShared then
            result(out) = leftValue
            out += 1
          left += 1
          right += 1

      if operation.includeLeftOnly then
        while left < members.length do
          result(out) = members(left)
          out += 1
          left += 1

      if operation.includeRightOnly then
        while right < that.members.length do
          result(out) = that.members(right)
          out += 1
          right += 1

      Region.fromSortedOwned(space, result.take(out))

  override def equals(other: Any): Boolean =
    other match
      case that: Region[?] =>
        space == that.space && Region.sameOrdinals(members, that.members)
      case _ =>
        false

  override def hashCode(): Int =
    var result = space.hashCode()
    var i = 0
    while i < members.length do
      result = 31 * result + members(i)
      i += 1
    result

  override def toString: String =
    s"Region(${space.name.value}, cardinality=$cardinality)"

object Region:
  private enum Operation:
    case Union
    case Intersection
    case Difference
    case SymmetricDifference

    def includeLeftOnly: Boolean =
      this match
        case Union | Difference | SymmetricDifference => true
        case Intersection => false

    def includeRightOnly: Boolean =
      this match
        case Union | SymmetricDifference => true
        case Intersection | Difference => false

    def includeShared: Boolean =
      this match
        case Union | Intersection => true
        case Difference | SymmetricDifference => false

    def maximumSize(left: Int, right: Int): Int =
      this match
        case Union | SymmetricDifference => left + right
        case Intersection => math.min(left, right)
        case Difference => left

  def empty[S](space: FiniteSpace[S]): Region[S] =
    fromSortedOwned(space, Array.emptyIntArray)

  def whole[S](space: FiniteSpace[S]): Region[S] =
    fromSortedOwned(space, Array.tabulate(space.size)(identity))

  def fromOrdinals[S](
      space: FiniteSpace[S],
      ordinals: IterableOnce[Int]
  ): Either[RegionError, Region[S]] =
    val input = ordinals.iterator.toArray
    var index = 0
    var error = Option.empty[RegionError]
    while index < input.length && error.isEmpty do
      val ordinal = input(index)
      if !space.containsOrdinal(ordinal) then
        error = Some(RegionError.OutOfBounds(index, ordinal, space.size))
      index += 1

    error match
      case Some(value) =>
        Left(value)
      case None =>
        Right(fromValidatedOrdinals(space, input))

  def fromPoints[S](
      space: FiniteSpace[S],
      points: IterableOnce[Point[S]]
  ): Either[RegionError, Region[S]] =
    val input = points.iterator.toArray
    val ordinals = Array.ofDim[Int](input.length)
    var index = 0
    var error = Option.empty[RegionError]
    while index < input.length && error.isEmpty do
      val point = input(index)
      space.validate(point) match
        case Left(PointError.OutOfBounds(ordinal, size)) =>
          error = Some(RegionError.OutOfBounds(index, ordinal, size))
        case Left(PointError.ForeignDomain(expected, actual)) =>
          error = Some(RegionError.ForeignDomain(index, expected, actual))
        case Right(ordinal) =>
          ordinals(index) = ordinal
      index += 1

    error match
      case Some(value) =>
        Left(value)
      case None =>
        Right(fromValidatedOrdinals(space, ordinals))

  def tabulate[S](
      space: FiniteSpace[S]
  )(predicate: Point[S] => Boolean): Region[S] =
    val builder = Array.newBuilder[Int]
    val points = space.points
    while points.hasNext do
      val point = points.next()
      if predicate(point) then builder += point.value
    fromSortedOwned(space, builder.result())

  private def fromValidatedOrdinals[S](
      space: FiniteSpace[S],
      ordinals: Array[Int]
  ): Region[S] =
    val sorted = ordinals.sorted
    if sorted.isEmpty then fromSortedOwned(space, sorted)
    else
      val unique = Array.ofDim[Int](sorted.length)
      unique(0) = sorted(0)
      var in = 1
      var out = 1
      while in < sorted.length do
        if sorted(in) != unique(out - 1) then
          unique(out) = sorted(in)
          out += 1
        in += 1
      fromSortedOwned(space, unique.take(out))

  private def fromSortedOwned[S](
      space: FiniteSpace[S],
      ordinals: Array[Int]
  ): Region[S] =
    new Region(space, ordinals)

  private def sameOrdinals(left: Array[Int], right: Array[Int]): Boolean =
    if left.length != right.length then false
    else
      var index = 0
      var same = true
      while index < left.length && same do
        same = left(index) == right(index)
        index += 1
      same
