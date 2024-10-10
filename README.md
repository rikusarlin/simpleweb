# Trying out UUDIv4 and UUIDv7 primary keys in Postgre
The idea of this repo is threefold:
- Compare the time it takes to write to table with UUDIv4 and UUIDv7 primary keys
- Figure out how to Set up simple web server and connection pool with raw Java 21, without any framework
- See whether we can compile the two tools (database writers and readers) to native with GraalVM with some ease

Theory is that UUIDv4 primary keys take longer to insert when size of the table gets bigger, since the B-tree backing up the primary key index is not
that well compatible with randomness of UUIDv4. This should not happen with UUIDv7 keys, at lest not
to same extent, since UUIDv7 keys begin with timestamps, making it uniformly growing. This makes it easier to keep the B-tree in balance.
To study that, we insert rows 500k at a time and print out the average insertion time and chart these.

For reading both should, in theory, handle ewually.

## API
We are creating an API with following 4 endpoints:
- POST /api/uuid4write/:nRows (inserts nRows rows to table with UUIDv4 primary key)
- POST /api/uuid7write/:nRows (inserts nRows rows to table with UUIDv7 primary key)
- GET /api/uuid4read/:unixtimestamp/:nrows (reads nRows starting from unixstamp in seconds from ´table with UUIDv4 primary key)
- GET /api/uuid7read/:unixtimestamp/:nrows (reads nRows starting from unixstamp in seconds from table with UUIDv7 primary key)

## Setup
You need to have a PostgreSQL instance running somewhere. I run PostgreSQL version 16 in local Docker container.

First, manually create a user and database in PostgreSQL, and the run create_db.sql script in that database.

## Building
Normal build (uberjar)
```
mvn clean install
```

Native build (bindary) - requires that you have graalvm and native-image installed:
```
mvn -Pnative package
```

If the previous command fails, we need to do some "reflecting". This means we have to run the java program with an "native image agent"
while we use the program as throughly as possible. The agent creates resource files that used by native-image to figure out which 
classes to add. Running the jar with agents looks like this:


## Running (uberjar)
We can run uberjar as follows (replacing db, user and pwd with your own, PORT is not mandatory and defaults to 8090):
```
export JDBC_URL="jdbc:postgresql://localhost:5432/<db>"
export JDBC_USER="<user>"
export JDBC_PASSWORD="<pwd>"
export PORT=8090
java -jar ./target/simplewebserver-1.0-SNAPSHOT-jar-with-dependencies.jar
```

In another terminal (or browser) window, you can the run following kinds of commands - modify if you are running in different port:
- curl -X POST http://localhost:8090/api/uuid4write/500000 (write 500k rows to table with UUIDv4 primary key)
- curl -X POST http://localhost:8090/api/uuid7write/500000 (write 500k rows to table with UUIDv7 primary key)
- curl -X GET http://localhost:8090/api/uuid4read/1728223052/20 (read 20 rows from table with UUIDv4 primary key, starting from given UNIX timestamp)
- curl -X GET http://localhost:8090/api/uuid7read/1728223052/20 (read 20 rows from table with UUIDv7 primary key, starting from given UNIX timestamp)

## Running (native jar)
Native image can be run as follows (replacing db, user and pwd with your own, PORT is not mandatory and defaults to 8090):
```
export JDBC_URL="jdbc:postgresql://localhost:5432/<db>"
export JDBC_USER="<user>"
export JDBC_PASSWORD="<pwd>"
export PORT=8090

./target/simpleWebServer
```

## Results, UUIDv4 and UUIDv7 writing
| Mrows | UUIDv4 avg insert us | UUIDv7 avg insert us |
| ----- | -------------------- | -------------------- |
| 5     | 385                  | 294                  |
| 10    | 337                  | 303                  |
| 15    | 347                  | 285                  |
| 20    | 427                  | 282                  |
| 25    | 413                  | 280                  |
| 30    | 463                  | 388                  |
| 35    | 417                  | 381                  |
| 40    | 511                  | 301                  |
| 45    | 488                  | 348                  |
| 50    | 420                  | 374                  |

## Results, UUIDv4 and UUIDv7 reading
I used Postman for these tests, randomizing the start timestamp between min and max timestamps of the tables.

Test was run for 5 minutes with 20 virtual users, resulting in 4251 requests being made to both UUIDv4 and UUIDv7 endpoints. The average response times in this local setup were as follows:
- UUIDv4 endpoint: 15 ms
- UUIDv7 endpoint: 13 ms

## Conclusions, UUIDv4 vs UUIDv7 primary keys
The results show that while UUIDv7 primary key writing is faster than UUIDv4 primary key writing:
- UUIDv4, average insert time: 421 microseconds
- UUIDv7, average insert time: 324 microseconds

That is, writing to table with UUIDv7 is 30 per cent faster.  

Both average inserton times grows as number of rows in table increases. The growth rate is slighly faster for UUIDv4 primary keys than for UUIDv7 primary keys. 

Reading from UUIDv7 endpoint was slightly faster, but difference was small (15 vs 13 ms). We are talking 15 per cent faster.

Furthermore, the follwing can be said:
- For both tables, the time increases as we table size increases
- There is some variability in insert times, possibly caused by test setup. The growth is not exactly linear

So we can say that writing to table with UUIDv7 primary key is somewhat faster and reading from table with UUIDv7 primary is slightly faster, but not dramatically so. 

We may need to add yet further rows to tables to see bigger differences.

## Conclusions, Java 21 servers capabilities
Java 21 is much better suited to server stuff than previous versions - Virtual Threads are a boon to server development.
Combined with JVM's built-in web server, it is easy to set up simple web servers for example for Rest service use.

## Conclusions, GraalVM status
Native image creation has been much improved during the last few years. With the simple but non-trivial example we have (3 typical libraries were used),
I was able to build and run native binary relatively easily. Running an "agent" to figure out dynamic dependencies can be built into Maven build with native-maven-plugin.
This is encouraging.

Furthermore, native-image has basic support for flight recording, meaning that you can build a native image that is able to produce Java Flight Recorder files
to analyse with JDK Mission Control. The files produced are not as complete as those produced by a proper JVM, but it is a start.
```
        <buildArgs>
            <arg>--enable-monitoring</arg>
        </buildArgs>
```

Native image produced by this web server was 48 megabytes, or 54 megabytes with the above mentioned monitoring support added.

Uberjar version starts in roughly 540 milliseconds.

Native version starts in roughly 540 milliseonds in the first try, and then in some 23 milliseconds on my development laptop! 
This "measurement" is the time it takes for the program to display the "Server started on port 8090 with virtual threads" text on display.