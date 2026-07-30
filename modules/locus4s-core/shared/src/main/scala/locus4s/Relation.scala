package locus4s

enum RelationError:
  case WrongRowCount(expected: Int, actual: Int)
  case WrongOffsetCount(expected: Int, actual: Int)
  case FirstOffsetNotZero(actual: Int)
  case OffsetsNotMonotonic(position: Int, previous: Int, current: Int)
  case FinalOffsetMismatch(expected: Int, actual: Int)
  case TargetOutOfBounds(
      sourceOrdinal: Int,
      position: Int,
      targetOrdinal: Int,
      targetSize: Int
  )
  case RowNotStrictlyIncreasing(
      sourceOrdinal: Int,
      position: Int,
      previous: Int,
      current: Int
  )
  case RowOffsetCountOverflow(sourceSize: Int)

  def message: String =
    this match
      case WrongRowCount(expected, actual) =>
        s"relation requires $expected rows, found $actual"
      case WrongOffsetCount(expected, actual) =>
        s"CSR relation requires $expected row offsets, found $actual"
      case FirstOffsetNotZero(actual) =>
        s"CSR relation first row offset must be 0, found $actual"
      case OffsetsNotMonotonic(position, previous, current) =>
        s"CSR offsets must be monotonic; offset $position is $current after $previous"
      case FinalOffsetMismatch(expected, actual) =>
        s"CSR final offset must equal target count $expected, found $actual"
      case TargetOutOfBounds(source, position, target, size) =>
        s"relation row $source target at position $position is outside " +
          s"[0, $size): $target"
      case RowNotStrictlyIncreasing(source, position, previous, current) =>
        s"relation row $source must be strictly increasing; position $position " +
          s"contains $current after $previous"
      case RowOffsetCountOverflow(sourceSize) =>
        s"a non-empty CSR relation over $sourceSize source indices cannot " +
          "represent sourceSize + 1 row offsets in a JVM/Scala.js array"

/** Immutable sparse Boolean relation in compressed sparse row form.
  *
  * Row traversal is O(row degree), and materializing a row allocates only in proportion
  * to that degree. Sparse composition visits relation edges and sorts/deduplicates only
  * the targets reached for each source; it never initializes a target-sized dense
  * marker array per row.
  */
