package Sprint2;

import battlecode.common.*;

import java.util.ArrayList;

import static Sprint2.CarrierSync.*;
import static Sprint2.HQSync.*;
import static Sprint2.LauncherSync.reportEnemy;
import static Sprint2.RobotPlayer.rng;
import static Sprint2.RobotPlayer.turnCount;
import static Sprint2.RobotSync.*;
import static Sprint2.Util.*;

public class Headquarters {

    static boolean stateLock = false;
    static MapLocation hqLocation = null;
    static int hqID = 0;
    public static ResourceType carrierAssignment = null;
    static final double MANA_TARGET_RATE = 0.69; // between 0 - 1
    static final double LAUNCHER_SPAWN_RATE = 0.75; // between 0 - 1
    static final double MAX_ROBOTS = 0.2; // ratio of map size
    static final double MAX_ROBOTS_BEFORE_ISLANDS = 0.1; // ratio of map size before we stop producing non-islands robots.
    static final double MIN_ROBOTS_FOR_ANCHOR = 40; // min robots to build anchor
    static final double MAX_ANCHORS = 8; // min robots to build anchor
    static int MAP_WIDTH;
    static int MAP_HEIGHT;
    static int numAnchors = 0;
    static RobotType robotBuildType = null;
    static int previousCarrierID = 0;
    static double adIncome;
    static double mnIncome;
    static ArrayList<Integer> adCarrierIDs = new ArrayList<>();
    static ArrayList<Integer> adCarriersLastSeen = new ArrayList<>();
    static int adAvgFarmTime = 0;
    static ArrayList<Integer> mnCarrierIDs = new ArrayList<>();
    static ArrayList<Integer> mnCarriersLastSeen = new ArrayList<>();
    static int mnAvgFarmTime = 0;
    static final int MAX_AD_CARRIERS = 9; // per well
    static final int MAX_MN_CARRIERS = 9; // per well
    static final double EXPIRED_CARRIER_TOLERNACE = 1.6; // multiplied by avg farm time to determine if carrier is expired
    static boolean carrierCapacityReached = false;

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

        //Make island carriers late-game.
        if (rc.getRobotCount() > MAP_HEIGHT * MAP_WIDTH * MAX_ROBOTS_BEFORE_ISLANDS) writeIsland(rc, 1);
            //In case we start losing, swap back.
        else if (readIsland(rc) == 1) writeIsland(rc, 0);

        // TODO: Need more robust island/anchor tracking
        // Build anchors once we have enough robots
        if (rc.canBuildAnchor(Anchor.STANDARD) && rc.getRobotCount() > MIN_ROBOTS_FOR_ANCHOR && numAnchors < MAX_ANCHORS) {
            rc.buildAnchor(Anchor.STANDARD);
            numAnchors++;
        }

        writeCarrierSpawnID(rc, previousCarrierID, hqID);

