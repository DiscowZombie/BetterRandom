package io.github.pr0methean.betterrandom.prng;

import static io.github.pr0methean.betterrandom.TestUtils.assertGreaterOrEqual;
import static io.github.pr0methean.betterrandom.TestUtils.assertLessOrEqual;
import static io.github.pr0methean.betterrandom.prng.BaseRandom.ENTROPY_OF_DOUBLE;
import static io.github.pr0methean.betterrandom.prng.BaseRandom.ENTROPY_OF_FLOAT;
import static io.github.pr0methean.betterrandom.prng.RandomTestUtils.STREAM_SIZE;
import static io.github.pr0methean.betterrandom.prng.RandomTestUtils.assertMonteCarloPiEstimateSane;
import static io.github.pr0methean.betterrandom.prng.RandomTestUtils.checkRangeAndEntropy;
import static io.github.pr0methean.betterrandom.prng.RandomTestUtils.checkStream;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.testing.SerializableTester;
import com.google.common.util.concurrent.Uninterruptibles;
import io.github.pr0methean.betterrandom.FlakyRetryAnalyzer;
import io.github.pr0methean.betterrandom.NamedFunction;
import io.github.pr0methean.betterrandom.TestUtils;
import io.github.pr0methean.betterrandom.prng.RandomTestUtils.EntropyCheckMode;
import io.github.pr0methean.betterrandom.seed.DefaultSeedGenerator;
import io.github.pr0methean.betterrandom.seed.FakeSeedGenerator;
import io.github.pr0methean.betterrandom.seed.PseudorandomSeedGenerator;
import io.github.pr0methean.betterrandom.seed.RandomSeeder;
import io.github.pr0methean.betterrandom.seed.SeedException;
import io.github.pr0methean.betterrandom.seed.SeedGenerator;
import io.github.pr0methean.betterrandom.util.BinaryUtils;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.api.mockito.mockpolicies.Slf4jMockPolicy;
import org.powermock.core.classloader.annotations.MockPolicy;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockTestCase;
import org.powermock.reflect.Whitebox;
import org.testng.Reporter;
import org.testng.annotations.Test;

@MockPolicy(Slf4jMockPolicy.class) @PrepareForTest(DefaultSeedGenerator.class) @PowerMockIgnore(
    {"javax.crypto.*", "javax.management.*", "javax.script.*", "jdk.nashorn.*", "javax.net.ssl.*",
        "javax.security.*", "javax.xml.*", "org.xml.sax.*", "org.w3c.dom.*",
        "org.springframework.context.*", "org.apache.log4j.*"})
public abstract class BaseRandomTest<T extends BaseRandom> extends PowerMockTestCase {

  protected static final int INSTANCES_TO_HASH = 25;
  protected static final int EXPECTED_UNIQUE_HASHES = (int) (0.8 * INSTANCES_TO_HASH);
  protected static final int TEST_BYTES_LENGTH = 100;
  protected final SeedGenerator pseudorandomSeedGenerator = new PseudorandomSeedGenerator();

  /**
   * The square root of 12, rounded from an extended-precision calculation that was done by Wolfram
   * Alpha (and thus at least as accurate as {@code StrictMath.sqrt(12.0)}).
   */
  protected static final double SQRT_12 = 3.4641016151377546;
  protected static final long TEST_SEED = 0x0123456789ABCDEFL;
  protected static final NamedFunction<Random, Double> NEXT_LONG =
      new NamedFunction<>(random -> (double) random.nextLong(), "Random::nextLong");
  protected static final NamedFunction<Random, Double> NEXT_INT =
      new NamedFunction<>(random -> (double) random.nextInt(), "Random::nextInt");
  protected static final NamedFunction<Random, Double> NEXT_DOUBLE =
      new NamedFunction<>(Random::nextDouble, "Random::nextDouble");
  protected static final NamedFunction<Random, Double> NEXT_GAUSSIAN =
      new NamedFunction<>(Random::nextGaussian, "Random::nextGaussian");
  protected final NamedFunction<Random, Double> setSeed = new NamedFunction<>(random -> {
    if (random instanceof BaseRandom) {
      BaseRandom baseRandom = (BaseRandom) random;
      baseRandom.setSeed(pseudorandomSeedGenerator.generateSeed(baseRandom.getNewSeedLength()));
    } else {
      random.setSeed(BinaryUtils.convertBytesToLong(pseudorandomSeedGenerator.generateSeed(8)));
    }
    return 0.0;
  }, "BaseRandom::setSeed(byte[])");

