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

import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

/**
 * Unit test for the seed generator that uses {@link java.security.SecureRandom} to produce seed
 * data.
 *
 * @author Daniel Dyer
 */
public class SecureRandomSeedGeneratorTest extends SeedGeneratorTest<SecureRandomSeedGenerator> {

  @Test(timeOut = 15000) public void testGenerator() throws SeedException {
    SeedTestUtils.testGenerator(seedGenerator, true);
  }

  @Test(enabled = false)
  @Override public void testWithEqualsVerifier() {
    // No-op: doesn't pass because of isDefaultInstance.
  }

  @Test public void testIsWorthTrying() {
    // Should always be true
    assertTrue(seedGenerator.isWorthTrying());
  }

  @Override protected SecureRandomSeedGenerator initializeSeedGenerator() {
    return SecureRandomSeedGenerator.DEFAULT_INSTANCE;
  }
}