        // Spawn launchers towards any enemies in vision.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
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
            reportEnemy(rc, enemy.location, false);
        }

        //If we need to build anchors and don't have the resources, only build with excess.
        if (rc.getRobotCount() > MAP_HEIGHT * MAP_WIDTH * MAX_ROBOTS_BEFORE_ISLANDS && rc.getNumAnchors(Anchor.STANDARD) == 0 && numAnchors < MAX_ANCHORS) {
            //Make sure we build anchors
            rc.setIndicatorString("Saving up for an anchor");
            return;
        }

        //This causes us to never have enough resources to make an anchor, need to apply some limiters.
        // Main robot building if other conditions aren't satisfied
        if (rc.getRobotCount() < MAP_HEIGHT * MAP_WIDTH * MAX_ROBOTS) {
            carrierCapacityReached = carrierCapacityReached(rc);
            if (rng.nextDouble() > LAUNCHER_SPAWN_RATE) {
                if (carrierCapacityReached && rc.getResourceAmount(ResourceType.ADAMANTIUM) >= 50) {
                    robotBuildType = RobotType.CARRIER;
                } else {
                    robotBuildType = RobotType.LAUNCHER;
                }
            } else {
                if (rc.getResourceAmount(ResourceType.MANA) >= 60) {
                    robotBuildType = RobotType.LAUNCHER;
                } else {
                    robotBuildType = RobotType.CARRIER;
                }

                switch (robotBuildType) {
                    case CARRIER:
                        if (!carrierCapacityReached) buildCarrier(rc);
                        break;

                    case LAUNCHER:
                        buildLauncher(rc);
                        break;
                }
            }
        } else {
            rc.setIndicatorString("Max robots reached");
        }

        rc.setIndicatorString(mnCarrierIDs.toString());
        if (robotBuildType != RobotType.CARRIER) {
            // Spawn limits for carriers
            RobotInfo[] nearbyCarriers = rc.senseNearbyRobots(-1, rc.getTeam());
            for (RobotInfo carrier : nearbyCarriers) {
                if (carrier.getType() != RobotType.CARRIER || carrier.getID() == previousCarrierID) {
                    continue;
                }

                int mnIndex = mnCarrierIDs.indexOf(carrier.ID);
                int adIndex = adCarrierIDs.indexOf(carrier.ID);

                if (mnIndex != -1) {
                    if (rc.getResourceAmount(ResourceType.MANA) == 0) {
                        continue;
                    }

                    // set only once
                    if (mnAvgFarmTime == 0) {
                        mnAvgFarmTime = (int) ((turnCount - mnCarriersLastSeen.get(mnIndex)) * EXPIRED_CARRIER_TOLERNACE);
                    }

                    mnCarriersLastSeen.set(mnIndex, turnCount);

                    //TODO: This really should be a map
                    ArrayList<Integer> expiredCarrierIDs = new ArrayList<>();
                    ArrayList<Integer> expiredCarrierTurns = new ArrayList<>();
                    for (int i = 0; i < mnCarriersLastSeen.size(); i++) {
                        int val = mnCarriersLastSeen.get(i);
                        if (val > mnAvgFarmTime) {
                            expiredCarrierIDs.add(mnCarrierIDs.get(i));
                            expiredCarrierTurns.add(val);
                        }
                    }
                    mnCarrierIDs.remove(expiredCarrierIDs);
                    mnCarriersLastSeen.remove(expiredCarrierTurns);

                } else if (adIndex != -1) {
                    if (rc.getResourceAmount(ResourceType.ADAMANTIUM) == 0) {
                        continue;
                    }

                    // set only once
                    if (adAvgFarmTime == 0) {
                        adAvgFarmTime = (int) ((turnCount - adCarriersLastSeen.get(adIndex)) * EXPIRED_CARRIER_TOLERNACE);
                    }

                    adCarriersLastSeen.set(adIndex, turnCount);

                    //TODO: This really should be a map
                    ArrayList<Integer> expiredCarrierIDs = new ArrayList<>();
                    ArrayList<Integer> expiredCarrierTurns = new ArrayList<>();
                    for (int i = 0; i < adCarriersLastSeen.size(); i++) {
                        int val = adCarriersLastSeen.get(i);
                        if (val > adAvgFarmTime) {
                            expiredCarrierIDs.add(adCarrierIDs.get(i));
                            expiredCarrierTurns.add(val);
                        }
                    }
                    adCarrierIDs.remove(expiredCarrierIDs);
                    adCarriersLastSeen.remove(expiredCarrierTurns);
                }
            }
        }
    }

    static void buildCarrier(RobotController rc) throws GameActionException {
        if (!carrierCapacityReached && rc.isActionReady() && rc.getResourceAmount(ResourceType.ADAMANTIUM) >= 50) {
            // Set the resource target of carrier spawns
            if (adCarrierIDs.size() < MAX_AD_CARRIERS && rng.nextDouble() > MANA_TARGET_RATE) {
                writeCarrierAssignment(rc, ResourceType.ADAMANTIUM, hqID);
                carrierAssignment = ResourceType.ADAMANTIUM;
            } else {
                writeCarrierAssignment(rc, ResourceType.MANA, hqID);
                carrierAssignment = ResourceType.MANA;
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
        if (rc.isActionReady() && rc.getResourceAmount(ResourceType.ADAMANTIUM) >= RobotType.CARRIER.buildCostAdamantium) {
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

    static void buildCarrier(RobotController rc, MapLocation loc) throws GameActionException {
        rc.buildRobot(RobotType.CARRIER, loc);
        int rcID = rc.senseRobotAtLocation(loc).getID();
        previousCarrierID = rcID;

        if (readIsland(rc) == 0) {
            if (carrierAssignment == ResourceType.MANA) {
                mnCarrierIDs.add(rcID);
                mnCarriersLastSeen.add(turnCount);
            } else {
                adCarrierIDs.add(rcID);
                adCarriersLastSeen.add(turnCount);
            }
        }
    }

    static boolean carrierCapacityReached(RobotController rc) throws GameActionException {
        if (readIsland(rc) == 1) return false;
        return adCarrierIDs.size() >= MAX_AD_CARRIERS && mnCarrierIDs.size() >= MAX_MN_CARRIERS;
    }
}