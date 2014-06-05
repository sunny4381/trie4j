package org.trie4j;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.trie4j.doublearray.DoubleArray;
import org.trie4j.doublearray.MapDoubleArray;
import org.trie4j.doublearray.MapTailDoubleArray;
import org.trie4j.doublearray.TailDoubleArray;
import org.trie4j.louds.MapTailLOUDSPPTrie;
import org.trie4j.louds.MapTailLOUDSTrie;
import org.trie4j.louds.TailLOUDSPPTrie;
import org.trie4j.louds.TailLOUDSTrie;
import org.trie4j.patricia.simple.MapPatriciaTrie;
import org.trie4j.patricia.simple.PatriciaTrie;
import org.trie4j.patricia.tail.MapTailPatriciaTrie;
import org.trie4j.patricia.tail.TailPatriciaTrie;
import org.trie4j.tail.ConcatTailArray;
import org.trie4j.tail.SBVConcatTailArray;
import org.trie4j.tail.SuffixTrieTailArray;
import org.trie4j.tail.builder.ConcatTailBuilder;
import org.trie4j.tail.builder.SuffixTrieTailBuilder;
import org.trie4j.test.LapTimer;
import org.trie4j.test.WikipediaTitles;
import org.trie4j.util.Pair;
import org.trie4j.util.Trio;

public class AllTries {
	private static Iterable<String> newWords() throws IOException{
		return WikipediaTitles.instance();
	}

	private static String createName(Class<?> trieClass, Class<?>... ctorParamClasses){
		StringBuilder b = new StringBuilder(trieClass.getSimpleName());
		if(ctorParamClasses.length > 0){
			b.append("(");
			boolean first = true;
			for(Class<?> c : ctorParamClasses){
				if(first) first = false;
				else b.append(",");
				b.append(c.getSimpleName());
			}
			b.append(")");
		}
		return b.toString();
	}

	private static interface Process{
		String getName();
		Trio<Object, Long, Long> run() throws Throwable;
	}

	private static abstract class AbstractProcess implements Process{
		private String name;
		public AbstractProcess(String name){
			this.name = name;
		}
		public String getName() {
			return name;
		}
		public void setName(String name){
			this.name = name;
		}
		/**
		 * Do process and returns container and construction and verification time.
		 * @return The pair of construction and verification time in ms.
		 * @throws Throwable
		 */
		public abstract Trio<Object, Long, Long> run() throws Throwable;
	}

	private static class SetProcess extends AbstractProcess{
		@SuppressWarnings("rawtypes")
		public SetProcess(Class<? extends Set> set){
			super(set.getSimpleName());
			this.clazz = set;
		}
		@SuppressWarnings("unchecked")
		public Trio<Object, Long, Long> run() throws Throwable{
			Set<String> set = (Set<String>)clazz.newInstance();
			long b = 0, c = 0;
			LapTimer lt = new LapTimer();
			for(String w : newWords()){
				lt.reset(); set.add(w); b += lt.lapNanos();
			}
			for(String w : newWords()){
				lt.reset();
				boolean r = set.contains(w);
				c += lt.lapNanos();
				if(!r) throw new RuntimeException("verification failed for \"" + w + "\"");
			}
			return Trio.create((Object)set, b / 1000000, c / 1000000);
		}
		@SuppressWarnings("rawtypes")
		private Class<? extends Set> clazz;
	}

	private static class MapProcess extends AbstractProcess{
		@SuppressWarnings("rawtypes")
		public MapProcess(Class<? extends Map> map){
			super(map.getSimpleName());
			this.clazz = map;
		}
		@SuppressWarnings("unchecked")
		public Trio<Object, Long, Long> run() throws Throwable{
			Map<String, Integer> map = (Map<String, Integer>)clazz.newInstance();
			long b = 0, c = 0;
			int i = 0;
			LapTimer lt = new LapTimer();
			for(String w : newWords()){
				lt.reset(); map.put(w, i); b += lt.lapNanos();
				i++;
			}
			i = 0;
			for(String w : newWords()){
				lt.reset();
				Integer r = map.get(w);
				c += lt.lapNanos();
				if((int)r != i) throw new RuntimeException("verification failed for \"" + w + "\"");
				i++;
			}
			return Trio.create((Object)map, b / 1000000, c / 1000000);
		}
		@SuppressWarnings("rawtypes")
		private Class<? extends Map> clazz;
	}

	private static interface TrieConsumer{
		String name();
		void consume(Trie trie);
	}

	private static abstract class AbstractTrieConsumer implements TrieConsumer{
		private String name;
		public AbstractTrieConsumer(String name) {
			this.name = name;
		}
		@Override
		public String name() {
			return name;
		}
	}

