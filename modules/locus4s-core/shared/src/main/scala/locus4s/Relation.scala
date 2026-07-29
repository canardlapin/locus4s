package locus4s

enum RelationError:
  case WrongRowCount(expected: Int, actual: Int)
  case TargetOutOfBounds(
      sourceOrdinal: Int,
      position: Int,
      targetOrdinal: Int,
      targetSize: Int
  )

  def message: String =
    this match
      case WrongRowCount(expected, actual) =>
        s"relation requires $expected rows, found $actual"
      case TargetOutOfBounds(source, position, target, size) =>
        s"relation row $source target at position $position is outside " +
          s"[0, $size): $target"

enum RelationQueryError:
  case ForeignSource(error: PointError)
  case ForeignTarget(error: PointError)

  def message: String =
    this match
      case ForeignSource(error) =>
        s"invalid relation source: ${error.message}"
      case ForeignTarget(error) =>
        s"invalid relation target: ${error.message}"

/** An immutable binary relation between two finite domains. */
final class Relation[X, Y] private (
    val from: FiniteSpace[X],
    val to: FiniteSpace[Y],
    private val rows: Array[Array[Int]]
):
  def row(point: Point[X]): Either[PointError, Region[Y]] =
    from
      .validate(point)
      .map: source =>
        Region.tabulate(to): target =>
          containsOrdinal(rows(source), target.value)

  def isRelated(
      source: Point[X],
      target: Point[Y]
  ): Either[RelationQueryError, Boolean] =
    from.validate(source) match
      case Left(error) =>
        Left(RelationQueryError.ForeignSource(error))
      case Right(sourceOrdinal) =>
        to.validate(target) match
          case Left(error) =>
            Left(RelationQueryError.ForeignTarget(error))
          case Right(targetOrdinal) =>
            Right(containsOrdinal(rows(sourceOrdinal), targetOrdinal))

  def andThen[Z](
      that: Relation[Y, Z]
  ): Either[SpaceMismatch, Relation[X, Z]] =
    if to.sameRuntimeOwnerAs(that.from) then
      val composed = Array.ofDim[Array[Int]](from.size)
      var source = 0
      while source < from.size do
        val included = Array.fill(that.to.size)(false)
        val intermediates = rows(source)
        var middleIndex = 0
        while middleIndex < intermediates.length do
          val targets = that.rows(intermediates(middleIndex))
          var targetIndex = 0
          while targetIndex < targets.length do
            included(targets(targetIndex)) = true
            targetIndex += 1
          middleIndex += 1
        composed(source) = Relation.ordinalsWhere(included)
        source += 1
      Right(Relation.fromValidated(from, that.to, composed))
    else
      Left(to.mismatch(that.from))

  def converse: Relation[Y, X] =
    val builders = Array.fill(to.size)(Array.newBuilder[Int])
    var source = 0
    while source < rows.length do
      val targets = rows(source)
      var index = 0
      while index < targets.length do
        builders(targets(index)) += source
        index += 1
      source += 1
    Relation.fromValidated(to, from, builders.map(_.result()))

  def union(
      that: Relation[X, Y]
  ): Either[SpaceMismatch, Relation[X, Y]] =
    if !from.sameRuntimeOwnerAs(that.from) then
      Left(from.mismatch(that.from))
    else if !to.sameRuntimeOwnerAs(that.to) then
      Left(to.mismatch(that.to))
    else
      val combined = Array.ofDim[Array[Int]](from.size)
      var source = 0
      while source < from.size do
        combined(source) = Relation.mergeUnion(rows(source), that.rows(source))
        source += 1
      Right(Relation.fromValidated(from, to, combined))

  def subsetOf(
      that: Relation[X, Y]
  ): Either[SpaceMismatch, Boolean] =
    if !from.sameRuntimeOwnerAs(that.from) then
      Left(from.mismatch(that.from))
    else if !to.sameRuntimeOwnerAs(that.to) then
      Left(to.mismatch(that.to))
    else
      var source = 0
      var subset = true
      while source < from.size && subset do
        subset = Relation.sortedSubset(rows(source), that.rows(source))
        source += 1
      Right(subset)

  def image(region: Region[X]): Either[SpaceMismatch, Region[Y]] =
    if from.sameRuntimeOwnerAs(region.space) then
      val included = Array.fill(to.size)(false)
      val sources = region.ordinalsInDomainOrder
      var sourceIndex = 0
      while sourceIndex < sources.length do
        val targets = rows(sources(sourceIndex))
        var targetIndex = 0
        while targetIndex < targets.length do
          included(targets(targetIndex)) = true
          targetIndex += 1
        sourceIndex += 1
      Right(Region.tabulate(to)(point => included(point.value)))
    else
      Left(from.mismatch(region.space))

  def ordinalRows: Array[Array[Int]] =
    rows.map(_.clone())

  override def equals(other: Any): Boolean =
    other match
      case that: Relation[?, ?] =>
        from == that.from &&
        to == that.to &&
        Relation.sameRows(rows, that.rows)
      case _ =>
        false

  override def hashCode(): Int =
    var result = 31 * from.hashCode() + to.hashCode()
    var source = 0
    while source < rows.length do
      var index = 0
      while index < rows(source).length do
        result = 31 * result + rows(source)(index)
        index += 1
      result = 31 * result + source
      source += 1
    result

  override def toString: String =
    val pairCount =
      rows.foldLeft(0)((total, row) => total + row.length)
    s"Relation(${from.name.value} -> ${to.name.value}, pairs=$pairCount)"

  private def containsOrdinal(row: Array[Int], ordinal: Int): Boolean =
    var low = 0
    var high = row.length - 1
    var found = false
    while low <= high && !found do
      val middle = low + (high - low) / 2
      if row(middle) == ordinal then found = true
      else if row(middle) < ordinal then low = middle + 1
      else high = middle - 1
    found

