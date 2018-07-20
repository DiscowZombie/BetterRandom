package io.github.pr0methean.betterrandom.seed;

public class FakeSeedGenerator implements SeedGenerator {

  private static final long serialVersionUID = 2310664903337315190L;
  private final String name;

  public FakeSeedGenerator() {
    this("FakeSeedGenerator");
  }

  /**
   * Creates a named instance.
   * @param name the name of this FakeSeedGenerator, returned by {@link #toString()}
   */
  public FakeSeedGenerator(final String name) {
    this.name = name;
  }

  @Override public void generateSeed(final byte[] output) throws SeedException {
    // No-op.
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    return this == o
        || (o instanceof FakeSeedGenerator
            && name.equals(o.name));
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }
}
