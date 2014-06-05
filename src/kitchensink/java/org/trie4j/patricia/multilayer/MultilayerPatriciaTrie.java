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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.trie4j.AbstractTrie;
import org.trie4j.Trie;
import org.trie4j.NodeVisitor;
import org.trie4j.patricia.multilayer.labeltrie.LabelTrie;
import org.trie4j.patricia.multilayer.node.CharsNode;
import org.trie4j.patricia.multilayer.node.TerminalCharsNode;

public class MultilayerPatriciaTrie extends AbstractTrie implements Trie{
	@Override
	public int size() {
		return size;
	}

	public Node getRoot(){
		return root;
	}

	public LabelTrie getLabelTrie() {
		return labelTrie;
	}

	public boolean contains(String word) {
		return root.contains(word.toCharArray(), 0);
	}
//*
	@Override
	public Iterable<String> commonPrefixSearch(final String query) {
		if(query.length() == 0) return new ArrayList<String>(0);
		return new Iterable<String>(){
			{
				this.queryChars = query.toCharArray();
			}
			private char[] queryChars;
			@Override
			public Iterator<String> iterator() {
				return new Iterator<String>() {
					private int cur;
					private StringBuilder currentChars = new StringBuilder();
					private Node current = root;
					private String next;
					{
						cur = 0;
						if(root != null) findNext();
					}
					private void findNext(){
						next = null;
						do{
							if(queryChars.length <= cur) return;
							Node child = current.getChild(queryChars[cur]);
							if(child == null) return;
							int rest = queryChars.length - cur;
							char[] letters = child.getLetters();
							int len = letters.length;
							if(rest < len) return;
							for(int i = 1; i < len; i++){
								int c = letters[i] - queryChars[cur + i];
								if(c != 0) return;
							}

							String b = new String(queryChars, cur, len);
							if(child.isTerminate()){
								next = currentChars + b;
							}
							cur += len;
							currentChars.append(b);
							current = child;
						} while(next == null);
					}
					@Override
					public boolean hasNext() {
						return next != null;
					}
					@Override
					public String next() {
						String ret = next;
						if(ret == null){
							throw new NoSuchElementException();
						}
						findNext();
						return ret;
					}
					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}
/*/
	@Override
	public Iterable<String> commonPrefixSearch(String query) {
		List<String> ret = new ArrayList<String>();
		char[] queryChars = query.toCharArray();
		int cur = 0;
		Node node = root;
		while(node != null){
			char[] letters = node.getLetters();
			if(letters.length > (queryChars.length - cur)) return ret;
			for(int i = 0; i < letters.length; i++){
				if(letters[i] != queryChars[cur + i]) return ret;
			}
			if(node.isTerminate()){
				ret.add(new String(queryChars, 0 , cur + letters.length));
			}
			cur += letters.length;
			if(queryChars.length == cur) return ret;
			node = node.getChild(queryChars[cur]);
		}
		return ret;
	}
//*/
	private static void enumLetters(org.trie4j.Node node, String prefix, List<String> letters){
		org.trie4j.Node[] children = node.getChildren();
		if(children == null) return;
		for(org.trie4j.Node child : children){
			String text = prefix + new String(child.getLetters());
			if(child.isTerminate()) letters.add(text);
			enumLetters(child, text, letters);
		}
	}

	@Override
	public Iterable<String> predictiveSearch(String prefix) {
		char[] queryChars = prefix.toCharArray();
		int cur = 0;
		Node node = root;
		while(node != null){
			char[] letters = node.getLetters();
			int n = Math.min(letters.length, queryChars.length - cur);
			for(int i = 0; i < n; i++){
				if(letters[i] != queryChars[cur + i]){
					return Collections.emptyList();
				}
			}
			cur += n;
			if(queryChars.length == cur){
				List<String> ret = new ArrayList<String>();
				prefix += new String(letters, n, letters.length - n);
				if(node.isTerminate()) ret.add(prefix);
				enumLetters(node, prefix, ret);
				return ret;
			}
			node = node.getChild(queryChars[cur]);
		}
		return Collections.emptyList();
	}
	
	public void insert(String text){
		if(labelTrie != null){
			throw new IllegalStateException("insert after pack is not supported.");
		}
		char[] letters = text.toCharArray();
		if(root == null){
			root = new TerminalCharsNode(letters);
		} else{
			Node newRoot = root.insertChild(letters, 0);
			if(newRoot != null){
				root = newRoot;
			}
		}
		size++;
	}

	public void pack(){
		if(labelTrie != null) return;
		labelTrie = new LabelTrie();
		root.pushLabel(labelTrie);
		labelTrie.pargeChildren();
	}

	public void visit(NodeVisitor visitor){
		root.visit(visitor, 0);
	}

	private int size;
	private Node root = new CharsNode(new char[]{}) ;
	private LabelTrie labelTrie;
}
