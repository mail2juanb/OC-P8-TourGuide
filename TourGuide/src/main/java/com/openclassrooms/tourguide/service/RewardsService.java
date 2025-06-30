package com.openclassrooms.tourguide.service;

import java.util.*;
import java.util.concurrent.*;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;

	// FIXME 250630 : Quelle valeur choisir, l'échelle est trop grande
	private final ExecutorService executorService = Executors.newFixedThreadPool(400);


	
	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}
	
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}
	
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}


	public void calculateRewards(User user) {
		// NOTE 250618 : Modification afin d'éviter l'erreur ConcurrentModificationException
//		List<VisitedLocation> userLocations = user.getVisitedLocations();
		CopyOnWriteArrayList<VisitedLocation> userLocations = new CopyOnWriteArrayList<>(user.getVisitedLocations());
		List<Attraction> attractions = gpsUtil.getAttractions();

		for (VisitedLocation visitedLocation : userLocations) {
			for (Attraction attraction : attractions) {
				// NOTE 250623 : Ré-écriture pour meilleure compréhension
//				if(user.getUserRewards().stream().filter(r -> r.attraction.attractionName.equals(attraction.attractionName)).count() == 0) {
				if (user.getUserRewards().stream().noneMatch(r -> r.attraction.attractionName.equals(attraction.attractionName))) {
					if (nearAttraction(visitedLocation, attraction)) {
						UserReward reward = new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user));
						user.addUserReward(reward);
						//System.out.println(reward);
					}
				}
			}
		}
	}

	// NOTE 250627 : Nouvelle méthode qui gère les listes
	public void calculateAllUsersRewards(List<User> users) {
		users.forEach(user -> executorService.submit(new Thread(() -> calculateRewards(user))));
		executorService.shutdown();
		try {
			executorService.awaitTermination(20, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}
	
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}

	private int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

	// NOTE 250620 : Création de la méthode. Plus simple d'utiliser directement l'UUID
	public int getRewardPoints(Attraction attraction, UUID userId) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, userId);
	}

	public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);
        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));
        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
	}
}
