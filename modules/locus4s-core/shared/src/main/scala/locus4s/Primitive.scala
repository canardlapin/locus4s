package locus4s

/** Privately owned immutable primitive storage with O(1) slices.
  *
  * Arrays enter through an ownership-taking constructor and never leave without
  * copying. Rebinding and relation-row views can therefore share this storage safely on
  * both JVM and Scala.js.
  */
private[locus4s] final class IntBuffer private (
    private val values: IArray[Int],
    private val offset: Int,
    val length: Int
):
  inline def apply(index: Int): Int =
    values(offset + index)

  def foreach(f: Int => Unit): Unit =
    var index = 0
    while index < length do
      f(values(offset + index))
      index += 1

  def iterator: Iterator[Int] =
    Iterator.range(0, length).map(apply)

  def slice(from: Int, until: Int): IntBuffer =
    val boundedFrom = math.max(0, math.min(length, from))
    val boundedUntil = math.max(boundedFrom, math.min(length, until))
    new IntBuffer(values, offset + boundedFrom, boundedUntil - boundedFrom)

  def toArray: Array[Int] =
    val result = Array.ofDim[Int](length)
    var index = 0
    while index < length do
      result(index) = values(offset + index)
      index += 1
    result

  def sameElements(that: IntBuffer): Boolean =
    if length != that.length then false
    else
      var index = 0
      var same = true
      while index < length && same do
        same = apply(index) == that(index)
        index += 1
      same

  def contentHash(seed: Int): Int =
    var result = seed
    var index = 0
    while index < length do
      result = 31 * result + apply(index)
      index += 1
    result

private[locus4s] object IntBuffer:
  private val Empty =
    new IntBuffer(IArray.emptyIntIArray, 0, 0)

  def empty: IntBuffer =
    Empty

  /** Takes exclusive ownership of `values`. The caller must not mutate it. */
  def fromOwnedArray(values: Array[Int]): IntBuffer =
    if values.isEmpty then Empty
    else
      new IntBuffer(
        IArray.unsafeFromArray(values),
        0,
        values.length
      )

  def copyFrom(values: IterableOnce[Int]): IntBuffer =
    fromOwnedArray(values.iterator.toArray)

  def tabulate(size: Int)(valueAt: Int => Int): IntBuffer =
    val values = Array.ofDim[Int](size)
    var index = 0
    while index < size do
      values(index) = valueAt(index)
      index += 1
    fromOwnedArray(values)
