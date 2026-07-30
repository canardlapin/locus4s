package locus4s

import java.lang.management.ManagementFactory

final class JvmAllocationCourtSuite extends munit.FunSuite:
  test("foreachIndex allocates no object per ordinal"):
    val space = ephemeral("jvm-allocation-traversal", 1_000_000)
    val (checksum, allocated) = traversalAllocation(space)

    assertEquals(
      checksum,
      30L * 1_000_000L * 999_999L / 2L
    )
    assert(
      allocated <= 4_096L,
      s"10,000,000 index visits allocated $allocated bytes"
    )

  private def traversalAllocation[S](
      space: FiniteDomain[S]
  ): (Long, Long) =
    var checksum = 0L
    val consume: Index[S] => Unit = index => checksum += index.ordinal.toLong

    var warmup = 0
    while warmup < 20 do
      space.foreachIndex(consume)
      warmup += 1

    val allocated = allocatedBytes:
      var pass = 0
      while pass < 10 do
        space.foreachIndex(consume)
        pass += 1

    (checksum, allocated)

  test("typed map and field lookup allocate no object per access"):
    val space = ephemeral("jvm-allocation-lookup", 100_000)
    val mapping = TotalMap.identity(space)
    val values = Array.tabulate(space.size)(identity)
    var checksum = 0L

    def readPass(): Unit =
      space.foreachIndex: index =>
        checksum += values(mapping(index).ordinal).toLong

    var warmup = 0
    while warmup < 50 do
      readPass()
      warmup += 1

    val allocated = allocatedBytes:
      var pass = 0
      while pass < 20 do
        readPass()
        pass += 1

    assertEquals(
      checksum,
      70L * 100_000L * 99_999L / 2L
    )
    assert(
      allocated <= 4_096L,
      s"2,000,000 typed map/array lookups allocated $allocated bytes"
    )

  test("validated ordinal recovery allocates no checked wrapper"):
    val space = ephemeral("jvm-validated-ordinal", 100_000)
    val (checksum, allocated) = validatedOrdinalAllocation(space)

    assertEquals(
      checksum,
      70L * 100_000L * 99_999L / 2L
    )
    assert(
      allocated <= 4_096L,
      s"2,000,000 validated ordinal recoveries allocated $allocated bytes"
    )

  private def validatedOrdinalAllocation[S](
      space: FiniteDomain[S]
  ): (Long, Long) =
    var checksum = 0L

    def readPass(): Unit =
      var ordinal = 0
      while ordinal < space.size do
        checksum += space.indexAtValidatedOrdinal(ordinal).ordinal.toLong
        ordinal += 1

    var warmup = 0
    while warmup < 50 do
      readPass()
      warmup += 1

    val allocated = allocatedBytes:
      var pass = 0
      while pass < 20 do
        readPass()
        pass += 1

    (checksum, allocated)

  private def allocatedBytes(body: => Unit): Long =
    ManagementFactory.getThreadMXBean match
      case bean: com.sun.management.ThreadMXBean
          if bean.isThreadAllocatedMemorySupported =>
        if !bean.isThreadAllocatedMemoryEnabled then
          bean.setThreadAllocatedMemoryEnabled(true)
        val thread = Thread.currentThread().threadId()
        val before = bean.getThreadAllocatedBytes(thread)
        body
        bean.getThreadAllocatedBytes(thread) - before
      case _ =>
        fail("the JVM does not expose per-thread allocation accounting")

  private def ephemeral(
      name: String,
      size: Int
  ): FiniteDomain[?] =
    FiniteDomain.ephemeral(name, size) match
      case Right(domain) => domain.value
      case Left(error)   => fail(error.message)
