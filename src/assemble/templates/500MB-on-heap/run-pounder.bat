java -server -verbose:gc -Xloggc:%TEMP%\pounder.gc.log -XX:+PrintGCDetails -XX:+PrintTenuringDistribution -XX:+PrintGCTimeStamps -XX:+UseParallelGC -Dorg.terracotta.license.path=..\..\terracotta-license.key -Xms600m -Xmx600m -XX:+UseCompressedOops -XX:MaxDirectMemorySize=1G -cp ..\..\.;..\..\jyaml-1.3.jar;..\..\ehcache-pounder-0.0.9-SNAPSHOT.jar;..\..\ehcache-core-ee.jar;..\..\slf4j-api-1.6.1.jar;..\..\slf4j-jdk14-1.6.1.jar;..\..\activation-1.1.jar;..\..\asm-3.1.jar;..\..\commons-beanutils-1.8.3.jar;..\..\ehcache-ee-rest-agent.jar;..\..\jackson-core-asl-1.9.2.jar;..\..\jackson-jaxrs-1.9.2.jar;..\..\jackson-mapper-asl-1.9.2.jar;..\..\jackson-xc-1.9.2.jar;..\..\jaxb-api-2.2.2.jar;..\..\jaxb-impl-2.2.3-1.jar;..\..\jcl-over-slf4j-1.6.1.jar;..\..\jersey-client-1.12.jar;..\..\jersey-core-1.12.jar;..\..\jersey-json-1.12.jar;..\..\jersey-server-1.12.jar;..\..\jersey-servlet-1.12.jar;..\..\jettison-1.1.jar;..\..\shiro-core-1.2.0.jar;..\..\shiro-web-1.2.0.jar;..\..\jetty-continuation-7.6.3.v20120416.jar;..\..\jetty-http-7.6.3.v20120416.jar;..\..\jetty-io-7.6.3.v20120416.jar;..\..\jetty-security-7.6.3.v20120416.jar;..\..\jetty-server-7.6.3.v20120416.jar;..\..\jetty-servlet-7.6.3.v20120416.jar;..\..\jetty-util-7.6.3.v20120416.jar;..\..\javax.servlet-2.5.0.v201103041518.jar org.sharrissf.ehcache.tools.EhcachePounder 
