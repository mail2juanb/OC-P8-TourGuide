package com.openclassrooms.tourguide.controller;

import java.util.List;

import com.openclassrooms.tourguide.dto.AttractionDTO;
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


    @Autowired
    TourGuideService tourGuideService;


    /** GET request that returns a welcome message
     *
     * @return a string message
     */
    @RequestMapping("/")
    public String index() {
        return "Greetings from TourGuide!";
    }


    /** GET request that returns the VisitedLocation (latitude, longitude and time visited) of a userName
     *
     * @param userName string of the userName
     * @return {@link VisitedLocation} of the userName requested
     */
    @RequestMapping("/getLocation")
    public VisitedLocation getLocation(@RequestParam String userName) {
        return tourGuideService.getUserLocation(getUser(userName));
    }


    /** GET request that return the closest five tourist attractions to the user - no matter how far away they are.
     *
     * @param userName the username for which to find nearby attractions
     * @return a list of {@link AttractionDTO} objects representing nearby attractions
     *
     */
    @RequestMapping("/getNearbyAttractions")
    public List<AttractionDTO> getNearbyAttractions(@RequestParam String userName) {
        VisitedLocation visitedLocation = tourGuideService.getUserLocation(getUser(userName));
        return tourGuideService.getNearByAttractions(visitedLocation);
    }


    /** GET request that return a list of UserReward (VisitedLocation, AttractionDTO, rewardPoints) of a userName
     *
     * @param userName string of the userName
     * @return a list of {@link UserReward} of the userName requested
     */
    @RequestMapping("/getRewards")
    public List<UserReward> getRewards(@RequestParam String userName) {
        return tourGuideService.getUserRewards(getUser(userName));
    }


    /** GET request that return a list of Provider (name, price, tripId) of a userName
     *
     * @param userName string of the userName
     * @return a list of {@link Provider} of the userName requested
     */
    @RequestMapping("/getTripDeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
        return tourGuideService.getTripDeals(getUser(userName));
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