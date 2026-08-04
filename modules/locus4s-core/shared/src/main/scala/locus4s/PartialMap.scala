package locus4s

import scala.collection.mutable

enum PartialMapError:
  case WrongTargetCount(expected: Int, actual: Int)
  case TargetOutOfBounds(
      sourceOrdinal: Int,
      targetOrdinal: Int,
      targetSize: Int
  )

  def message: String =
    this match
      case WrongTargetCount(expected, actual) =>
        s"partial map requires $expected target slots, found $actual"
      case TargetOutOfBounds(source, target, size) =>
        s"defined target for source ordinal $source is outside [0, $size): $target"

/** Immutable partial function between finite domains.
  *
  * Undefined entries use a private primitive sentinel. The sentinel is never exposed as
  * an index and is validated at every public construction boundary.
  */
final class PartialMap[X, Y] private (
    val from: FiniteDomain[X],
    val to: FiniteDomain[Y],
    private val targets: IntBuffer
):
  def apply(index: Index[X]): Option[Index[Y]] =
    val target = targets(index.ordinal)
    if target == PartialMap.Undefined then None
    else Some(to.indexAtOrdinal(target))

  def isDefinedAt(index: Index[X]): Boolean =
    targets(index.ordinal) != PartialMap.Undefined

  def definedRegion: Region[X] =
    Region.tabulate(from)(isDefinedAt)

  /** Sources that map to `target`. Each call scans the source and allocates a Region.
    */
  def preimage(target: Index[Y]): Region[X] =
    Region.tabulate(from): source =>
      targets(source.ordinal) == target.ordinal

  /** The defined graph, oriented from sources to targets.
    *
    * Materialization scans the source once and allocates fresh CSR storage. A non-empty
    * graph whose source has `Int.MaxValue` rows reports the Relation representation
    * limit instead of overflowing its row-offset count.
    */
  def toRelation: Either[RelationError, Relation[X, Y]] =
    if from.size == Int.MaxValue then
      Left(RelationError.RowOffsetCountOverflow(from.size))
    else
      val offsets = Array.ofDim[Int](from.size + 1)
      val output = Array.newBuilder[Int]
      var pairCount = 0
      var source = 0
      while source < from.size do
        val target = targets(source)
        if target != PartialMap.Undefined then
          output += target
          pairCount += 1
        offsets(source + 1) = pairCount
        source += 1
      Relation.fromCsr(from, to, offsets, output.result())

  /** All fibers, oriented from targets to their source members.
    *
    * This operation scans the PartialMap source once. It allocates one temporary bucket
    * per non-empty fiber and a fresh `Relation[Y, X]`; it never rescans the source once
    * for each target. Total work is O(|X| + |Y|).
    */
  def fibers: Either[RelationError, Relation[Y, X]] =
    if to.size == Int.MaxValue then
      var source = 0
      var hasDefinedTarget = false
      while source < from.size && !hasDefinedTarget do
        hasDefinedTarget = targets(source) != PartialMap.Undefined
        source += 1
      if hasDefinedTarget then Left(RelationError.RowOffsetCountOverflow(to.size))
      else Right(Relation.empty(to, from))
    else
      val rows = Array.fill[mutable.ArrayBuffer[Int] | Null](to.size)(null)
      var source = 0
      while source < from.size do
        val target = targets(source)
        if target != PartialMap.Undefined then
          val existing = rows(target)
          val row =
            if existing == null then
              val created = mutable.ArrayBuffer.empty[Int]
              rows(target) = created
              created
            else existing
          row += source
        source += 1

      val offsets = Array.ofDim[Int](to.size + 1)
      val output = Array.newBuilder[Int]
      var pairCount = 0
      var target = 0
      while target < to.size do
        val row = rows(target)
        if row != null then
          output ++= row
          pairCount += row.size
        offsets(target + 1) = pairCount
        target += 1
      Relation.fromCsr(to, from, offsets, output.result())

  def foreachDefined(f: (Index[X], Index[Y]) => Unit): Unit =
    from.foreachIndex: source =>
      apply(source).foreach(target => f(source, target))

  def andThen[Z](that: PartialMap[Y, Z]): PartialMap[X, Z] =
    val result = Array.ofDim[Int](from.size)
    var source = 0
    while source < from.size do
      val middle = targets(source)
      result(source) =
        if middle == PartialMap.Undefined then PartialMap.Undefined
        else that.targets(middle)
      source += 1
    PartialMap.fromOwned(from, that.to, result)

  def andThen[Z](that: TotalMap[Y, Z]): PartialMap[X, Z] =
    val result = Array.ofDim[Int](from.size)
    var source = 0
    while source < from.size do
      val middle = targets(source)
      result(source) =
        if middle == PartialMap.Undefined then PartialMap.Undefined
        else that(to.indexAtOrdinal(middle)).ordinal
      source += 1
    PartialMap.fromOwned(from, that.to, result)

  def image(region: Region[X]): Region[Y] =
    val builder = Region.newBuilder(to)
    region.foreachIndex: source =>
      apply(source).foreach(target => builder.add(target))
    builder.result()

  def rebindFrom[A](
      alignment: DomainAlignment[X, A]
  ): PartialMap[A, Y] =
    new PartialMap(alignment.right, to, targets)

  def rebindTo[B](
      alignment: DomainAlignment[Y, B]
  ): PartialMap[X, B] =
    new PartialMap(from, alignment.right, targets)

  def optionalTargetOrdinals: Vector[Option[Int]] =
    Vector.tabulate(from.size): source =>
      val target = targets(source)
      if target == PartialMap.Undefined then None else Some(target)

  override def equals(other: Any): Boolean =
    other match
      case that: PartialMap[?, ?] =>
        from == that.from &&
        to == that.to &&
        targets.sameElements(that.targets)
      case _ =>
        false

  override def hashCode(): Int =
    targets.contentHash(31 * from.hashCode() + to.hashCode())

  override def toString: String =
    s"PartialMap(${from.name.value} ⇀ ${to.name.value}, size=${from.size})"