	private static class TrieFreezer extends AbstractTrieConsumer implements TrieConsumer{
		public TrieFreezer() {
			super("freezed");
		}
		@Override
		public void consume(Trie trie) {
			trie.freeze();
		}
	}

	private static class TrieProcess extends AbstractProcess{
		private Class<?> trieClass;
		private Class<?>[] ctorParamClasses;
		private TrieConsumer consumer;
		public TrieProcess(){
			this(TailPatriciaTrie.class, ConcatTailBuilder.class);
		}
		public TrieProcess(Class<? extends Trie> clazz, Class<?>... ctorParamClasses){
			super(createName(clazz, ctorParamClasses));
			this.trieClass = clazz;
			this.ctorParamClasses = ctorParamClasses;
		}
		public AbstractProcess afterBuild(TrieConsumer consumer){
			this.consumer = consumer;
			setName(getName() + ":" + consumer.name());
			return this;
		}
		public AbstractProcess second(final Class<? extends Trie> secondTrieClass, final Class<?>... ctorParamClasses){
			return new AbstractProcess(createName(secondTrieClass, ctorParamClasses)){
				@Override
				public Trio<Object, Long, Long> run() throws Throwable {
					Trie first = buildFirstTrie().getFirst();
					Pair<Trie, Long> tries = buildSecondTrie(first);
					Trie second = tries.getFirst();
					first = null;
					System.gc();
					System.gc();
					long c = verifyTrie(second);
					return Trio.create((Object)second, tries.getSecond(), c);
				}
				private Pair<Trie, Long> buildSecondTrie(Trie firstTrie)
				throws InstantiationException, IllegalAccessException{
					Object[] args = new Object[1 + ctorParamClasses.length];
					args[0] = firstTrie;
					for(int i = 0; i < ctorParamClasses.length; i++){
						args[i + 1] = ctorParamClasses[i].newInstance();
					}
					for(Constructor<?> c : secondTrieClass.getConstructors()){
						try{
							if(c.getParameterTypes().length == args.length){
								LapTimer lt = new LapTimer();
								Object ret = c.newInstance(args);
								long ms = lt.lapMillis();
								return Pair.create((Trie)ret, ms);
							}
						} catch(IllegalAccessException  e){
						} catch(SecurityException e){
						} catch(IllegalArgumentException e){
						} catch(InvocationTargetException e){
						}
					}
					throw new RuntimeException("no suitable constructor.");
				}
			};
		}
		@Override
		public Trio<Object, Long, Long> run() throws Throwable {
			Pair<Trie, Long> tries = buildFirstTrie();
			Trie trie = tries.getFirst();
			return Trio.create((Object)trie, tries.getSecond(), verifyTrie(trie));
		}
		private Pair<Trie, Long> buildFirstTrie()
		throws InstantiationException, IllegalAccessException, IOException{
			Object[] params = new Object[ctorParamClasses.length];
			for(int i = 0; i < ctorParamClasses.length; i++){
				params[i] = ctorParamClasses[i].newInstance();
			}
			for(Constructor<?> c : trieClass.getConstructors()){
				try{
					if(c.getParameterTypes().length == params.length){
						Trie trie = (Trie)c.newInstance(params);
						long b = 0;
						LapTimer lt = new LapTimer();
						for(String w : newWords()){ lt.reset(); trie.insert(w); b += lt.lapNanos();}
						b += lt.lapNanos();
						if(consumer != null) consumer.consume(trie);
						return Pair.create(trie, b / 1000000);
					}
				} catch(InstantiationException e){
				} catch(IllegalAccessException  e){
				} catch(IllegalArgumentException e){
				} catch(InvocationTargetException e){
				}
			}
			throw new RuntimeException("no suitable constructor.");
		}
		private long verifyTrie(Trie trie) throws IOException{
			long c = 0;
			LapTimer lt = new LapTimer();
			for(String w : newWords()){
				lt.reset();
				boolean r = trie.contains(w);
				c += lt.lapNanos();
				if(!r) throw new RuntimeException("verification failed for \"" + w + "\"");
			}
			return c / 1000000;
		}
	}

