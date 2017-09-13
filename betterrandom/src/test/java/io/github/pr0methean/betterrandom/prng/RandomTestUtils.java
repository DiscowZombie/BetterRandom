// ============================================================================
//   Copyright 2006-2012 Daniel W. Dyer
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
// ============================================================================
package io.github.pr0methean.betterrandom.prng;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import io.github.pr0methean.betterrandom.seed.DefaultSeedGenerator;
import io.github.pr0methean.betterrandom.seed.RandomSeederThread;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.BaseStream;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.testng.Reporter;

/**
 * Provides methods used for testing the operation of RNG implementations.
 *
 * @author Daniel Dyer
 */
public final class RandomTestUtils {

  public static final RandomSeederThread DEFAULT_SEEDER =
      RandomSeederThread.getInstance(DefaultSeedGenerator.DEFAULT_SEED_GENERATOR);

  private RandomTestUtils() {
    // Prevents instantiation of utility class.
  }

  /**
   * @param origin Minimum expected value, inclusive.
   * @param bound Maximum expected value, exclusive.
   * @param checkEntropy
   */
  public static void checkRangeAndEntropy(
      BaseRandom prng,
      long expectedEntropySpent,
      Supplier<? extends Number> numberSupplier,
      double origin,
      double bound, boolean checkEntropy) {
    long oldEntropy = prng.entropyBits();
    Number output = numberSupplier.get();
    assertTrue(output.doubleValue() >= origin);
    assertTrue(output.doubleValue() < bound);
    if (checkEntropy) {
      assertEquals(oldEntropy - expectedEntropySpent, prng.entropyBits());
    }
  }

  /**
   * @param expectedCount Negative for an endless stream.
   * @param origin Minimum expected value, inclusive.
   * @param bound Maximum expected value, exclusive.
   */
  public static void checkStream(
      BaseRandom prng,
      long maxEntropySpentPerNumber,
      BaseStream<? extends Number, ?> stream,
      long expectedCount,
      double origin,
      double bound,
      boolean checkEntropyCount) {
    long expectedMinEntropy = prng.entropyBits();
    long count = 0;
    for (Iterator<? extends Number> streamIter = stream.iterator(); streamIter.hasNext(); ) {
      count++;
      if (expectedCount < 0) {
        if (count > 20) {
          break;
        }
      } else {
        assertTrue(count <= expectedCount);
      }
      Number number = streamIter.next();
      assertGreaterOrEqual(origin, number.doubleValue());
      assertLess(bound, number.doubleValue());
    }
    if (expectedCount >= 0 && checkEntropyCount) {
      expectedMinEntropy -= maxEntropySpentPerNumber * expectedCount;
      assertGreaterOrEqual(expectedMinEntropy, prng.entropyBits());
    }

  }

  public static void assertGreaterOrEqual(long expected, long actual) {
    if (actual < expected) {
      throw new AssertionError(
          String.format("Expected at least %d but found %d", expected, actual));
    }
  }

  public static void assertGreaterOrEqual(double expected, double actual) {
    if (actual < expected) {
      throw new AssertionError(
          String.format("Expected at least %f but found %f", expected, actual));
    }
  }

  public static void assertLess(double expected, double actual) {
    if (actual >= expected) {
      throw new AssertionError(
          String.format("Expected less than %f but found %f", expected, actual));
    }
  }

  /**
   * Test that the given parameterless constructor, called twice, doesn't produce RNGs that compare
   * as equal. Also checks for compliance with basic parts of the Object.equals() contract.
   */
  @SuppressWarnings({"EqualsWithItself", "ObjectEqualsNull", "argument.type.incompatible"})
  public static void doEqualsSanityChecks(final Supplier<? extends Random> ctor) {
    final Random rng = ctor.get();
    final Random rng2 = ctor.get();
    assert !(rng.equals(rng2));
    assert rng.equals(rng) : "RNG doesn't compare equal to itself";
    assert !(rng.equals(null)) : "RNG compares equal to null";
    assert !(rng.equals(new Random())) : "RNG compares equal to new Random()";
  }

  /**
   * Test that in a sample of 100 RNGs from the given parameterless constructor, there are at least
   * 90 unique hash codes.
   */
  public static boolean testHashCodeDistribution(final Supplier<? extends Random> ctor) {
    final HashSet<Integer> uniqueHashCodes = new HashSet<>();
    for (int i = 0; i < 25; i++) {
      uniqueHashCodes.add(ctor.get().hashCode());
    }
    return uniqueHashCodes.size() >= 20;
  }

  /**
   * Test to ensure that two distinct RNGs with the same seed return the same sequence of numbers
   * and compare as equal.
   *
   * @param rng1 The first RNG.  Its output is compared to that of {@code rng2}.
   * @param rng2 The second RNG.  Its output is compared to that of {@code rng1}.
   * @param iterations The number of values to generate from each RNG and compare.
   * @return true if the two RNGs produce the same sequence of values, false otherwise.
   */
  public static boolean testEquivalence(final Random rng1,
      final Random rng2,
      final int iterations) {
    for (int i = 0; i < iterations; i++) {
      if (rng1.nextInt() != rng2.nextInt()) {
        return false;
      }
    }
    return true;
  }

