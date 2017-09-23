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
package io.github.pr0methean.betterrandom.seed;

import static org.testng.Assert.assertEquals;

import io.github.pr0methean.betterrandom.TestUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Unit test for the seed generator that connects to random.org to get seed data.
 *
 * @author Daniel Dyer
 */
public class RandomDotOrgSeedGeneratorTest {

  public static final int SMALL_REQUEST_SIZE = 32;

  @BeforeClass
  public void setUp() {
    if (!TestUtils.canRunRandomDotOrgLargeTest()) {
      RandomDotOrgSeedGenerator.setMaxRequestSize(SMALL_REQUEST_SIZE);
    }
  }

  @Test(timeOut = 120000)
  public void testGenerator() throws SeedException {
    SeedTestUtils.testGenerator(RandomDotOrgSeedGenerator.RANDOM_DOT_ORG_SEED_GENERATOR);
  }

  /**
   * Try to acquire a large number of bytes, more than are cached internally by the seed generator
   * implementation.
   */
  @Test(timeOut = 120000)
  public void testLargeRequest() throws SeedException {
    // Request more bytes than are cached internally.
    int seedLength = 1025;
    final SeedGenerator generator = RandomDotOrgSeedGenerator.RANDOM_DOT_ORG_SEED_GENERATOR;
    assertEquals(generator.generateSeed(seedLength).length, seedLength,
        "Failed to generate seed of length " + seedLength);
  }
}
