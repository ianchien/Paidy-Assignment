# Scala Coding Exercises
## Introduction
This is a practice of Scala. Although I had no previous experience with Scala, there are still many parts that need further improvement or modification. However, what's important is that I learned a lot and gained valuable experience throughout this process.
## Running the program
1. `cd` to the the repo folder
2. setup [application.conf](https://github.com/ianchien/paidy-assignment/blob/main/src/main/resources/application.conf) in resources folder before running the program
3. run the command
    ```
    sbt run
    ```

4. or run the test
    ```
    sbt test
    ```

## Requirements
- Create a API server for returning an exchange rate. This should consume the [One-Frame API](https://hub.docker.com/r/paidyinc/one-frame).
- The service returns an exchange rate when provided with 2 supported currencies
- The rate should not be older than 5 minutes
- The service should support at least 10,000 successful requests per day with 1 API token

## Solution
According to the above, there are two main limitations.
1. Data needs to be constantly updated (not older than five minutes), so we need a `scheduler` to ensure the data is updated regularly.
2. We need to use the one-frame API to get currency rates. However, the drawback of the one-frame API is that a token only allows 1000 requests per day, and currently, there is only one token available for use. Therefore, based on the limitations, we need to use a `database/cache` to store the data. When a user accesses the forex service, the service will retrieve the corresponding data from the `database/cache` instead of making a request to the one-frame API every time.

## Implementaion
1. Implement a simple scheduler using threads and loops. Considering network latency and retry attempts, this scheduler will request the rates for all currencies from the one-frame API every 3 minutes. 

2. Since the data is frequently updated, has a simple format, and doesn't require preserving old data based on the current requirements, I have decided to use in-memory cache to store the data. In this case, a simple hashmap will be used as a memory cache.

## Improvment and Enhancement

1. Currently, the scheduler and cache are implemented within the OneFrameClient object. Ideally, they should be separated into independent interfaces, which would provide better decoupling and implementation flexibility. Furthermore, introducing a messaging queue could enhance asynchronous processing and further improve decoupling effects.

2. The exception handling is not yet fully completed, and the HTTP response also has no implementation for returning error messages.

3. Replacing the scheduler's thread with the Cats-effect framework would provide better efficiency and scalability. Currently, a regular thread-based approach is being used.

4. Adding an HTTP request interface to send HTTP requests to other services, It has better mocking of HTTP request responses during unit testing.

5. Containerize the project.