  protected final List<NamedFunction<? super BaseRandom, Double>>
      functionsForThreadSafetyTest =
      ImmutableList.of(NEXT_LONG, NEXT_INT, NEXT_DOUBLE, NEXT_GAUSSIAN);
  protected final List<NamedFunction<? super BaseRandom, Double>>
      functionsForThreadCrashTest =
      ImmutableList.of(NEXT_LONG, NEXT_INT, NEXT_DOUBLE, NEXT_GAUSSIAN, setSeed);
  protected static final int TEST_BYTE_ARRAY_LENGTH = STREAM_SIZE;
  private static final String HELLO = "Hello";
  private static final String HOW_ARE_YOU = "How are you?";
  private static final String GOODBYE = "Goodbye";
  private static final String[] STRING_ARRAY = {HELLO, HOW_ARE_YOU, GOODBYE};
  private static final List<String> STRING_LIST =
      Collections.unmodifiableList(Arrays.asList(STRING_ARRAY));
  private static final int ELEMENTS = 100;
  private static final double UPPER_BOUND_FOR_ROUNDING_TEST =
      Double.longBitsToDouble(Double.doubleToLongBits(1.0) + 3);
  protected final ForkJoinPool pool = new ForkJoinPool(2);
  private DefaultSeedGenerator oldDefaultSeedGenerator;

  @SafeVarargs
  private static <E> void testGeneratesAll(final Supplier<E> generator, final E... expected) {
    final E[] selected = Arrays.copyOf(expected, ELEMENTS); // Saves passing in a Class<E>
    for (int i = 0; i < ELEMENTS; i++) {
      selected[i] = generator.get();
    }
    assertTrue(Arrays.asList(selected).containsAll(Arrays.asList(expected)));
  }

  protected static void checkSetSeedLong(BaseRandom rng, BaseRandom rng2) {
    rng.nextLong(); // ensure they won't both be in initial state before reseeding
    rng.setSeed(0x0123456789ABCDEFL);
    rng2.setSeed(0x0123456789ABCDEFL);
    RandomTestUtils
        .assertEquivalent(rng, rng2, STREAM_SIZE, "Output mismatch after reseeding with same seed");
  }

  protected SeedGenerator getTestSeedGenerator() {
    ThreadLocalRandom.current(); // ensure initialized for calling thread
    return pseudorandomSeedGenerator;
  }

  protected EntropyCheckMode getEntropyCheckMode() {
    return EntropyCheckMode.EXACT;
  }

  /**
   * Replaces the default seed generator with a faster mock. Must be undone after the test using
   * {@link #unmockDefaultSeedGenerator()}.
   */
  protected void mockDefaultSeedGenerator() {
    oldDefaultSeedGenerator = DefaultSeedGenerator.DEFAULT_SEED_GENERATOR;
    final DefaultSeedGenerator mockDefaultSeedGenerator =
        PowerMockito.mock(DefaultSeedGenerator.class);
    when(mockDefaultSeedGenerator.generateSeed(anyInt())).thenAnswer(
        invocation -> pseudorandomSeedGenerator.generateSeed((Integer) (invocation.getArgument(0))));
    doAnswer(invocation -> {
      pseudorandomSeedGenerator.generateSeed(invocation.getArgument(0));
      return null;
    }).when(mockDefaultSeedGenerator).generateSeed(any(byte[].class));
    Whitebox.setInternalState(DefaultSeedGenerator.class, "DEFAULT_SEED_GENERATOR",
        mockDefaultSeedGenerator);
  }

  /**
   * Undoes {@link #mockDefaultSeedGenerator()}, restoring the factory default.
   */
  protected void unmockDefaultSeedGenerator() {
    Whitebox.setInternalState(DefaultSeedGenerator.class, "DEFAULT_SEED_GENERATOR",
        oldDefaultSeedGenerator);
  }

  protected Map<Class<?>, Object> constructorParams() {
    final int seedLength = getNewSeedLength();
    final HashMap<Class<?>, Object> params = new HashMap<>(4);
    params.put(int.class, seedLength);
    params.put(long.class, TEST_SEED);
    params.put(byte[].class, new byte[seedLength]);
    params.put(SeedGenerator.class, pseudorandomSeedGenerator);
    return params;
  }

  protected int getNewSeedLength() {
    return createRng().getNewSeedLength();
  }

  /**
   * Must have a looser type bound than T in case T is a generic type.
   * @return the class under test
   */
  protected Class<? extends BaseRandom> getClassUnderTest() {
    return createRng().getClass();
  }

  /**
   * Test to ensure that two distinct RNGs with the same seed return the same sequence of numbers.
   */
  @Test(timeOut = 15_000) public void testRepeatability() throws SeedException {
    final BaseRandom rng = createRng();
    // Create second RNG using same seed.
    final BaseRandom duplicateRNG = createRng(rng.getSeed());
    RandomTestUtils.assertEquivalent(rng, duplicateRNG, TEST_BYTES_LENGTH, "Output mismatch");
  }

  /**
   * Test that nextGaussian never returns a stale cached value.
   */
  @Test(timeOut = 15_000) public void testRepeatabilityNextGaussian() throws SeedException {
    final BaseRandom rng = createRng();
    final byte[] seed = getTestSeedGenerator().generateSeed(getNewSeedLength());
    rng.nextGaussian();
    rng.setSeed(seed);
    // Create second RNG using same seed.
    final BaseRandom duplicateRNG = createRng(seed);
    // Do not inline this; dump() must be evaluated before nextGaussian
    final String failureMessage = String
        .format("Mismatch in output between %n%s%n and %n%s%n", rng.dump(), duplicateRNG.dump());
    assertEquals(rng.nextGaussian(), duplicateRNG.nextGaussian(), failureMessage);
  }

