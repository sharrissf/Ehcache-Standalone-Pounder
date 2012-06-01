#!/usr/bin/perl -w
#
# Creates template configs based on the cache size, entry count, and whether it's on or off heap.
# 

use strict;

my ($TEMPLATE_NAME, $CACHE_SIZE_IN_GB, $OFFHEAP) = @ARGV;

my $UNIX_LIB_DIR = "../../";
my $WIN_LIB_DIR = "..\\..\\";
my $UNIX_POUNDER_LOGFILE = "/tmp/pounder.log";
my $WIN_POUNDER_LOGFILE = "%TEMP%\\pounder.log";
my $UNIX_POUNDER_GC_LOGFILE = "/tmp/pounder.gc.log";
my $WIN_POUNDER_GC_LOGFILE = "%TEMP%\\pounder.gc.log";
my $disk_store_path = "java.io.tmpdir";

my $VERSION = "0.0.9-SNAPSHOT";

my $SRC_DIR ="src";

my $TEMPLATE_BASE = "$SRC_DIR/assemble/templates";
my $TEMPLATE_DIR = $TEMPLATE_BASE . "/" . $TEMPLATE_NAME;
my $STORE_TYPE = $OFFHEAP ? "OFFHEAP" : "ONHEAP";


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


my @libs = (
	    ".",
	    "jyaml-1.3.jar",
	    "ehcache-pounder-$VERSION.jar",
	    "ehcache-core-ee.jar",
	    "slf4j-api-1.6.1.jar",
	    "slf4j-jdk14-1.6.1.jar",
	    "activation-1.1.jar",
	    "asm-3.1.jar",
	    "commons-beanutils-1.8.3.jar",
	    "ehcache-ee-rest-agent.jar",
	    "jackson-core-asl-1.9.2.jar",
	    "jackson-jaxrs-1.9.2.jar",
	    "jackson-mapper-asl-1.9.2.jar",
	    "jackson-xc-1.9.2.jar",
	    "jaxb-api-2.2.2.jar",
	    "jaxb-impl-2.2.3-1.jar",
	    "jcl-over-slf4j-1.6.1.jar",
	    "jersey-client-1.12.jar",
	    "jersey-core-1.12.jar",
	    "jersey-json-1.12.jar",
	    "jersey-server-1.12.jar",
	    "jersey-servlet-1.12.jar",
	    "jettison-1.1.jar",
	    "shiro-core-1.2.0.jar",
	    "shiro-web-1.2.0.jar",
	    "jetty-continuation-7.6.3.v20120416.jar",
	    "jetty-http-7.6.3.v20120416.jar",
	    "jetty-io-7.6.3.v20120416.jar",
	    "jetty-security-7.6.3.v20120416.jar",
	    "jetty-server-7.6.3.v20120416.jar",
	    "jetty-servlet-7.6.3.v20120416.jar",
	    "jetty-util-7.6.3.v20120416.jar",
	    "javax.servlet-2.5.0.v201103041518.jar",
	    );

my $unixClasspath = join ":$UNIX_LIB_DIR", @libs;
$unixClasspath = $UNIX_LIB_DIR . $unixClasspath;

my $winClasspath = join ";$WIN_LIB_DIR", @libs;
$winClasspath = $WIN_LIB_DIR . $winClasspath;

my $UNIX_RUN_POUNDER = <<"EOF";
java -server -verbose:gc -Xloggc:${UNIX_POUNDER_GC_LOGFILE} -XX:+PrintGCDetails -XX:+PrintTenuringDistribution -XX:+PrintGCTimeStamps -XX:+UseParallelGC -Dorg.terracotta.license.path=${UNIX_LIB_DIR}terracotta-license.key -Xms${min_heap} -Xmx${max_heap} -XX:+UseCompressedOops -XX:MaxDirectMemorySize=${max_direct_memory_size}G -cp $unixClasspath org.sharrissf.ehcache.tools.EhcachePounder | tee ${UNIX_POUNDER_LOGFILE}
EOF

my $WIN_RUN_POUNDER = <<"EOF";
java -server -verbose:gc -Xloggc:${WIN_POUNDER_GC_LOGFILE} -XX:+PrintGCDetails -XX:+PrintTenuringDistribution -XX:+PrintGCTimeStamps -XX:+UseParallelGC -Dorg.terracotta.license.path=${WIN_LIB_DIR}terracotta-license.key -Xms${min_heap} -Xmx${max_heap} -XX:+UseCompressedOops -XX:MaxDirectMemorySize=${max_direct_memory_size}G -cp $winClasspath org.sharrissf.ehcache.tools.EhcachePounder 
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
monitoringEnabled: false
EOF

print "run-pounder.sh\n" . $UNIX_RUN_POUNDER . "\n";
print "run-pounder.bat\n" . $WIN_RUN_POUNDER . "\n";
print "CONFIG\n" . $CONFIG_YML . "\n";

`mkdir $TEMPLATE_DIR` unless -d $TEMPLATE_DIR;
die "$TEMPLATE_DIR does not exist" unless -d $TEMPLATE_DIR;

open SH_FILE, ">$TEMPLATE_DIR/run-pounder.sh" or die "Can't open $TEMPLATE_DIR/run-pounder.sh for writing";
print SH_FILE $UNIX_RUN_POUNDER;
close SH_FILE;

open BAT_FILE, ">$TEMPLATE_DIR/run-pounder.bat" or die "Can't open $TEMPLATE_DIR/run-pounder.bat for writing";
print BAT_FILE $WIN_RUN_POUNDER;
close BAT_FILE;

open FILE, ">$TEMPLATE_DIR/config.yml" or die "Can't open $TEMPLATE_DIR/config.yml for writing";
print FILE $CONFIG_YML;
close FILE;
