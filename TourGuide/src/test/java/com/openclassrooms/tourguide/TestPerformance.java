package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
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

	/*
	 * Remarque sur les améliorations de performances :
	 *
	 * Le nombre d'utilisateurs générés pour les tests à haut volume peut être facilement
	 * ajusté à l'aide de cette méthode :
	 *
	 * InternalTestHelper.setInternalUserNumber(100000);
	 *
	 *
	 * Ces tests peuvent être modifiés pour s'adapter à de nouvelles solutions, à condition que
	 * les mesures de performance à la fin des tests restent cohérentes.
	 *
	 * Voici les mesures de performance que nous essayons d'atteindre :
	 *
	 * highVolumeTrackLocation : 100 000 utilisateurs en 15 minutes :
	 * assertTrue(TimeUnit.MINUTES.toSeconds(15) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 *
	 * highVolumeGetRewards : 100 000 utilisateurs en 20 minutes :
	 * assertTrue(TimeUnit.MINUTES.toSeconds(20) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 */


	// NOTE 250618 : Le test était désactivé lorsque j'ai récupéré l'application
	//@Disabled
	@Test
	public void highVolumeTrackLocation() {

		// 250618 NOTE : Constante pour gérer les différents cas
		final int NUM_USERS = 100;
		final int TIME_THRESHOLD_SECONDS = 15;


		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		// Users should be incremented up to 100,000, and test finishes within 15
		// minutes
		InternalTestHelper.setInternalUserNumber(NUM_USERS);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		List<User> allUsers = new ArrayList<>();
		allUsers = tourGuideService.getAllUsers();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		for (User user : allUsers) {
			tourGuideService.trackUserLocation(user);
		}
		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		System.out.println("highVolumeTrackLocation: Time Elapsed: "
				+ TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");

		// NOTE 250618 : J'ai changé le code pour passer en secondes. C'est mieux d'avoir toujours la même unité
//		assertTrue(TimeUnit.MINUTES.toSeconds(75) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
		assertTrue(TIME_THRESHOLD_SECONDS >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}


	// NOTE 250618 : Le test était désactivé lorsque j'ai récupéré l'application
	//@Disabled
	@Test
	public void highVolumeGetRewards() {

		// 250618 NOTE : Constante pour gérer les différents cas
		final int NUM_USERS = 100;
		final int TIME_THRESHOLD_SECONDS = 1200;


		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		// Users should be incremented up to 100,000, and test finishes within 20
		// minutes
		InternalTestHelper.setInternalUserNumber(NUM_USERS);
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		Attraction attraction = gpsUtil.getAttractions().get(0);
		List<User> allUsers = new ArrayList<>();
		allUsers = tourGuideService.getAllUsers();
		allUsers.forEach(u -> u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date())));

		allUsers.forEach(u -> rewardsService.calculateRewards(u));

		for (User user : allUsers) {
			assertTrue(user.getUserRewards().size() > 0);
		}
		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		System.out.println("highVolumeGetRewards: Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime())
				+ " seconds.");
		assertTrue(TIME_THRESHOLD_SECONDS >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}

}