	private static class MapTrieProcess extends AbstractProcess{
		private Class<?> trieClass;
		private Class<?>[] ctorParamClasses;
		private TrieConsumer consumer;
		public MapTrieProcess(){
			this(MapTailPatriciaTrie.class, ConcatTailBuilder.class);
		}
		@SuppressWarnings("rawtypes")
		public MapTrieProcess(Class<? extends MapTrie> clazz, Class<?>... ctorParamClasses){
			super(createName(clazz, ctorParamClasses));
			this.trieClass = clazz;
			this.ctorParamClasses = ctorParamClasses;
		}
		public AbstractProcess afterBuild(TrieConsumer consumer){
			this.consumer = consumer;
			setName(getName() + ":" + consumer.name());
			return this;
		}
		@SuppressWarnings("rawtypes")
		public AbstractProcess second(final Class<? extends Trie> secondTrieClass, final Class<?>... ctorParamClasses){
			return new AbstractProcess(createName(secondTrieClass, ctorParamClasses)){
				@Override
				public Trio<Object, Long, Long> run() throws Throwable {
					MapTrie first = buildFirstTrie().getFirst();
					Pair<MapTrie, Long> tries = buildSecondTrie(first);
					MapTrie second = tries.getFirst();
					first = null;
					System.gc();
					System.gc();
					long c = verifyTrie(second);
					return Trio.create((Object)second, tries.getSecond(), c);
				}
				private Pair<MapTrie, Long> buildSecondTrie(MapTrie firstTrie)
				throws InstantiationException, IllegalAccessException{
					Object[] args = new Object[1 + ctorParamClasses.length];
					args[0] = firstTrie;
					for(int i = 0; i < ctorParamClasses.length; i++){
						args[i + 1] = ctorParamClasses[i].newInstance();
					}
					for(Constructor<?> c : secondTrieClass.getConstructors()){
						try{
							if(c.getParameterTypes().length == args.length){
								LapTimer lt = new LapTimer();
								Object ret = c.newInstance(args);
								long ms = lt.lapMillis();
								return Pair.create((MapTrie)ret, ms);
							}
						} catch(InstantiationException e){
						} catch(IllegalAccessException  e){
						} catch(SecurityException e){
						} catch(IllegalArgumentException e){
						} catch(InvocationTargetException e){
						}
					}
					throw new RuntimeException("no suitable constructor.");
				}
			};
		}
		@Override
		@SuppressWarnings("rawtypes")
		public Trio<Object, Long, Long> run() throws Throwable {
			Pair<MapTrie, Long> tries = buildFirstTrie();
			MapTrie trie = tries.getFirst();
			return Trio.create((Object)trie, tries.getSecond(), verifyTrie(trie));
		}
		@SuppressWarnings({"rawtypes", "unchecked"})
		private Pair<MapTrie, Long> buildFirstTrie()
		throws InstantiationException, IllegalAccessException, IOException{
			Object[] params = new Object[ctorParamClasses.length];
			for(int i = 0; i < ctorParamClasses.length; i++){
				params[i] = ctorParamClasses[i].newInstance();
			}
			for(Constructor<?> c : trieClass.getConstructors()){
				try{
					if(c.getParameterTypes().length == params.length){
						MapTrie<Integer> trie = (MapTrie<Integer>)c.newInstance(params);
						long b = 0;
						LapTimer lt = new LapTimer();
						int i = 0;
						for(String w : newWords()){
							lt.reset(); trie.insert(w, i); b += lt.lapNanos(); i++;
						}
						b += lt.lapNanos();
						if(consumer != null) consumer.consume(trie);
						return Pair.create((MapTrie)trie, b / 1000000);
					}
				} catch(InstantiationException e){
				} catch(IllegalAccessException  e){
				} catch(IllegalArgumentException e){
				} catch(InvocationTargetException e){
				}
			}
			throw new RuntimeException("no suitable constructor.");
		}
		@SuppressWarnings("rawtypes")
		private long verifyTrie(MapTrie trie) throws IOException{
			long c = 0;
			int i = 0;
			LapTimer lt = new LapTimer();
			for(String w : newWords()){
				lt.reset();
				Integer r = (Integer)trie.get(w);
				c += lt.lapNanos();
				if((int)r != i) throw new RuntimeException("verification failed for \"" + w + "\"");
				i++;
			}
			return c / 1000000;
		}
	}

