package Sprint2;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static Sprint2.CarrierSync.*;
import static Sprint2.HQSync.*;
import static Sprint2.RobotPlayer.rng;
import static Sprint2.RobotSync.*;
import static Sprint2.Util.*;

public class Headquarters {

    static boolean stateLock = false;
    static MapLocation hqLocation = null;
    static int hqID = 0;
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
    static int previousCarrierID = 0;
    static double adIncome;
    static double mnIncome;
    ArrayList<Integer> Well0Robots = new ArrayList<>();
    ArrayList<Integer> Well1Robots = new ArrayList<>();

    static void run(RobotController rc) throws GameActionException {
        // runs on hq creation
        if (!stateLock) {
            MAP_WIDTH = rc.getMapWidth();
            MAP_HEIGHT = rc.getMapHeight();
            hqLocation = rc.getLocation();

            hqID = readNumHQs(rc);
            writeHQLocation(rc, hqLocation, hqID);
            System.out.println("Created HQ " + hqID + " at " + readHQLocation(rc, hqID));

            // sense any nearby wells and write them, maximum of 2 assigned per hq
            WellInfo[] wells = rc.senseNearbyWells();
            for (int i = 0; i < Math.min(wells.length, 2); i++) {
                writeWell(rc, wells[i].getResourceType(), wells[i].getMapLocation(), hqID);
            }

            stateLock = true;
        }

        // TODO: Merge to CarrierSync
        //Make island carriers late-game.
        System.out.println("Bytecode 1: " + Clock.getBytecodeNum());
        if (rc.getRobotCount() > MAP_HEIGHT * MAP_WIDTH / 8) writeIsland(rc, 1);
        //In case we start losing, swap back.
        else if (readIsland(rc) == 1) writeIsland(rc, 0);
        System.out.println("Bytecode 2: " + Clock.getBytecodeNum());

        // TODO: Need more robust island/anchor tracking
        // Build anchors once we have enough robots
        if (rc.canBuildAnchor(Anchor.STANDARD) && rc.getRobotCount() > MIN_ROBOTS_FOR_ANCHOR && numAnchors < MAX_ANCHORS) {
            rc.buildAnchor(Anchor.STANDARD);
            numAnchors++;
        }
        System.out.println("Bytecode 3: " + Clock.getBytecodeNum());

        writeCarrierSpawnID(rc, previousCarrierID, hqNum);
        System.out.println("Bytecode 4: " + Clock.getBytecodeNum());

        // Spawn launchers towards any enemies in vision.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        System.out.println("Bytecode 5: " + Clock.getBytecodeNum());
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

        System.out.println("Bytecode 6: " + Clock.getBytecodeNum());

        //If we need to build anchors and don't have the resources, only build with excess.
        if (rc.getRobotCount() > MAP_HEIGHT * MAP_WIDTH / 8 && rc.getNumAnchors(Anchor.STANDARD) == 0 && numAnchors < MAX_ANCHORS) {
            //Make sure we build anchors
            rc.setIndicatorString("Saving up for an anchor");
            return;
        }

        System.out.println("Bytecode 7: " + Clock.getBytecodeNum());

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

        System.out.println("Bytecode 8: " + Clock.getBytecodeNum());

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
        System.out.println("Bytecode 10: " + Clock.getBytecodeNum());
        if (rc.isActionReady() && rc.getResourceAmount(ResourceType.ADAMANTIUM) >= RobotType.CARRIER.buildCostAdamantium) {
            // Set the resource target of carrier spawns
            // TODO: HQ active count of number of carriers for each well, distributed with small multiplier for mana
            if (rng.nextDouble() > MANA_TARGET_RATE) {
                writeCarrierAssignment(rc, ResourceType.ADAMANTIUM, hqID);
                carrierAssignment = ResourceType.ADAMANTIUM;
            } else {
                writeCarrierAssignment(rc, ResourceType.MANA, hqID);
                carrierAssignment = ResourceType.MANA;
            }

            System.out.println("Bytecode 11: " + Clock.getBytecodeNum());

            // If not all wells have been found, spawn scout carrier in random location
            if (readNumWellsFound(rc, hqID) < 2) {
                // Create a list of random spawn locations sorted farthest from hq
                MapLocation[] spawnLocations = farthestLocationsInActionRadius(rc, hqLocation, hqLocation);
                //Huge bytecode cost. Also farthestLocationsInActionRadius returns everything, so this
                //is equivalent to a pure random list. Replacing with randomized outer layer, as it's likely
                //this is ONLY called when there is plenty of space.
                //List<MapLocation> randomSpawnLocations = Arrays.asList(spawnLocations);
                //Collections.shuffle(randomSpawnLocations);
                System.out.println("Bytecode 12: " + Clock.getBytecodeNum());
                MapLocation[] farthestLayer = new MapLocation[12];

                System.out.println("Bytecode 13: " + Clock.getBytecodeNum());
                System.arraycopy(spawnLocations, 0, farthestLayer, 0, farthestLayer.length);
                for (MapLocation loc : farthestLayer) {
                    System.out.println("Bytecode 1x: " + Clock.getBytecodeNum() + ", " + loc + ", " + rc.isLocationOccupied(loc));
                    if (!rc.isLocationOccupied(loc)) {
                        if(!rc.canBuildRobot(RobotType.CARRIER, loc)) continue;

                        rc.buildRobot(RobotType.CARRIER, loc);

                        previousCarrierID = rc.senseRobotAtLocation(loc).getID();
                        break;
                    }
                }
                System.out.println("Bytecode 15: " + Clock.getBytecodeNum());

            } else {
                // Spawn as close to the well as possible
                MapLocation wellLocation = readWellLocation(rc, carrierAssignment, hqNum);
                System.out.println("Bytecode 16: " + Clock.getBytecodeNum());
                MapLocation[] spawnLocations = closestLocationsInActionRadius(rc, hqLocation, wellLocation);
                System.out.println("Bytecode 17: " + Clock.getBytecodeNum() + ", " + spawnLocations[0]);

                for (MapLocation loc : spawnLocations) {
                    System.out.println("Bytecode 18x: " + Clock.getBytecodeNum());
                    if (!rc.isLocationOccupied(loc)) {
                        if(!rc.canBuildRobot(RobotType.CARRIER, loc)) continue;

                        rc.buildRobot(RobotType.CARRIER, loc);

                        previousCarrierID = rc.senseRobotAtLocation(loc).getID();
                        break;
                    }
                }
                System.out.println("Bytecode 19: " + Clock.getBytecodeNum());
            }
        }
    }

    // Build launchers closest to middle of the map
    static void buildLauncher(RobotController rc) throws GameActionException {
        System.out.println("Bytecode 20: " + Clock.getBytecodeNum());
        if (rc.isActionReady() && rc.getResourceAmount(ResourceType.ADAMANTIUM) >= RobotType.CARRIER.buildCostAdamantium) {
            MapLocation middle = new MapLocation(MAP_WIDTH / 2, MAP_HEIGHT / 2);
            MapLocation[] spawnLocations = closestLocationsInActionRadius(rc, hqLocation, middle);

            System.out.println("Bytecode 21: " + Clock.getBytecodeNum());

            for (MapLocation loc : spawnLocations) {
                System.out.println("Bytecode 2x: " + Clock.getBytecodeNum());
                if (rc.canBuildRobot(RobotType.LAUNCHER, loc)) {
                    rc.buildRobot(RobotType.LAUNCHER, loc);
                    break;
                }
            }

            System.out.println("Bytecode 23: " + Clock.getBytecodeNum());
        }
    }
}
