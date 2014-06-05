package org.trie4j.test;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assume.assumeNotNull;

public class WikipediaTitlesTest {
	@Test
	public void test() throws Exception{
		assumeNotNull("download jawiki-20XXXXXX-all-titles-in-ns0.gz to `data` directory.",
				WikipediaTitles.instance());
		Iterable<String> itb = WikipediaTitles.instance();
		int len = 0;
		for(String w : itb){
			len += w.length();
		}
		itb = null;
		System.out.println(len);
		Assert.assertTrue(len > 100000);
	}
}
