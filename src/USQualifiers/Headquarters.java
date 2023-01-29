package USQualifiers;

import battlecode.common.*;

import java.util.ArrayList;

import static USQualifiers.CarrierSync.*;
import static USQualifiers.HQSync.*;
import static USQualifiers.LauncherSync.reportEnemy;
import static USQualifiers.RobotPlayer.*;
import static USQualifiers.Util.closestLocationsInActionRadius;
import static USQualifiers.Util.farthestLocationsInActionRadius;

public class Headquarters {

    static boolean stateLock = false;
    static MapLocation hqLocation = null;
    static int hqID = 0;
    public static ResourceType carrierAssignment = null;
    static final double MANA_TARGET_RATE = 0.72; // between 0 - 1
    static final double LAUNCHER_SPAWN_RATE = 0.75; // between 0 - 1
    static final double MAX_ROBOTS = 0.2; // ratio of map size
    static int MIN_ROBOTS_FOR_ANCHOR = 0; // min robots to build anchor
    static final double MAX_ANCHORS = 8; // min robots to build anchor
    static final int ANCHOR_MAX_TURN_COUNT = 1200; //turn at which we build anchors if we don't have enough bots
    static int MAP_WIDTH;
    static int MAP_HEIGHT;
    static int numAnchors = 0;
    static RobotType robotBuildType = null;
    static ArrayList<Integer> islandCarriers = new ArrayList<>();
    static int turnSpawned = 2000;
    static boolean balling = false;
    static int numAdCarriers = 0;
    static int numMnCarriers = 0;

    //Booleans for building robots while saving for an anchor.
    static boolean buildLaunch = false;
    static boolean buildCar = false;
    static RobotInfo[] nearbyCarriers;
    static int previousCarrierID = 0;

