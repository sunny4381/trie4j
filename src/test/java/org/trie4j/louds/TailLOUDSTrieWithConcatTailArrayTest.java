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

public class TailLOUDSTrieWithConcatTailArrayTest extends AbstractTermIdTrieTest{
	@Override
	protected TailLOUDSTrie buildSecondTrie(Trie firstTrie) {
		return new TailLOUDSTrie(firstTrie, new ConcatTailArray(firstTrie.size()));
	}

	@Test
	public void test() throws Exception{
		String[] words = {"こんにちは", "さようなら", "おはよう", "おおきなかぶ", "おおやまざき"};
		Trie trie = new PatriciaTrie();
		for(String w : words) trie.insert(w);
		Trie lt = new TailLOUDSTrie(trie);
//		System.out.println(lt.getBv());
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
		TailLOUDSTrie lt = new TailLOUDSTrie(trie);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		lt.writeExternal(oos);
		oos.flush();
		lt = new TailLOUDSTrie();
		lt.readExternal(new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray())));
		for(String w : words){
			Assert.assertTrue(lt.contains(w));
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
}
