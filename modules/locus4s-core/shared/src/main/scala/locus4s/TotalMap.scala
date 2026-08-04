package locus4s

enum TotalMapError:
  case WrongTargetCount(expected: Int, actual: Int)
  case TargetOutOfBounds(
      sourceOrdinal: Int,
      targetOrdinal: Int,
      targetSize: Int
  )

  def message: String =
    this match
      case WrongTargetCount(expected, actual) =>
        s"total map requires $expected targets, found $actual"
      case TargetOutOfBounds(source, target, size) =>
        s"target for source ordinal $source is outside [0, $size): $target"

/** An immutable total function between two live finite domains.
  *
  * Lookup is total, O(1), and allocation-free. Targets use contiguous primitive
  * storage. Composition allocates exactly one target buffer for the result and performs
  * O(|from|) lookups.
  */
final class TotalMap[X, Y] private (
    val from: FiniteDomain[X],
    val to: FiniteDomain[Y],
    private val targets: IntBuffer
):
  def apply(index: Index[X]): Index[Y] =
    to.indexAtOrdinal(targets(index.ordinal))

  /** Compatibility spelling for total typed lookup. */
  @deprecated("Use apply; scheduled for removal in 1.0.", "0.1.0")
  def at(index: Index[X]): Index[Y] =
    apply(index)

  /** Dynamic-boundary copy of the target ordinals. O(|from|). */
  def targetOrdinals: Array[Int] =
    targets.toArray

  def foreachTarget(f: Index[Y] => Unit): Unit =
    targets.foreach(ordinal => f(to.indexAtOrdinal(ordinal)))

  def foreachMapping(f: (Index[X], Index[Y]) => Unit): Unit =
    from.foreachIndex(source => f(source, apply(source)))

  def andThen[Z](that: TotalMap[Y, Z]): TotalMap[X, Z] =
    val result = Array.ofDim[Int](targets.length)
    var source = 0
    while source < targets.length do
      result(source) = that.targets(targets(source))
      source += 1
    TotalMap.fromOwned(from, that.to, result)

  /** Checked composition for operands recovered behind unrelated existentials. */
  def andThenChecked[M, Z](
      that: TotalMap[M, Z]
  ): Either[SpaceMismatch, TotalMap[X, Z]] =
    if to.sameRuntimeOwnerAs(that.from) then
      Right(andThen(new TotalMap(to, that.to, that.targets)))
    else Left(to.mismatch(that.from))

  /** Boolean-algebra pullback. O(|from| log |region|) for sparse regions. */
  def pullback(region: Region[Y]): Region[X] =
    Region.tabulate(from): index =>
      region.containsOrdinal(targets(index.ordinal))

  def pullbackChecked[T](
      region: Region[T]
  ): Either[SpaceMismatch, Region[X]] =
    if to.sameRuntimeOwnerAs(region.space) then
      to.align(region.space) match
        case Right(alignment) =>
          Right(pullback(alignment.reverse.transport(region)))
        case Left(_) =>
          Left(to.mismatch(region.space))
    else Left(to.mismatch(region.space))

  /** Set image, with work proportional to selected sources plus output sorting. */
  def image(region: Region[X]): Region[Y] =
    val builder = Region.newBuilder(to)
    region.foreachIndex(source => builder.add(apply(source)))
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

  /** O(1) source-owner transport sharing target storage. */
  def rebindFrom[A](
      alignment: DomainAlignment[X, A]
  ): TotalMap[A, Y] =
    new TotalMap(alignment.right, to, targets)

  /** O(1) target-owner transport sharing target storage. */
  def rebindTo[B](
      alignment: DomainAlignment[Y, B]
  ): TotalMap[X, B] =
    new TotalMap(from, alignment.right, targets)

  override def equals(other: Any): Boolean =
    other match
      case that: TotalMap[?, ?] =>
        from == that.from &&
        to == that.to &&
        targets.sameElements(that.targets)
      case _ =>
        false

  override def hashCode(): Int =
    targets.contentHash(31 * from.hashCode() + to.hashCode())

  override def toString: String =
    s"TotalMap(${from.name.value} -> ${to.name.value}, size=${from.size})"

object TotalMap:
  def fromTargetOrdinals[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y],
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
          error = Some(
            TotalMapError.TargetOutOfBounds(
              source,
              target,
              to.size
            )
          )
        source += 1

      error match
        case Some(value) => Left(value)
        case None        => Right(fromOwned(from, to, input))

  def fromTargetIndices[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y],
      targetIndices: IterableOnce[Index[Y]]
  ): Either[TotalMapError, TotalMap[X, Y]] =
    val targets = targetIndices.iterator.map(_.ordinal).toArray
    if targets.length != from.size then
      Left(TotalMapError.WrongTargetCount(from.size, targets.length))
    else Right(fromOwned(from, to, targets))

  def tabulate[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y]
  )(mapping: Index[X] => Index[Y]): TotalMap[X, Y] =
    val targets = Array.ofDim[Int](from.size)
    from.foreachIndex: source =>
      targets(source.ordinal) = mapping(source).ordinal
    fromOwned(from, to, targets)

  def identity[S](space: FiniteDomain[S]): TotalMap[S, S] =
    fromOwned(
      space,
      space,
      Array.tabulate(space.size)(ordinal => ordinal)
    )

  private def fromOwned[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y],
      targetOrdinals: Array[Int]
  ): TotalMap[X, Y] =
    new TotalMap(
      from,
      to,
      IntBuffer.fromOwnedArray(targetOrdinals)
    )
