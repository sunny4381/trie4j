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
package org.trie4j.patricia.multilayer.node;

import org.trie4j.patricia.multilayer.Node;
import org.trie4j.patricia.multilayer.labeltrie.LabelNode;

public class TerminalInternalCharsNode extends InternalCharsNode {
	public TerminalInternalCharsNode(char[] letters, Node[] children) {
		super(letters, children);
	}

	@Override
	public boolean isTerminate() {
		return true;
	}

	@Override
	protected Node newInternalCharsNode(char[] letters, Node[] children) {
		return new TerminalInternalCharsNode(letters, children);
	}

	@Override
	protected Node cloneWithLetters(char[] letters) {
		return new TerminalInternalCharsNode(letters, getChildren());
	}

	protected Node newLabelTrieNode(LabelNode ln, Node[] children){
		if(children != null){
			return new TerminalInternalLabelTrieNode(ln, children);
		} else{
			return new TerminalLabelTrieNode(ln);
		}
	}
}
