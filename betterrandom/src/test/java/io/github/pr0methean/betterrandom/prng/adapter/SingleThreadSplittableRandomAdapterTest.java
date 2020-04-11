package io.github.pr0methean.betterrandom.prng.adapter;

import static io.github.pr0methean.betterrandom.prng.RandomTestUtils.assertEquivalent;

import com.google.common.testing.SerializableTester;
import io.github.pr0methean.betterrandom.TestingDeficiency;
import io.github.pr0methean.betterrandom.prng.BaseRandom;
import io.github.pr0methean.betterrandom.prng.BaseRandomTest;
import io.github.pr0methean.betterrandom.seed.SeedException;
import org.testng.annotations.Test;

public class SingleThreadSplittableRandomAdapterTest extends BaseRandomTest<BaseSplittableRandomAdapter> {

  @Override protected Class<? extends BaseSplittableRandomAdapter> getClassUnderTest() {
    return SingleThreadSplittableRandomAdapter.class;
  }

  @Override @Test(enabled = false) public void testThreadSafety() {
    // No-op because this class isn't thread-safe.
  }

  /**
   * {@inheritDoc} Overridden in subclasses, so that subclassing the test can test the subclasses.
   */
  @Override protected BaseSplittableRandomAdapter createRng() throws SeedException {
    return new SingleThreadSplittableRandomAdapter(getTestSeedGenerator());
  }

  @Override protected BaseSplittableRandomAdapter createRng(final byte[] seed) throws SeedException {
    final BaseSplittableRandomAdapter adapter = createRng();
    adapter.setSeed(seed);
    return adapter;
  }

  @Test public void testGetSplittableRandom() {
    createRng().getSplittableRandom().nextLong();
  }

  @TestingDeficiency @Override @Test public void testSerializable() throws SeedException {
    final BaseSplittableRandomAdapter adapter = createRng();
    // May change when serialized and deserialized, but deserializing twice should yield same object
    // and deserialization should be idempotent
    final BaseSplittableRandomAdapter adapter2 = SerializableTester.reserialize(adapter);
    final BaseSplittableRandomAdapter adapter3 = SerializableTester.reserialize(adapter);
    // final BaseSplittableRandomAdapter adapter4 = SerializableTester.reserialize(adapter2);
    assertEquivalent(adapter2, adapter3, 20, "Deserializing twice doesn't yield equivalent instance");
    // FIXME Failing:
    // assertEquivalent(adapter2, adapter4, 20, "Serialization round-trip is not idempotent");
  }

  @Override public void testSetSeedLong() throws SeedException {
    final BaseRandom rng = createRng();
    final BaseRandom rng2 = createRng();
    rng.nextLong(); // ensure they won't both be in initial state before reseeding
    rng.setSeed(0x0123456789ABCDEFL);
    rng2.setSeed(0x0123456789ABCDEFL);
    assertEquivalent(rng, rng2, 20, "Output mismatch after reseeding with same seed");
  }

  @Override @Test public void testNullSeed() {
    // No-op.
  }

  @Override @Test(enabled = false) public void testEquals() {
    // No-op.
  }

  @Override @Test(enabled = false) public void testHashCode() {
    // No-op.
  }
}