object PartialMap:
  private val Undefined = -1

  def empty[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y]
  ): PartialMap[X, Y] =
    fromOwned(from, to, Array.fill(from.size)(Undefined))

  def fromOptionalTargetOrdinals[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y],
      targetOrdinals: IterableOnce[Option[Int]]
  ): Either[PartialMapError, PartialMap[X, Y]] =
    val input = targetOrdinals.iterator.toArray
    if input.length != from.size then
      Left(PartialMapError.WrongTargetCount(from.size, input.length))
    else
      val encoded = Array.ofDim[Int](input.length)
      var source = 0
      var error = Option.empty[PartialMapError]
      while source < input.length && error.isEmpty do
        input(source) match
          case Some(target) =>
            if !to.containsOrdinal(target) then
              error = Some(
                PartialMapError.TargetOutOfBounds(
                  source,
                  target,
                  to.size
                )
              )
            else encoded(source) = target
          case None =>
            encoded(source) = Undefined
        source += 1

      error match
        case Some(value) => Left(value)
        case None        => Right(fromOwned(from, to, encoded))

  def fromOptionalTargets[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y],
      targets: IterableOnce[Option[Index[Y]]]
  ): Either[PartialMapError, PartialMap[X, Y]] =
    fromOptionalTargetOrdinals(
      from,
      to,
      targets.iterator.map(_.map(_.ordinal))
    )

  def tabulate[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y]
  )(mapping: Index[X] => Option[Index[Y]]): PartialMap[X, Y] =
    val targets = Array.ofDim[Int](from.size)
    from.foreachIndex: source =>
      targets(source.ordinal) = mapping(source).fold(Undefined)(_.ordinal)
    fromOwned(from, to, targets)

  private def fromOwned[X, Y](
      from: FiniteDomain[X],
      to: FiniteDomain[Y],
      targets: Array[Int]
  ): PartialMap[X, Y] =
    new PartialMap(from, to, IntBuffer.fromOwnedArray(targets))
