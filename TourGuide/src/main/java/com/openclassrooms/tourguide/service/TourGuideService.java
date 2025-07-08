package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.dto.AttractionDTO;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.model.User;
import com.openclassrooms.tourguide.model.UserReward;

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
	private static final Executor executor = Executors.newFixedThreadPool(256);
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
	 *
	 * @see User
	 * @see VisitedLocation
	 */
	public VisitedLocation getUserLocation(User user) {
		return (user.getVisitedLocations().isEmpty()) ? trackUserLocation(user) : user.getLastVisitedLocation();
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


	/** Retrieves a list of trip deals for a given user based on their preferences and reward points.
	 * This method calculates the cumulative reward points of the user and uses them along with
	 * other user preferences to fetch trip deals from a trip pricing service. The fetched trip deals
	 * are then associated with the user.
	 *
	 * @param user The user for whom trip deals are to be retrieved. This user object should contain
	 *             necessary information such as user ID, preferences, and reward points.
	 * @return A list of {@link Provider} objects representing the trip deals available for the user.
	 *         The list is also set to the user object.
	 *
	 * @see User
	 * @see UserReward
	 * @see Provider
	 */
	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(UserReward::getRewardPoints).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}


	/** Tracks the current location of a user and updates their visited locations list.
	 * This method also triggers the calculation of rewards for the user based on their location.
	 *
	 * @param user The user whose location needs to be tracked.
	 * @return The {@link VisitedLocation} object representing the user's current location.
	 *
	 * @see User
	 * @see VisitedLocation
	 */
	public VisitedLocation trackUserLocation(User user) {
		VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
		user.addToVisitedLocations(visitedLocation);
		rewardsService.calculateRewards(user);
		return visitedLocation;
	}


	/** Tracks the current locations of all users in the provided list concurrently.
	 * This method uses asynchronous processing to handle each user's location tracking
	 * and collects the results in a thread-safe manner.
	 *
	 * @param users The list of users whose locations need to be tracked.
	 * @return A list of {@link VisitedLocation} objects representing the current locations of all users.
	 * @throws RuntimeException if there is an error tracking the location for any user.
	 *
	 * @see User
	 * @see VisitedLocation
	 * @see CompletableFuture
	 */
	public List<VisitedLocation> trackAllUsersLocation(List<User> users) {
		List<VisitedLocation> visitedLocations = Collections.synchronizedList(new ArrayList<>());
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (User user : users) {
			CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
				try {
					VisitedLocation visitedLocation = trackUserLocation(user);
					visitedLocations.add(visitedLocation);
					return visitedLocation;
				} catch (Exception e) {
					throw new RuntimeException("Failed to track location for user: " + user.getUserName(), e);
				}
			}, executor).thenAccept(result -> {
			}).exceptionally(ex -> {
				System.err.println("An error occurred in thread " + Thread.currentThread().getName() + ": " + ex.getMessage());
				return null;
			});

			futures.add(future);
		}
		CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
		allOf.join();
		return visitedLocations;
	}


	/** Get the closest five tourist attractions to the user - no matter how far away they are.
	 *
	 * @param visitedLocation The location visited by the user, containing the user ID and location coordinates.
	 * @return A list of {@link AttractionDTO} objects representing the top five nearest attractions,
	 *         each containing the attraction name, latitude, longitude, user's latitude and longitude,
	 *         distance from the user's location, and reward points.
	 *
	 * @see VisitedLocation
	 * @see AttractionWithDistance
	 * @see AttractionDTO
	 */
	public List<AttractionDTO> getNearByAttractions(VisitedLocation visitedLocation) {
		List<Attraction> attractions = gpsUtil.getAttractions();

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


	/**
	 * Represents an attraction along with its distance.
	 *
	 * <p>This record encapsulates an {@link Attraction} object and a distance value,
	 * providing a convenient way to associate an attraction with its respective distance.
	 * The components of this record are automatically private and final, and it includes
	 * built-in methods such as {@code equals()}, {@code hashCode()}, and {@code toString()}.</p>
	 *
	 * @param attraction the attraction object
	 * @param distance the distance associated with the attraction
	 */
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
        logger.debug("Created {} internal test users.", InternalTestHelper.getInternalUserNumber());
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