  @Test(timeOut = 15_000, expectedExceptions = IllegalArgumentException.class)
  public void testSeedTooLong() throws GeneralSecurityException, SeedException {
    createRng(getTestSeedGenerator()
        .generateSeed(getNewSeedLength() + 1)); // Should throw an exception.
  }

  protected abstract T createRng();

  protected abstract T createRng(byte[] seed);

  /**
   * Test to ensure that the output from the RNG is broadly as expected.  This will not detect the
   * subtle statistical anomalies that would be picked up by Diehard, but it provides a simple check
   * for major problems with the output.
   */
  @Test(timeOut = 20_000, groups = "non-deterministic") public void testDistribution()
      throws SeedException {
    final BaseRandom rng = createRng();
    assertMonteCarloPiEstimateSane(rng);
  }

  /**
   * Test to ensure that the output from the RNG is broadly as expected.  This will not detect the
   * subtle statistical anomalies that would be picked up by Diehard, but it provides a simple check
   * for major problems with the output.
   */
  @Test(timeOut = 30_000, groups = "non-deterministic") public void testIntegerSummaryStats()
      throws SeedException {
    final BaseRandom rng = createRng();
    // Expected standard deviation for a uniformly distributed population of values in the range
    // 0..n
    // approaches n/sqrt(12).
    // Expected standard deviation for a uniformly distributed population of values in the range
    // 0..n
    // approaches n/sqrt(12).
    for (final long n : new long[]{100, 1L << 32, Long.MAX_VALUE}) {
      final int iterations = 10_000;
      final SynchronizedDescriptiveStatistics stats =
          RandomTestUtils.summaryStats(rng, n, iterations);
      final double observedSD = stats.getStandardDeviation();
      final double expectedSD = n / SQRT_12;
      Reporter.log("Expected SD: " + expectedSD + ", observed SD: " + observedSD);
      assertGreaterOrEqual(observedSD, 0.97 * expectedSD);
      assertLessOrEqual(observedSD, 1.03 * expectedSD);
      assertGreaterOrEqual(stats.getMax(), 0.9 * n);
      assertLessOrEqual(stats.getMax(), n - 1);
      assertGreaterOrEqual(stats.getMin(), 0);
      assertLessOrEqual(stats.getMin(), 0.1 * n);
      assertGreaterOrEqual(stats.getMean(), 0.4 * n);
      assertLessOrEqual(stats.getMean(), 0.6 * n);
      final double median = stats.getPercentile(50);
      assertGreaterOrEqual(median, 0.4 * n);
      assertLessOrEqual(median, 0.6 * n);
    }
  }

  /**
   * Test to ensure that the output from nextGaussian is broadly as expected.
   */
  @Test(timeOut = 40_000, groups = "non-deterministic") public void testNextGaussianStatistically()
      throws SeedException {
    final BaseRandom rng = createRng();
    final int iterations = 20_000;
    final SynchronizedDescriptiveStatistics stats = new SynchronizedDescriptiveStatistics();
    rng.gaussians(iterations).spliterator().forEachRemaining((DoubleConsumer) stats::addValue);
    final double observedSD = stats.getStandardDeviation();
    Reporter.log("Expected SD for Gaussians: 1, observed SD: " + observedSD);
    assertGreaterOrEqual(observedSD, 0.965);
    assertLessOrEqual(observedSD, 1.035);
    assertGreaterOrEqual(stats.getMax(), 2.0);
    assertLessOrEqual(stats.getMin(), -2.0);
    assertGreaterOrEqual(stats.getMean(), -0.1);
    assertLessOrEqual(stats.getMean(), 0.1);
    final double median = stats.getPercentile(50);
    assertGreaterOrEqual(median, -0.1);
    assertLessOrEqual(median, 0.1);
  }

  /**
   * Make sure that the RNG does not accept seeds that are too small since this could affect the
   * distribution of the output.
   */
  @Test(timeOut = 15_000, expectedExceptions = IllegalArgumentException.class)
  public void testSeedTooShort() throws SeedException {
    createRng(new byte[]{1, 2, 3}); // One byte too few, should cause an IllegalArgumentException.
  }

  /**
   * RNG must not accept a null seed otherwise it will not be properly initialized.
   */
  @Test(timeOut = 15_000, expectedExceptions = IllegalArgumentException.class)
  public void testNullSeed() throws SeedException {
    createRng(null);
  }

  @Test(timeOut = 45_000) public void testSerializable() throws SeedException {
    testSerializable(createRng());
  }

  public void testSerializable(BaseRandom rng) {
    RandomTestUtils.assertEquivalentWhenSerializedAndDeserialized(rng);
    // Can't use a PseudorandomSeedGenerator, because Random.equals() breaks equality check
    final SeedGenerator seedGenerator =
        new FakeSeedGenerator(getClass().getSimpleName() + "::testSerializable #" + rng.nextInt());
    RandomSeeder randomSeeder = new RandomSeeder(seedGenerator);
    rng.setRandomSeeder(randomSeeder);
    try {
      final BaseRandom rng2 = SerializableTester.reserialize(rng);
      assertEquals(randomSeeder, rng2.getRandomSeeder());
      rng2.setRandomSeeder(null);
    } finally {
      RandomTestUtils.removeAndAssertEmpty(randomSeeder, rng);
    }
  }

