package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.commons.lang3.time.StopWatch;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

public class TestPerformance {

	/*
	 * A note on performance improvements:
	 *
	 * The number of users generated for the high volume tests can be easily
	 * adjusted via this method:
	 *
	 * InternalTestHelper.setInternalUserNumber(100000);
	 *
	 *
	 * These tests can be modified to suit new solutions, just as long as the
	 * performance metrics at the end of the tests remains consistent.
	 *
	 * These are performance metrics that we are trying to hit:
	 *
	 * highVolumeTrackLocation: 100,000 users within 15 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(15) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 *
	 * highVolumeGetRewards: 100,000 users within 20 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(20) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 */



	// NOTE 250618 : Le test était désactivé lorsque j'ai récupéré l'application
	//@Disabled
	//@Test
	// NOTE 250630 : Modification en Parametrized Test pour tester la charge avec un nombre croissant de User
	@ParameterizedTest(name = "Test with {0} users and a time threshold of {1} seconds")
	@MethodSource("provideUsersForTrackLocation")
	public void highVolumeTrackLocation(int numUsers, int timeThresholdSeconds) {

		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		// Users should be incremented up to 100,000, and test finishes within 15 minutes
		InternalTestHelper.setInternalUserNumber(numUsers);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		List<User> allUsers = tourGuideService.getAllUsers();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

//		for (User user : allUsers) {
//			tourGuideService.trackUserLocation(user);
//		}
		// NOTE 250627 : Changement de fonctionnement, utilisation de la méthode qui gère les listes
		tourGuideService.trackAllUsersLocation(allUsers);

		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		System.out.println("highVolumeTrackLocation: Time Elapsed: "
				+ TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");

		// NOTE 250618 : J'ai changé le code pour passer en secondes. C'est mieux d'avoir toujours la même unité
//		assertTrue(TimeUnit.MINUTES.toSeconds(75) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
		assertTrue(timeThresholdSeconds >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}

	private static Stream<Arguments> provideUsersForTrackLocation() {
		return Stream.of(
				Arguments.of(100, 10),
				Arguments.of(1_000, 50),
				Arguments.of(5_000, 100),
				Arguments.of(10_000, 200),
				Arguments.of(50_000, 500),
				Arguments.of(100_000, 900)
		);
	}



	// NOTE 250618 : Le test était désactivé lorsque j'ai récupéré l'application
	// NOTE 250627 : Modification de la méthode pour travailler sur la méthode qui gère les listes
	//@Test
	// NOTE 250630 : Modification en Parametrized Test pour tester la charge avec un nombre croissant de User
	@ParameterizedTest(name = "Test with {0} users and a time threshold of {1} seconds")
	@MethodSource("provideUsersForGetsRewards")
	public void highVolumeGetRewards(int numUsers, int timeThresholdSeconds) {

		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		// Users should be incremented up to 100,000, and test finishes within 20 minutes
		InternalTestHelper.setInternalUserNumber(numUsers);
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		Attraction attraction = gpsUtil.getAttractions().get(0);
		List<User> allUsers = tourGuideService.getAllUsers();

		allUsers.forEach(u -> u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date())));

//		allUsers.forEach(u -> rewardsService.calculateRewards(u));
		// NOTE 250627 : Remplacement de la boucle par celle qui gère les listes
		rewardsService.calculateAllUsersRewards(allUsers);

		for (User user : allUsers) {
//			assertTrue(user.getUserRewards().size() > 0);
			// NOTE 250630 : Simplification de l'assertion
            assertFalse(user.getUserRewards().isEmpty());
		}

		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		//Asserting part that the time is as performant as wanted
		System.out.println("highVolumeGetRewards: Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
		assertTrue(timeThresholdSeconds >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}

	private static Stream<Arguments> provideUsersForGetsRewards() {
		return Stream.of(
				Arguments.of(100, 10),
				Arguments.of(1_000, 100),
				Arguments.of(10_000, 400),
				Arguments.of(100_000, 1_200)
		);
	}

}
