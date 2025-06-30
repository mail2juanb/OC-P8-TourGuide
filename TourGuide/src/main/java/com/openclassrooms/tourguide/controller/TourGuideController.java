package com.openclassrooms.tourguide.controller;

import java.util.List;

import com.openclassrooms.tourguide.attraction.AttractionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gpsUtil.location.VisitedLocation;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import tripPricer.Provider;

@RestController
public class TourGuideController {

    private static final Logger logger = LoggerFactory.getLogger(TourGuideController.class);
    @Autowired
    TourGuideService tourGuideService;

    /** GET request that returns a welcome message
     *
     * @return a string message
     */
    @RequestMapping("/")
    public String index() {
        String HOME_MESSAGE = "Greetings from TourGuide!";
        logger.info("Received / index");
        String USER_MESSAGE = "List of top 5 users : ";
        List<String> getUsers = tourGuideService.getAllUsers().stream().limit(5)
                .map(User::getUserName)
                .toList();
        String FINAL_MESSAGE = HOME_MESSAGE + "<br>" + "<br>" + USER_MESSAGE + "<br>" + String.join("<br>", getUsers);
        logger.info(FINAL_MESSAGE);
        return FINAL_MESSAGE;
    }

    /** GET request that returns the VisitedLocation (latitude, longitude and time visited) of a userName
     *
     * @param userName string of the userName
     * @return {@link VisitedLocation} of the userName requested
     */
    @RequestMapping("/getLocation")
    public VisitedLocation getLocation(@RequestParam String userName) {
        logger.info("Received /getLocation about {}", userName);
        VisitedLocation getLocationOf = tourGuideService.getUserLocation(getUser(userName));
//        logger.debug("userId = " + getLocationOf.userId);
//        logger.debug("latitude = " + getLocationOf.location.latitude);
//        logger.debug("longitude = " + getLocationOf.location.longitude);
//        logger.debug("longitude = " + getLocationOf.location.longitude);
//        logger.debug("timeVisited = " + getLocationOf.timeVisited);
        return getLocationOf;
    }

    //  TODO: Change this method to no longer return a List of Attractions.
    //  Instead: Get the closest five tourist attractions to the user - no matter how far away they are.
    //  Return a new JSON object that contains:
    // Name of Tourist attraction,
    // Tourist attractions lat/long,
    // The user's location lat/long,
    // The distance in miles between the user's location and each of the attractions.
    // The reward points for visiting each Attraction.
    //    Note: Attraction reward points can be gathered from RewardsCentral
//    @RequestMapping("/getNearbyAttractions")
//    public List<AttractionDTO> getNearbyAttractions(@RequestParam String userName) {
//        logger.info("Received /getNearbyAttractions about {}", userName);
//        VisitedLocation visitedLocation = tourGuideService.getUserLocation(getUser(userName));
//        logger.info("getNearbyAttractions --> VisitedLocation of {} : lat = {} / long = {}", userName, visitedLocation.location.latitude, visitedLocation.location.longitude);
//        List<AttractionDTO> attractionsOf = tourGuideService.getNearByAttractions(visitedLocation);
//        logger.info("getNearbyAttractions --> There is {} attractions near {} ", attractionsOf.size(), userName);
//        return attractionsOf;
//    }
    // NOTE 250623 : Change de méthode pour implémenter les nouveautés

    @RequestMapping("/getNearbyAttractions")
    public List<AttractionDTO> getNearbyAttractions(@RequestParam String userName) {
        logger.info("Received /getNearbyAttractions about {}", userName);
        VisitedLocation visitedLocation = tourGuideService.getUserLocation(getUser(userName));
        logger.info("getNearbyAttractions --> VisitedLocation of {} : lat = {} / long = {}", userName, visitedLocation.location.latitude, visitedLocation.location.longitude);
        List<AttractionDTO> attractionsOf = tourGuideService.getTop5Attractions(visitedLocation);
        logger.info("getNearbyAttractions --> getTop5Attractions(visitedLocation)");
        int i=1;
        for (AttractionDTO attractionDTO : attractionsOf) {
            logger.info("{} - attractionDTO = {}", i++, attractionDTO.getAttractionName());
        }
        return attractionsOf;
    }

    /** GET request that return a list of UserReward (VisitedLocation, AttractionDTO, rewardPoints) of a userName
     *
     * @param userName string of the userName
     * @return a list of {@link UserReward} of the userName requested
     */
    @RequestMapping("/getRewards")
    public List<UserReward> getRewards(@RequestParam String userName) {
        logger.info("Received /getRewards about {}", userName);
        List<UserReward> getRewardsOf = tourGuideService.getUserRewards(getUser(userName));
        logger.info("UserReward size list = {}", getRewardsOf.size());
        return getRewardsOf;
    }

    /** GET request that return a list of Provider (name, price, tripId) of a userName
     *
     * @param userName string of the userName
     * @return a list of {@link Provider} of the userName requested
     */
    @RequestMapping("/getTripDeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
        logger.info("Received /getTripDeals about {}", userName);
        List<Provider> getTripDealsOf = tourGuideService.getTripDeals(getUser(userName));
        logger.info("Provider size list = {}", getTripDealsOf.size());
        return getTripDealsOf;
    }

    /** private method used to find the User (userId, userName, phoneNumber, emailAddress) associated with the userName sent
     *
     * @param userName string of the userName
     * @return a {@link User} of the userName requested
     */
    private User getUser(String userName) {
        return tourGuideService.getUser(userName);
    }


}