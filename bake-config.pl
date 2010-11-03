#!/usr/bin/perl -w
#
# Creates template configs based on the cache size, entry count, and whether it's on or off heap.
# 

use strict;

my $VERSION = "0.0.2-SNAPSHOT";

my $SRC_DIR ="src";

my $TEMPLATE_BASE = "$SRC_DIR/assemble/templates";
my $TEMPLATE_NAME = "20GB-BigMemory";
my $TEMPLATE_DIR = $TEMPLATE_BASE . "/" . $TEMPLATE_NAME;
my $CACHE_SIZE_IN_GB = 20;
my $OFFHEAP = 1;
my $STORE_TYPE = $OFFHEAP ? "OFFHEAP" : "ONHEAP";
my $LIB_DIR = "../../";


my $min_heap = $OFFHEAP ? "200m" : ($CACHE_SIZE_IN_GB + 1) . "g";
my $max_heap = $min_heap;
my $max_direct_memory_size = $CACHE_SIZE_IN_GB * 2;

my $max_value_size_in_bytes = 1000;
my $min_value_size_in_bytes = 200;

my $entry_count = $CACHE_SIZE_IN_GB * 1000000000 / $max_value_size_in_bytes;

my $disk_store_path = "/tmp";

my @libs = (
	    ".",
	    "jyaml-1.3.jar",
	    "ehcache-pounder-$VERSION.jar",
	    "ehcache-core-ee.jar",
	    "slf4j-api-1.5.11.jar",
	    "slf4j-jdk14-1.5.11.jar"
	    );

my $classpath = join ":$LIB_DIR", @libs;
$classpath = $LIB_DIR . $classpath;

my $RUN_POUNDER = <<"EOF";
java -verbose:gc  -Xms${min_heap} -Xmx${max_heap} -XX:+UseCompressedOops -XX:MaxDirectMemorySize=${max_direct_memory_size}G -cp $classpath org.sharrissf.ehcache.tools.EhcachePounder
EOF

my $CONFIG_YML = <<"EOF";
storeType: ${STORE_TYPE}
threadCount: 33
entryCount: $entry_count
offHeapSize: "${CACHE_SIZE_IN_GB}G"
maxOnHeapCount: 5000
batchCount: 50000
maxValueSize: $max_value_size_in_bytes
minValueSize: $min_value_size_in_bytes
hotSetPercentage: 99
rounds: 40
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

open FILE, ">$TEMPLATE_DIR/config.yml" or die "CAn't open $TEMPLATE_DIR/config.yml for writing";
print FILE $CONFIG_YML;
close FILE;