  @Test(timeOut = 120_000)
  public void testAllPublicConstructors() throws SeedException {
    mockDefaultSeedGenerator();
    try {
      TestUtils.testConstructors(getClassUnderTest(), false, ImmutableMap.copyOf(constructorParams()),
          BaseRandom::nextInt);
    } finally {
      unmockDefaultSeedGenerator();
    }
  }

  /**
   * Assertion-free since many implementations have a fallback behavior.
   */
  @Test(timeOut = 60_000) public void testSetSeedLong() {
    mockDefaultSeedGenerator();
    try {
      createRng().setSeed(0x0123456789ABCDEFL);
    } finally {
      unmockDefaultSeedGenerator();
    }
  }

  @Test(timeOut = 15_000) public void testSetSeedAfterNextLong() throws SeedException {
    checkSetSeedAfter(this::createRng, BaseRandom::nextLong);
  }

  @Test(timeOut = 15_000) public void testSetSeedAfterNextInt() throws SeedException {
    checkSetSeedAfter(this::createRng, BaseRandom::nextInt);
  }

  protected void checkSetSeedAfter(final Supplier<BaseRandom> supplier,
      Consumer<? super BaseRandom> stateChange) throws SeedException {
    checkSetSeedAfter(supplier, this::createRng, stateChange);
  }

  protected void checkSetSeedAfter(final Supplier<BaseRandom> creator,
      final Function<byte[], BaseRandom> creatorForSeed, Consumer<? super BaseRandom> stateChange) throws SeedException {
    final byte[] seed = getTestSeedGenerator().generateSeed(getNewSeedLength());
    final BaseRandom rng = creator.get();
    final BaseRandom rng2 = creator.get();
    final BaseRandom rng3 = creatorForSeed.apply(seed);
    stateChange.accept(rng);
    rng.setSeed(seed);
    rng2.setSeed(seed);
    RandomTestUtils
        .assertEquivalent(rng, rng2, 64, "Output mismatch after reseeding with same seed");
    rng.setSeed(seed);
    RandomTestUtils.assertEquivalent(rng, rng3, 64, "Output mismatch vs a new PRNG with same seed");
  }

  @Test(timeOut = 15_000) public void testSetSeedZero() throws SeedException {
    final int length = getNewSeedLength();
    final byte[] zeroSeed = new byte[length];
    final byte[] realSeed = new byte[length];
    do {
      getTestSeedGenerator().generateSeed(realSeed);
    } while (Arrays.equals(realSeed, zeroSeed));
    final BaseRandom rng = createRng(realSeed);
    final BaseRandom rng2 = createRng(zeroSeed);
    RandomTestUtils
        .assertDistinct(rng, rng2, STREAM_SIZE, "Output with real seed matches output with all-zeroes seed");
  }

  @Test(timeOut = 15_000) public void testEquals() throws SeedException {
    RandomTestUtils.doEqualsSanityChecks(this::createRng);
  }

  @Test(timeOut = 60_000) public void testHashCode() {
    final HashSet<Integer> uniqueHashCodes = new HashSet<>(INSTANCES_TO_HASH);
    for (int i = 0; i < INSTANCES_TO_HASH; i++) {
      uniqueHashCodes.add(createRng().hashCode());
    }
    assertGreaterOrEqual(uniqueHashCodes.size(), EXPECTED_UNIQUE_HASHES,
        "Too many hashCode collisions");
  }

  /**
   * dump() doesn't have much of a contract, but we should at least expect it to output enough state
   * for two independently-generated instances to give unequal dumps.
   */
  @Test(timeOut = 15_000) public void testDump() throws SeedException {
    final BaseRandom rng = createRng();
    assertNotEquals(rng.dump(), createRng().dump());
    rng.nextBoolean(); // Kill a mutant where dump doesn't unlock the lock
  }

  @Test public void testReseeding() throws SeedException {
    final byte[] output1 = new byte[STREAM_SIZE];
    final BaseRandom rng1 = createRng();
    final BaseRandom rng2 = createRng();
    rng1.nextBytes(output1);
    final byte[] output2 = new byte[STREAM_SIZE];
    rng2.nextBytes(output2);
    final int seedLength = rng1.getNewSeedLength();
    rng1.setSeed(getTestSeedGenerator().generateSeed(seedLength));
    assertGreaterOrEqual(rng1.getEntropyBits(), seedLength * 8L);
    rng1.nextBytes(output1);
    rng2.nextBytes(output2);
    assertFalse(Arrays.equals(output1, output2));
  }

  /**
   * When not overridden, this also tests {@link BaseRandom#getRandomSeeder()} and
   * {@link BaseRandom#setRandomSeeder(RandomSeeder)}.
   */
  @Test(timeOut = 60_000, retryAnalyzer = FlakyRetryAnalyzer.class)
  public void testRandomSeederIntegration() {
    final SeedGenerator seedGenerator = new PseudorandomSeedGenerator(new Random(),
        UUID.randomUUID().toString());
    final BaseRandom rng = createRng();
    RandomTestUtils.checkReseeding(seedGenerator, rng, true);
  }

