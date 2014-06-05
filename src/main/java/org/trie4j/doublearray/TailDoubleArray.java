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
package org.trie4j.doublearray;

import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.trie4j.AbstractTermIdTrie;
import org.trie4j.Node;
import org.trie4j.TermIdNode;
import org.trie4j.TermIdTrie;
import org.trie4j.Trie;
import org.trie4j.bv.Rank1OnlySuccinctBitVector;
import org.trie4j.bv.SuccinctBitVector;
import org.trie4j.tail.FastTailCharIterator;
import org.trie4j.tail.TailBuilder;
import org.trie4j.tail.TailCharIterator;
import org.trie4j.tail.TailUtil;
import org.trie4j.tail.builder.SuffixTrieTailBuilder;
import org.trie4j.util.BitSet;
import org.trie4j.util.FastBitSet;
import org.trie4j.util.Pair;

public class TailDoubleArray extends AbstractTermIdTrie implements TermIdTrie, Externalizable{
	public static interface TermNodeListener{
		void listen(Node node, int nodeIndex);
	}

	public TailDoubleArray(){
	}

	public TailDoubleArray(Trie orig){
		this(orig, new SuffixTrieTailBuilder());
	}

	public TailDoubleArray(Trie orig, TailBuilder tb){
		this(orig, tb, new TermNodeListener(){
			@Override
			public void listen(Node node, int nodeIndex) {
			}
		});
	}

