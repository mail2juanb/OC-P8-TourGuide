package com.openclassrooms.tourguide.DTO;

public class AttractionDTO {

    // Name of Tourist attraction,
    // Tourist attractions lat/long,
    // The user's location lat/long,
    // The distance in miles between the user's location and each of the attractions.
    // The reward points for visiting each Attraction.

    private String attractionName;
    private double attractionLat;
    private double attractionLong;
    private double userLat;
    private double userLong;
    private double distance;
    private int rewardPoints;

    public AttractionDTO(String attractionName, double attractionLat, double attractionLong, double userLat, double userLong, double distance, int rewardPoints) {
        this.attractionName = attractionName;
        this.attractionLat = attractionLat;
        this.attractionLong = attractionLong;
        this.userLat = userLat;
        this.userLong = userLong;
        this.distance = distance;
        this.rewardPoints = rewardPoints;
    }

    public String getAttractionName() {
        return attractionName;
    }

    public void setAttractionName(String attractionName) {
        this.attractionName = attractionName;
    }

    public double getAttractionLat() {
        return attractionLat;
    }

    public void setAttractionLat(double attractionLat) {
        this.attractionLat = attractionLat;
    }

    public double getAttractionLong() {
        return attractionLong;
    }

    public void setAttractionLong(double attractionLong) {
        this.attractionLong = attractionLong;
    }

    public double getUserLat() {
        return userLat;
    }

    public void setUserLat(double userLat) {
        this.userLat = userLat;
    }

    public double getUserLong() {
        return userLong;
    }

    public void setUserLong(double userLong) {
        this.userLong = userLong;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public int getRewardPoints() {
        return rewardPoints;
    }

    public void setRewardPoints(int rewardPoints) {
        this.rewardPoints = rewardPoints;
    }
}
