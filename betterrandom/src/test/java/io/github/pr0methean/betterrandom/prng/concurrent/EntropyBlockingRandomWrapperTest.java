package io.github.pr0methean.betterrandom.prng.concurrent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

import com.google.common.collect.ImmutableMap;
import io.github.pr0methean.betterrandom.prng.BaseRandom;
import io.github.pr0methean.betterrandom.seed.FailingSeedGenerator;
import io.github.pr0methean.betterrandom.seed.RandomSeederThread;
import io.github.pr0methean.betterrandom.seed.SeedException;
import io.github.pr0methean.betterrandom.seed.SeedGenerator;
import io.github.pr0methean.betterrandom.seed.SemiFakeSeedGenerator;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import org.mockito.Mockito;
import org.testng.annotations.Test;

public class EntropyBlockingRandomWrapperTest extends RandomWrapperRandomTest {
  private static final long DEFAULT_MAX_ENTROPY = -1000L;

  @Override public void testAllPublicConstructors()
      throws SeedException {
    mockDefaultSeedGenerator();
    try {
      for (final Constructor<?> constructor : getClassUnderTest().getDeclaredConstructors()) {
        final int modifiers = constructor.getModifiers();
        if (Modifier.isPublic(modifiers)) {
          constructor.setAccessible(true);
          final int nParams = constructor.getParameterCount();
          final Parameter[] parameters = constructor.getParameters();
          final Object[] constructorParams = new Object[nParams];
          try {
            for (int i = 0; i < nParams; i++) {
              if ("minimumEntropy".equals(parameters[i].getName())) {
                constructorParams[i] = DEFAULT_MAX_ENTROPY;
              } else {
                constructorParams[i] =
                    ((Map<Class<?>, Object>) ImmutableMap.copyOf(constructorParams()))
                        .get(parameters[i].getType());
              }
            }
            ((BaseRandom) constructor.newInstance(constructorParams)).nextInt();
          } catch (final IllegalAccessException | InstantiationException | InvocationTargetException | IllegalArgumentException e) {
            throw new AssertionError(String
                .format("Failed to call%n%s%nwith parameters%n%s", constructor.toGenericString(),
                    Arrays.toString(constructorParams)), e);
          }
        }
      }
    } finally {
      unmockDefaultSeedGenerator();
    }
  }

  @Test public void testGetSameThreadSeedGen() {
    SeedGenerator seedGen = getTestSeedGenerator();
    EntropyBlockingRandomWrapper random = new EntropyBlockingRandomWrapper(seedGen, 0L);
    assertSame(random.getSameThreadSeedGen(), seedGen);
  }

  @Override @Test public void testReseeding() {
    // TODO
    SeedGenerator seedGen = Mockito.spy(getTestSeedGenerator());
    EntropyBlockingRandomWrapper random = new EntropyBlockingRandomWrapper(seedGen, 0L);
    assertNull(random.getRandomSeeder());
    random.nextLong();
    Mockito.verify(seedGen, Mockito.atLeastOnce()).generateSeed(any(byte[].class));
    Mockito.verify(seedGen, Mockito.atMost(2)).generateSeed(any(byte[].class));
  }

  @Test public void testManualReseeding() {
    EntropyBlockingRandomWrapper random = new EntropyBlockingRandomWrapper(null, 0L);
    random.nextInt();
    random.setSeed(getTestSeedGenerator().generateSeed(8));
    random.nextInt();
    try {
      random.nextInt();
      fail("Expected an IllegalStateException");
    } catch (IllegalStateException expected) {}
  }

  @Test public void testRandomSeederThreadUsedFirst() {
    SeedGenerator seederSeedGen = Mockito.spy(getTestSeedGenerator());
    RandomSeederThread seeder = new RandomSeederThread(seederSeedGen);
    SeedGenerator sameThreadSeedGen
        = Mockito.spy(new SemiFakeSeedGenerator(new SplittableRandomAdapter()));
    EntropyBlockingRandomWrapper random = new EntropyBlockingRandomWrapper(sameThreadSeedGen, 0L);
    random.setRandomSeeder(seeder);
    try {
      assertEquals(random.getSameThreadSeedGen(), sameThreadSeedGen,
          "Same-thread seed generator changed after setting RandomSeederThread, when already non-null");
      random.nextLong();
      Mockito.verify(seederSeedGen, Mockito.atLeastOnce()).generateSeed(any(byte[].class));
      Mockito.verify(seederSeedGen, Mockito.atMost(2)).generateSeed(any(byte[].class));
      Mockito.verify(sameThreadSeedGen, Mockito.never()).generateSeed(any(byte[].class));
      Mockito.verify(sameThreadSeedGen, Mockito.never()).generateSeed(anyInt());
    } finally {
      random.setRandomSeeder(null);
      seeder.stopIfEmpty();
    }
  }

  @Test public void testFallbackFromRandomSeederThread() {
    SeedGenerator failingSeedGen = Mockito.spy(FailingSeedGenerator.FAILING_SEED_GENERATOR);
    RandomSeederThread seeder = new RandomSeederThread(failingSeedGen);
    SeedGenerator sameThreadSeedGen
        = Mockito.spy(new SemiFakeSeedGenerator(new SplittableRandomAdapter()));
    EntropyBlockingRandomWrapper random = new EntropyBlockingRandomWrapper(sameThreadSeedGen, 0L);
    random.setRandomSeeder(seeder);
    try {
      random.nextLong();
      Mockito.verify(sameThreadSeedGen, Mockito.atLeastOnce()).generateSeed(any(byte[].class));
      Mockito.verify(sameThreadSeedGen, Mockito.atMost(2)).generateSeed(any(byte[].class));
    } finally {
      random.setRandomSeeder(null);
      seeder.stopIfEmpty();
    }
  }

  @Test public void testSetSameThreadSeedGen() {
    SeedGenerator seedGen = Mockito.spy(getTestSeedGenerator());
    EntropyBlockingRandomWrapper random = new EntropyBlockingRandomWrapper(null, 0L);
    random.setSameThreadSeedGen(seedGen);
    assertSame(random.getSameThreadSeedGen(), seedGen);
    random.nextLong();
    Mockito.verify(seedGen, Mockito.atLeastOnce()).generateSeed(any(byte[].class));
  }
}