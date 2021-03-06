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
package org.trie4j.tail.index;

import org.junit.Assert;
import org.junit.Test;
import org.trie4j.tail.TailIndex;

public class ArrayTailIndexTest {
	@Test
	public void test() throws Exception{
		TailIndex ti = new ArrayTailIndex();
		ti.add(0, 10);
		ti.add(10, 15);
		Assert.assertEquals(0, ti.get(0));
		Assert.assertEquals(10, ti.get(1));
	}

	@Test
	public void test2() throws Exception{
		TailIndex ti = new ArrayTailIndex();
		try{
			ti.get(0);
			Assert.fail();
		} catch(ArrayIndexOutOfBoundsException e){
		}
	}

	@Test
	public void test3() throws Exception{
		TailIndex ti = new ArrayTailIndex();
		ti.add(0, 10);
		ti.add(-1, -1);
		ti.add(10, 15);
		Assert.assertEquals(0, ti.get(0));
		Assert.assertEquals(-1, ti.get(1));
		Assert.assertEquals(10, ti.get(2));
	}
}
