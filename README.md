# scala3-cats3
test task

# prerequisites
java >= 21, sbt, docker engine, docker-compose
# run
1. navigate to db folder and execute command:
`docker-compose up`
this will start postgresql image and fill the db with test data
2. navigate to the root of the project and execute command:
`sbt "runMain com.github.olegbogaty.oradian.App"`
3. In your browser go to http://localhost:8080/docs to open SwaggerUI
4. for tests use `sbt test`
5. run test coverage `sbt coverageReport`, then open `target/scala-3.5.2/scoverage-report/index.html`
6. default log level is Debug
7. // refactor with [cats-actors](https://github.com/suprnation/cats-actors) (optional)

the default postgres port is 5454
the default http server port is 8080