  /**
   * This is a rudimentary check to ensure that the output of a given RNG is approximately uniformly
   * distributed.  If the RNG output is not uniformly distributed, this method will return a poor
   * estimate for the value of pi.
   *
   * @param rng The RNG to test.
   * @param iterations The number of random points to generate for use in the calculation.  This
   *     value needs to be sufficiently large in order to produce a reasonably accurate result
   *     (assuming the RNG is uniform). Less than 10,000 is not particularly useful.  100,000 should
   *     be sufficient.
   * @return An approximation of pi generated using the provided RNG.
   */
  public static double calculateMonteCarloValueForPi(final Random rng,
      final int iterations) {
    // Assumes a quadrant of a circle of radius 1, bounded by a box with
    // sides of length 1.  The area of the square is therefore 1 square unit
    // and the area of the quadrant is (pi * r^2) / 4.
    int totalInsideQuadrant = 0;
    // Generate the specified number of random points and count how many fall
    // within the quadrant and how many do not.  We expect the number of points
    // in the quadrant (expressed as a fraction of the total number of points)
    // to be pi/4.  Therefore pi = 4 * ratio.
    for (int i = 0; i < iterations; i++) {
      final double x = rng.nextDouble();
      final double y = rng.nextDouble();
      if (isInQuadrant(x, y)) {
        ++totalInsideQuadrant;
      }
    }
    // From these figures we can deduce an approximate value for Pi.
    return 4 * ((double) totalInsideQuadrant / iterations);
  }

  /**
   * Uses Pythagoras' theorem to determine whether the specified coordinates fall within the area of
   * the quadrant of a circle of radius 1 that is centered on the origin.
   *
   * @param x The x-coordinate of the point (must be between 0 and 1).
   * @param y The y-coordinate of the point (must be between 0 and 1).
   * @return True if the point is within the quadrant, false otherwise.
   */
  private static boolean isInQuadrant(final double x, final double y) {
    final double distance = Math.sqrt((x * x) + (y * y));
    return distance <= 1;
  }

  /**
   * Generates a sequence of values from a given random number generator and then calculates the
   * standard deviation of the sample.
   *
   * @param rng The RNG to use.
   * @param maxValue The maximum value for generated integers (values will be in the range [0,
   *     maxValue)).
   * @param iterations The number of values to generate and use in the standard deviation
   *     calculation.
   * @return The standard deviation of the generated sample.
   */
  public static double calculateSampleStandardDeviation(final Random rng,
      final int maxValue,
      final int iterations) {
    final DescriptiveStatistics stats = new DescriptiveStatistics();
    for (int i = 0; i < iterations; i++) {
      stats.addValue(rng.nextInt(maxValue));
    }
    return stats.getStandardDeviation();
  }

  @SuppressWarnings("unchecked")
  public static <T extends Serializable> T serializeAndDeserialize(final T object) {
    try (
        ByteArrayOutputStream byteOutStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutStream = new ObjectOutputStream(byteOutStream)) {
      objectOutStream.writeObject(object);
      final byte[] serialCopy = byteOutStream.toByteArray();
      // Read the object back-in.
      try (ObjectInputStream objectInStream = new ObjectInputStream(
          new ByteArrayInputStream(serialCopy))) {
        return (T) (objectInStream.readObject());
      }
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings({"unchecked", "ObjectEquality"})
  public static <T extends Random> void assertEquivalentWhenSerializedAndDeserialized(final T rng) {
    final T rng2 = serializeAndDeserialize(rng);
    assert rng != rng2 : "Deserialised RNG should be distinct object.";
    // Both RNGs should generate the same sequence.
    assert testEquivalence(rng, rng2, 20) : "Output mismatch after serialisation.";
  }

  public static void assertStandardDeviationSane(final Random rng) {
    // Expected standard deviation for a uniformly distributed population of values in the range 0..n
    // approaches n/sqrt(12).
    final int n = 100;
    final double observedSD = calculateSampleStandardDeviation(rng, n, 10000);
    final double expectedSD = n / Math.sqrt(12);
    Reporter.log("Expected SD: " + expectedSD + ", observed SD: " + observedSD);
    assertEquals(observedSD, expectedSD, 0.02 * expectedSD,
        "Standard deviation is outside acceptable range: " + observedSD);
  }

  public static void assertMonteCarloPiEstimateSane(final Random rng) {
    assertMonteCarloPiEstimateSane(rng, 100000);
  }

  public static void assertMonteCarloPiEstimateSane(
      final Random rng, final int iterations) {
    final double pi = calculateMonteCarloValueForPi(rng, iterations);
    Reporter.log("Monte Carlo value for Pi: " + pi);
    assertEquals(pi, Math.PI, 0.01 * Math.PI,
        "Monte Carlo value for Pi is outside acceptable range:" + pi);
  }
}