    static void run(RobotController rc) throws GameActionException {
        // runs on hq creation
        if (!stateLock) {
            MAP_WIDTH = rc.getMapWidth();
            MAP_HEIGHT = rc.getMapHeight();
            hqLocation = rc.getLocation();

            MIN_ROBOTS_FOR_ANCHOR = (int) (0.9 * (Math.log(Math.pow(MAP_WIDTH * MAP_HEIGHT, .75) - 30) / Math.log(1.05) - 48));
            System.out.println("Anchor: " + MIN_ROBOTS_FOR_ANCHOR);

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

        //Make island carriers if we have an anchor and no island carrier.
        boolean islandCarrier = false;
        for (Integer i : islandCarriers) {
            if (rc.canSenseRobot(i)) {
                System.out.println("Found friend " + i);
                islandCarrier = true;
                break;
            }
        }

        System.out.println("Island Carrier: " + islandCarrier + ", " + readIsland(rc, hqID));

        if (rc.getNumAnchors(Anchor.STANDARD) > 0 && !islandCarrier) assignIsland(rc, hqID, 1);
        else if (readIsland(rc, hqID) == 1 && turnCount - turnSpawned > 1) assignIsland(rc, hqID, 0);

        writeCarrierSpawnID(rc, previousCarrierID, hqID);

        // Spawn launchers towards any enemies in vision.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, opponentTeam);
        if (enemies.length == 0) balling = false;
        if (turnCount < ANCHOR_MAX_TURN_COUNT && enemies.length > 0) {
            rc.setIndicatorString("Enemies Detected");
            RobotInfo enemy = enemies[0];
            int neededMana = (enemies.length + 1) * 60;

            if (rc.getResourceAmount(ResourceType.MANA) < 60 && rc.getResourceAmount(ResourceType.ADAMANTIUM) < 100) {
                balling = false;
            }

            // Spawn a robot in the closest spot to the enemy
            if ((rc.getResourceAmount(ResourceType.MANA) > neededMana || (rc.getResourceAmount(ResourceType.MANA) > 60 && balling) && rc.isActionReady())) {
                MapLocation[] spawnLocations = closestLocationsInActionRadius(rc, hqLocation, enemy.location);
                balling = true;

                for (MapLocation loc : spawnLocations) {
                    if (rc.canBuildRobot(RobotType.LAUNCHER, loc)) {
                        rc.buildRobot(RobotType.LAUNCHER, loc);
                    }
                }
            }

            reportEnemy(rc, enemy.location, false);
            return;
        }

        boolean makeLauncher = true;
        boolean makeCarrier = true;

        buildLaunch = false;

        //If we need to build anchors and don't have the resources, only build with excess.
        if ((rc.getRobotCount() > MIN_ROBOTS_FOR_ANCHOR || turnCount >= ANCHOR_MAX_TURN_COUNT) && rc.getNumAnchors(Anchor.STANDARD) == 0 && enemies.length == 0) {
            //Make sure we build anchors
            System.out.println("Saving");
            buildLaunch = true;

            rc.setIndicatorString("Saving up for an anchor! Island carrier: " + islandCarrier);
            if (rc.canBuildAnchor(Anchor.STANDARD)) {
                rc.buildAnchor(Anchor.STANDARD);
                numAnchors++;
            }
            if(rc.getResourceAmount(ResourceType.MANA) < 140) makeLauncher = false;
            if(rc.getResourceAmount(ResourceType.ADAMANTIUM) < 130) makeCarrier = false;
        }

        if(makeLauncher && rc.getResourceAmount(ResourceType.MANA) > 60) buildLauncher(rc);
        if(makeCarrier && rc.getResourceAmount(ResourceType.ADAMANTIUM) > 50) buildCarrier(rc);
    }

    static void buildCarrier(RobotController rc) throws GameActionException {
        System.out.println("Trying to build a carrier!");

        if (readIsland(rc, hqID) == 1 && rc.isActionReady() && rc.getResourceAmount(ResourceType.ADAMANTIUM) >= 50) {
            //Build an island carrier adjacent to HQ.
            int rand = rng.nextInt(directions.length);
            for(int i = 0; i < 8; i++) {
                MapLocation spawn = rc.getLocation().add(directions[rand++%8]);
                if(rc.canBuildRobot(RobotType.CARRIER, spawn)) {
                    buildCarrier(rc, spawn);
                    return;
                }
            }
        }
        if (rc.isActionReady() && rc.getResourceAmount(ResourceType.ADAMANTIUM) >= 50) {
            // Set the resource target of carrier spawns
            if (rng.nextDouble() > MANA_TARGET_RATE) {
                writeCarrierAssignment(rc, ResourceType.ADAMANTIUM, hqID);
                carrierAssignment = ResourceType.ADAMANTIUM;
                numAdCarriers++;
            } else {
                writeCarrierAssignment(rc, ResourceType.MANA, hqID);
                carrierAssignment = ResourceType.MANA;
                numMnCarriers++;
            }

            // If not all wells have been found, spawn scout carrier in random location
            if (readNumWellsFound(rc, hqID) < 2) {
                // Create a list of random spawn locations sorted farthest from hq
                MapLocation[] spawnLocations = farthestLocationsInActionRadius(rc, hqLocation, hqLocation);
                //Huge bytecode cost. Also farthestLocationsInActionRadius returns everything, so this
                //is equivalent to a pure random list. Replacing with randomized outer layer, as it's likely
                //this is ONLY called when there is plenty of space.
                //List<MapLocation> randomSpawnLocations = Arrays.asList(spawnLocations);
                //Collections.shuffle(randomSpawnLocations);
                int numFar = 12, posX = rc.getLocation().x, posY = rc.getLocation().y;
                if(posX == 0 || posX == rc.getMapWidth() - 1) numFar = numFar / 2 + 1;
                if(posY == 0 || posY == rc.getMapHeight() - 1) numFar = numFar / 2 + 1;
                MapLocation[] farthestLayer = new MapLocation[numFar];

                System.arraycopy(spawnLocations, 0, farthestLayer, 0, farthestLayer.length);

                for (MapLocation loc : farthestLayer) {
                    if (!rc.isLocationOccupied(loc)) {
                        if(!rc.canBuildRobot(RobotType.CARRIER, loc)) continue;

                        buildCarrier(rc, loc);
                        break;
                    }
                }

            } else {
                // Spawn as close to the well as possible
                MapLocation wellLocation = readWellLocation(rc, carrierAssignment, hqID);
                MapLocation[] spawnLocations = closestLocationsInActionRadius(rc, hqLocation, wellLocation);

                for (MapLocation loc : spawnLocations) {
                    if (!rc.isLocationOccupied(loc)) {
                        if(!rc.canBuildRobot(RobotType.CARRIER, loc)) continue;

                        buildCarrier(rc, loc);
                        break;
                    }
                }
            }
        }
    }

    // Build launchers closest to middle of the map
    static void buildLauncher(RobotController rc) throws GameActionException {
        if (turnCount < ANCHOR_MAX_TURN_COUNT && rc.isActionReady() && rc.getResourceAmount(ResourceType.MANA) >= 60) {
            MapLocation middle = new MapLocation(MAP_WIDTH / 2, MAP_HEIGHT / 2);
            MapLocation[] spawnLocations = closestLocationsInActionRadius(rc, hqLocation, middle);

            for (MapLocation loc : spawnLocations) {
                if(!rc.isActionReady()) break;
                if (rc.getResourceAmount(ResourceType.MANA) >= (buildLaunch ? 140 : 60) && rc.canBuildRobot(RobotType.LAUNCHER, loc)) {
                    rc.buildRobot(RobotType.LAUNCHER, loc);
                }
            }
        }
    }

    static void buildCarrier(RobotController rc, MapLocation loc) throws GameActionException {
        rc.buildRobot(RobotType.CARRIER, loc);
        int rcID = rc.senseRobotAtLocation(loc).getID();
        previousCarrierID = rcID;

        if (readIsland(rc, hqID) != 0) {
            turnSpawned = turnCount;
            islandCarriers.add(rcID);
        }
    }
}