  @Test(timeOut = 10_000) public void testWithProbability() {
    final BaseRandom prng = createRng();
    final long originalEntropy = prng.getEntropyBits();
    assertFalse(prng.withProbability(0.0));
    assertTrue(prng.withProbability(1.0));
    assertEquals(originalEntropy, prng.getEntropyBits());
    checkRangeAndEntropy(prng, 1, () -> prng.withProbability(0.7) ? 0 : 1, 0, 2,
        getEntropyCheckMode());
  }

  @Test(timeOut = 20_000, groups = "non-deterministic")
  public void testWithProbabilityStatistically() {
    final BaseRandom prng = createRng();
    int trues = 0;
    for (int i = 0; i < 3000; i++) {
      if (prng.withProbability(0.6)) {
        trues++;
      }
    }
    // Significance test at p=3.15E-6 (unadjusted for the multiple subclasses and environments!)
    assertGreaterOrEqual(trues, 1675);
    assertLessOrEqual(trues, 1925);
    trues = 0;
    for (int i = 0; i < 3000; i++) {
      if (prng.withProbability(0.5)) {
        trues++;
      }
    }
    // Significance test at p=4.54E-6 (unadjusted for the multiple subclasses and environments!)
    assertGreaterOrEqual(trues, 1375);
    assertLessOrEqual(trues, 1625);
  }

  @Test(timeOut = 20_000, groups = "non-deterministic") public void testNextBooleanStatistically() {
    final BaseRandom prng = createRng();
    int trues = 0;
    for (int i = 0; i < 3000; i++) {
      if (prng.nextBoolean()) {
        trues++;
      }
    }
    // Significance test at p=4.54E-6 (unadjusted for the multiple subclasses and environments!)
    assertGreaterOrEqual(trues, 1375);
    assertLessOrEqual(trues, 1625);
  }

  @Test(timeOut = 30_000L) public void testNextBytes() {
    final byte[] testBytes = new byte[TEST_BYTE_ARRAY_LENGTH];
    final BaseRandom prng = createRng();
    final long oldEntropy = prng.getEntropyBits();
    prng.nextBytes(testBytes);
    assertFalse(Arrays.equals(testBytes, new byte[TEST_BYTE_ARRAY_LENGTH]));
    final long entropy = prng.getEntropyBits();
    final long expectedEntropy = oldEntropy - (8 * TEST_BYTE_ARRAY_LENGTH);
    EntropyCheckMode entropyCheckMode = getEntropyCheckMode();
    switch (entropyCheckMode) {
      case EXACT:
        assertEquals(entropy, expectedEntropy);
        break;
      case LOWER_BOUND:
        assertGreaterOrEqual(entropy, expectedEntropy);
        break;
      case OFF:
        break;
      default:
        fail("Unhandled entropy check mode " + entropyCheckMode);
    }
  }

