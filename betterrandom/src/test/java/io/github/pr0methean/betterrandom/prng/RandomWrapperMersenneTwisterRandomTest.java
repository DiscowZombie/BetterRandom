package io.github.pr0methean.betterrandom.prng;

import io.github.pr0methean.betterrandom.seed.SeedException;
import java.lang.reflect.InvocationTargetException;
import org.testng.annotations.Test;

public class RandomWrapperMersenneTwisterRandomTest extends BaseRandomTest {

  @Override
  protected Class<? extends BaseRandom> getClassUnderTest() {
    return RandomWrapper.class;
  }

  @Override
  @Test(enabled = false)
  public void testAllPublicConstructors()
      throws SeedException, IllegalAccessException, InstantiationException, InvocationTargetException {
    // No-op: redundant to super insofar as it works.
  }

  @Override
  protected RandomWrapper tryCreateRng() throws SeedException {
    return new RandomWrapper(new MersenneTwisterRandom());
  }

  @Override
  protected RandomWrapper createRng(final byte[] seed) throws SeedException {
    return new RandomWrapper(new MersenneTwisterRandom(seed));
  }
}