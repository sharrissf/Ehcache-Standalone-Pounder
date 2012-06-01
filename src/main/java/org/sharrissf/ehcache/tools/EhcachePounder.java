package org.sharrissf.ehcache.tools;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.DiskStoreConfiguration;
import net.sf.ehcache.config.ManagementRESTServiceConfiguration;
import net.sf.ehcache.config.MemoryUnit;
import net.sf.ehcache.config.PersistenceConfiguration;
import net.sf.ehcache.config.PersistenceConfiguration.Strategy;

import org.ho.yaml.Yaml;

/**
 * This is a small app that exercises the characteristics of the enterprise
 * ehcache tiered store .
 * 
 * Configuration is done through the config.yml file. All settings are mandatory
 * 
 * storeType: OFFHEAP|DISK|ONHEAP
 * 
 * threadCount: number of execution threads
 * 
 * entryCount: Total number of entries to load in the load phase and the number
 * of operations to perform in the read/update phase
 * 
 * offHeapSize: Amount of ram to dedicate to offheap (NOTE: you also have to
 * have at least that number in the -XX:MaxDirectMemorySize=4g)
 * 
 * maxOnHeapCount: Count of onHeap cache tier entries. This only helps if you
 * have a hot set a slowly moving hotset that fits in memory. If you don't set
 * this thing to 1024
 * 
 * batchCount: How often you print the current status and change the entry size
 * for each thread
 * 
 * maxValueSize: Max size in bytes of the value portion of the entry (a random
 * number is picked bellow this)
 * 
 * minValueSize: min size in bytes of the value portion of the entry (a random
 * number is picked above this)
 * 
 * hotSetPercentage: Percentage of time you hit an entry from the onHeap portion
 * of the cache
 * 
 * rounds: Number of times your execute entryCount operations matching the above
 * config elements
 * 
 * updatePercentage: Number of times out of 100 that you update an entry instead
 * of reading
 * 
 * diskStorePath: Where to put the disk store if one is in use
 * 
 * @author steve
 * 
 */
public class EhcachePounder {
	public enum StoreType {
		OFFHEAP, ONHEAP, DISK
	};

	private final int maxOnHeapCount;
	private final int batchCount;
	private final int maxValueSize;
	private final int minValueSize;
	private final int hotSetPercentage;
	private final int rounds;
	private final int updatePercentage;
	private final String diskStorePath;
	private static final Random random = new Random();

	private volatile boolean isWarmup = true;
	private volatile AtomicLong maxBatchTimeMillis = new AtomicLong();

	private CacheManager cacheManager;

	private Ehcache cache;
	private final long entryCount;
	private final int threadCount;
	private final String offHeapSize;
	private final Results results;
	private final AtomicLong maxGetTime = new AtomicLong(0);
	private final PrintWriter csvOut;

