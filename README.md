# Spanner - OpenCensus Example

## About Cloud Spanner

[Cloud Spanner](https://cloud.google.com/spanner/) is a fully managed, mission-critical, 
relational database service that offers transactional consistency at global scale, 
schemas, SQL (ANSI 2011 with extensions), and automatic, synchronous replication 
for high availability.

Be sure to activate the Cloud Spanner API on the Developer's Console to
use Cloud Spanner from your project.

See the [Spanner client lib docs](https://googleapis.dev/java/google-cloud-clients/latest/index.html?com/google/cloud/spanner/package-summary.html) to learn how to
interact with Cloud Spanner using this Client Library.

## About OpenCensus

OpenCensus and OpenTracing have merged to form OpenTelemetry, which serves as
the next major version of OpenCensus and OpenTracing. OpenTelemetry will offer
backwards compatibility with existing OpenCensus integrations, and we will
continue to make security patches to existing OpenCensus libraries for two years.

Read more [here](https://opencensus.io/).


### Running Sample App (Locally)
Please refer to the [getting
started](https://cloud.google.com/spanner/docs/getting-started/java/) guide.

### 1. Set up Cloud Spanner with the Expected Schema

Create a database with the following schema:

```
CREATE TABLE person (
	id STRING(MAX),
	name STRING(MAX),
	email STRING(MAX),
) PRIMARY KEY (id)
```
Make note of your project ID, instance ID, and database name.

### 2. Set Up Your Environment and Auth

Follow the [set up instructions](https://cloud.google.com/spanner/docs/getting-started/set-up) in the Cloud Spanner documentation to set up your environment and authentication. When not running on a GCE VM, make sure you run `gcloud auth application-default login`.

### 3. Specify Properties

In `PersonController.java`, specify your instance ID, database name and table name.
```
  String instanceId = "sample-instance";
  String databaseId = "sample-database";
  String table = "person";
```

### 4. To build the example
```bash
$ mvn clean package
```

### 5.  To Run the example
```bash
$ mvn exec:java -Dexec.mainClass=com.sample.spanner.App
```

Available endpoints:

 - For read request:  http://localhost:8080/spanner/read 
 - For query request:  http://localhost:8080/spanner/query

### Running Sample App (k8s)

> TODO