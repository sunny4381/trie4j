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
package org.trie4j;

import java.io.PrintWriter;

import org.junit.Test;
import org.trie4j.patricia.simple.MapPatriciaTrie;
import org.trie4j.test.LapTimer;
import org.trie4j.test.WikipediaTitles;

import static org.junit.Assume.assumeNotNull;

public class AbstractMapTrieWikipediaTest extends AbstractWikipediaTest{
	@Override
	protected MapTrie<Integer> createFirstTrie(){
		return new MapPatriciaTrie<Integer>();
	}

	protected MapTrie<Integer> buildSecondTrie(MapTrie<Integer> firstTrie){
		return firstTrie;
	}

	@Override
	@SuppressWarnings("unchecked")
	protected final MapTrie<Integer> buildSecondTrie(Trie firstTrie){
		return (MapTrie<Integer>)buildSecondTrie((MapTrie<Integer>)firstTrie);
	}

	protected void afterVerification(Trie trie) throws Exception{
	}

	@Test
	public void test() throws Exception{
		assumeNotNull("download jawiki-20XXXXXX-all-titles-in-ns0.gz to `data` directory.",
				WikipediaTitles.instance());
		MapTrie<Integer> trie = createFirstTrie();
		System.out.println("building first trie: " + trie.getClass().getName());
		int c = 0, chars = 0;
		long b = 0;
		LapTimer t = new LapTimer();
		for(String word : WikipediaTitles.instance()){
			try{
				t.reset();
				trie.insert(word, c);
				b += t.lapNanos();
			} catch(Exception e){
				System.out.println("exception at " + c + "th word: " + word);
				trie.dump(new PrintWriter(System.out));
				throw e;
			}
			c++;
			chars += word.length();
		}
		System.out.println(String.format("done in %d millis with %d words and %d chars."
				, (b / 1000000), c, chars));

		MapTrie<Integer> second = buildSecondTrie(trie);
		try{
			getClass().getDeclaredMethod("buildSecondTrie", Trie.class);
			System.out.print("building second trie: ");
			t.reset();
			second = buildSecondTrie(trie);
			System.out.println("done in " + t.lapMillis() + "millis.");
		} catch(NoSuchMethodException e){}

		System.out.println("verifying trie.");
		long sum = 0;
		c = 0;
		for(String word : WikipediaTitles.instance()){
			t.reset();
			boolean found = (int)second.get(word) == c;
			sum += t.lapNanos();
			c++;
			if(!found){
				System.out.println(String.format(
						"verification failed.  trie not contains %d th word: [%s]"
						+ " with id: [%d] actual: [%d]."
						, c, word, c, second.get(word)));
				break;
			}
		}
		System.out.println("done in " + (sum / 1000000) + " millis with " + c + " words.");
		afterVerification(second);
	}
}
