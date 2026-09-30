package locus4s

final class RegionOverlapSuite extends munit.FunSuite:
  private def ok[E, A](result: Either[E, A]): A =
    result.fold(error => fail(s"unexpected error: $error"), identity)

  test("Dice and Jaccard match independent sets for every pair of subsets"):
    val space = ok(FiniteDomain.ephemeral("four points", 4)).value
    val subsets =
      (0 until 16).map(bits => (0 until 4).filter(i => (bits & (1 << i)) != 0).toSet)
    for left <- subsets; right <- subsets do
      val a = ok(Region.fromOrdinals(space, left.toVector))
      val b = ok(Region.fromOrdinals(space, right.toVector))
      val intersection = (left intersect right).size.toDouble
      val total = left.size.toDouble + right.size.toDouble
      val union = (left union right).size.toDouble
      val expectedDice = if total == 0.0 then 1.0 else 2.0 * intersection / total
      val expectedJaccard = if union == 0.0 then 1.0 else intersection / union
      assertEqualsDouble(a.dice(b), expectedDice, 1e-15)
      assertEqualsDouble(a.jaccard(b), expectedJaccard, 1e-15)
      assertEqualsDouble(ok(a.diceChecked(b)), expectedDice, 1e-15)
      assertEqualsDouble(ok(a.jaccardChecked(b)), expectedJaccard, 1e-15)
      assertEqualsDouble(a.dice(b), b.dice(a), 1e-15)
      assertEqualsDouble(a.jaccard(b), b.jaccard(a), 1e-15)

  test("whole and empty regions at Int.MaxValue stay compact and cannot overflow"):
    val space = ok(FiniteDomain.ephemeral("maximum domain", Int.MaxValue)).value
    val whole = Region.whole(space)
    val empty = Region.empty(space)
    assertEqualsDouble(whole.dice(whole), 1.0, 0.0)
    assertEqualsDouble(whole.jaccard(whole), 1.0, 0.0)
    assertEqualsDouble(empty.dice(empty), 1.0, 0.0)
    assertEqualsDouble(empty.jaccard(empty), 1.0, 0.0)
    assertEqualsDouble(whole.dice(empty), 0.0, 0.0)
    assertEqualsDouble(whole.jaccard(empty), 0.0, 0.0)

  test("equal shapes and persistent identities do not bypass live-owner admission"):
    val record = ok(DomainRecord.parse("shared", "domain", 4))
    val left = ok(DomainRegistry.empty.restore(record)).space
    val right = ok(DomainRegistry.empty.restore(record)).space
    assert(left.samePersistentIdentityAs(right))
    val a = ok(Region.fromOrdinals(left, Vector(0, 2)))
    val b = ok(Region.fromOrdinals(right, Vector(0, 2)))
    assert(a.diceChecked(b).isLeft)
    assert(a.jaccardChecked(b).isLeft)
    assert(Region.empty(left).diceChecked(Region.empty(right)).isLeft)
    assert(Region.empty(left).jaccardChecked(Region.empty(right)).isLeft)
    val aligned = b.rebind(ok(right.align(left)))
    assertEqualsDouble(ok(a.diceChecked(aligned)), 1.0, 0.0)
    assertEqualsDouble(ok(a.jaccardChecked(aligned)), 1.0, 0.0)