final class Relation[X, Y] private (
    val from: FiniteDomain[X],
    val to: FiniteDomain[Y],
    private val rowOffsets: IntBuffer,
    private val targets: IntBuffer,
    private val emptyRepresentation: Boolean
):
  def pairCount: Int =
    targets.length

  def isEmpty: Boolean =
    emptyRepresentation

  /** Materialized sparse row in O(row degree), never O(target size). */
  def row(source: Index[X]): Region[Y] =
    val (start, end) = rowBounds(source.ordinal)
    val builder = Region.newBuilder(to)
    var position = start
    while position < end do
      builder.add(to.indexAtOrdinal(targets(position)))
      position += 1
    builder.result()

  def foreachTarget(
      source: Index[X]
  )(f: Index[Y] => Unit): Unit =
    val (start, end) = rowBounds(source.ordinal)
    var position = start
    while position < end do
      f(to.indexAtOrdinal(targets(position)))
      position += 1

  def hasTargets(source: Index[X]): Boolean =
    val (start, end) = rowBounds(source.ordinal)
    start < end

  def isRelated(
      source: Index[X],
      target: Index[Y]
  ): Boolean =
    val (start, end) = rowBounds(source.ordinal)
    Relation.binarySearch(targets, start, end, target.ordinal)

  def andThen[Z](that: Relation[Y, Z]): Relation[X, Z] =
    if isEmpty || that.isEmpty then Relation.empty(from, that.to)
    else
      val offsets = Array.ofDim[Int](from.size + 1)
      val output = Array.newBuilder[Int]
      var outputSize = 0
      var source = 0
      while source < from.size do
        val reached = Array.newBuilder[Int]
        val (middleStart, middleEnd) = rowBounds(source)
        var middlePosition = middleStart
        while middlePosition < middleEnd do
          val middle = targets(middlePosition)
          val (targetStart, targetEnd) = that.rowBounds(middle)
          var targetPosition = targetStart
          while targetPosition < targetEnd do
            reached += that.targets(targetPosition)
            targetPosition += 1
          middlePosition += 1

        val canonical = Relation.sortedDistinct(reached.result())
        output ++= canonical
        outputSize += canonical.length
        offsets(source + 1) = outputSize
        source += 1

      Relation.fromOwnedCsr(from, that.to, offsets, output.result())

  def andThenChecked[M, Z](
      that: Relation[M, Z]
  ): Either[SpaceMismatch, Relation[X, Z]] =
    if to.sameRuntimeOwnerAs(that.from) then
      Right(
        andThen(
          new Relation(
            to,
            that.to,
            that.rowOffsets,
            that.targets,
            that.emptyRepresentation
          )
        )
      )
    else Left(to.mismatch(that.from))

  def converse: Relation[Y, X] =
    if isEmpty then Relation.empty(to, from)
    else
      val counts = Array.ofDim[Int](to.size)
      var position = 0
      while position < targets.length do
        counts(targets(position)) += 1
        position += 1

      val offsets = Array.ofDim[Int](to.size + 1)
      var target = 0
      while target < to.size do
        offsets(target + 1) = offsets(target) + counts(target)
        target += 1

      val next = offsets.clone()
      val reversedTargets = Array.ofDim[Int](targets.length)
      var source = 0
      while source < from.size do
        val (start, end) = rowBounds(source)
        var rowPosition = start
        while rowPosition < end do
          val relatedTarget = targets(rowPosition)
          val outputPosition = next(relatedTarget)
          reversedTargets(outputPosition) = source
          next(relatedTarget) += 1
          rowPosition += 1
        source += 1

      Relation.fromOwnedCsr(to, from, offsets, reversedTargets)

  def union(that: Relation[X, Y]): Relation[X, Y] =
    if isEmpty then
      new Relation(
        from,
        to,
        that.rowOffsets,
        that.targets,
        that.emptyRepresentation
      )
    else if that.isEmpty then this
    else combineRows(that, Relation.RowOperation.Union)

  def intersect(that: Relation[X, Y]): Relation[X, Y] =
    if isEmpty || that.isEmpty then Relation.empty(from, to)
    else combineRows(that, Relation.RowOperation.Intersection)

  def subsetOf(that: Relation[X, Y]): Boolean =
    if isEmpty then true
    else if that.isEmpty then false
    else
      var source = 0
      var subset = true
      while source < from.size && subset do
        val (leftStart, leftEnd) = rowBounds(source)
        val (rightStart, rightEnd) = that.rowBounds(source)
        subset = Relation.sortedSubset(
          targets,
          leftStart,
          leftEnd,
          that.targets,
          rightStart,
          rightEnd
        )
        source += 1
      subset

  def unionChecked[A, B](
      that: Relation[A, B]
  ): Either[SpaceMismatch, Relation[X, Y]] =
    checkedOperand(that).map(union)

  def intersectChecked[A, B](
      that: Relation[A, B]
  ): Either[SpaceMismatch, Relation[X, Y]] =
    checkedOperand(that).map(intersect)

  def subsetOfChecked[A, B](
      that: Relation[A, B]
  ): Either[SpaceMismatch, Boolean] =
    checkedOperand(that).map(subsetOf)

  /** Sparse image with work tied to visited rows plus output canonicalization. */
  def image(region: Region[X]): Region[Y] =
    if isEmpty || region.isEmpty then Region.empty(to)
    else
      val builder = Region.newBuilder(to)
      region.foreachIndex: source =>
        foreachTarget(source)(builder.add)
      builder.result()

  def imageChecked[T](
      region: Region[T]
  ): Either[SpaceMismatch, Region[Y]] =
    if from.sameRuntimeOwnerAs(region.space) then
      from.align(region.space) match
        case Right(alignment) =>
          Right(image(alignment.reverse.transport(region)))
        case Left(_) =>
          Left(from.mismatch(region.space))
    else Left(from.mismatch(region.space))

  /** O(1) source-owner transport sharing CSR storage. */
  def rebindFrom[A](
      alignment: DomainAlignment[X, A]
  ): Relation[A, Y] =
    new Relation(
      alignment.right,
      to,
      rowOffsets,
      targets,
      emptyRepresentation
    )

  /** O(1) target-owner transport sharing CSR storage. */
  def rebindTo[B](
      alignment: DomainAlignment[Y, B]
  ): Relation[X, B] =
    new Relation(
      from,
      alignment.right,
      rowOffsets,
      targets,
      emptyRepresentation
    )

  /** Dynamic-boundary defensive CSR copy.
    *
    * Empty relations use empty offset and target arrays rather than expanding an
    * all-zero offset array. Non-empty copies cost O(source size + pair count).
    */
  def csr: Relation.Csr =
    if emptyRepresentation then
      Relation.Csr(
        Array.emptyIntArray,
        Array.emptyIntArray
      )
    else Relation.Csr(rowOffsets.toArray, targets.toArray)

  /** Compatibility copy of canonical rows. O(|from| + pairCount). */
  def ordinalRows: Array[Array[Int]] =
    Array.tabulate(from.size): source =>
      val (start, end) = rowBounds(source)
      targets.slice(start, end).toArray

  private def combineRows(
      that: Relation[X, Y],
      operation: Relation.RowOperation
  ): Relation[X, Y] =
    val offsets = Array.ofDim[Int](from.size + 1)
    val output = Array.newBuilder[Int]
    var outputSize = 0
    var source = 0
    while source < from.size do
      val (leftStart, leftEnd) = rowBounds(source)
      val (rightStart, rightEnd) = that.rowBounds(source)
      val row =
        Relation.mergeRows(
          targets,
          leftStart,
          leftEnd,
          that.targets,
          rightStart,
          rightEnd,
          operation
        )
      output ++= row
      outputSize += row.length
      offsets(source + 1) = outputSize
      source += 1
    Relation.fromOwnedCsr(from, to, offsets, output.result())

  private def checkedOperand[A, B](
      that: Relation[A, B]
  ): Either[SpaceMismatch, Relation[X, Y]] =
    if !from.sameRuntimeOwnerAs(that.from) then Left(from.mismatch(that.from))
    else if !to.sameRuntimeOwnerAs(that.to) then Left(to.mismatch(that.to))
    else
      Right(
        new Relation(
          from,
          to,
          that.rowOffsets,
          that.targets,
          that.emptyRepresentation
        )
      )

  private def rowBounds(source: Int): (Int, Int) =
    if emptyRepresentation then (0, 0)
    else (rowOffsets(source), rowOffsets(source + 1))

  override def equals(other: Any): Boolean =
    other match
      case that: Relation[?, ?] =>
        from == that.from &&
        to == that.to &&
        emptyRepresentation == that.emptyRepresentation &&
        rowOffsets.sameElements(that.rowOffsets) &&
        targets.sameElements(that.targets)
      case _ =>
        false

  override def hashCode(): Int =
    targets.contentHash(
      rowOffsets.contentHash(31 * from.hashCode() + to.hashCode())
    )

  override def toString: String =
    s"Relation(${from.name.value} -> ${to.name.value}, pairs=$pairCount)"

