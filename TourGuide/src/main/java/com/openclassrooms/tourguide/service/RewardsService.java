package com.openclassrooms.tourguide.service;

import java.util.*;
import java.util.concurrent.*;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.model.User;
import com.openclassrooms.tourguide.model.UserReward;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// NOTE : proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	private final ExecutorService executorService = Executors.newFixedThreadPool(512); // old 400


	
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


	/** Calculates rewards for a user based on their visited locations and nearby attractions.
	 * This method checks each visited location of the user against a list of attractions
	 * to determine if the user qualifies for a reward. If a user is near an attraction
	 * and has not already received a reward for it, a new reward is created and added
	 * to the user's rewards list.
	 *
	 * <p>This method uses a thread-safe copy of the user's visited locations to avoid
	 * {@link java.util.ConcurrentModificationException}. It also checks for existing rewards
	 * to prevent duplicate rewards for the same attraction.</p>
	 *
	 * <p><b>Note:</b> This method is designed to be used in a multi-threaded environment.
	 * Each user's rewards are calculated independently, and the method handles thread
	 * interruptions gracefully.</p>
	 *
	 * @param user The user for whom rewards are to be calculated.
	 *
	 * @see User
	 * @see VisitedLocation
	 * @see Attraction
	 * @see UserReward
	 */
	public void calculateRewards(User user) {
//		List<VisitedLocation> userLocations = user.getVisitedLocations();
		// NOTE 250618 : Modification afin d'éviter l'erreur ConcurrentModificationException
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
					}
				}
			}
		}
	}

	// NOTE 250627 : Nouvelle méthode qui gère les listes
	// NOTE 250702 : j'ai ajouté une vérification pour voir si l'ExecutorService a terminé dans le temps imparti.
	// Si ce n'est pas le cas, un message d'avertissement est imprimé.
	// De plus, j'ai amélioré la gestion de l'exception InterruptedException en restaurant l'état d'interruption
	// du thread, ce qui est une bonne pratique lorsque vous attrapez cette exception.
	/**
	 * Calculates rewards for all users in the provided list by submitting each user's
	 * reward calculation task to an {@link java.util.concurrent.ExecutorService}.
	 * This method initiates the reward calculation process for each user in parallel
	 * and waits for the completion of all tasks within a specified time limit.
	 *
	 * <p>If the tasks do not complete within the specified time, a warning message is printed.
	 * In case of thread interruption, the method restores the interrupt status of the thread
	 * and prints an error message.</p>
	 *
	 * @param users The list of users for whom rewards are to be calculated.
	 *
	 * @see User
	 * @see java.util.concurrent.ExecutorService
	 */
	public void calculateAllUsersRewards(List<User> users) {
		users.forEach(user -> executorService.submit(new Thread(() -> calculateRewards(user))));
		executorService.shutdown();
		try {
//			executorService.awaitTermination(20, TimeUnit.MINUTES);
			// NOTE 250702 : Modification pour meilleur remonté d'erreur
			boolean terminated = executorService.awaitTermination(20, TimeUnit.MINUTES);
			if (!terminated) {
				System.err.println("Warning: The executor did not terminate within the specified time.");
			}
		} catch (InterruptedException e) {
//			e.printStackTrace();
			// NOTE 250702 : Modification pour meilleur remonté d'erreur
			Thread.currentThread().interrupt(); // Restaure l'état d'interruption
			System.err.println("Thread was interrupted while waiting for termination: " + e.getMessage());
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
