package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.DTO.AttractionDTO;
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
	private final Executor executor = Executors.newFixedThreadPool(132); // old=52 // 32
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
//		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
//				: trackUserLocation(user);
//		return visitedLocation;
		// NOTE 250630 : Ré-écriture pour meilleur lisibilité et compréhension
		return (user.getVisitedLocations().isEmpty()) ? trackUserLocation(user)
				: user.getLastVisitedLocation();
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

	public VisitedLocation trackUserLocation(User user) {
		VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
		user.addToVisitedLocations(visitedLocation);
		// NOTE 250624 : Pourquoi on déclenche calculateRewards ici ???? On renvoi un VisitedLocation (userId, location, timeVisited).
		rewardsService.calculateRewards(user);
		return visitedLocation;
	}

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
	public List<AttractionDTO> getTop5Attractions(VisitedLocation visitedLocation) {

		// NOTE 250623 : Récupère les attractions
		List<Attraction> attractions = gpsUtil.getAttractions();

		// NOTE 250630 : Calculer les distances et trier les attractions
		List<Attraction> topFiveAttractionsNear = attractions.stream()
				.map(attraction -> {
					Location attractionLocation = new Location(attraction.latitude, attraction.longitude);
					double distance = rewardsService.getDistance(visitedLocation.location, attractionLocation);
					return new AttractionWithDistance(attraction, distance);
				})
				.sorted(Comparator.comparingDouble(AttractionWithDistance::distance))
				.limit(5)
				.map(AttractionWithDistance::attraction)
				.toList();

		// NOTE 250630 : Log les attractions sélectionnées
		IntStream.range(0, topFiveAttractionsNear.size()).forEach(i -> {
			Attraction att = topFiveAttractionsNear.get(i);
		});

		// NOTE 250630 : Get reward points for the selected attractions and create AttractionDTO list
		List<AttractionDTO> attractionInfoList = topFiveAttractionsNear.stream()
				.map(att -> {
					Location attractionLocation = new Location(att.latitude, att.longitude);
					double distance = rewardsService.getDistance(visitedLocation.location, attractionLocation);
					int rewardPoints = rewardsService.getRewardPoints(att, visitedLocation.userId);
					return new AttractionDTO(
							att.attractionName,
							att.latitude,
							att.longitude,
							visitedLocation.location.latitude,
							visitedLocation.location.longitude,
							distance,
							rewardPoints
					);
				})
				.toList();

		return attractionInfoList;
	}

	// NOTE 250630 : Définir le record AttractionWithDistance.
	// Les composants sont automatiquement privés et finals.
	// Pour chaque composant, un accesseur (méthode getter) est généré.
	// Méthodes automatiques : equals(), hashCode(), et toString().
	record AttractionWithDistance(Attraction attraction, double distance) {
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
