# Application TourGuide

TourGuide is a Spring Boot application and a key part of the company's application portfolio.
It allows users to see what tourist attractions are nearby and get discounts on hotel stays
and tickets to various shows.

# Technologies

* Java 17.0.15  
* Spring Boot 3.1.1  
* Apache Maven 3.9.6  
* JUnit 5  

# Dependencies to install : gpsUtil, rewardCentral and tripPricer
* **gpsUtil :** mvn install:install-file -Dfile=/libs/gpsUtil.jar -DgroupId=gpsUtil -DartifactId=gpsUtil -Dversion=1.0.0 -Dpackaging=jar  
* **rewardCentral :** mvn install:install-file -Dfile=/libs/RewardCentral.jar -DgroupId=rewardCentral -DartifactId=rewardCentral -Dversion=1.0.0 -Dpackaging=jar  
* **tripPricer :** mvn install:install-file -Dfile=/libs/TripPricer.jar -DgroupId=tripPricer -DartifactId=tripPricer -Dversion=1.0.0 -Dpackaging=jar

[//]: # (- mvn install:install-file -Dfile=C:\Users\michaudj\IdeaProjects\oc\JavaPathENProject8\TourGuide\libs\gpsUtil.jar -DgroupId=gpsUtil -DartifactId=gpsUtil -Dversion=1.0.0 -Dpackaging=jar)
[//]: # (- mvn install:install-file -Dfile=C:\Users\michaudj\IdeaProjects\oc\JavaPathENProject8\TourGuide\libs\RewardCentral.jar -DgroupId=rewardCentral -DartifactId=rewardCentral -Dversion=1.0.0 -Dpackaging=jar)
[//]: # (- mvn install:install-file -Dfile=C:\Users\michaudj\IdeaProjects\oc\JavaPathENProject8\TourGuide\libs\TripPricer.jar -DgroupId=tripPricer -DartifactId=tripPricer -Dversion=1.0.0 -Dpackaging=jar)

# EndPoints
* **GET** ``http://localhost:8080/``
* **GET** ``http://localhost:8080/getLocation?userName=[userName]``
* **GET** ``http://localhost:8080/getNearbyAttractions?userName=[userName]``
* **GET** ``http://localhost:8080/getRewards?userName=[userName]``
* **GET** ``http://localhost:8080/getTripDeals?userName=[userName]``