object Relation:
  def empty[X, Y](
      from: FiniteSpace[X],
      to: FiniteSpace[Y]
  ): Relation[X, Y] =
    fromValidated(
      from,
      to,
      Array.fill(from.size)(Array.emptyIntArray)
    )

  def identity[S](space: FiniteSpace[S]): Relation[S, S] =
    fromValidated(
      space,
      space,
      Array.tabulate(space.size)(ordinal => Array(ordinal))
    )

  def fromOrdinalRows[X, Y](
      from: FiniteSpace[X],
      to: FiniteSpace[Y],
      inputRows: IterableOnce[IterableOnce[Int]]
  ): Either[RelationError, Relation[X, Y]] =
    val rows = inputRows.iterator.map(_.iterator.toArray).toArray
    if rows.length != from.size then
      Left(RelationError.WrongRowCount(from.size, rows.length))
    else
      val canonical = Array.ofDim[Array[Int]](from.size)
      var source = 0
      var error = Option.empty[RelationError]
      while source < rows.length && error.isEmpty do
        val input = rows(source)
        var position = 0
        while position < input.length && error.isEmpty do
          val target = input(position)
          if !to.containsOrdinal(target) then
            error =
              Some(
                RelationError.TargetOutOfBounds(
                  source,
                  position,
                  target,
                  to.size
                )
              )
          position += 1
        canonical(source) = input.sorted.distinct
        source += 1

      error match
        case Some(value) =>
          Left(value)
        case None =>
          Right(fromValidated(from, to, canonical))

  def tabulate[X, Y](
      from: FiniteSpace[X],
      to: FiniteSpace[Y]
  )(row: Point[X] => Region[Y]): Either[SpaceMismatch, Relation[X, Y]] =
    val rows = Array.ofDim[Array[Int]](from.size)
    val points = from.points
    var mismatch = Option.empty[SpaceMismatch]
    while points.hasNext && mismatch.isEmpty do
      val sourcePoint = points.next()
      val source = sourcePoint.value
      val region = row(sourcePoint)
      if !to.sameRuntimeOwnerAs(region.space) then
        mismatch = Some(to.mismatch(region.space))
      else
        rows(source) = region.ordinalsInDomainOrder

    mismatch match
      case Some(value) =>
        Left(value)
      case None =>
        Right(fromValidated(from, to, rows))

  private def fromValidated[X, Y](
      from: FiniteSpace[X],
      to: FiniteSpace[Y],
      rows: Array[Array[Int]]
  ): Relation[X, Y] =
    new Relation(from, to, rows)

  private def ordinalsWhere(included: Array[Boolean]): Array[Int] =
    val builder = Array.newBuilder[Int]
    var ordinal = 0
    while ordinal < included.length do
      if included(ordinal) then builder += ordinal
      ordinal += 1
    builder.result()

  private def mergeUnion(
      left: Array[Int],
      right: Array[Int]
  ): Array[Int] =
    val result = Array.ofDim[Int](left.length + right.length)
    var leftIndex = 0
    var rightIndex = 0
    var out = 0
    while leftIndex < left.length && rightIndex < right.length do
      if left(leftIndex) < right(rightIndex) then
        result(out) = left(leftIndex)
        leftIndex += 1
      else if right(rightIndex) < left(leftIndex) then
        result(out) = right(rightIndex)
        rightIndex += 1
      else
        result(out) = left(leftIndex)
        leftIndex += 1
        rightIndex += 1
      out += 1
    while leftIndex < left.length do
      result(out) = left(leftIndex)
      leftIndex += 1
      out += 1
    while rightIndex < right.length do
      result(out) = right(rightIndex)
      rightIndex += 1
      out += 1
    result.take(out)

  private def sortedSubset(
      left: Array[Int],
      right: Array[Int]
  ): Boolean =
    var leftIndex = 0
    var rightIndex = 0
    var subset = true
    while leftIndex < left.length && subset do
      while rightIndex < right.length && right(rightIndex) < left(leftIndex) do
        rightIndex += 1
      if rightIndex >= right.length || right(rightIndex) != left(leftIndex) then
        subset = false
      leftIndex += 1
    subset

  private def sameRows(
      left: Array[Array[Int]],
      right: Array[Array[Int]]
  ): Boolean =
    if left.length != right.length then false
    else
      var source = 0
      var same = true
      while source < left.length && same do
        val leftRow = left(source)
        val rightRow = right(source)
        if leftRow.length != rightRow.length then
          same = false
        else
          var index = 0
          while index < leftRow.length && same do
            same = leftRow(index) == rightRow(index)
            index += 1
        source += 1
      same