	/**
	 * 
	 * @param storeType
	 *            - ONHEAP| OFFHEAP|DISK
	 * @param threadCount
	 *            - Number of threads executing operations
	 * @param entryCount
	 *            - Total number of entries in the load phase and total number
	 *            of operations in each of the subsequent rounds.
	 * @param offHeapSize
	 *            - Size in bytes of the off heap store (i.e. 1G)
	 * @param maxOnHeapCount
	 *            - Number of entries to be stored on heap (keep low if
	 *            possible)
	 * @param batchCount
	 *            - Number of operations per status update/value size change
	 *            (Making this too low can impact performance)
	 * @param maxValueSize
	 *            - Max size in bytes of the value being put
	 * @param minValueSize
	 *            - min size in bytes of the value being put
	 * @param hotSetPercentage
	 *            - percentage of time to hit the onHeap Cache
	 * @param rounds
	 *            - number of rounds of entryCount operations
	 * @param updatePercentage
	 *            - percentage of time to do an update instead of a read
	 * @param diskStorePath
	 *            - location of disk store file
	 * @param monitoringEnabled
	 *            - Enable monitoring with the TMC.
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public EhcachePounder(StoreType storeType, int threadCount,
			long entryCount, String offHeapSize, int maxOnHeapCount,
			int batchCount, int maxValueSize, int minValueSize,
			int hotSetPercentage, int rounds, int updatePercentage,
			String diskStorePath, boolean monitoringEnabled) throws InterruptedException, IOException {
		this.entryCount = entryCount;
		this.threadCount = threadCount;
		this.offHeapSize = offHeapSize;
		this.maxOnHeapCount = maxOnHeapCount;
		this.batchCount = batchCount;
		this.maxValueSize = maxValueSize;
		this.minValueSize = minValueSize;
		this.hotSetPercentage = hotSetPercentage;
		this.rounds = rounds;
		this.updatePercentage = updatePercentage;
		this.diskStorePath = diskStorePath;
		initializeCache(storeType, monitoringEnabled);
		this.results = new Results(storeType);
		this.csvOut = new PrintWriter(new FileWriter("results.csv"));
	}

	/**
	 * Kicks off the run of the test for this node
	 * 
	 * NOTE: It will wait for nodeCount nodes before actually running
	 * 
	 * @throws InterruptedException
	 */
	public void start() throws InterruptedException {

		System.out
				.println(System.currentTimeMillis()
						+ " Starting with threadCount: " + threadCount
						+ " entryCount: " + entryCount + " Max Length: "
						+ maxValueSize);
		for (int i = 0; i < rounds; i++) {
			final long totalTime = performCacheOperationsInThreads(i, isWarmup);
			final int tps = (int) (entryCount / (totalTime / 1000d));
			outputRoundData(i, cache.getSize(), totalTime, tps);
			results.addRound(totalTime, cache.getSize(), tps);
			if (isWarmup) {
				this.maxBatchTimeMillis.set(0);
			}
			isWarmup = false;
		}
		results.setMaxGetTime(maxGetTime.get());
		results.printResults(System.out);
	}

	private void outputRoundData(int round, int cacheSize,
			final long totalTime, final int tps) {
		System.out.println(System.currentTimeMillis() + " ROUND " + round
				+ " size: " + cacheSize);
		System.out.println(System.currentTimeMillis() + " Took: " + totalTime
				+ " final size was " + cacheSize + " TPS: " + tps);
		// csvOut.println(round + "," + cacheSize + ',' + totalTime + ',' +
		// tps);
		// csvOut.flush();
	}

	private long performCacheOperationsInThreads(final int round,
			final boolean warmup) throws InterruptedException {
		long t1 = System.currentTimeMillis();

		Thread[] threads = new Thread[threadCount];
		for (int i = 0; i < threads.length; i++) {

			final int current = i;
			threads[i] = new Thread() {

				public void run() {
					try {
						executeLoad(round, warmup, (entryCount / threadCount)
								* current, (entryCount / threadCount)
								* (current + 1));
					} catch (InterruptedException e) {
						throw new RuntimeException(e);
					}
				}
			};
			threads[i].start();
		}
		for (int i = 0; i < threads.length; i++) {
			threads[i].join();
		}
		long totalTime = (System.currentTimeMillis() - t1);
		return totalTime;
	}

	private void executeLoad(int round, final boolean warmup, final long start,
			final long entryCount) throws InterruptedException {

		byte[] value = buildValue();
		int readCount = 0;
		int writeCount = 0;
		long t = System.currentTimeMillis();
		int currentSize = 1;

		for (long i = start; i < entryCount; i++) {

			if ((i + 1) % batchCount == 0) {

				long batchTimeMillis = (System.currentTimeMillis() - t);
				synchronized (maxBatchTimeMillis) {
					maxBatchTimeMillis
							.set(batchTimeMillis > maxBatchTimeMillis.get() ? batchTimeMillis
									: maxBatchTimeMillis.get());
				}
				currentSize = cache.getSize();

				outputBatchData(round, System.currentTimeMillis(), warmup,
						value.length, readCount, writeCount, currentSize,
						batchTimeMillis);
				value = buildValue();
				t = System.currentTimeMillis();
				readCount = 0;
				writeCount = 0;

			}
			if (warmup || isWrite()) {
				Object k = createKeyFromCount(i);

				cache.put(new Element(k, value.clone()));
				writeCount++;
			}
			if (!isWrite() && !warmup) {
				long getTime = 0;
				if (readCount % threadCount == 0) {
					getTime = System.currentTimeMillis();
				}
				readEntry(createKeyFromCount(pickNextReadNumber(i, currentSize)));
				if (getTime > 0) {
					long ct = System.currentTimeMillis() - getTime;
					if (maxGetTime.get() < ct) {
						maxGetTime.set(ct);
					}
				}
				readCount++;
			}
		}

	}

