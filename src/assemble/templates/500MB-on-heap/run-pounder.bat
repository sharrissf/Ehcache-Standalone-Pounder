java -server -verbose:gc -Xloggc:%TEMP%\pounder.gc.log -XX:+PrintGCDetails -XX:+PrintTenuringDistribution -XX:+PrintGCTimeStamps -XX:+UseParallelGC -Dorg.terracotta.license.path=..\..\terracotta-license.key -Xms600m -Xmx600m -XX:+UseCompressedOops -XX:MaxDirectMemorySize=1G -cp ..\..\.;..\..\jyaml-1.3.jar;..\..\ehcache-pounder-0.0.11-SNAPSHOT.jar;..\..\ehcache-core-ee.jar;..\..\ehcache-ee-rest-agent.jar;..\..\slf4j-api-1.6.1.jar;..\..\slf4j-jdk14-1.6.1.jar;..\..\ehcache-terracotta-ee.jar;..\..\terracotta-toolkit-1.6-runtime-ee-5.0.0.jar org.sharrissf.ehcache.tools.EhcachePounder 
