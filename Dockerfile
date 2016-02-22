FROM java:8

ADD ./target/uberjar/docker-testing-demo-0.1.0-SNAPSHOT-standalone.jar /

CMD java -jar /docker-testing-demo-0.1.0-SNAPSHOT-standalone.jar
