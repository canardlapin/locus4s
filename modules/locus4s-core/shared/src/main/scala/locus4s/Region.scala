package locus4s

enum RegionError:
  case OutOfBounds(position: Int, pointOrdinal: Int, size: Int)
  case NotStrictlyIncreasing(
      position: Int,
      previous: Int,
      current: Int
  )

  def message: String =
    this match
      case OutOfBounds(position, pointOrdinal, size) =>
        s"region ordinal at position $position is outside [0, $size): $pointOrdinal"
      case NotStrictlyIncreasing(position, previous, current) =>
        s"region ordinals must be strictly increasing; position $position " +
          s"contains $current after $previous"

/** An immutable set of indices in one live finite domain.
  *
  * Empty and whole regions require no ordinal storage. Sparse members are kept in
  * increasing primitive storage, with O(log cardinality) membership.
  */
final class Region[S] private (
    val space: FiniteDomain[S],
    private val representation: Region.Representation
):
  import Region.Representation

  def cardinality: Int =
    representation match
      case Representation.Empty          => 0
      case Representation.Whole          => space.size
      case Representation.Sparse(values) => values.length

  def isEmpty: Boolean =
    representation == Representation.Empty

  def isWhole: Boolean =
    representation == Representation.Whole || space.size == 0

  /** Total typed membership. O(1) for empty/whole and O(log n) for sparse. */
  def contains(index: Index[S]): Boolean =
    containsOrdinal(index.ordinal)

  def foreachIndex(f: Index[S] => Unit): Unit =
    foreachOrdinal(ordinal => f(space.indexAtOrdinal(ordinal)))

  def indicesInDomainOrder: Iterator[Index[S]] =
    ordinalIterator.map(space.indexAtOrdinal)

  /** Compatibility spelling for `indicesInDomainOrder`. */
  @deprecated(
    "Use indicesInDomainOrder; scheduled for removal in 1.0.",
    "0.1.0"
  )
  def pointsInDomainOrder: Iterator[Index[S]] =
    indicesInDomainOrder

  /** Dynamic-boundary copy of the canonical ordinals. O(cardinality). */
  def ordinalsInDomainOrder: Array[Int] =
    representation match
      case Representation.Empty =>
        Array.emptyIntArray
      case Representation.Whole =>
        Array.tabulate(space.size)(identity)
      case Representation.Sparse(values) =>
        values.toArray

  def subsetOf(that: Region[S]): Boolean =
    (representation, that.representation) match
      case (Representation.Empty, _) =>
        true
      case (_, Representation.Whole) =>
        true
      case (Representation.Whole, _) =>
        false
      case (_, Representation.Empty) =>
        false
      case (
            Representation.Sparse(left),
            Representation.Sparse(right)
          ) =>
        Region.sortedSubset(left, right)

  def union(that: Region[S]): Region[S] =
    (representation, that.representation) match
      case (Representation.Whole, _) | (_, Representation.Whole) =>
        Region.whole(space)
      case (Representation.Empty, _) =>
        new Region(space, that.representation)
      case (_, Representation.Empty) =>
        this
      case (
            Representation.Sparse(left),
            Representation.Sparse(right)
          ) =>
        Region.fromSortedBuffer(
          space,
          Region.merge(left, right, Region.Operation.Union)
        )

  def intersect(that: Region[S]): Region[S] =
    (representation, that.representation) match
      case (Representation.Empty, _) | (_, Representation.Empty) =>
        Region.empty(space)
      case (Representation.Whole, _) =>
        new Region(space, that.representation)
      case (_, Representation.Whole) =>
        this
      case (
            Representation.Sparse(left),
            Representation.Sparse(right)
          ) =>
        Region.fromSortedBuffer(
          space,
          Region.merge(left, right, Region.Operation.Intersection)
        )

  def diff(that: Region[S]): Region[S] =
    (representation, that.representation) match
      case (Representation.Empty, _) | (_, Representation.Whole) =>
        Region.empty(space)
      case (_, Representation.Empty) =>
        this
      case (Representation.Whole, _) =>
        that.complement
      case (
            Representation.Sparse(left),
            Representation.Sparse(right)
          ) =>
        Region.fromSortedBuffer(
          space,
          Region.merge(left, right, Region.Operation.Difference)
        )

  def xor(that: Region[S]): Region[S] =
    (representation, that.representation) match
      case (Representation.Empty, _) =>
        new Region(space, that.representation)
      case (_, Representation.Empty) =>
        this
      case (Representation.Whole, _) =>
        that.complement
      case (_, Representation.Whole) =>
        complement
      case (
            Representation.Sparse(left),
            Representation.Sparse(right)
          ) =>
        Region.fromSortedBuffer(
          space,
          Region.merge(left, right, Region.Operation.SymmetricDifference)
        )

  def complement: Region[S] =
    representation match
      case Representation.Empty =>
        Region.whole(space)
      case Representation.Whole =>
        Region.empty(space)
      case Representation.Sparse(values) =>
        val result = Array.ofDim[Int](space.size - values.length)
        var ordinal = 0
        var member = 0
        var out = 0
        while ordinal < space.size do
          if member < values.length && values(member) == ordinal then member += 1
          else
            result(out) = ordinal
            out += 1
          ordinal += 1
        Region.fromSortedOwned(space, result)

  /** Checked dynamic-boundary union requiring one live owner. */
  def unionChecked[T](
      that: Region[T]
  ): Either[SpaceMismatch, Region[S]] =
    checkedOperand(that).map(union)

  def intersectChecked[T](
      that: Region[T]
  ): Either[SpaceMismatch, Region[S]] =
    checkedOperand(that).map(intersect)

  def diffChecked[T](
      that: Region[T]
  ): Either[SpaceMismatch, Region[S]] =
    checkedOperand(that).map(diff)

  def xorChecked[T](
      that: Region[T]
  ): Either[SpaceMismatch, Region[S]] =
    checkedOperand(that).map(xor)

  def subsetOfChecked[T](
      that: Region[T]
  ): Either[SpaceMismatch, Boolean] =
    checkedOperand(that).map(subsetOf)

  /** O(1) transport sharing immutable ordinal storage. */
  def rebind[B](alignment: DomainAlignment[S, B]): Region[B] =
    new Region(alignment.right, representation)

  private def checkedOperand[T](
      that: Region[T]
  ): Either[SpaceMismatch, Region[S]] =
    if space.sameRuntimeOwnerAs(that.space) then
      Right(new Region(space, that.representation))
    else Left(space.mismatch(that.space))

  private[locus4s] def containsOrdinal(ordinal: Int): Boolean =
    representation match
      case Representation.Empty =>
        false
      case Representation.Whole =>
        space.containsOrdinal(ordinal)
      case Representation.Sparse(values) =>
        Region.binarySearch(values, ordinal)

  private[locus4s] def foreachOrdinal(f: Int => Unit): Unit =
    representation match
      case Representation.Empty =>
        ()
      case Representation.Whole =>
        var ordinal = 0
        while ordinal < space.size do
          f(ordinal)
          ordinal += 1
      case Representation.Sparse(values) =>
        values.foreach(f)

  private[locus4s] def ordinalIterator: Iterator[Int] =
    representation match
      case Representation.Empty =>
        Iterator.empty
      case Representation.Whole =>
        Iterator.range(0, space.size)
      case Representation.Sparse(values) =>
        values.iterator

  override def equals(other: Any): Boolean =
    other match
      case that: Region[?] =>
        space == that.space &&
        Region.sameRepresentation(representation, that.representation)
      case _ =>
        false

  override def hashCode(): Int =
    representation match
      case Representation.Empty =>
        31 * space.hashCode()
      case Representation.Whole =>
        31 * space.hashCode() + 1
      case Representation.Sparse(values) =>
        values.contentHash(31 * space.hashCode() + 2)

  override def toString: String =
    s"Region(${space.name.value}, cardinality=$cardinality)"

