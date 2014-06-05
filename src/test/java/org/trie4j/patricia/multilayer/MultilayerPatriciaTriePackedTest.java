/*
 * Copyright 2012 Takao Nakaguchi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.trie4j.patricia.multilayer;

import org.junit.Assert;
import org.junit.Test;
import org.trie4j.AbstractTrieTest;
import org.trie4j.Trie;

public class MultilayerPatriciaTriePackedTest extends AbstractTrieTest{
	@Override
	protected Trie createFirstTrie() {
		return new MultilayerPatriciaTrie();
	}

	@Override
	protected Trie buildSecondTrie(Trie firstTrie) {
		((MultilayerPatriciaTrie)firstTrie).pack();
		return firstTrie;
	}

	@Test
	@Override
	public void test_contains_1() throws Exception{
		try {
			super.test_contains_1();
			Assert.fail("unreachable here");
		} catch (IllegalArgumentException e) {
			// this exception is expected.
		}
	}

	@Test
	@Override
	public void test_size_5() throws Exception{
		try {
			super.test_size_5();
			Assert.fail("unreachable here");
		} catch (IllegalArgumentException e) {
			// this exception is expected.
		}
	}
}
