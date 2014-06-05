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
package org.trie4j.louds;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Assert;
import org.junit.Test;
import org.trie4j.AbstractTermIdTrieTest;
import org.trie4j.Node;
import org.trie4j.Trie;
import org.trie4j.patricia.simple.PatriciaTrie;
import org.trie4j.tail.ConcatTailArray;

public class TailLOUDSPPTrieWithConcatTailArrayTest extends AbstractTermIdTrieTest{
	@Override
	protected TailLOUDSPPTrie buildSecondTrie(Trie firstTrie) {
		return new TailLOUDSPPTrie(firstTrie, new ConcatTailArray());
	}

	@Test
	public void test() throws Exception{
		String[] words = {"こんにちは", "さようなら", "おはよう", "おおきなかぶ", "おおやまざき"};
		Trie trie = new PatriciaTrie();
		for(String w : words) trie.insert(w);
		
//		final SBVTailIndex ti = new SBVTailIndex();
		TailLOUDSPPTrie lt = new TailLOUDSPPTrie(trie);
//		System.out.println(lt.getBvTree());
//		System.out.println(ti.getSBV());
//		Algorithms.dump(lt.getRoot(), new OutputStreamWriter(System.out));
		for(String w : words){
			Assert.assertTrue(w, lt.contains(w));
		}
		Assert.assertFalse(lt.contains("おやすみなさい"));

		StringBuilder b = new StringBuilder();
		Node[] children = lt.getRoot().getChildren();
		for(Node n : children){
			char[] letters = n.getLetters();
			b.append(letters[0]);
		}
		Assert.assertEquals("おこさ", b.toString());
	}

	@Test
	public void test_save_load() throws Exception{
		String[] words = {"こんにちは", "さようなら", "おはよう", "おおきなかぶ", "おおやまざき"};
		Trie trie = new PatriciaTrie();
		for(String w : words) trie.insert(w);
		TailLOUDSPPTrie lt = new TailLOUDSPPTrie(trie);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(baos);
 		try {
			lt.writeExternal(oos);
			oos.flush();
			lt = new TailLOUDSPPTrie();
			lt.readExternal(new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray())));
			for(String w : words){
				Assert.assertTrue(lt.contains(w));
			}
			Assert.assertFalse(lt.contains("おやすみなさい"));
		} finally {
			oos.close();
		}

		StringBuilder b = new StringBuilder();
		Node[] children = lt.getRoot().getChildren();
		for(Node n : children){
			char[] letters = n.getLetters();
			b.append(letters[0]);
		}
		Assert.assertEquals("おこさ", b.toString());
	}
}
