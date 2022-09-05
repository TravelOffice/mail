# Backend for marketing module

## 1. Prerequisites
* **JAVA:** OpenJDK 17+
* **IDE:** Intellij
* **Build environment:** Linux, MacOS, Windows
***

## 2. Service structure
A service is responsible for single functionality and independant with others, bases on microservice idea.

The service should follow clean architecture and its structure look like:
* `entities` includes declarations of entities such as User, Tour,...
* `providers` includes classes which is used to interactive with platform APIs, database,...
* `repositories` represents adapter layer, includes methods for transform data between domain layers and providers. In this layer, data retrieved from the database should be converted to the format which is most convenient for the services and entities layer.
* `services` includes services, in there an services should handle flow of data to perform a function.
* `utils` package contains util classes (miscellaneous handling), for example convert end date, simple calculations.
* `exceptions` package contains classes that are responsible for handling exceptions in Spring Boot.
* `common` package contains defined classes like enum, interface, shared and simple class