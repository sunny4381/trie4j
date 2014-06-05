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

/**
 * Represents the Node of Trie.
 * @author Takao Nakaguchi
 */
public interface Node {
	/**
	 * Returns the letter of this Node.
	 * @return letter
	 */
	char[] getLetters();

	/**
	 * Returns true if this node is terminal (leaf).
	 * @return true if the terminal
	 */
	boolean isTerminate();

	/**
	 * Get the child that has character passed by c as the first letter.
	 * @param c first letter of child node.
	 * @return child node or null if no child node that has c exist.
	 */
	Node getChild(char c);
	Node[] getChildren();
}