  @Test public void testNextInt1() {
    final BaseRandom prng = createRng();
    final Supplier<? extends Number> numberSupplier = () -> prng.nextInt(3 << 29);
    checkRangeAndEntropy(prng, 31, numberSupplier, 0, (3 << 29), getEntropyCheckMode());
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNextInt1InvalidBound() {
    createRng().nextInt(0);
  }

  @Test public void testNextInt() {
    final BaseRandom prng = createRng();
    checkRangeAndEntropy(prng, 32, (Supplier<? extends Number>) prng::nextInt, Integer.MIN_VALUE,
        (Integer.MAX_VALUE + 1L), getEntropyCheckMode());
  }

  @Test public void testNextInt2() {
    final BaseRandom prng = createRng();
    final Supplier<? extends Number> numberSupplier = () -> prng.nextInt(1 << 27, 1 << 29);
    checkRangeAndEntropy(prng, 29, numberSupplier, (1 << 27), (1 << 29), getEntropyCheckMode());
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNextInt2InvalidBound() {
    createRng().nextInt(1, 1);
  }

  @Test public void testNextInt2HugeRange() {
    final BaseRandom prng = createRng();
    final Supplier<? extends Number> numberSupplier =
        () -> prng.nextInt(Integer.MIN_VALUE, 1 << 29);
    checkRangeAndEntropy(prng, 32, numberSupplier, Integer.MIN_VALUE, (1 << 29),
        getEntropyCheckMode());
  }

  @Test public void testNextLong() {
    final BaseRandom prng = createRng();
    checkRangeAndEntropy(prng, 64, (Supplier<? extends Number>) prng::nextLong, Long.MIN_VALUE,
        Long.MAX_VALUE + 1.0, getEntropyCheckMode());
  }

  @Test public void testNextLong1() {
    final BaseRandom prng = createRng();
    for (int i = 0; i < STREAM_SIZE; i++) {
      // check that the bound is exclusive, to kill an off-by-one mutant
      final Supplier<? extends Number> numberSupplier = () -> prng.nextLong(2);
      checkRangeAndEntropy(prng, 1, numberSupplier, 0, 2, getEntropyCheckMode());
    }
    final Supplier<? extends Number> numberSupplier = () -> prng.nextLong(1L << 42);
    checkRangeAndEntropy(prng, 42, numberSupplier, 0, (1L << 42), getEntropyCheckMode());
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNextLong1InvalidBound() {
    createRng().nextLong(-1);
  }

  @Test public void testNextLong2() {
    final BaseRandom prng = createRng();
    final Supplier<? extends Number> numberSupplier = () -> prng.nextLong(1L << 40, 1L << 42);
    checkRangeAndEntropy(prng, 42, numberSupplier, (1L << 40), (1L << 42), getEntropyCheckMode());
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNextLong2InvalidBound() {
    createRng().nextLong(10, 9);
  }

  @Test public void testNextLong2HugeRange() {
    final BaseRandom prng = createRng();
    final Supplier<? extends Number> numberSupplier = () -> prng.nextLong(Long.MIN_VALUE, 1L << 62);
    checkRangeAndEntropy(prng, 64, numberSupplier, Long.MIN_VALUE, (1L << 62),
        getEntropyCheckMode());
  }

  @Test public void testNextDouble() {
    final BaseRandom prng = createRng();
    checkRangeAndEntropy(prng, ENTROPY_OF_DOUBLE, (Supplier<? extends Number>) prng::nextDouble,
        0.0, 1.0, getEntropyCheckMode());
  }

  @Test public void testNextFloat() {
    final BaseRandom prng = createRng();
    checkRangeAndEntropy(prng, ENTROPY_OF_FLOAT, (Supplier<? extends Number>) prng::nextFloat, 0.0,
        1.0, getEntropyCheckMode());
  }

  @Test public void testNextDouble1() {
    final BaseRandom prng = createRng();
    final Supplier<? extends Number> numberSupplier = () -> prng.nextDouble(13.37);
    checkRangeAndEntropy(prng, ENTROPY_OF_DOUBLE, numberSupplier, 0.0, 13.37,
        getEntropyCheckMode());
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNextDouble1InvalidBound() {
    createRng().nextDouble(-1.0);
  }

  @Test public void testNextDouble2() {
    final BaseRandom prng = createRng();
    final Supplier<? extends Number> numberSupplier2 = () -> prng.nextDouble(-1.0, 13.37);
    checkRangeAndEntropy(prng, ENTROPY_OF_DOUBLE, numberSupplier2, -1.0, 13.37,
        getEntropyCheckMode());
    final Supplier<? extends Number> numberSupplier1 = () -> prng.nextDouble(5.0, 13.37);
    checkRangeAndEntropy(prng, ENTROPY_OF_DOUBLE, numberSupplier1, 5.0, 13.37,
        getEntropyCheckMode());
    final Supplier<? extends Number> numberSupplier =
        () -> prng.nextDouble(1.0, UPPER_BOUND_FOR_ROUNDING_TEST);
    checkRangeAndEntropy(prng, ENTROPY_OF_DOUBLE, numberSupplier, 1.0,
        UPPER_BOUND_FOR_ROUNDING_TEST, getEntropyCheckMode());
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNextDouble2InvalidBound() {
    createRng().nextDouble(3.5, 3.5);
  }

  @Test(timeOut = 10_000) public void testNextGaussian() {
    final BaseRandom prng = createRng();
    // TODO: Find out the actual Shannon entropy of nextGaussian() and adjust the entropy count to
    // it in a wrapper function.
    checkRangeAndEntropy(prng, 2 * ENTROPY_OF_DOUBLE,
        () -> prng.nextGaussian() + prng.nextGaussian(), -Double.MAX_VALUE, Double.MAX_VALUE,
        getEntropyCheckMode());
  }

  @Test(timeOut = 10_000) public void testNextBoolean() {
    final BaseRandom prng = createRng();
    final Supplier<? extends Number> numberSupplier = () -> prng.nextBoolean() ? 0 : 1;
    checkRangeAndEntropy(prng, 1L, numberSupplier, 0, 2, getEntropyCheckMode());
  }

  @Test(timeOut = 10_000) public void testInts() {
    final BaseRandom prng = createRng();
    checkStream(prng, 32, prng.ints().boxed(), -1, Integer.MIN_VALUE, Integer.MAX_VALUE + 1L, true);
  }

  @Test(timeOut = 10_000) public void testInts1() {
    final BaseRandom prng = createRng();
    checkStream(prng, 32, prng.ints(STREAM_SIZE).boxed(),
        STREAM_SIZE, Integer.MIN_VALUE, Integer.MAX_VALUE + 1L,
        true);
  }

  @Test(timeOut = 10_000) public void testInts2() {
    final BaseRandom prng = createRng();
    checkStream(prng, 29, prng.ints(1 << 27, 1 << 29).boxed(), -1, 1 << 27, 1 << 29, true);
  }

  @Test(timeOut = 10_000) public void testInts3() {
    final BaseRandom prng = createRng();
    checkStream(prng, 29, prng.ints(3, 1 << 27, 1 << 29).boxed(), 3, 1 << 27, 1 << 29, true);
  }

  @Test(timeOut = 10_000) public void testLongs() {
    final BaseRandom prng = createRng();
    checkStream(prng, 64, prng.longs().boxed(), -1, Long.MIN_VALUE, Long.MAX_VALUE + 1.0, true);
  }

  @Test(timeOut = 10_000) public void testLongs1() {
    final BaseRandom prng = createRng();
    checkStream(prng, 64, prng.longs(STREAM_SIZE).boxed(),
        STREAM_SIZE, Long.MIN_VALUE, Long.MAX_VALUE + 1.0, true);
  }

  @Test(timeOut = 10_000) public void testLongs2() {
    final BaseRandom prng = createRng();
    checkStream(prng, 42, prng.longs(1L << 40, 1L << 42).boxed(), -1, 1L << 40, 1L << 42, true);
  }

  @Test(timeOut = 10_000) public void testLongs3() {
    final BaseRandom prng = createRng();
    checkStream(prng, 42, prng.longs(STREAM_SIZE, 1L << 40, 1L << 42).boxed(), STREAM_SIZE, 1L << 40, 1L << 42, true);
  }

  @Test(timeOut = 10_000) public void testLongs3SmallRange() {
    final long bound = (1L << 40) + 2;
    final BaseRandom prng = createRng();
    checkStream(prng, 31, prng.longs(STREAM_SIZE, 1L << 40, bound).boxed(), STREAM_SIZE, 1L << 40, bound, true);
  }

  @Test(timeOut = 20_000L) public void testDoubles() {
    final BaseRandom prng = createRng();
    checkStream(prng, ENTROPY_OF_DOUBLE, prng.doubles().boxed(), -1, 0.0, 1.0, true);
  }

  @Test(timeOut = 20_000L) public void testDoubles1() {
    final BaseRandom prng = createRng();
    checkStream(prng, ENTROPY_OF_DOUBLE, prng.doubles(STREAM_SIZE).boxed(), STREAM_SIZE, 0.0, 1.0, true);
  }

  @Test(timeOut = 20_000L) public void testDoubles2() {
    final BaseRandom prng = createRng();
    checkStream(prng, ENTROPY_OF_DOUBLE, prng.doubles(-5.0, 8.0).boxed(), -1, -5.0, 8.0, true);
  }

  @Test(timeOut = 20_000L) public void testDoubles3() {
    final BaseRandom prng = createRng();
    checkStream(prng, ENTROPY_OF_DOUBLE, prng.doubles(STREAM_SIZE, -5.0, 8.0).boxed(), STREAM_SIZE, -5.0, 8.0, true);
  }

  @Test(timeOut = 20_000L) public void testDoubles3RoundingCorrection() {
    final BaseRandom prng = createRng();
    checkStream(prng, ENTROPY_OF_DOUBLE,
        prng.doubles(STREAM_SIZE, 1.0, UPPER_BOUND_FOR_ROUNDING_TEST).boxed(), STREAM_SIZE, -5.0, 8.0, true);
  }

  @Test(timeOut = 30_000L) public void testGaussians() {
    final BaseRandom prng = createRng();
    checkStream(prng, ENTROPY_OF_DOUBLE, prng.gaussians().boxed(), -1, -Double.MAX_VALUE,
        Double.MAX_VALUE, true);
  }

  @Test(timeOut = 30_000L) public void testGaussians1() {
    final BaseRandom prng = createRng();
    checkStream(prng, ENTROPY_OF_DOUBLE, prng.gaussians(100).boxed(), 100, -Double.MAX_VALUE,
        Double.MAX_VALUE, true);
  }

  @Test public void testNextElementArray() {
    final BaseRandom prng = createRng();
    testGeneratesAll(() -> prng.nextElement(STRING_ARRAY), STRING_ARRAY);
  }

  @Test public void testNextElementList() {
    final BaseRandom prng = createRng();
    testGeneratesAll(() -> prng.nextElement(STRING_LIST), STRING_ARRAY);
  }

  @Test public void testNextEnum() {
    final BaseRandom prng = createRng();
    testGeneratesAll(() -> prng.nextEnum(TestEnum.class), TestEnum.RED, TestEnum.YELLOW,
        TestEnum.BLUE);
  }

  @Test public void testGetNewSeedLength() {
    assertTrue(createRng().getNewSeedLength() > 0);
  }

  @Test(timeOut = 90_000) public void testThreadSafety() {
    checkThreadSafety(functionsForThreadSafetyTest, functionsForThreadSafetyTest);
  }

  @Test public void testThreadSafetySetSeed() {
    checkThreadSafetyVsCrashesOnly(30, Collections.singletonList(setSeed),
        functionsForThreadCrashTest);
  }

  @Test public void testInitialEntropy() {
    int seedSize = getNewSeedLength();
    byte[] seed = getTestSeedGenerator().generateSeed(seedSize);
    assertEquals(createRng(seed).getEntropyBits(), 8 * seedSize, "Wrong initial entropy");
  }

  protected void checkThreadSafetyVsCrashesOnly(final int timeoutSec,
      final List<? extends NamedFunction<? super T, Double>> functions) {
    checkThreadSafetyVsCrashesOnly(timeoutSec, functions, functions);
  }

  protected void checkThreadSafetyVsCrashesOnly(final int timeoutSec,
      final List<? extends NamedFunction<? super T, Double>> functionsThread1,
      final List<? extends NamedFunction<? super T, Double>> functionsThread2) {
    final int seedLength = createRng().getNewSeedLength();
    final byte[] seed = getTestSeedGenerator().generateSeed(seedLength);
    for (final NamedFunction<? super T, Double> supplier1 : functionsThread1) {
      for (final NamedFunction<? super T, Double> supplier2 : functionsThread2) {
        runParallel(supplier1, supplier2, seed, timeoutSec,
            (supplier1 == setSeed || supplier2 == setSeed) ? 200 : 1000);
      }
    }
  }

  protected void checkThreadSafety(final List<? extends NamedFunction<? super T, Double>> functions,
      final List<? extends NamedFunction<? super T, Double>> pairwiseFunctions) {
    checkThreadSafety(functions, pairwiseFunctions, this::createRng);
  }

  protected void checkThreadSafety(final List<? extends NamedFunction<? super T, Double>> functions,
      final List<? extends NamedFunction<? super T, Double>> pairwiseFunctions,
      final Function<byte[], ? extends T> randomCreator) {
    final int seedLength = createRng().getNewSeedLength();
    final byte[] seed = getTestSeedGenerator().generateSeed(seedLength);
    for (final NamedFunction<? super T, Double> supplier : functions) {
      for (int i = 0; i < 5; i++) {
        // This loop is necessary to control the false pass rate, especially during mutation
        // testing.
        final SortedSet<Double> sequentialOutput =
            runSequential(supplier, supplier, randomCreator.apply(seed));
        final SortedSet<Double> parallelOutput =
            runParallel(supplier, supplier, 25, 1000, randomCreator.apply(seed));
        assertEquals(sequentialOutput, parallelOutput,
            "output differs between sequential & parallel calls to " + supplier);
      }
    }

    // Check that each pair won't crash no matter which order they start in
    // (this part is assertion-free because we can't tell whether A-bits-as-long and
    // B-bits-as-double come from the same bit stream as vice-versa).
    for (final NamedFunction<? super T, Double> supplier1 : pairwiseFunctions) {
      for (final NamedFunction<? super T, Double> supplier2 : pairwiseFunctions) {
        if (supplier1 != supplier2) {
          runParallel(supplier1, supplier2, seed, 25, 1000);
        }
      }
    }
  }

  protected SortedSet<Double> runParallel(final NamedFunction<? super T, Double> supplier1,
      final NamedFunction<? super T, Double> supplier2, final byte[] seed, final int timeoutSec,
      final int iterations) {
    return runParallel(supplier1, supplier2, timeoutSec, iterations, createRng(seed));
  }

  protected SortedSet<Double> runParallel(final NamedFunction<? super T, Double> supplier1, final NamedFunction<? super T, Double> supplier2,
      final int timeoutSec, final int iterations, final T random) {
    // See https://www.yegor256.com/2018/03/27/how-to-test-thread-safety.html for why a
    // CountDownLatch is used.
    CountDownLatch latch = new CountDownLatch(2);
    final SortedSet<Double> output = new ConcurrentSkipListSet<>();
    pool.execute(new GeneratorForkJoinTask<>(random, output, supplier1, latch, iterations));
    pool.execute(new GeneratorForkJoinTask<>(random, output, supplier2, latch, iterations));
    assertTrue(pool.awaitQuiescence(timeoutSec, TimeUnit.SECONDS),
        String.format("Timed out waiting for %s and %s to finish", supplier1, supplier2));
    return output;
  }

  protected SortedSet<Double> runSequential(final NamedFunction<? super T, Double> supplier1,
      final NamedFunction<? super T, Double> supplier2, final T random) {
    final SortedSet<Double> output = new TreeSet<>();
    new GeneratorForkJoinTask<>(random, output, supplier1, new CountDownLatch(1), 1000)
        .exec();
    new GeneratorForkJoinTask<>(random, output, supplier2, new CountDownLatch(1), 1000)
        .exec();
    return output;
  }

  private enum TestEnum {
    RED, YELLOW, BLUE
  }

  /**
   * ForkJoinTask that reads random longs and adds them to the set.
   */
  protected static final class GeneratorForkJoinTask<TRandom extends Random, TOut>
      extends ForkJoinTask<Void> {

    private static final long serialVersionUID = 9155874155769888368L;
    private final TRandom prng;
    private final SortedSet<TOut> set;
    private final NamedFunction<? super TRandom, ? extends TOut> function;
    private final CountDownLatch latch;
    private final int iterations;

    public GeneratorForkJoinTask(final TRandom prng, final SortedSet<TOut> set,
        final NamedFunction<? super TRandom, ? extends TOut> function,
        final CountDownLatch latch, final int iterations) {
      this.prng = prng;
      this.set = set;
      this.function = function;
      this.latch = latch;
      this.iterations = iterations;
    }

    @Override public Void getRawResult() {
      return null;
    }

    @Override protected void setRawResult(final Void value) {
      // No-op.
    }

    @Override protected boolean exec() {
      latch.countDown();
      Uninterruptibles.awaitUninterruptibly(latch);
      for (int i = 0; i < iterations; i++) {
        set.add(function.apply(prng));
      }
      return true;
    }
  }

}
