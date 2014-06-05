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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import org.trie4j.AbstractTrie;
import org.trie4j.Node;
import org.trie4j.Trie;
import org.trie4j.bv.BytesSuccinctBitVector;
import org.trie4j.tail.TailBuilder;
import org.trie4j.tail.TailCharIterator;
import org.trie4j.tail.TailUtil;
import org.trie4j.tail.builder.SuffixTrieTailBuilder;
import org.trie4j.util.Pair;

public class InlinedTailLOUDSTrie
extends AbstractTrie
implements Externalizable, Trie {
	public InlinedTailLOUDSTrie(){
		bv = new BytesSuccinctBitVector(0);
	}

	public InlinedTailLOUDSTrie(Trie orig){
		this(orig, new SuffixTrieTailBuilder());
	}

	public InlinedTailLOUDSTrie(Trie orig, TailBuilder tb){
		this(orig, tb, new BytesSuccinctBitVector(orig.size() * 2));
	}

	public InlinedTailLOUDSTrie(Trie orig, TailBuilder tb, BytesSuccinctBitVector bv){
		this.bv = bv;
		size = orig.size();
		labels = new char[size];
		tail = new int[size];
		term = new BitSet(size);
		LinkedList<Node> queue = new LinkedList<Node>();
		int count = 0;
		if(orig.getRoot() != null) queue.add(orig.getRoot());
		while(!queue.isEmpty()){
			Node node = queue.pollFirst();
			int index = count++;
			if(index >= labels.length){
				extend();
			}
			if(node.isTerminate()) term.set(index);
			for(Node c : node.getChildren()){
				bv.append1();
				queue.offerLast(c);
			}
			bv.append0();
			char[] letters = node.getLetters();
			if(letters.length == 0){
				labels[index] = 0xffff;
				tail[index] = -1;
			} else{
				labels[index] = letters[0];
				if(letters.length >= 2){
					tail[index] = tb.insert(letters, 1, letters.length - 1);
				} else{
					tail[index] = -1;
				}
			}
		}
		nodeSize = count;
		tails = tb.getTails();
	}

	public BytesSuccinctBitVector getBv() {
		return bv;
	}

	@Override
	public Node getRoot(){
		return new LOUDSNode(0);
	}

	@Override
	public void dump(Writer writer) throws IOException{
		super.dump(writer);
		String bvs = bv.toString();
		writer.write("bitvec: " + ((bvs.length() > 100) ? bvs.substring(0, 100) : bvs));
		writer.write("\nlabels: ");
		int count = 0;
		for(char c : labels){
			writer.write(c);
			if(count++ == 99) break;
		}
		writer.write("\n");
	}

	@Override
	public boolean contains(String text){
		int nodeId = 0; // root
		TailCharIterator it = new TailCharIterator(tails, -1);
		int n = text.length();
		for(int i = 0; i < n; i++){
			nodeId = getChildNode(nodeId, text.charAt(i));
			if(nodeId == -1) return false;
			it.setIndex(tail[nodeId]);
			while(it.hasNext()){
				i++;
				if(i == n) return false;
				if(text.charAt(i) != it.next()) return false;
			}
		}
		return term.get(nodeId);
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public Iterable<String> commonPrefixSearch(String query) {
		List<String> ret = new ArrayList<String>();
		char[] chars = query.toCharArray();
		int charsLen = chars.length;
		int nodeId = 0; // root
		TailCharIterator tci = new TailCharIterator(tails, -1);
		for(int charsIndex = 0; charsIndex < charsLen; charsIndex++){
			int child = getChildNode(nodeId, chars[charsIndex]);
			if(child == -1) return ret;
			tci.setIndex(tail[child]);
			while(tci.hasNext()){
				charsIndex++;
				if(charsLen <= charsIndex) return ret;
				if(chars[charsIndex] != tci.next()) return ret;
			}
			if(term.get(child)){
				ret.add(new String(chars, 0, charsIndex + 1));
			}
			nodeId = child;
		}
		return ret;
	}

	@Override
	public Iterable<String> predictiveSearch(String query) {
		List<String> ret = new ArrayList<String>();
		char[] chars = query.toCharArray();
		int charsLen = chars.length;
		int nodeId = 0; // root
		TailCharIterator tci = new TailCharIterator(tails, -1);
		String pfx = null;
		int charsIndexBack = 0;
		for(int charsIndex = 0; charsIndex < charsLen; charsIndex++){
			charsIndexBack = charsIndex;
			int child = getChildNode(nodeId, chars[charsIndex]);
			if(child == -1) return ret;
			tci.setIndex(tail[child]);
			while(tci.hasNext()){
				charsIndex++;
				if(charsIndex >= charsLen) break;
				if(chars[charsIndex] != tci.next()) return ret;
			}
			nodeId = child;
		}
		pfx = new String(chars, 0, charsIndexBack);

		Deque<Pair<Integer, String>> queue = new LinkedList<Pair<Integer,String>>();
		queue.offerLast(Pair.create(nodeId, pfx));
		while(queue.size() > 0){
			Pair<Integer, String> element = queue.pollFirst();
			int nid = element.getFirst();

			StringBuilder b = new StringBuilder(element.getSecond());
			b.append(labels[nid]);
			tci.setIndex(tail[nid]);
			while(tci.hasNext()) b.append(tci.next());
			String letter = b.toString();
			if(term.get(nid)) ret.add(letter);
			int s = bv.select0(nid) + 1;
			int e = bv.next0(s);
			int lastNodeId = bv.rank1(s) + e - s - 1;
			for(int i = (e - 1); i >= s; i--){
				queue.offerFirst(Pair.create(lastNodeId--, letter));
			}
		}
		return ret;
	}

	@Override
	public void insert(String word) {
		throw new UnsupportedOperationException();
	}

	public class LOUDSNode implements Node{
		public LOUDSNode(int nodeId) {
			this.nodeId = nodeId;
		}
		public int getId(){
			return nodeId;
		}
		@Override
		public char[] getLetters() {
			StringBuilder b = new StringBuilder();
			char h = labels[nodeId];
			if(h != 0xffff){
				b.append(h);
			}
			int ti = tail[nodeId];
			if(ti != -1){
				TailUtil.appendChars(tails, ti, b);
			}
			return b.toString().toCharArray();
		}
		@Override
		public boolean isTerminate() {
			return term.get(nodeId);
		}
		@Override
		public Node getChild(char c) {
			int nid = getChildNode(nodeId, c);
			if(nid == -1) return null;
			else return new LOUDSNode(nid);
		}
		@Override
		public Node[] getChildren() {
			int start = 0;
			if(nodeId > 0){
				start = bv.select0(nodeId) + 1;
			}
			int end = bv.next0(start);
			int ci = bv.rank1(start);
			int n = end - start;
			Node[] children = new Node[n];
			for(int i = 0; i < n; i++){
				children[i] = new LOUDSNode(ci + i);
			}
			return children;
		}

		private int nodeId;
	}

	public void trimToSize(){
		if(labels.length > nodeSize){
			labels = Arrays.copyOf(labels, nodeSize);
			tail = Arrays.copyOf(tail, nodeSize);
		}
		bv.trimToSize();
	}

	@Override
	public void readExternal(ObjectInput in)
	throws IOException, ClassNotFoundException {
		size = in.readInt();
		nodeSize = in.readInt();
		labels = new char[nodeSize];
		for(int i = 0; i < nodeSize; i++){
			labels[i] = in.readChar();
		}
		tail = new int[nodeSize];
		for(int i = 0; i < nodeSize; i++){
			tail[i] = in.readInt();
		}
		int ts = in.readInt();
		StringBuilder b = new StringBuilder(ts);
		for(int i = 0; i < ts; i++){
			b.append(in.readChar());
		}
		tails = b;
		term = (BitSet)in.readObject();
		bv.readExternal(in);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(size);
		out.writeInt(nodeSize);
		trimToSize();
		for(char c : labels){
			out.writeChar(c);
		}
		for(int i : tail){
			out.writeInt(i);
		}
		out.writeInt(tails.length());
		for(int i = 0; i < tails.length(); i++){
			out.writeChar(tails.charAt(i));
		}
		out.writeObject(term);
		bv.writeExternal(out);
	}

	private int getChildNode(int nodeId, char c){
		int start = bv.select0(nodeId) + 1;
		int end = bv.next0(start);
		if(end == -1) return -1;
		int pos2Id = bv.rank1(start) - start;
		if((end - start) <= 16){
			for(int i = start; i < end; i++){
				int index = i + pos2Id;
				int d = c - labels[index];
				if(d == 0){
					return index;
				}
			}
			return -1;
		} else{
			do{
				int i = (start + end) / 2;
				int index = i + pos2Id;
				int d = c - labels[index];
				if(d < 0){
					end = i;
				} else if(d > 0){
					if(start == i) return -1;
					else start = i;
				} else{
					return index;
				}
			} while(start != end);
			return -1;
		}
	}

	private void extend(){
		int nsz = (int)(labels.length * 1.2);
		if(nsz <= labels.length) nsz = labels.length * 2 + 1;
		labels = Arrays.copyOf(labels, nsz);
		tail = Arrays.copyOf(tail, nsz);
	}

	private BytesSuccinctBitVector bv;
	private int size;
	private char[] labels;
	private int[] tail;
	private CharSequence tails;
	private BitSet term;
	private int nodeSize;
}