	private void outputBatchData(int round, long timeMillis,
			final boolean warmup, int entrySize, int readCount, int writeCount,
			int currentSize, long batchTimeMillis) {
		System.out.println(timeMillis + " size:" + (currentSize)
				+ " batch time: " + batchTimeMillis
				+ " Max batch time millis: "
				+ (warmup ? "warmup" : ("" + maxBatchTimeMillis))
				+ " value size:" + entrySize + " READ: " + readCount
				+ " WRITE: " + writeCount + " Hotset: " + hotSetPercentage);
		csvOut.println(round + "," + timeMillis + "," + currentSize + ","
				+ batchTimeMillis + "," + warmup + "," + entrySize + ","
				+ readCount + "," + writeCount + "," + hotSetPercentage);
		csvOut.flush();

	}

	private long pickNextReadNumber(long i, int currentSize) {
		if ((i % 100) < hotSetPercentage) {
			return random.nextInt(maxOnHeapCount);
		} else {
			return random.nextInt(currentSize);
		}
	}

	private boolean isWrite() {

		return random.nextInt(100) < updatePercentage;
	}

	private Object createKeyFromCount(long i) {
		return "K" + i + "-";
	}

	private void readEntry(Object key) {

		Element e = cache.get(key);
		if (e == null) {
			return;
		}
		byte[] value = (byte[]) e.getValue();

		if (!validateValue(value))
			throw new RuntimeException("Invalid Value:");
	}

	/**
	 * create an entry of random size within the tests params and add in a basic
	 * check sum in the beginning and end of the value
	 * 
	 * @return byte[] for the value
	 */
	private byte[] buildValue() {
		Random r = new Random();
		int size = r.nextInt(maxValueSize - minValueSize + 10) + minValueSize;
		byte[] bytes = new byte[size];
		for (int i = 0; i < bytes.length; i++) {
			if (i < 5) {
				bytes[i] = (byte) i;
			} else if ((bytes.length - i) < 5) {
				bytes[i] = (byte) (bytes.length - i);
			} else {
				bytes[i] = (byte) r.nextInt(128);
			}
		}
		return bytes;
	}

	/**
	 * 
	 * @param bytes
	 *            - make sure the checksum is still legit when the entry is
	 *            retrieved
	 * @return boolean as to whether the entry is valid
	 */
	private boolean validateValue(byte[] bytes) {
		for (byte i = 0; i < 5; i++) {
			if (i != bytes[i]) {
				System.out.println(System.currentTimeMillis()
						+ " First Expected: " + i + " got: " + bytes[i]);
				return false;
			}
		}

		for (byte i = 1; i < 5; i++) {
			if (i != bytes[bytes.length - i]) {
				System.out.println(System.currentTimeMillis()
						+ " Last Expected: " + i + " got: "
						+ bytes[bytes.length - i]);
				return false;
			}
		}
		return true;
	}

	private void initializeCache(StoreType storeType, boolean monitoringEnabled) {
		Configuration cacheManagerConfig = new Configuration();

		// Add default cache
		cacheManagerConfig.addDefaultCache(new CacheConfiguration());

		// Create Cache
		CacheConfiguration cacheConfig = null;
		if (storeType.equals(StoreType.OFFHEAP)) {
			cacheConfig = new CacheConfiguration().name("testCache").eternal(true)
					.maxElementsInMemory(maxOnHeapCount)
					.overflowToOffHeap(true).maxMemoryOffHeap(offHeapSize);
		} else if (storeType.equals(StoreType.ONHEAP)) {
			cacheConfig = new CacheConfiguration().name("testCache").eternal(true)
					.maxElementsInMemory(maxOnHeapCount);
		} else if (storeType.equals(StoreType.DISK)) {
			cacheManagerConfig.addDiskStore(new DiskStoreConfiguration()
					.path(diskStorePath));
			cacheConfig = new CacheConfiguration().name("testCache")
					.eternal(true).maxBytesLocalHeap(100, MemoryUnit.MEGABYTES)
					.overflowToOffHeap(true).maxMemoryOffHeap(offHeapSize)
					.persistence(
                           new PersistenceConfiguration()
                            .strategy(Strategy.LOCALENTERPRISE));
		}

		// Enable TMC if desired.
		if(monitoringEnabled) {
			// Enable REST services
			ManagementRESTServiceConfiguration managementRESTConfig = new ManagementRESTServiceConfiguration();
			managementRESTConfig.setEnabled(true);
			managementRESTConfig.setBind("0.0.0.0:9888");
			cacheManagerConfig.addManagementRESTService(managementRESTConfig);
			
			// Monitor this cache
			cacheConfig.setStatistics(true);
		}
		
		
		cacheManagerConfig.addCache(cacheConfig);

		this.cacheManager = new CacheManager(cacheManagerConfig);
		System.out.println("Printing Ehchache configuration:");
		this.cache = this.cacheManager.getCache("testCache");
		System.out
				.println(cacheManager.getActiveConfigurationText("testCache"));
	}