	public TailDoubleArray(Trie orig, TailBuilder tb, TermNodeListener listener){
		size = orig.size();
		int as = size;
		if(as <= 1) as = 2;
		base = new int[as];
		Arrays.fill(base, BASE_EMPTY);
		check = new int[as];
		Arrays.fill(check, -1);
		tail = new int[as];
		Arrays.fill(tail, -1);
		Arrays.fill(charToCode, (char)0);

		FastBitSet bs = new FastBitSet(65536);
		build(orig.getRoot(), 0, tb, bs, listener);
		term = new Rank1OnlySuccinctBitVector(bs.getBytes(), bs.size());
		tails = tb.getTails();
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public TermIdNode getRoot() {
		return new TailDoubleArrayNode(0);
	}

	public int[] getBase(){
		return base;
	}

	public int[] getCheck(){
		return check;
	}

	public BitSet getTerm() {
		return term;
	}

	private class TailDoubleArrayNode implements TermIdNode{
		public TailDoubleArrayNode(int nodeId){
			this.nodeId = nodeId;
		}

		public TailDoubleArrayNode(int nodeId, char firstChar){
			this.nodeId = nodeId;
			this.firstChar = firstChar;
		}

		@Override
		public boolean isTerminate() {
			return term.get(nodeId);
		}

		@Override
		public int getTermId() {
			return term.get(nodeId) ? term.rank1(nodeId) - 1 : -1;
		}

		@Override
		public char[] getLetters() {
			StringBuilder ret = new StringBuilder();
			ret.append(firstChar);
			TailUtil.appendChars(tails, tail[nodeId],ret);
			return ret.toString().toCharArray();
		}

		@Override
		public TermIdNode[] getChildren() {
			List<TermIdNode> ret = new ArrayList<TermIdNode>();
			int b = base[nodeId];
			for(char c : chars){
				int code = charToCode[c];
				int nid = b + code;
				if(nid >= 0 && nid < check.length && check[nid] == nodeId){
					ret.add(new TailDoubleArrayNode(nid, c));
				}
			}
			return ret.toArray(emptyNodes);
		}

		@Override
		public TailDoubleArrayNode getChild(char c) {
			int code = charToCode[c];
			if(code == -1) return null;
			int nid = base[nodeId] + c;
			if(nid >= 0 && nid < check.length && check[nid] == nodeId) return new TailDoubleArrayNode(nid, c);
			return null;
		}

		private char firstChar = 0;
		private int nodeId;
	}

	@Override
	public int getTermId(String text) {
		int nodeIndex = 0; // root
		FastTailCharIterator it = new FastTailCharIterator(tails, -1);
		int n = text.length();
		for(int i = 0; i < n; i++){
			char cid = charToCode[text.charAt(i)];
			if(cid == 0) return -1;
			int next = base[nodeIndex] + cid;
			if(next < 0 || check[next] != nodeIndex) return -1;
			nodeIndex = next;
			int ti = tail[nodeIndex];
			if(ti == -1) continue;
			it.setIndex(ti);
			char c;
			while((c = it.getNext()) != '\0'){
				i++;
				if(i == n) return -1;
				if(text.charAt(i) != c) return -1;
			}
		}
		return term.get(nodeIndex) ? term.rank1(nodeIndex) - 1 : -1;
	}

	@Override
	public Iterable<Pair<String, Integer>> commonPrefixSearchWithTermId(String query) {
		List<Pair<String, Integer>> ret = new ArrayList<Pair<String, Integer>>();
		int charsLen = query.length();
		int ci = 0;
		int ni = 0;
		FastTailCharIterator it = new FastTailCharIterator(tails, -1);
		for(; ci < charsLen; ci++){
			int cid = findCharId(query.charAt(ci));
			if(cid == -1) return ret;
			int b = base[ni];
			if(b == BASE_EMPTY) return ret;
			int next = b + cid;
			if(check.length <= next || check[next] != ni) return ret;
			ni = next;
			int ti = tail[ni];
			if(ti != -1){
				it.setIndex(ti);
				char c;
				while((c = it.getNext()) != '\0'){
					ci++;
					if(ci >= charsLen) return ret;
					if(c != query.charAt(ci)) return ret;
				}
			}
			if(term.get(ni)) ret.add(Pair.create(
					query.substring(0, ci + 1),
					term.rank1(ni) - 1
					));
		}
		return ret;
	}

	@Override
	public Iterable<Pair<String, Integer>> predictiveSearchWithTermId(String prefix) {
		Set<Pair<String, Integer>> ret = new TreeSet<Pair<String, Integer>>(
				new Comparator<Pair<String, Integer>>() {
					@Override
					public int compare(
							Pair<String, Integer> o1,
							Pair<String, Integer> o2) {
						return o1.getFirst().compareTo(o2.getFirst());
					}
				});
		StringBuilder current = new StringBuilder();
		char[] chars = prefix.toCharArray();
		int charsLen = chars.length;
		int checkLen = check.length;
		int nodeIndex = 0;
		TailCharIterator it = new TailCharIterator(tails,  -1);
		for(int i = 0; i < chars.length; i++){
			int ti = tail[nodeIndex];
			if(ti != -1){
				int first = i;
				it.setIndex(ti);
				do{
					if(!it.hasNext()) break;
					if(it.next() != chars[i]) return ret;
					i++;
				} while(i < charsLen);
				if(i >= charsLen) break;
				current.append(chars, first, i - first);
			}
			int cid = findCharId(chars[i]);
			if(cid == -1) return ret;
			int next = base[nodeIndex] + cid;
			if(next < 0 || checkLen <= next || check[next] != nodeIndex) return ret;
			nodeIndex = next;
			current.append(chars[i]);
		}
		Deque<Pair<Integer, char[]>> q = new LinkedList<Pair<Integer,char[]>>();
		q.add(Pair.create(nodeIndex, current.toString().toCharArray()));
		while(!q.isEmpty()){
			Pair<Integer, char[]> p = q.pop();
			int ni = p.getFirst();
			StringBuilder buff = new StringBuilder().append(p.getSecond());
			int ti = tail[ni];
			if(ti != -1){
				it.setIndex(ti);
				while(it.hasNext()){
					buff.append(it.next());
				}
			}
			if(term.get(ni)){
				ret.add(Pair.create(
					buff.toString(),
					term.rank1(ni) - 1));
			}
			int b = base[ni];
			if(b == BASE_EMPTY) continue;
			for(char v : this.chars){
				int next = b + charToCode[v];
				if(next >= checkLen) continue;
				if(check[next] == ni){
					StringBuilder bu = new StringBuilder(buff);
					bu.append(v);
					q.push(Pair.create(next, bu.toString().toCharArray()));
				}
			}
		}
		return ret;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(size);
		out.writeInt(base.length);
		for(int v : base){
			out.writeInt(v);
		}
		for(int v : check){
			out.writeInt(v);
		}
		for(int v : tail){
			out.writeInt(v);
		}
		out.writeObject(term);
		out.flush();
		out.writeInt(firstEmptyCheck);
		out.writeInt(tails.length());
		out.writeChars(tails.toString());
		out.writeInt(chars.size());
		for(char c : chars){
			out.writeChar(c);
			out.writeChar(charToCode[c]);
		}
	}

	public void save(OutputStream os) throws IOException{
		ObjectOutputStream out = new ObjectOutputStream(os);
		try{
			writeExternal(out);
		} finally{
			out.flush();
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException {
		size = in.readInt();
		int len = in.readInt();
		base = new int[len];
		for(int i = 0; i < len; i++){
			base[i] = in.readInt();
		}
		check = new int[len];
		for(int i = 0; i < len; i++){
			check[i] = in.readInt();
		}
		tail = new int[len];
		for(int i = 0; i < len; i++){
			tail[i] = in.readInt();
		}
		try{
			term = (SuccinctBitVector)in.readObject();
		} catch(ClassNotFoundException e){
			throw new IOException(e);
		}
		firstEmptyCheck = in.readInt();
		int n = in.readInt();
		StringBuilder b = new StringBuilder(n);
		for(int i = 0; i < n; i++){
			b.append(in.readChar());
		}
		tails = b;
		n = in.readInt();
		for(int i = 0; i < n; i++){
			char c = in.readChar();
			char v = in.readChar();
			chars.add(c);
			charToCode[c] = v;
		}
	}

	public void load(InputStream is) throws IOException{
		try{
			readExternal(new ObjectInputStream(is));
		} catch(ClassNotFoundException e){
			throw new IOException(e);
		}
	}

	@Override
	public void dump(Writer w){
		PrintWriter writer = new PrintWriter(w);
		try{
			writer.println("--- dump " + getClass().getSimpleName() + " ---");
			writer.println("array size: " + base.length);
			writer.println("last index of valid element: " + last);
			int vc = 0;
			for(int i = 0; i < base.length; i++){
				if(base[i] != BASE_EMPTY || check[i] >= 0) vc++;
			}
			writer.println("valid elements: " + vc);
			writer.print("      |");
			for(int i = 0; i < 16; i++){
				writer.print(String.format("%3d|", i));
			}
			writer.println();
			writer.print("|base |");
			for(int i = 0; i < 16; i++){
				if(base[i] == BASE_EMPTY){
					writer.print("N/A|");
				} else{
					writer.print(String.format("%3d|", base[i]));
				}
			}
			writer.println();
			writer.print("|check|");
			for(int i = 0; i < 16; i++){
				if(check[i] < 0){
					writer.print("N/A|");
				} else{
					writer.print(String.format("%3d|", check[i]));
				}
			}
			writer.println();
			writer.print("|tail |");
			for(int i = 0; i < 16; i++){
				if(tail[i] < 0){
					writer.print("N/A|");
				} else{
					writer.print(String.format("%3d|", tail[i]));
				}
			}
			writer.println();
			writer.print("|term |");
			for(int i = 0; i < 16; i++){
				writer.print(String.format("%3d|", term.get(i) ? 1 : 0));
			}
			writer.println();
			int count = 0;
			for(int i : tail){
				if(i != -1) count++;
			}
			writer.println("tail count: " + count);
			writer.println();
			writer.print("tails: [");
			char[] tailChars = tails.subSequence(0, Math.min(tails.length(), 64)).toString().toCharArray();
			for(int i = 0; i < tailChars.length; i++){
				char c = tailChars[i];
				if(c == '\0'){
					writer.print("\\0");
					continue;
				}
				if(c == '\1'){
					int index = tailChars[i + 1] + (tailChars[i + 2] << 16);
					i += 2;
					writer.print(String.format("\\1(%d)", index));
					continue;
				}
				writer.print(c);
			}
			writer.println("]");
			writer.print("tailBuf size: " + tails.length());
			if(tails instanceof StringBuilder){
				writer.print("(capacity: " + ((StringBuilder)tails).capacity() + ")");
			}
			writer.println();
			{
				writer.print("chars: ");
				int c = 0;
				for(char e : chars){
					writer.print(String.format("%c:%d,", e, (int)charToCode[e]));
					c++;
					if(c > 16) break;
				}
				writer.println();
				writer.println("chars count: " + chars.size());
			}
			{
				writer.println("calculating max and min base.");
				int min = Integer.MAX_VALUE;
				int max = Integer.MIN_VALUE;
				int maxDelta = Integer.MIN_VALUE;
				for(int i = 0; i < base.length; i++){
					int b = base[i];
					if(b == BASE_EMPTY) continue;
					min = Math.min(min, b);
					max = Math.max(max, b);
					maxDelta = Math.max(maxDelta, Math.abs(i - b));
				}
				writer.println("maxDelta: " + maxDelta);
				writer.println("max: " + max);
				writer.println("min: " + min);
			}
			{
				writer.println("calculating min check.");
				int min = Integer.MAX_VALUE;
				for(int i = 0; i < base.length; i++){
					int b = check[i];
					min = Math.min(min, b);
				}
				writer.println("min: " + min);
			}
			writer.println();
		} finally{
			writer.flush();
		}
	}

	@Override
	public void trimToSize(){
		int sz = last + 1 + 0xFFFF;
		base = Arrays.copyOf(base, sz);
		check = Arrays.copyOf(check, sz);
		tail = Arrays.copyOf(tail, sz);
		if(tails instanceof StringBuilder){
			((StringBuilder)tails).trimToSize();
		}
	}

	private void build(Node node, int nodeIndex, TailBuilder tailb,
			FastBitSet bs, TermNodeListener listener){
		// letters
		char[] letters = node.getLetters();
		if(letters.length > 1){
			tail[nodeIndex] = tailb.insert(letters, 1, letters.length - 1);
		}
		if(node.isTerminate()){
			bs.set(nodeIndex);
			listener.listen(node, nodeIndex);
		} else if(bs.size() <= nodeIndex){
			bs.unsetIfLE(nodeIndex);
		}

		// children
		Node[] children = node.getChildren();
		int childrenLen = children.length;
		if(childrenLen == 0) return;
		int[] heads = new int[childrenLen];
		int maxHead = 0;
		int minHead = Integer.MAX_VALUE;
		for(int i = 0; i < childrenLen; i++){
			heads[i] = getCharId(children[i].getLetters()[0]);
			maxHead = Math.max(maxHead, heads[i]);
			minHead = Math.min(minHead, heads[i]);
		}

		int offset = findInsertOffset(heads, minHead, maxHead);
		base[nodeIndex] = offset;
		for(int cid : heads){
			setCheck(offset + cid, nodeIndex);
		}
/*
		for(int i = 0; i < children.length; i++){
			build(children[i], offset + heads[i]);
		}
/*/
		// sort children by children's children count.
		Map<Integer, List<Pair<Node, Integer>>> nodes = new TreeMap<Integer, List<Pair<Node, Integer>>>(new Comparator<Integer>() {
			@Override
			public int compare(Integer arg0, Integer arg1) {
				return arg0 - arg1;//arg1 - arg0;
			}
		});
		for(int i = 0; i < children.length; i++){
			Node[] c = children[i].getChildren();
			int n = 0;
			if(c != null){
				n = c.length;
			}
			List<Pair<Node, Integer>> p = nodes.get(n);
			if(p == null){
				p = new ArrayList<Pair<Node, Integer>>();
				nodes.put(n, p);
			}
			p.add(Pair.create(children[i], heads[i]));
		}
		for(Map.Entry<Integer, List<Pair<Node, Integer>>> e : nodes.entrySet()){
			for(Pair<Node, Integer> e2 : e.getValue()){
				build(e2.getFirst(), e2.getSecond() + offset, tailb,
						bs, listener);
			}
		}
//*/
	}

	private int findInsertOffset(int[] heads, int minHead, int maxHead){
		for(int empty = findFirstEmptyCheck(); ; empty = findNextEmptyCheck(empty)){
			int offset = empty - minHead;
			if((offset + maxHead) >= check.length){
				extend(offset + maxHead);
			}
			// find space
			boolean found = true;
			for(int cid : heads){
				if(check[offset + cid] >= 0){
					found = false;
					break;
				}
			}
			if(found) return offset;
		}
	}

	private int getCharId(char c){
		char v = charToCode[c];
		if(v != 0) return v;
		v = (char)(chars.size() + 1);
		chars.add(c);
		charToCode[c] = v;
		return v;
	}

	private int findCharId(char c){
		char v = charToCode[c];
		if(v != 0) return v;
		return -1;
	}

	private void extend(int i){
		int sz = base.length;
		int nsz = Math.max(i + 0xFFFF, (int)(sz * 1.5));
//		System.out.println("extend to " + nsz);
		base = Arrays.copyOf(base, nsz);
		Arrays.fill(base, sz, nsz, BASE_EMPTY);
		check = Arrays.copyOf(check, nsz);
		Arrays.fill(check, sz, nsz, -1);
		tail = Arrays.copyOf(tail, nsz);
		Arrays.fill(tail, sz, nsz, -1);
	}

	private int findFirstEmptyCheck(){
		int i = firstEmptyCheck;
		while(check[i] >= 0 || base[i] != BASE_EMPTY){
			i++;
		}
		firstEmptyCheck = i;
		return i;
	}

	private int findNextEmptyCheck(int i){
/*
		for(i++; i < check.length; i++){
			if(check[i] < 0) return i;
		}
		extend(i);
		return i;
/*/
		int d = check[i] * -1;
		if(d <= 0){
			throw new RuntimeException();
		}
		int prev = i;
		i += d;
		if(i >= check.length){
			extend(i);
			return i;
		}
		if(check[i] < 0){
			return i;
		}
		for(i++; i < check.length; i++){
			if(check[i] < 0){
				check[prev] = prev - i;
				return i;
			}
		}
		extend(i);
		check[prev] = prev - i;
		return i;
//*/
	}

	private void setCheck(int index, int value){
		if(firstEmptyCheck == index){
			firstEmptyCheck = findNextEmptyCheck(firstEmptyCheck);
		}
		check[index] = value;
		last = Math.max(last, index);
	}

	private int size;
	private int[] base;
	private int[] check;
	private int[] tail;
	private int firstEmptyCheck = 1;
	private int last;
	private SuccinctBitVector term;
	private CharSequence tails;
	private Set<Character> chars = new TreeSet<Character>();
	private char[] charToCode = new char[Character.MAX_VALUE];
	private static final TermIdNode[] emptyNodes = {};
	private static final int BASE_EMPTY = Integer.MAX_VALUE;

}
