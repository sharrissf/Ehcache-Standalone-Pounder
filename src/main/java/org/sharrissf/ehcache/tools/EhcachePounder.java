package org.sharrissf.ehcache.tools;

import java.io.FileReader;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.DiskStoreConfiguration;

import org.ho.yaml.Yaml;

/**
 * This is a small sample app that demonstrates the bulk loading characteristics of ehcache.
 * 
 * Usage: EhcachePounder [OFFHEAP | ONHEAP | DISK] <threadCount> <entryCount> [size of offheap]
 * 
 * 
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
    private static final Random random = new Random();

    private volatile boolean isWarmup = true;
    private volatile AtomicLong maxBatchTimeMillis = new AtomicLong();

    private CacheManager cacheManager;

    private Ehcache cache;
    private final long entryCount;
    private final int threadCount;
    private final String offHeapSize;

    /**
     * 
     * @param threadCount
     *            - number of threads to run.
     * @param entryCount
     *            - number of entries to put in the cache
     * 
     * @throws InterruptedException
     */
    public EhcachePounder(StoreType storeType, int threadCount, long entryCount, String offHeapSize, int maxOnHeapCount, int batchCount,
            int maxValueSize, int minValueSize, int hotSetPercentage, int rounds, int updatePercentage) throws InterruptedException {
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
        initializeCache(storeType);
    }

    /**
     * Kicks off the run of the test for this node
     * 
     * NOTE: It will wait for nodeCount nodes before actually running
     * 
     * @throws InterruptedException
     */
    public void start() throws InterruptedException {

        System.out.println("Starting with threadCount: " + threadCount + " entryCount: " + entryCount + " Max Length: " + maxValueSize);
        for (int i = 0; i < rounds; i++) {
            performCacheOperationsInThreads(isWarmup);
            System.out.println("ROUND " + i + " size: " + cache.getSize());
            if (isWarmup) {
                this.maxBatchTimeMillis.set(0);
            }
            isWarmup = false;
        }
    }

    private void performCacheOperationsInThreads(final boolean warmup) throws InterruptedException {
        long t1 = System.currentTimeMillis();

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threads.length; i++) {

            final int current = i;
            threads[i] = new Thread() {

                public void run() {
                    try {
                        executeLoad(warmup, (entryCount / threadCount) * current, (entryCount / threadCount) * (current + 1));
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
        System.out.println("Took: " + totalTime + " final size was " + cache.getSize() + " TPS: "
                + (int) (entryCount / (totalTime / 1000d)));
    }

    private void executeLoad(final boolean warmup, final long start, final long entryCount) throws InterruptedException {

        byte[] value = buildValue();
        int readCount = 0;
        int writeCount = 0;
        long t = System.currentTimeMillis();
        int currentSize = 1;

        for (long i = start; i < entryCount; i++) {

            if ((i + 1) % batchCount == 0) {

                long batchTimeMillis = (System.currentTimeMillis() - t);
                synchronized (maxBatchTimeMillis) {
                    maxBatchTimeMillis.set(batchTimeMillis > maxBatchTimeMillis.get() ? batchTimeMillis : maxBatchTimeMillis.get());
                }
                currentSize = cache.getSize();

                System.out.println(" size:" + (currentSize) + " time: " + batchTimeMillis + " Max batch time millis: "
                        + (warmup ? "warmup" : ("" + maxBatchTimeMillis)) + " value size:" + value.length + " READ: " + readCount
                        + " WRITE: " + writeCount + " Hotset: " + hotSetPercentage);
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
                readEntry(createKeyFromCount(pickNextReadNumber(i, currentSize)));
                readCount++;
            }
        }

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

    private boolean validateValue(byte[] bytes) {
        for (byte i = 0; i < 5; i++) {
            if (i != bytes[i]) {
                System.out.println("First Expected: " + i + " got: " + bytes[i]);
                return false;
            }
        }

        for (byte i = 1; i < 5; i++) {
            if (i != bytes[bytes.length - i]) {
                System.out.println("Last Expected: " + i + " got: " + bytes[bytes.length - i]);
                return false;
            }
        }
        return true;
    }

    private void initializeCache(StoreType storeType) {
        System.getProperties().setProperty("net.sf.ehcache.offheap.testCache.config.maximumSegmentCount", "64k");
        Configuration cacheManagerConfig = new Configuration();

        // FactoryConfiguration factoryConfig = new FactoryConfiguration();
        // factoryConfig.setClass("org.terracotta.ehcachedx.monitor.probe.ProbePeerListenerFactory");
        // factoryConfig.setProperties("monitorAddress=localhost, monitorPort=9889");
        // cacheManagerConfig.addCacheManagerPeerListenerFactory(factoryConfig);

        // Add default cache
        cacheManagerConfig.addDefaultCache(new CacheConfiguration());

        // Create Cache
        CacheConfiguration cacheConfig = null;
        if (storeType.equals(StoreType.OFFHEAP)) {
            cacheConfig = new CacheConfiguration("testCache", -1).eternal(true).maxElementsInMemory(maxOnHeapCount).overflowToOffHeap(true)
                    .maxMemoryOffHeap(offHeapSize);
        } else if (storeType.equals(StoreType.ONHEAP)) {
            cacheConfig = new CacheConfiguration("testCache", -1).eternal(true);
        } else if (storeType.equals(StoreType.DISK)) {
            cacheManagerConfig.addDiskStore(new DiskStoreConfiguration().path("/export1/dev/"));
            cacheConfig = new CacheConfiguration("testCache", -1).eternal(true).maxElementsInMemory(maxOnHeapCount).overflowToOffHeap(true)
                    .maxMemoryOffHeap(offHeapSize).diskPersistent(true).diskAccessStripes(16);
        }
        cacheManagerConfig.addCache(cacheConfig);

        this.cacheManager = new CacheManager(cacheManagerConfig);

        this.cache = this.cacheManager.getCache("testCache");
    }

    @SuppressWarnings("unchecked")
    public static final void main(String[] args) throws Exception {
        Map<String, Object> config = (Map<String, Object>) Yaml.load(new FileReader("config.yml"));

        System.out.println("Printing Config Values:");
        for (String k : config.keySet()) {
            System.out.println(k + ": " + config.get(k));
        }
        StoreType storeType = StoreType.valueOf((String) config.get("storeType"));
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

        new EhcachePounder(storeType, threadCount, entryCount, offHeapSize, maxOnHeapCount, batchCount, maxValueSize, minValueSize,
                hotSetPercentage, rounds, updatePercentage).start();
    }
}