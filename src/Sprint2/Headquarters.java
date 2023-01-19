package Sprint2;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static Sprint1.RobotPlayer.directions;
import static Sprint2.CarrierSync.*;
import static Sprint2.RobotPlayer.rng;
import static Sprint2.Util.*;

public class Headquarters {

    static boolean stateLock = false;
    static MapLocation hqLocation = null;
    public static ResourceType carrierAssignment = null;
    static final double MANA_TARGET_RATE = 0.69; // between 0 - 1
    static final double LAUNCHER_SPAWN_RATE = 0.75; // between 0 - 1
    static final double MAX_ROBOTS = 0.38; // ratio of map size
    static final double MIN_ROBOTS_FOR_ANCHOR = 40; // min robots to build anchor
    static final double MAX_ANCHORS = 8; // min robots to build anchor
    static int MAP_WIDTH;
    static int MAP_HEIGHT;
    static int numAnchors = 0;
    static RobotType robotBuildType = null;
    static double[] resourceGenDerivative = new double[2]; // track resource generation

    static void run(RobotController rc) throws GameActionException {
        // runs on hq creation
        if (!stateLock) {
            MAP_WIDTH = rc.getMapWidth();
            MAP_HEIGHT = rc.getMapHeight();

            // sense any nearby wells and write them
            WellInfo[] wells = rc.senseNearbyWells();
            for (WellInfo well : wells) {
                writeWell(rc, well.getResourceType(), well.getMapLocation());
            }

            stateLock = true;
        }

        // TODO: Merge to CarrierSync
        //Make island carriers late-game.
        if (rc.getRobotCount() > MAP_HEIGHT * MAP_WIDTH / 8) rc.writeSharedArray(ISLAND_INDEX, 1);
        //In case we start losing, swap back.
        else if (rc.readSharedArray(ISLAND_INDEX) == 1) rc.writeSharedArray(ISLAND_INDEX, 0);

        // TODO: Need more robust island/anchor tracking
        // Build anchors once we have enough robots
        if (rc.canBuildAnchor(Anchor.STANDARD) && rc.getRobotCount() > MIN_ROBOTS_FOR_ANCHOR && numAnchors < MAX_ANCHORS) {
            rc.buildAnchor(Anchor.STANDARD);
            numAnchors++;
        }

        // Spawn launchers towards any enemies in vision.
        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam().opponent());
        if (enemies.length > 0) {
            rc.setIndicatorString("Enemies Detected");
            RobotInfo enemy = enemies[0];

            // Spawn a robot in the closest spot to the enemy
            if (rc.isActionReady()) {
                MapLocation[] spawnLocations = closestLocationsInActionRadius(rc, hqLocation, enemy.location);

                for (MapLocation loc : spawnLocations) {
                    if (rc.canBuildRobot(RobotType.LAUNCHER, loc)) {
                        rc.buildRobot(RobotType.LAUNCHER, loc);
                        break;
                    }
                }
            }

            //TODO: Move to LauncherSync
            for (int j = 32; j < 44; j++) {
                int read = rc.readSharedArray(j);
                int dist = distance(enemy.location, intToLoc(read));
                if (dist < 3) {
                    return;
                }
            }
            for (int j = 32; j < 44; j++) {
                if (rc.readSharedArray(j) == 0) {
                    rc.writeSharedArray(j, locToInt(enemy.location));
                    return;
                }
            }
        }

        //If we need to build anchors and don't have the resources, only build with excess.
        if (rc.getRobotCount() > MAP_HEIGHT * MAP_WIDTH / 8 && rc.getNumAnchors(Anchor.STANDARD) == 0 && numAnchors < MAX_ANCHORS) {
            //Make sure we build anchors
            rc.setIndicatorString("Saving up for an anchor");
            return;
        }

        //This causes us to never have enough resources to make an anchor, need to apply some limiters.
        // Main robot building if other conditions aren't satisfied
        if (rc.getRobotCount() < MAP_HEIGHT * MAP_WIDTH * MAX_ROBOTS) {
            if (rng.nextDouble() > LAUNCHER_SPAWN_RATE) {
                if (rc.getResourceAmount(ResourceType.ADAMANTIUM) > 50) {
                    robotBuildType = RobotType.CARRIER;
                } else {
                    robotBuildType = RobotType.LAUNCHER;
                }
            } else {
                if (rc.getResourceAmount(ResourceType.MANA) > 60) {
                    robotBuildType = RobotType.LAUNCHER;
                } else {
                    robotBuildType = RobotType.CARRIER;
                }
            }
        } else {
            rc.setIndicatorString("Max robots reached");
        }

        switch (robotBuildType) {
            case CARRIER:
                buildCarrier(rc);
                break;

            case LAUNCHER:
                buildLauncher(rc);
                break;

            case HEADQUARTERS:
                break;
        }
    }

    static void buildCarrier(RobotController rc) throws GameActionException {
        if (rc.isActionReady()) {
            // Set the resource target of carrier spawns
            if (rng.nextDouble() > MANA_TARGET_RATE) {
                setCarrierAssignment(rc, ResourceType.ADAMANTIUM);
                carrierAssignment = ResourceType.ADAMANTIUM;
            } else {
                setCarrierAssignment(rc, ResourceType.MANA);
                carrierAssignment = ResourceType.MANA;
            }

            // If not all wells have been found, spawn scout carrier in random location
            if (getNumWellsFound(rc) < NUM_WELLS_STORED) {
                // Create a list of random spawn locations sorted farthest from hq
                MapLocation[] spawnLocations = farthestLocationsInActionRadius(rc, hqLocation, hqLocation);
                List<MapLocation> randomSpawnLocations = Arrays.asList(spawnLocations);

                Collections.shuffle(randomSpawnLocations);
                for (MapLocation loc : randomSpawnLocations) {
                    if (rc.canBuildRobot(RobotType.CARRIER, loc)) {
                        rc.buildRobot(RobotType.CARRIER, loc);
                    }
                }

            } else {
                // Spawn carriers in direction of closest random well of selected type
                ArrayList<Integer> targetWellIndices = new ArrayList<>();
                for (int i = WELL_INDEX_MIN; i <= WELL_INDEX_MAX; i++) {
                    if (getWellType(rc, i).equals(carrierAssignment)) targetWellIndices.add(i);
                }

                // Pick a random well from our list of wells with the correct type
                Collections.shuffle(targetWellIndices);
                int wellIndex = targetWellIndices.get(0);

                // Spawn as close to the well as possible
                MapLocation wellLocation = getWellLocation(rc, wellIndex);
                MapLocation[] spawnLocations = closestLocationsInActionRadius(rc, hqLocation, wellLocation);

                for (MapLocation loc : spawnLocations) {
                    if (rc.canBuildRobot(RobotType.CARRIER, loc)) {
                        rc.buildRobot(RobotType.CARRIER, loc);
                        break;
                    }
                }
            }
        }
    }

    // Build launchers closest to middle of the map
    static void buildLauncher(RobotController rc) throws GameActionException {
        if (rc.isActionReady()) {
            MapLocation middle = new MapLocation(MAP_WIDTH / 2, MAP_HEIGHT / 2);
            MapLocation[] spawnLocations = closestLocationsInActionRadius(rc, hqLocation, middle);

            for (MapLocation loc : spawnLocations) {
                if (rc.canBuildRobot(RobotType.LAUNCHER, loc)) {
                    rc.buildRobot(RobotType.LAUNCHER, loc);
                    break;
                }
            }
        }
    }
}
