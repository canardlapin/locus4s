package locus4s

enum TotalMapError:
  case WrongTargetCount(expected: Int, actual: Int)
  case TargetOutOfBounds(
      sourceOrdinal: Int,
      targetOrdinal: Int,
      targetSize: Int
  )
  case TargetFromForeignDomain(
      sourceOrdinal: Int,
      expected: DomainRecord,
      actual: DomainRecord
  )

  def message: String =
    this match
      case WrongTargetCount(expected, actual) =>
        s"total map requires $expected targets, found $actual"
      case TargetOutOfBounds(source, target, size) =>
        s"target for source ordinal $source is outside [0, $size): $target"
      case TargetFromForeignDomain(source, expected, actual) =>
        s"target for source ordinal $source belongs to ${actual.id.value}, " +
          s"expected ${expected.id.value}"

/** An immutable function defined at every point of `from`. */
final class TotalMap[X, Y] private (
    val from: FiniteSpace[X],
    val to: FiniteSpace[Y],
    private val targets: Array[Int]
):
  def at(point: Point[X]): Either[PointError, Point[Y]] =
    from.validate(point).flatMap(source => to.point(targets(source)))

  def targetOrdinals: Array[Int] =
    targets.clone()

  def andThen[Z](
      that: TotalMap[Y, Z]
  ): Either[SpaceMismatch, TotalMap[X, Z]] =
    if to.sameRuntimeOwnerAs(that.from) then
      val result = Array.ofDim[Int](targets.length)
      var source = 0
      while source < targets.length do
        result(source) = that.targets(targets(source))
        source += 1
      Right(TotalMap.fromValidated(from, that.to, result))
    else
      Left(to.mismatch(that.from))

  def pullback(region: Region[Y]): Either[SpaceMismatch, Region[X]] =
    if to.sameRuntimeOwnerAs(region.space) then
      Right(
        Region.tabulate(from): point =>
          region.containsOrdinal(targets(point.value))
      )
    else
      Left(to.mismatch(region.space))

  def image(region: Region[X]): Either[SpaceMismatch, Region[Y]] =
    if from.sameRuntimeOwnerAs(region.space) then
      val included = Array.fill(to.size)(false)
      val sources = region.ordinalsInDomainOrder
      var index = 0
      while index < sources.length do
        included(targets(sources(index))) = true
        index += 1
      Right(Region.tabulate(to)(point => included(point.value)))
    else
      Left(from.mismatch(region.space))

  override def equals(other: Any): Boolean =
    other match
      case that: TotalMap[?, ?] =>
        from == that.from &&
        to == that.to &&
        TotalMap.sameTargets(targets, that.targets)
      case _ =>
        false

  override def hashCode(): Int =
    var result = 31 * from.hashCode() + to.hashCode()
    var index = 0
    while index < targets.length do
      result = 31 * result + targets(index)
      index += 1
    result

  override def toString: String =
    s"TotalMap(${from.name.value} -> ${to.name.value}, size=${from.size})"

object TotalMap:
  def fromTargetOrdinals[X, Y](
      from: FiniteSpace[X],
      to: FiniteSpace[Y],
      targetOrdinals: IterableOnce[Int]
  ): Either[TotalMapError, TotalMap[X, Y]] =
    val input = targetOrdinals.iterator.toArray
    if input.length != from.size then
      Left(TotalMapError.WrongTargetCount(from.size, input.length))
    else
      var source = 0
      var error = Option.empty[TotalMapError]
      while source < input.length && error.isEmpty do
        val target = input(source)
        if !to.containsOrdinal(target) then
          error =
            Some(
              TotalMapError.TargetOutOfBounds(
                source,
                target,
                to.size
              )
            )
        source += 1

      error match
        case Some(value) =>
          Left(value)
        case None =>
          Right(fromValidated(from, to, input))

  def tabulate[X, Y](
      from: FiniteSpace[X],
      to: FiniteSpace[Y]
  )(mapping: Point[X] => Point[Y]): Either[TotalMapError, TotalMap[X, Y]] =
    val targets = Array.ofDim[Int](from.size)
    val points = from.points
    var error = Option.empty[TotalMapError]
    while points.hasNext && error.isEmpty do
      val sourcePoint = points.next()
      val source = sourcePoint.value
      val target = mapping(sourcePoint)
      to.validate(target) match
        case Left(PointError.OutOfBounds(ordinal, size)) =>
          error =
            Some(TotalMapError.TargetOutOfBounds(source, ordinal, size))
        case Left(PointError.ForeignDomain(expected, actual)) =>
          error =
            Some(
              TotalMapError.TargetFromForeignDomain(
                source,
                expected,
                actual
              )
            )
        case Right(ordinal) =>
          targets(source) = ordinal

    error match
      case Some(value) =>
        Left(value)
      case None =>
        Right(fromValidated(from, to, targets))

  def identity[S](space: FiniteSpace[S]): TotalMap[S, S] =
    fromValidated(
      space,
      space,
      Array.tabulate(space.size)(ordinal => ordinal)
    )

  private def fromValidated[X, Y](
      from: FiniteSpace[X],
      to: FiniteSpace[Y],
      targetOrdinals: Array[Int]
  ): TotalMap[X, Y] =
    new TotalMap(from, to, targetOrdinals)

  private def sameTargets(left: Array[Int], right: Array[Int]): Boolean =
    if left.length != right.length then false
    else
      var index = 0
      var same = true
      while index < left.length && same do
        same = left(index) == right(index)
        index += 1
      same
