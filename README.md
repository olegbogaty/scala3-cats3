# oradian
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
5. default lof level is Debug
6. // refactor with cats actor (optional)

the default postgres port is 5454
the default http server port is 8080