object Relation:
  final case class Csr(rowOffsets: Array[Int], targets: Array[Int])

  private enum RowOperation:
    case Union
    case Intersection

  def empty[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y]
  ): Relation[X, Y] =
    new Relation(
      from,
      to,
      IntBuffer.empty,
      IntBuffer.empty,
      true
    )

  def identity[S](space: FiniteDomain[S]): Relation[S, S] =
    if space.size == 0 then empty(space, space)
    else
      val offsets = Array.tabulate(space.size + 1)(ordinal => ordinal)
      val targets = Array.tabulate(space.size)(ordinal => ordinal)
      fromOwnedCsr(space, space, offsets, targets)

  def fromOrdinalRows[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y],
      inputRows: IterableOnce[IterableOnce[Int]]
  ): Either[RelationError, Relation[X, Y]] =
    val rows = inputRows.iterator.map(_.iterator.toArray).toArray
    if rows.length != from.size then
      Left(RelationError.WrongRowCount(from.size, rows.length))
    else
      val offsets = Array.ofDim[Int](from.size + 1)
      val output = Array.newBuilder[Int]
      var outputSize = 0
      var source = 0
      var error = Option.empty[RelationError]
      while source < rows.length && error.isEmpty do
        val input = rows(source)
        var position = 0
        while position < input.length && error.isEmpty do
          val target = input(position)
          if !to.containsOrdinal(target) then
            error = Some(
              RelationError.TargetOutOfBounds(
                source,
                position,
                target,
                to.size
              )
            )
          position += 1
        val canonical = sortedDistinct(input)
        output ++= canonical
        outputSize += canonical.length
        offsets(source + 1) = outputSize
        source += 1

      error match
        case Some(value) => Left(value)
        case None        =>
          Right(fromOwnedCsr(from, to, offsets, output.result()))

  def fromCsr[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y],
      inputOffsets: IterableOnce[Int],
      inputTargets: IterableOnce[Int]
  ): Either[RelationError, Relation[X, Y]] =
    val offsets = inputOffsets.iterator.toArray
    val targets = inputTargets.iterator.toArray
    if targets.isEmpty && offsets.isEmpty then Right(empty(from, to))
    else if from.size == Int.MaxValue then
      Left(RelationError.RowOffsetCountOverflow(from.size))
    else if offsets.length != from.size + 1 then
      Left(
        RelationError.WrongOffsetCount(
          from.size + 1,
          offsets.length
        )
      )
    else if offsets.headOption.exists(_ != 0) then
      Left(RelationError.FirstOffsetNotZero(offsets(0)))
    else
      var offsetPosition = 1
      var error = Option.empty[RelationError]
      while offsetPosition < offsets.length && error.isEmpty do
        if offsets(offsetPosition) < offsets(offsetPosition - 1) then
          error = Some(
            RelationError.OffsetsNotMonotonic(
              offsetPosition,
              offsets(offsetPosition - 1),
              offsets(offsetPosition)
            )
          )
        offsetPosition += 1

      if error.isEmpty && offsets.lastOption.exists(_ != targets.length) then
        error = Some(
          RelationError.FinalOffsetMismatch(
            targets.length,
            offsets.last
          )
        )

      var source = 0
      while source < from.size && error.isEmpty do
        val start = offsets(source)
        val end = offsets(source + 1)
        var position = start
        while position < end && error.isEmpty do
          val target = targets(position)
          if !to.containsOrdinal(target) then
            error = Some(
              RelationError.TargetOutOfBounds(
                source,
                position - start,
                target,
                to.size
              )
            )
          else if position > start && targets(position - 1) >= target then
            error = Some(
              RelationError.RowNotStrictlyIncreasing(
                source,
                position - start,
                targets(position - 1),
                target
              )
            )
          position += 1
        source += 1

      error match
        case Some(value) => Left(value)
        case None        =>
          Right(fromOwnedCsr(from, to, offsets, targets))

  def tabulate[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y]
  )(row: Index[X] => Region[Y]): Relation[X, Y] =
    val offsets = Array.ofDim[Int](from.size + 1)
    val output = Array.newBuilder[Int]
    var outputSize = 0
    from.foreachIndex: source =>
      val region = row(source)
      region.foreachOrdinal: target =>
        output += target
        outputSize += 1
      offsets(source.ordinal + 1) = outputSize
    fromOwnedCsr(from, to, offsets, output.result())

  private def fromOwnedCsr[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y],
      offsets: Array[Int],
      targets: Array[Int]
  ): Relation[X, Y] =
    if targets.isEmpty then empty(from, to)
    else
      new Relation(
        from,
        to,
        IntBuffer.fromOwnedArray(offsets),
        IntBuffer.fromOwnedArray(targets),
        false
      )

  private def binarySearch(
      values: IntBuffer,
      start: Int,
      end: Int,
      ordinal: Int
  ): Boolean =
    var low = start
    var high = end - 1
    var found = false
    while low <= high && !found do
      val middle = low + (high - low) / 2
      val candidate = values(middle)
      if candidate == ordinal then found = true
      else if candidate < ordinal then low = middle + 1
      else high = middle - 1
    found

  private def sortedDistinct(input: Array[Int]): Array[Int] =
    if input.isEmpty then input
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
      unique.take(out)

  private def sortedSubset(
      left: IntBuffer,
      leftStart: Int,
      leftEnd: Int,
      right: IntBuffer,
      rightStart: Int,
      rightEnd: Int
  ): Boolean =
    var leftPosition = leftStart
    var rightPosition = rightStart
    var subset = true
    while leftPosition < leftEnd && subset do
      while rightPosition < rightEnd &&
        right(rightPosition) < left(leftPosition)
      do rightPosition += 1
      if rightPosition >= rightEnd ||
        right(rightPosition) != left(leftPosition)
      then subset = false
      leftPosition += 1
    subset

  private def mergeRows(
      left: IntBuffer,
      leftStart: Int,
      leftEnd: Int,
      right: IntBuffer,
      rightStart: Int,
      rightEnd: Int,
      operation: RowOperation
  ): Array[Int] =
    val builder = Array.newBuilder[Int]
    var leftPosition = leftStart
    var rightPosition = rightStart
    while leftPosition < leftEnd && rightPosition < rightEnd do
      val leftValue = left(leftPosition)
      val rightValue = right(rightPosition)
      if leftValue < rightValue then
        if operation == RowOperation.Union then builder += leftValue
        leftPosition += 1
      else if rightValue < leftValue then
        if operation == RowOperation.Union then builder += rightValue
        rightPosition += 1
      else
        builder += leftValue
        leftPosition += 1
        rightPosition += 1

    if operation == RowOperation.Union then
      while leftPosition < leftEnd do
        builder += left(leftPosition)
        leftPosition += 1
      while rightPosition < rightEnd do
        builder += right(rightPosition)
        rightPosition += 1

    builder.result()
