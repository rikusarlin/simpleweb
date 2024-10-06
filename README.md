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
mvn clean install -P
```

## Running
We can then run ìnsertion (for UUIDv4) as follows (replacing db, user and pwd with your own):
```
export JDBC_URL="jdbc:postgresql://localhost:5432/<db>"
export DB_USERNAME="<user>"
export DB_PASSWORD="<pwd>"
java -jar ./target/simplewebserver-1.0-SNAPSHOT-jar-with-dependencies.jar
```

In another terminal (or browser) window, you can the run following kinds of commands
- curl -X POST http://localhost:8080/api/uuid4write/500000 (write 500k rows to table with UUIDv4 primary key)
- curl -X POST http://localhost:8080/api/uuid7write/500000 (write 500k rows to table with UUIDv7 primary key)
- curl -X GET http://localhost:8080/api/uuid4read/1728223052/20 (read 20 rows from table with UUIDv4 primary key, starting from given UNIX timestamp)
- curl -X GET http://localhost:8080/api/uuid7read/1728223052/20 (read 20 rows from table with UUIDv7 primary key, starting from given UNIX timestamp)

## Results, UUIDv4 and UUIDv7 writing
| Mrows | UUIDv4 avg insert us | UUIDv7 avg insert us |
| ----- | -------------------- | -------------------- |
| 0,5   | 441,234              | 365,824              |
| 1     | 443,737              | 360,929              |
| 1,5   | 508,891              | 379,562              |
| 2     | 517,167              | 352,044              |
| 2,5   | 521,498              | 355,281              |
| 3     | 529,393              | 380,372              |
| 3,5   | 515,762              | 510,768              |
| 4     | 478,7                | 461,496              |
| 4,5   | 472,741              | 504,573              |
| 5     | 484,769              | 513,353              |
| 5,5   | 532,012              | 506,307              |
| 6     | 526,264              | 516,015              |
| 6,5   | 530,776              | 475,822              |
| 7     | 525,49               | 501,867              |
| 7,5   | 532,947              | 543,258              |
| 8     | 531,531              | 475,448              |
| 8,5   | 533,196              | 502,351              |
| 9     | 552,601              | 504,261              |
| 9,5   | 534,087              | 524,243              |
| 10    | 495,192              | 457,293              |

## Results, UUIDv4 and UUIDv7 reading
I used Postman for these tests, randomizing the start timestamp between min and max timestamps of the tables.

Test was run for 5 minutes with 20 virtual users, resulting in 4251 requests being made to both UUIDv4 and UUIDv7 endpoints. The average response times in this local setup were as follows:
- UUIDv4 endpoint: 15 ms
- UUIDv7 endpoint: 13 ms

## Conclusions, UUIDv4 vs UUIDv7 primary keys
The results show that while UUIDv7 primary key writing is faster than UUIDv4 primary key writing, the difference is not that big:
- UUIDv4, average insert time: 510 microseconds
- UUIDv7, average insert time: 460 microseconds

That is, writing to table with UUIDv7 is 11 per cent faster.  

Reading from UUIDv7 endpoint was slightly faster, but difference was small (15 vs 13 ms). We are talking 15 per cent faster.

Furthermore, the follwing can be said:
- For both tables, the time increases as we table size increases
- There wide variability in insert times (possibly caused by test setup)

So we can say, that writing to and reading from table with UUIDv7 primary key is slightly faster, but not dramatically so. 

We may need to add further rows to tables to see more differences.

## Conclusions, Java 21 servers capabilities
Java 21 is much better suited to server stuff than previous versions - Virtual Threads are a boon to server development.
Combined with JVM's built-in web server, it is easy to set up simple web servers for example for Rest service use.

## Conclusions, GraalVM status
TBD