object Region:
  private enum Representation:
    case Empty
    case Whole
    case Sparse(values: IntBuffer)

  private enum Operation:
    case Union
    case Intersection
    case Difference
    case SymmetricDifference

    def includeLeftOnly: Boolean =
      this match
        case Union | Difference | SymmetricDifference => true
        case Intersection                             => false

    def includeRightOnly: Boolean =
      this match
        case Union | SymmetricDifference => true
        case Intersection | Difference   => false

    def includeShared: Boolean =
      this match
        case Union | Intersection             => true
        case Difference | SymmetricDifference => false

  def empty[S](space: FiniteDomain[S]): Region[S] =
    new Region(space, Representation.Empty)

  def whole[S](space: FiniteDomain[S]): Region[S] =
    if space.size == 0 then empty(space)
    else new Region(space, Representation.Whole)

  def fromOrdinals[S](
      space: FiniteDomain[S],
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
        val sorted = input.sorted
        val unique = Array.ofDim[Int](sorted.length)
        var in = 0
        var out = 0
        while in < sorted.length do
          if out == 0 || sorted(in) != unique(out - 1) then
            unique(out) = sorted(in)
            out += 1
          in += 1
        Right(fromSortedOwned(space, unique.take(out)))

  def fromSortedDistinct[S](
      space: FiniteDomain[S],
      ordinals: IterableOnce[Int]
  ): Either[RegionError, Region[S]] =
    val input = ordinals.iterator.toArray
    var index = 0
    var error = Option.empty[RegionError]
    while index < input.length && error.isEmpty do
      val ordinal = input(index)
      if !space.containsOrdinal(ordinal) then
        error = Some(RegionError.OutOfBounds(index, ordinal, space.size))
      else if index > 0 && input(index - 1) >= ordinal then
        error = Some(
          RegionError.NotStrictlyIncreasing(
            index,
            input(index - 1),
            ordinal
          )
        )
      index += 1

    error match
      case Some(value) => Left(value)
      case None        => Right(fromSortedOwned(space, input))

  def fromIndices[S](
      space: FiniteDomain[S],
      indices: IterableOnce[Index[S]]
  ): Region[S] =
    fromSortedOwned(
      space,
      indices.iterator.map(_.ordinal).toArray.sorted.distinct
    )

  /** Compatibility spelling for `fromIndices`. */
  @deprecated("Use fromIndices; scheduled for removal in 1.0.", "0.1.0")
  def fromPoints[S](
      space: FiniteDomain[S],
      points: IterableOnce[Index[S]]
  ): Region[S] =
    fromIndices(space, points)

  def tabulate[S](
      space: FiniteDomain[S]
  )(predicate: Index[S] => Boolean): Region[S] =
    val builder = Array.newBuilder[Int]
    space.foreachIndex: index =>
      if predicate(index) then builder += index.ordinal
    fromSortedOwned(space, builder.result())

  private def fromSortedOwned[S](
      space: FiniteDomain[S],
      ordinals: Array[Int]
  ): Region[S] =
    fromSortedBuffer(space, IntBuffer.fromOwnedArray(ordinals))

  private def fromSortedBuffer[S](
      space: FiniteDomain[S],
      ordinals: IntBuffer
  ): Region[S] =
    if ordinals.length == 0 then empty(space)
    else if ordinals.length == space.size then whole(space)
    else new Region(space, Representation.Sparse(ordinals))

  private def binarySearch(values: IntBuffer, ordinal: Int): Boolean =
    var low = 0
    var high = values.length - 1
    var found = false
    while low <= high && !found do
      val middle = low + (high - low) / 2
      val candidate = values(middle)
      if candidate == ordinal then found = true
      else if candidate < ordinal then low = middle + 1
      else high = middle - 1
    found

  private def sortedSubset(left: IntBuffer, right: IntBuffer): Boolean =
    var leftIndex = 0
    var rightIndex = 0
    var subset = true
    while leftIndex < left.length && subset do
      while rightIndex < right.length &&
        right(rightIndex) < left(leftIndex)
      do rightIndex += 1
      if rightIndex >= right.length ||
        right(rightIndex) != left(leftIndex)
      then subset = false
      leftIndex += 1
    subset

  private def merge(
      left: IntBuffer,
      right: IntBuffer,
      operation: Operation
  ): IntBuffer =
    val builder = Array.newBuilder[Int]
    builder.sizeHint(left.length + right.length)
    var leftIndex = 0
    var rightIndex = 0
    while leftIndex < left.length && rightIndex < right.length do
      val leftValue = left(leftIndex)
      val rightValue = right(rightIndex)
      if leftValue < rightValue then
        if operation.includeLeftOnly then builder += leftValue
        leftIndex += 1
      else if rightValue < leftValue then
        if operation.includeRightOnly then builder += rightValue
        rightIndex += 1
      else
        if operation.includeShared then builder += leftValue
        leftIndex += 1
        rightIndex += 1

    if operation.includeLeftOnly then
      while leftIndex < left.length do
        builder += left(leftIndex)
        leftIndex += 1

    if operation.includeRightOnly then
      while rightIndex < right.length do
        builder += right(rightIndex)
        rightIndex += 1

    IntBuffer.fromOwnedArray(builder.result())

  private def sameRepresentation(
      left: Representation,
      right: Representation
  ): Boolean =
    (left, right) match
      case (Representation.Empty, Representation.Empty) =>
        true
      case (Representation.Whole, Representation.Whole) =>
        true
      case (
            Representation.Sparse(leftValues),
            Representation.Sparse(rightValues)
          ) =>
        leftValues.sameElements(rightValues)
      case _ =>
        false

  /** Mutable construction aid whose result owns canonical immutable storage.
    *
    * Only typed indices can be added, so callers cannot introduce foreign or
    * out-of-bounds ordinals. Already sorted, distinct input is retained in O(k); other
    * input is sorted and deduplicated exactly once.
    */
  final class Builder[S] private[Region] (
      val space: FiniteDomain[S]
  ):
    private val ordinals = Array.newBuilder[Int]
    private var lastOrdinal = -1
    private var strictlyIncreasing = true

    def add(index: Index[S]): this.type =
      val ordinal = index.ordinal
      if ordinal <= lastOrdinal then strictlyIncreasing = false
      ordinals += ordinal
      lastOrdinal = ordinal
      this

    def result(): Region[S] =
      val input = ordinals.result()
      if input.isEmpty then Region.empty(space)
      else if strictlyIncreasing then Region.fromSortedOwned(space, input)
      else
        val sorted = input.sorted
        val unique = Array.ofDim[Int](sorted.length)
        unique(0) = sorted(0)
        var in = 1
        var out = 1
        while in < sorted.length do
          if sorted(in) != unique(out - 1) then
            unique(out) = sorted(in)
            out += 1
          in += 1
        Region.fromSortedOwned(space, unique.take(out))

  def newBuilder[S](space: FiniteDomain[S]): Builder[S] =
    new Builder(space)
