package com.openclassrooms.tourguide.service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

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


	// NOTE 250626 : Cette méthode a été ré-écrite plus bas calculateRewardsASync
	public void calculateRewards(User user) {
		// NOTE 250618 : Modification afin d'éviter l'erreur ConcurrentModificationException
//		List<VisitedLocation> userLocations = user.getVisitedLocations();
		CopyOnWriteArrayList<VisitedLocation> userLocations = new CopyOnWriteArrayList<>(user.getVisitedLocations());
		List<Attraction> attractions = gpsUtil.getAttractions();
//		CopyOnWriteArrayList<Attraction> attractions = new CopyOnWriteArrayList<>(gpsUtil.getAttractions());


		// NOTE 250624 : Ré-écriture pour ajouter un parallelStream pour traiter les VisitedLocation
		// NOTE 250625 : Version originale			---		1000u -> 15s
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

		// NOTE 250625 : Version parallelStream sur la 1ere boucle			---		1000u -> 15s
//		userLocations.parallelStream().forEach(visitedLocation -> {
//			attractions.stream()
//					.filter(attraction ->
//							user.getUserRewards().stream()
//									.noneMatch(r -> r.attraction.attractionName.equals(attraction.attractionName)))
//					.filter(attraction -> nearAttraction(visitedLocation, attraction))
//					.forEach(attraction -> {
//						user.addUserReward(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
//					});
//		});
//	}

		// NOTE 250626 : Version parallelStream sur la 2e boucle			---		1000u -> 15s
//		userLocations.forEach(visitedLocation -> {
//			attractions.parallelStream().forEach(attraction -> {
//				// Vérification pour s'assurer qu'une récompense similaire n'existe pas déjà
//				boolean rewardExists = user.getUserRewards().stream()
//						.anyMatch(r -> r.attraction.attractionName.equals(attraction.attractionName));
//
//				if (!rewardExists && nearAttraction(visitedLocation, attraction)) {
//					// Ajouter une récompense à l'utilisateur
//					user.addUserReward(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
//				}
//			});
//		});
//	}

	// NOTE 250626 : Version avec CompletableFuture sur l'intérieur de la 2e boucle 			---		1000u -> 17s (déclenche une erreur sur le test nearAllAttractions)
//		ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
//
//		List<CompletableFuture<Void>> futures = new ArrayList<>();
//
//		for (VisitedLocation visitedLocation : userLocations) {
//			for (Attraction attraction : attractions) {
//				CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//					boolean rewardExists = user.getUserRewards().stream()
//							.anyMatch(r -> r.attraction.attractionName.equals(attraction.attractionName));
//					if (!rewardExists && nearAttraction(visitedLocation, attraction)) {
//						// Ajouter une récompense à l'utilisateur
//						user.addUserReward(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
//					}
//				}, executor);
//				futures.add(future);
//			}
//		}
//
//		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//		executor.shutdown();
//	}

		// NOTE 250626 : Version avec Executor 			---		1000u -> 15s
//		ExecutorService executorService = Executors.newFixedThreadPool(49); // Utilisation d'un pool de 4 threads
//
//		for (VisitedLocation visitedLocation : userLocations) {
//			executorService.submit(() -> {
//				for (Attraction attraction : attractions) {
//					if (user.getUserRewards().stream().noneMatch(r -> r.attraction.attractionName.equals(attraction.attractionName))) {
//						if (nearAttraction(visitedLocation, attraction)) {
//							user.addUserReward(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
//						}
//					}
//				}
//			});
//		}
//
//		executorService.shutdown();
//		try {
//			boolean terminated = executorService.awaitTermination(1, TimeUnit.MINUTES);
//			if (!terminated) {
//				System.out.println("Le service ne s'est pas terminé dans le temps imparti.");
//			}
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
//	}

	// NOTE 250626 : Méthode calculateRewards ré-écrite pour implémenter CompletableFuture
//	public CompletableFuture<Void> calculateRewardsASync(User user) {
//		CopyOnWriteArrayList<VisitedLocation> userLocations = new CopyOnWriteArrayList<>(user.getVisitedLocations());
//		List<Attraction> attractions = gpsUtil.getAttractions();
//		//CopyOnWriteArrayList<Attraction> attractions = new CopyOnWriteArrayList<>(gpsUtil.getAttractions());
//
//		// Utiliser un verrou pour éviter les conditions de course lors de l'ajout de récompenses
//		//Object lock = new Object();
//
//		// Utiliser CompletableFuture pour traiter les VisitedLocation
//		List<CompletableFuture<Void>> futures = userLocations.parallelStream().map(visitedLocation ->
//				CompletableFuture.runAsync(() -> {
//					attractions.forEach(attraction -> {
//						// Synchronisation sur un verrou pour chaque utilisateur
////						synchronized (lock) {
////							if (user.getUserRewards().stream().noneMatch(r -> r.attraction.attractionName.equals(attraction.attractionName))) {
////								if (nearAttraction(visitedLocation, attraction)) {
////									user.addUserReward(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
////								}
////							}
//						boolean rewardExists = user.getUserRewards().stream()
//							.anyMatch(r -> r.attraction.attractionName.equals(attraction.attractionName));
//						if (!rewardExists && nearAttraction(visitedLocation, attraction)) {
//							// Ajouter une récompense à l'utilisateur
//							user.addUserReward(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
//						}
////						}
//					});
//				}, executor)
//		).collect(Collectors.toList());
//
//		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
//	}


	// NOTE 250626 : Version avec CompletableFuture et parallelStream 			---		1000u -> 12s
//	public CompletableFuture<Void> calculateRewardsASync(User user) {
//
//		CopyOnWriteArrayList<VisitedLocation> userLocations = new CopyOnWriteArrayList<>(user.getVisitedLocations());
//		List<Attraction> attractions = gpsUtil.getAttractions();
//		//CopyOnWriteArrayList<Attraction> attractions = new CopyOnWriteArrayList<>(gpsUtil.getAttractions());
//
//		// Utiliser un Set concurrent pour garder une trace des récompenses
//		Set<String> rewardedAttractions = Collections.newSetFromMap(new ConcurrentHashMap<>());
//		user.getUserRewards().forEach(reward ->
//				rewardedAttractions.add(reward.attraction.attractionName)
//		);
//
//		// Utiliser CompletableFuture pour traiter les VisitedLocation en parallèle
//		List<CompletableFuture<Void>> futures = userLocations.parallelStream().map(visitedLocation ->
//				CompletableFuture.runAsync(() -> {
//					attractions.forEach(attraction -> {
//						// Vérifier si l'attraction a déjà une récompense et est proche
//						String attractionName = attraction.attractionName;
//						if (!rewardedAttractions.contains(attractionName) && nearAttraction(visitedLocation, attraction)) {
//							// Ajouter une récompense à l'utilisateur
//							user.addUserReward(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
//							rewardedAttractions.add(attractionName);
//						}
//					});
//				}, executor)
//		).collect(Collectors.toList());
//
//		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
//	}

	// NOTE 250627 : Changement du type de retour pour intégration dans la méthode calculateAllUserRewards
//	public CompletableFuture<User> calculateUserRewards(User user) {
//
//		CopyOnWriteArrayList<VisitedLocation> userLocations = new CopyOnWriteArrayList<>(user.getVisitedLocations());
//		List<Attraction> attractions = gpsUtil.getAttractions();
//		Set<String> rewardedAttractions = Collections.newSetFromMap(new ConcurrentHashMap<>());
//
//		user.getUserRewards().forEach(reward ->
//				rewardedAttractions.add(reward.attraction.attractionName)
//		);
//
//		List<CompletableFuture<Void>> futures = userLocations.parallelStream().map(visitedLocation ->
//				CompletableFuture.runAsync(() -> {
//					attractions.forEach(attraction -> {
//						String attractionName = attraction.attractionName;
//						if (!rewardedAttractions.contains(attractionName) && nearAttraction(visitedLocation, attraction)) {
//							user.addUserReward(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
//							rewardedAttractions.add(attractionName);
//						}
//					});
//				}, executor)
//		).collect(Collectors.toList());
//
//		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
//				.thenApply(v -> user);
//	}

	// NOTE 250627 : Nouvelle méthode pour gérer les listes de users
//	public CompletableFuture<List<User>> calculateAllUsersRewards(List<User> users) {
//		List<CompletableFuture<User>> userFutures = users.stream()
//				.map(user -> calculateUserRewards(user))
//				.collect(Collectors.toList());
//
//		CompletableFuture<Void> allFutures = CompletableFuture.allOf(
//				userFutures.toArray(new CompletableFuture[0])
//		);
//
//		return allFutures.thenApply(v ->
//				userFutures.stream()
//						.map(CompletableFuture::join)
//						.collect(Collectors.toList())
//		);
//	}

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

		// NOTE 250617 : Mettre en cache dans un ConcurrentHashMap.
		//  En utilisant une Map pour stocker les distances déjà calculées pour des paires de localisations,
		//  nous pouvons éviter de recalculer la distance pour les mêmes paires de points.
//		String cacheKey = getCacheKey(loc1, loc2);
//		Double cachedDistance = distanceCache.get(cacheKey);
//
//		if (cachedDistance != null) {
//			return cachedDistance;
//		}

        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;

//		distanceCache.put(cacheKey, statuteMiles);

        return statuteMiles;
	}

	// NOTE 250618 : Implémenter une méthode getCacheKey.
	//  La méthode getCacheKey génère une clé unique pour
	//  chaque paire de localisations en utilisant leurs coordonnées.
	private String getCacheKey(Location loc1, Location loc2) {
		String loc1Key = loc1.latitude + "," + loc1.longitude;
		String loc2Key = loc2.latitude + "," + loc2.longitude;

		if (loc1Key.compareTo(loc2Key) < 0) {
			return loc1Key + "|" + loc2Key;
		} else {
			return loc2Key + "|" + loc1Key;
		}
	}

}
