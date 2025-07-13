# Assignment Service

The Assignment Service manages all assignments in MEITREX. It supports:

- **Exercise sheets** and **physical tests** from TMS
- **Code assignments** graded via GitHub Classroom

It handles assignment creation, grading, and publishing grading results. External results (like GitHub autograding) or assignments (GitHub, TMS) are fetched on demand.

## Environment variables
### Relevant for deployment
| Name                       | Description                        | Value in Dev Environment                            | Value in Prod Environment                                                  |
|----------------------------|------------------------------------|-----------------------------------------------------|----------------------------------------------------------------------------|
| spring.datasource.url      | PostgreSQL database URL            | jdbc:postgresql://localhost:1132/assignment_service | jdbc:postgresql://assignment-service-db-postgresql:5432/assignment-service |
| spring.datasource.username | Database usernam                   | root                                                | gits                                                                       |
| spring.datasource.password | Database password                  | root                                                | *secret*                                                                   |
| DAPR_HTTP_PORT             | Dapr HTTP Port*                    | 1100                                                | 3500                                                                       |
| server.port                | Port on which the application runs | 1101                                                | 1101                                                                       |
| course_service.url         | URL for course service GraphQL     | http://localhost:2001/graphql                       | http://localhost:3500/v1.0/invoke/course-service/method/graphql            |
| content_service.url        | URL for content service GraphQL    | http://localhost:4001/graphql                       | http://localhost:3500/v1.0/invoke/content-service/method/graphql           |
| user_service.url           | URL for user service GraphQL       | http://localhost:5001/graphql                       | http://localhost:3500/v1.0/invoke/user-service/method/graphql              |
| github.organization_name | GitHub org for managing and grading code assignments | MEITREX-TEST | MEITREX-ASSIGNMENTS |
### Other properties

| Name                                    | Description                               | Value in Dev Environment                | Value in Prod Environment               |
|-----------------------------------------|-------------------------------------------|-----------------------------------------|-----------------------------------------|
| spring.graphql.graphiql.enabled         | Enable GraphiQL web interface for GraphQL | true                                    | true                                    |
| spring.graphql.graphiql.path            | Path for GraphiQL when enabled            | /graphiql                               | /graphiql                               |
| spring.profiles.active                  | Active Spring profile                     | dev                                     | prod                                    |
| spring.jpa.properties.hibernate.dialect | Hibernate dialect for PostgreSQL          | org.hibernate.dialect.PostgreSQLDialect | org.hibernate.dialect.PostgreSQLDialect |
| spring.datasource.driver-class-name     | JDBC driver class                         | org.postgresql.Driver                   | org.postgresql.Driver                   |
| spring.sql.init.mode                    | SQL initialization mode                   | always                                  | always                                  |
| spring.jpa.show-sql                     | Show SQL queries in logs                  | true                                    | false                                   |
| spring.sql.init.continue-on-error       | Continue on SQL init error                | true                                    | true                                    |
| spring.jpa.hibernate.ddl-auto           | Hibernate DDL auto strategy               | create                                  | update                                  |
| logging.level.root                      | Logging level for root logger             | DEBUG                                   | -                                       |
| DAPR_GRPC_PORT                          | Dapr gRPC Port                            | -                                       | 50001                                   |

## API description

The GraphQL API is described in the [api.md file](api.md).

The endpoint for the GraphQL API is `/graphql`. The GraphQL Playground is available at `/graphiql`.

## Get started
A guide how to start development can be
found in the [wiki](https://meitrex.readthedocs.io/en/latest/dev-manuals/backend/get-started.html).
