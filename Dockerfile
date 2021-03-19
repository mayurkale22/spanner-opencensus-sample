FROM openjdk:8
VOLUME /tmp
COPY target/spanner-opencensus-sample-1.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]