#!/usr/bin/perl -w
#
# Creates template configs based on the cache size, entry count, and whether it's on or off heap.
# 

use strict;

my ($TEMPLATE_NAME, $CACHE_SIZE_IN_GB, $OFFHEAP) = @ARGV;

my $VERSION = "0.0.3-SNAPSHOT";
my $EHCACHE_VERSION = "2.3.0";
my $POUNDER_LOGFILE = "/tmp/pounder.log";
my $POUNDER_GC_LOGFILE = "/tmp/pounder.gc.log";

my $SRC_DIR ="src";

my $TEMPLATE_BASE = "$SRC_DIR/assemble/templates";
my $TEMPLATE_DIR = $TEMPLATE_BASE . "/" . $TEMPLATE_NAME;
my $STORE_TYPE = $OFFHEAP ? "OFFHEAP" : "ONHEAP";
my $LIB_DIR = "../../";

my $offHeapSize;
my $min_heap;
if ($CACHE_SIZE_IN_GB < 1) {
  $min_heap = $OFFHEAP ? "200m" : (($CACHE_SIZE_IN_GB * 1000) + 100) . "m";
  $offHeapSize = $OFFHEAP ? (($CACHE_SIZE_IN_GB * 1000) . "M") : "0";
}
else {
  $min_heap = $OFFHEAP ? "200m" : ($CACHE_SIZE_IN_GB + 1) . "g";
  $offHeapSize = $OFFHEAP ? ($CACHE_SIZE_IN_GB) . "G" : "0";
}

my $max_heap = $min_heap;
my $max_direct_memory_size = $CACHE_SIZE_IN_GB * 2;




my $max_value_size_in_bytes = 1000;
my $min_value_size_in_bytes = 200;

my $entry_count = $CACHE_SIZE_IN_GB * 1000000000 / $max_value_size_in_bytes;
my $max_on_heap_count = $OFFHEAP ? 5000 : $entry_count;

my $rounds = ($CACHE_SIZE_IN_GB < 10 ? 40 : 4);

my $disk_store_path = "/tmp";

my @libs = (
	    ".",
	    "jyaml-1.3.jar",
	    "ehcache-pounder-$VERSION.jar",
	    "ehcache-core-ee-${EHCACHE_VERSION}.jar",
	    "slf4j-api-1.5.11.jar",
	    "slf4j-jdk14-1.5.11.jar"
	    );

my $classpath = join ":$LIB_DIR", @libs;
$classpath = $LIB_DIR . $classpath;

my $RUN_POUNDER = <<"EOF";
java -server -verbose:gc -Xloggc:${POUNDER_GC_LOGFILE} -XX:+PrintGCDetails -XX:+PrintTenuringDistribution -XX:+PrintGCTimeStamps -XX:+UseParallelGC -Dorg.terracotta.license.path=${LIB_DIR}terracotta-license.key -Xms${min_heap} -Xmx${max_heap} -XX:+UseCompressedOops -XX:MaxDirectMemorySize=${max_direct_memory_size}G -cp $classpath org.sharrissf.ehcache.tools.EhcachePounder | tee ${POUNDER_LOGFILE}
EOF

my $CONFIG_YML = <<"EOF";
storeType: ${STORE_TYPE}
threadCount: 33
entryCount: $entry_count
offHeapSize: "${offHeapSize}"
maxOnHeapCount: ${max_on_heap_count}
batchCount: 50000
maxValueSize: $max_value_size_in_bytes
minValueSize: $min_value_size_in_bytes
hotSetPercentage: 99
rounds: ${rounds}
updatePercentage: 10
diskStorePath: ${disk_store_path}
EOF

print "run-pounder.sh\n" . $RUN_POUNDER . "\n";
print "CONFIG\n" . $CONFIG_YML . "\n";

`mkdir $TEMPLATE_DIR` unless -d $TEMPLATE_DIR;
die "$TEMPLATE_DIR does not exist" unless -d $TEMPLATE_DIR;

open FILE, ">$TEMPLATE_DIR/run-pounder.sh" or die "Can't open $TEMPLATE_DIR/run-pounder.sh for writeing";
print FILE $RUN_POUNDER;
close FILE;

open FILE, ">$TEMPLATE_DIR/config.yml" or die "Can't open $TEMPLATE_DIR/config.yml for writing";
print FILE $CONFIG_YML;
close FILE;
