package org.trie4j.doublearray;

import org.junit.Test;
import org.trie4j.Algorithms;
import org.trie4j.Node;
import org.trie4j.NodeVisitor;
import org.trie4j.patricia.simple.PatriciaTrie;
import org.trie4j.test.WikipediaTitles;

import static org.junit.Assume.assumeNotNull;

public class IterateDoubleArrayNodesTest {
	@Test
	public void test() throws Exception{
		assumeNotNull("download jawiki-20XXXXXX-all-titles-in-ns0.gz to `data` directory.",
				WikipediaTitles.instance());
		Algorithms.traverseByBreadth(
				new DoubleArray(WikipediaTitles.instance().insertTo(new PatriciaTrie())).getRoot(),
				new NodeVisitor() {
					@Override
					public boolean visit(Node node, int nest) {
						return true;
					}
				});
	}
}