	private static AbstractProcess[] procs = {
//*
		new SetProcess(HashSet.class),
		new SetProcess(TreeSet.class),
		new MapProcess(HashMap.class),
		new MapProcess(TreeMap.class),
		new TrieProcess(PatriciaTrie.class),
		new MapTrieProcess(MapPatriciaTrie.class),
		new TrieProcess(TailPatriciaTrie.class, SuffixTrieTailBuilder.class),
		new TrieProcess(TailPatriciaTrie.class, SuffixTrieTailBuilder.class).afterBuild(new TrieFreezer()),
		new TrieProcess(TailPatriciaTrie.class, ConcatTailBuilder.class),
		new MapTrieProcess(MapTailPatriciaTrie.class, SuffixTrieTailBuilder.class),
		new MapTrieProcess(MapTailPatriciaTrie.class, SuffixTrieTailBuilder.class).afterBuild(new TrieFreezer()),
		new MapTrieProcess(MapTailPatriciaTrie.class, ConcatTailBuilder.class),
//*/
/*
			new TrieProcess("MultilayerPatriciaTrie(no pack)"){
				public Pair<Long, Long> run() throws IOException {
					return runForTrie(new MultilayerPatriciaTrie());
				}
			},
			new TrieProcess("MultilayerPatriciaTrie"){
				public Pair<Long, Long> run() throws IOException {
					return runForTrie(new MultilayerPatriciaTrie());
				}
				protected void afterBuildTrie(Trie trie) {
					((MultilayerPatriciaTrie)trie).pack();
				}
			},
//*/
//*
			new TrieProcess().second(DoubleArray.class),
			new MapTrieProcess().second(MapDoubleArray.class),
			new TrieProcess().second(TailDoubleArray.class, SuffixTrieTailBuilder.class),
			new TrieProcess().second(TailDoubleArray.class, ConcatTailBuilder.class),
			new MapTrieProcess().second(MapTailDoubleArray.class, SuffixTrieTailBuilder.class),
			new MapTrieProcess().second(MapTailDoubleArray.class, ConcatTailBuilder.class),
//			new TrieProcess().second(TailDoubleArray.class, SuffixTrieTailBuilder.class),
//			new TrieProcess().second(TailDoubleArray.class, ConcatTailBuilder.class),
//*/
/*
			new TrieProcess2("LOUDSTrie"){
				protected Trie buildFrom(Trie trie){
					return new LOUDSTrie(trie, 65536);
				}
			},
//*/
//*
			new TrieProcess().second(TailLOUDSTrie.class, SuffixTrieTailArray.class),
			new TrieProcess().second(TailLOUDSTrie.class, ConcatTailArray.class),
			new TrieProcess().second(TailLOUDSTrie.class, SBVConcatTailArray.class),
			new MapTrieProcess().second(MapTailLOUDSTrie.class, SuffixTrieTailArray.class),
			new MapTrieProcess().second(MapTailLOUDSTrie.class, ConcatTailArray.class),
			new MapTrieProcess().second(MapTailLOUDSTrie.class, SBVConcatTailArray.class),
			new TrieProcess().second(TailLOUDSPPTrie.class, SuffixTrieTailArray.class),
			new TrieProcess().second(TailLOUDSPPTrie.class, ConcatTailArray.class),
			new TrieProcess().second(TailLOUDSPPTrie.class, SBVConcatTailArray.class),
			new MapTrieProcess().second(MapTailLOUDSPPTrie.class, SuffixTrieTailArray.class),
			new MapTrieProcess().second(MapTailLOUDSPPTrie.class, ConcatTailArray.class),
			new MapTrieProcess().second(MapTailLOUDSPPTrie.class, SBVConcatTailArray.class),
//*/
			};

	public static void main(String[] args) throws Throwable{
		MemoryMXBean mb = ManagementFactory.getMemoryMXBean();
		int n = 3;
		System.out.println("run each process " + n + " times.");
		System.out.println("warming up... running all trie once");
		for(AbstractProcess p : procs){
			System.out.print(p.getName() + " ");
			p.run();
			System.gc();
		}
		System.out.println("warming up... done");
		for(AbstractProcess p : procs){
			System.out.print(p.getName());
			p.run();
			mb.gc();
			mb.gc();
			long b = 0, c = 0;
			Object trie = null;
			for(int i = 0; i < n; i++){
				Trio<Object, Long, Long> r = p.run();
				b += r.getSecond();
				c += r.getThird();
				trie = r.getFirst();
				mb.gc();
				mb.gc();
			}
			System.out.println(String.format(
					", %d, %d, %d",
					b / n, c / n, mb.getHeapMemoryUsage().getUsed()));
//			System.out.println("sleeping...");
//			Thread.sleep(10000);
			trie.hashCode();
			trie = null;
		}
//*
//*/
/*
		{	// optimized double array
			lt.lap();
			OptimizedTailDoubleArray da = new OptimizedTailDoubleArray(pattrie, 65536, new SuffixTrieTailBuilder());
			long b = lt.lap();
			long c = lapContains(da);
			log("OptimizedTailDoubleArray(suffixTrieTail), %d, %d", b, c);
		}
		System.gc();
		System.gc();

		{	// optimized double array with simple tb
			lt.lap();
			OptimizedTailDoubleArray da = new OptimizedTailDoubleArray(pattrie, 655536, new ConcatTailBuilder());
			long b = lt.lap();
			long c = lapContains(da);
			log("OptimizedTailDoubleArray(concatTail), %d, %d", b, c);
		}
		System.gc();
		System.gc();
//*/
	}
}