	@SuppressWarnings("unchecked")
	public static final void main(String[] args) throws Exception {
		Map<String, Object> config = (Map<String, Object>) Yaml
				.load(new FileReader("config.yml"));

		System.out.println(" Printing Pounder YAML config values:");
		for (String k : config.keySet()) {
			System.out.println(k + ": " + config.get(k));
		}

		StoreType storeType = StoreType.valueOf((String) config
				.get("storeType"));
		Integer entryCount = (Integer) config.get("entryCount");
		int threadCount = (Integer) config.get("threadCount");
		String offHeapSize = (String) config.get("offHeapSize");
		int maxOnHeapCount = (Integer) config.get("maxOnHeapCount");
		int batchCount = (Integer) config.get("batchCount");
		int maxValueSize = (Integer) config.get("maxValueSize");
		int minValueSize = (Integer) config.get("minValueSize");
		int hotSetPercentage = (Integer) config.get("hotSetPercentage");
		int rounds = (Integer) config.get("rounds");
		int updatePercentage = (Integer) config.get("updatePercentage");
		String diskStorePath = (String) config.get("diskStorePath");
		Boolean monitoringEnabled = (Boolean) config.get("monitoringEnabled");
		new EhcachePounder(storeType, threadCount, entryCount, offHeapSize,
				maxOnHeapCount, batchCount, maxValueSize, minValueSize,
				hotSetPercentage, rounds, updatePercentage, diskStorePath, monitoringEnabled)
				.start();
	}

	private static final class Results {
		private final List<Round> rounds = new LinkedList<Round>();
		private final StoreType storeType;
		private long maxGetTime;

		public Results(StoreType storeType) {
			this.storeType = storeType;
		}

		public void addRound(final long elapsedTime, final int finalSize,
				final int tps) {
			rounds.add(new Round(elapsedTime, finalSize, tps));
		}

		public void setMaxGetTime(long maxGetTime) {
			this.maxGetTime = maxGetTime;
		}

		public void printResults(final PrintStream out) {
			out.println("All Rounds:");
			float tpsSum = 0;
			long timeSum = 0;
			for (int i = 0; i < rounds.size(); i++) {
				final Round round = rounds.get(i);
				if (i > 0) {
					// exclude round 1 from the averages, since it's always an
					// outlier.
					tpsSum += round.getThroughputTPS();
					timeSum += round.getElapsedTimeMillis();
				}
				out.println("Round " + (i + 1) + ": elapsed time: "
						+ round.getElapsedTimeMillis() + ", final cache size: "
						+ round.getFinalCacheSize() + ", tps: "
						+ round.getThroughputTPS());
			}
			out.println((StoreType.OFFHEAP.equals(storeType) ? "BigMemory"
					: storeType.toString()) + " Pounder Final Results");
			out.println("TOTAL TIME: " + timeSum
					+ "ms, AVG TPS (excluding round 1): " + tpsSum
					/ (rounds.size() - 1) + " MAX GET LATENCY: " + maxGetTime);
		}
	}

	private static final class Round {
		private final long elapsedTime;
		private final int finalSize;
		private final int tps;

		public Round(final long elapsedTime, final int finalSize, final int tps) {
			this.elapsedTime = elapsedTime;
			this.finalSize = finalSize;
			this.tps = tps;
		}

		public long getElapsedTimeMillis() {
			return elapsedTime;
		}

		public int getFinalCacheSize() {
			return finalSize;
		}

		public int getThroughputTPS() {
			return tps;
		}
	}
}
