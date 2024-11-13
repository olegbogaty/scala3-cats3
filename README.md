# oradian
test task

# prerequisites
java >= 21, sbt, docker engine, docker-compose
# run
1. navidate to db folder and execute command:
`docker-compose up`
this will start postgresql image and fill the db with test data
2. navifate to the root of the project and execute command:
`sbt "runMain App"`
3. In your browser go to http://localhost:8080/docs to open SwaggerUI

the default postgres port is 5454
the default http server port is 8080