package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.attraction.AttractionInfo;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import gpsUtil.location.Attraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	// NOTE 250624 : Pool de thread pour l'utilisation de la classe CompletableFuture
	private final Executor executor = Executors.newFixedThreadPool(32); // old=52
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		
		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	/** Public method used to find either the last location visited by a user,
	 * or their current location if no location has been visited.
	 *
	 * @param user {@link User} sent
	 * @return {@link VisitedLocation} of the User requested
	 */
	public VisitedLocation getUserLocation(User user) {
		//logger.info("Method getUserLocation of {}", user.getUserName());
		// NOTE 250623 : condition ré-écrite pour une meilleure lisibilité
//		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation() : trackUserLocation(user);
//		VisitedLocation visitedLocation = (user.getVisitedLocations().isEmpty()) ? trackUserLocation(user) : user.getLastVisitedLocation();
		// NOTE 250624 : Modification de la méthode pour obtenir le résultat du Completable
//		VisitedLocation visitedLocation = (user.getVisitedLocations().isEmpty())
//				? trackUserLocation(user).join()
//				: user.getLastVisitedLocation();
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
				: trackUserLocation(user);
		// NOTE 250627 : Retour à l'original

		//logger.info("Method getUserLocation --> user.getVisitedLocations().isEmpty() = {}", user.getVisitedLocations().isEmpty());
		//logger.info("Method getUserLocation --> VisitedLocation of {} ({}) is : lat = {} / long = {}", user.getUserName(), visitedLocation.timeVisited, visitedLocation.location.latitude, visitedLocation.location.longitude);
		return visitedLocation;
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	// NOTE 250624 : Ré-écriture de cette méthode en implémentant la classe CompletableFuture afin de ne pas perdre de temps lors de l'appel à gpsUtil
	public VisitedLocation trackUserLocation(User user) {
		//logger.info("Method trackUserLocation of {}", user.getUserName());
		VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
		//logger.info("Method trackUserLocation --> getUserLocation of {} ({}) is : lat = {} / long = {}", user.getUserName(), visitedLocation.timeVisited, visitedLocation.location.latitude, visitedLocation.location.longitude);
		user.addToVisitedLocations(visitedLocation);
		//logger.info("Method trackUserLocation --> {} visited {} locations", user.getUserName(), user.getVisitedLocations().size());
		// NOTE 250624 : Pourquoi on déclenche calculateRewards ici ???? On renvoi un VisitedLocation (userId, location, timeVisited).
		rewardsService.calculateRewards(user);
		return visitedLocation;
	}

	// NOTE 250627 : Finalement on ne va pas utiliser cette façon de faire... Retour à l'original
//	public CompletableFuture<VisitedLocation> trackUserLocation(User user) {
//		return CompletableFuture.supplyAsync(() -> {
//			//logger.info("Method trackUserLocation of {}", user.getUserName());
//			return gpsUtil.getUserLocation(user.getUserId());
//		}, executor).thenApply(visitedLocation -> {
//			user.addToVisitedLocations(visitedLocation);
//			//logger.info("Method trackUserLocation --> getUserLocation of {} ({}) is : lat = {} / long = {}",
//			//		user.getUserName(), visitedLocation.timeVisited, visitedLocation.location.latitude, visitedLocation.location.longitude);
//			return visitedLocation;
//		}).thenCompose(visitedLocation -> {
//			return CompletableFuture.runAsync(() -> {
//				rewardsService.calculateRewards(user);
//			}, executor).thenApply(v -> visitedLocation);
//			// Remplacez calculateRewards par calculateRewardsAsync
////			return rewardsService.calculateRewardsASync(user).thenApply(v -> visitedLocation);
//		}).thenApply(visitedLocation -> {
//			//logger.info("Method trackUserLocation --> {} visited {} locations",
//			//		user.getUserName(), user.getVisitedLocations().size());
//			return visitedLocation;
//		});
//	}

	// NOTE 250627 : Nouvelle méthode pour gérer les listes
	public List<VisitedLocation> trackAllUsersLocation(List<User> users) {
		// Use thread-safe list or gather results with CompletableFuture
		List<VisitedLocation> visitedLocations = Collections.synchronizedList(new ArrayList<>());
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (User user : users) {
			CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
				VisitedLocation visitedLocation = trackUserLocation(user);
				visitedLocations.add(visitedLocation);
				return visitedLocation;
			}, executor).thenAccept(result -> {});

			futures.add(future);
		}

		CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
		allOf.join();

		return visitedLocations;
	}


	// NOTE 250623 : Cette méthode est remplacée par getTop5Attractions
	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
		List<Attraction> nearbyAttractions = new ArrayList<>();
		for (Attraction attraction : gpsUtil.getAttractions()) {
			if (rewardsService.isWithinAttractionProximity(attraction, visitedLocation.location)) {
				nearbyAttractions.add(attraction);
			}
		}

		return nearbyAttractions;
	}


	// NOTE 250623 : Cette méthode remplace getNearByAttractions car la demande client a évoluée
	//  Instead: Get the closest five tourist attractions to the user - no matter how far away they are.
	public List<AttractionInfo> getTop5Attractions(VisitedLocation visitedLocation) {
		logger.info("Method getTop5Attractions --> start with visitedLocation.userId = {}", visitedLocation.userId);

		// NOTE 250623 : Récupère les attractions
//		logger.info("Method getTop5Attractions --> Récupère les attractions");
		List<Attraction> attractions = gpsUtil.getAttractions();
//		if (attractions.isEmpty()) {
//			logger.info("Method getTop5Attractions --> Aucune attraction récupérée");
//		} else {
//			logger.info("Method getTop5Attractions --> {} attractions récupérées", attractions.size());
//		}

		// NOTE 250623 : Calcul de la distance entre l'utilisateur et chaque attraction
//		logger.info("Method getTop5Attractions --> Calcul de la distance entre l'utilisateur et les attractions");
		Map<String, Double> mapDistance = new HashMap<>();
		for (Attraction attraction : attractions) {
			Location attractionLocation = new Location(attraction.latitude, attraction.longitude);
			mapDistance.put(attraction.attractionName, rewardsService.getDistance(visitedLocation.location, attractionLocation));
		}

		// NOTE 250423 : Trie des attractions par distance, de la plus petite à la plus grande
		logger.info("Method getTop5Attractions --> Trie les entrées de la liste");
		List<Map.Entry<String, Double>> distanceSort = new ArrayList<>(mapDistance.entrySet());
		distanceSort.sort(Map.Entry.comparingByValue());

		// NOTE 250623 : Filtre les 5 attractions les plus proches
		logger.info("Method getTop5Attractions --> Filtre les 5 attractions les plus proches");
		List<Map.Entry<String, Double>> distanceSortLimit = distanceSort.stream().limit(5).toList();
		for (Map.Entry<String, Double> entry : distanceSortLimit) {
			logger.info("Method getTop5Attractions --> AttractionName = {} // Distance = {}", entry.getKey(), entry.getValue());
		}

		// NOTE 250623 : création de la liste des 5 attractions les plus proches
		logger.info("Method getTop5Attractions --> Création de la liste des 5 attractions les plus proches");
		List<String> attractionNameSelected = distanceSortLimit.stream()
				.map(Map.Entry::getKey)
				.toList();
		List<Attraction> topFiveAttractionsNear = attractionNameSelected.stream()
				.map(name -> attractions.stream()
						.filter(attraction -> attraction.attractionName.equals(name))
						.findFirst()
						.orElse(null))
				.filter(Objects::nonNull)
				.toList();
		int i = 1;
		for (Attraction att : topFiveAttractionsNear) {
			logger.info("Method getTop5Attractions --> {} - Attraction Selected = {}", i++, att.attractionName);
		}

		// NOTE 250623 : Récupère les RewardPoints des Attractions selected
		logger.info("Method getTop5Attractions --> Récupère les RewardPoints des Attractions sélectionnées ");
		Map<String, Integer> attractionRewardPoints = new HashMap<>();
		for (Attraction att : topFiveAttractionsNear) {
			attractionRewardPoints.put(att.attractionName, rewardsService.getRewardPoints(att, visitedLocation.userId));
		}
		for (Map.Entry<String, Integer> attRP : attractionRewardPoints.entrySet()) {
			logger.info("Method getTop5Attractions --> AttractionName = {} // RewardPoints = {}", attRP.getKey(), attRP.getValue());
		}

		// NOTE 250620 : Transforme la List<HashMap> en HashMap pour l'utiliser dans la construction de
		Map<String, Double> mapDistanceSortedLimit = distanceSortLimit.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		// NOTE 250623 : création de la liste des 5 AttractionInfo les plus proches de l'utilisateur
		List<AttractionInfo> attractionInfoList = new ArrayList<>();
		for (Attraction att : topFiveAttractionsNear) {
			AttractionInfo attractionInfo = new AttractionInfo(
					att.attractionName,
					att.latitude,
					att.longitude,
					visitedLocation.location.latitude,
					visitedLocation.location.longitude,
					mapDistanceSortedLimit.get(att.attractionName),
					attractionRewardPoints.get(att.attractionName)
			);
			attractionInfoList.add(attractionInfo);
		}

		return attractionInfoList;
	}

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
			}
		});
	}

	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}
