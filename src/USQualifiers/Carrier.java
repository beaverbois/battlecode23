package USQualifiers;

import battlecode.common.*;

import java.util.*;

import static USQualifiers.CarrierSync.*;
import static USQualifiers.HQSync.readHQLocation;
import static USQualifiers.LauncherSync.checkEnemy;
import static USQualifiers.LauncherSync.reportEnemy;
import static USQualifiers.RobotPlayer.directions;
import static USQualifiers.RobotSync.readIsland;
import static USQualifiers.Util.*;

public class Carrier {
    enum CarrierState {
        SCOUTING,
        MOVING,
        FARMING,
        RETURNING,
        ISLAND
    }

    static boolean reportingWell = false;
    private static Direction scoutDirection = null;
    static CarrierState state = null;
    static boolean stateLock = false;
    static int hqID = 0;
    static MapLocation hqLocation = null;
    static MapLocation targetWellLocation = null;
    static final int maxCollectionCycles = 25;
    static int numCycles = 0;
    static List<Direction> shuffledDir;
    public static ResourceType targetType = null;

    static boolean reportingEnemy = false;
    static boolean pathBlocked = false;
    static Direction blockedTraverseDirection = null;
    static Direction blockedTargetDirection = null;
    static MapLocation corner = new MapLocation(-1, -1);
    static Team opponentTeam = null;
    static void run(RobotController rc) throws GameActionException {
        if (state == null) {
            // this will run when the bot is created
            state = CarrierState.SCOUTING;
            hqID = getHQNum(rc);
            hqLocation = readHQLocation(rc, hqID);
            targetType = readCarrierAssignment(rc, hqID);
            opponentTeam = rc.getTeam().opponent();

            shuffledDir = new ArrayList<>(Arrays.asList(directions));

            //Do islands if instructed to.
            if (readIsland(rc) == 1) {
                state = CarrierState.ISLAND;
            }
//            if (getI)
        }

        senseEnemies(rc);

        rc.setIndicatorString(state.toString());

        switch (state) {
            case SCOUTING:
                // if we have not discovered all wells, scout in a direction away from hq
                if (!stateLock) {
                    if (readNumWellsFound(rc, hqID) < 2) {
                        scoutDirection = hqLocation.directionTo(rc.getLocation());
                        stateLock = true;
                        scout(rc);
                    } else {
                        // if we have discovered all wells, set our targetWell
                        targetWellLocation = readWellLocation(rc, targetType, hqID);
                        state = CarrierState.MOVING;
                        moveTowardsTargetWell(rc);
                        break;
                    }
                } else {
                    scout(rc);
                }

                //Check for enemies and enemy HQ
//                senseEnemies(rc);
                break;

            case MOVING:
                moveTowardsTargetWell(rc);
                break;

            case FARMING:
                farm(rc);
                break;

            case RETURNING:
                //TODO: double moves on return
                returningToHQ(rc);
                break;
            case ISLAND: {
                islands(rc);
                break;
            }
        }
    }

    private static void scout(RobotController rc) throws GameActionException {
        rc.setIndicatorString(state.toString() + " " + targetType);
        // once we have picked an initial direction, go in that direction till we can no longer
        if (rc.canMove(scoutDirection)) {
            rc.move(scoutDirection);
            if (rc.canMove(scoutDirection)) {
                rc.move(scoutDirection);
            }
        } else {
            // if we can't go that way, randomly pick another direction until one is found
            Collections.shuffle(shuffledDir);
            for (Direction dir : shuffledDir) {
                if (rc.canMove(dir)) {
                    scoutDirection = dir;
                    rc.move(scoutDirection);
                    break;
                }
            }
        }

        // if all wells are discovered while scouting, set our target well and move towards it
        if (isWellDiscovered(rc, targetType, hqID)) {
            targetWellLocation = readWellLocation(rc, targetType, hqID);
            state = CarrierState.MOVING;
            moveTowardsTargetWell(rc);
        } else {
            // when we discover a nearby well, make sure it is the right type and not already stored before we write it
            WellInfo[] wells = rc.senseNearbyWells(targetType);
            if (wells.length > 0) {
                targetWellLocation = wells[0].getMapLocation();
                if (rc.canWriteSharedArray(0, 1)) {
                    writeWell(rc, targetType, targetWellLocation, hqID);
                } else {
                    reportingWell = true;
                    state = CarrierState.RETURNING;
                }
            }
        }
    }

    private static void moveTowardsTargetWell(RobotController rc) throws GameActionException {
        // check if we are already adjacent to a well or if we cannot move
        if (canFarm(rc) || !rc.isMovementReady()) {
            return;
        }

        // move towards the closest square available around target well
        MapLocation targetLocation = closestAvailableLocationTowardsRobot(rc, targetWellLocation);
        Direction targetDir;
        if (targetLocation != null) {
            targetDir = closestAvailableDirectionAroundRobot(rc, targetLocation);
        } else {
            targetDir = closestAvailableDirectionAroundRobot(rc, targetWellLocation);
        }

        if (targetDir != null) {
            rc.move(targetDir);
            rc.setIndicatorString("MOVING " + targetDir + " TO " + targetLocation);
        } else {
            if (checkIfBlocked(rc, targetWellLocation)) {
                return;
            }
        }

        // check if we are adjacent to a well and change state accordingly
        if (canFarm(rc)) {
            return;
        }

        // move a second time if we can
        if (rc.isMovementReady()) {
            moveTowards(rc, targetWellLocation);
        }
    }

    private static void farm(RobotController rc) throws GameActionException {
        // if we can collect resources, do so, if we can no longer move back to well
        if (!checkAndCollectResources(rc)) {
            state = CarrierState.MOVING;
            moveTowardsTargetWell(rc);
            return;
        }

        // once we reach maxCollectionCycles, return and move towards hq
        if (numCycles >= maxCollectionCycles) {
            state = CarrierState.RETURNING;
            numCycles = 0;

            moveTowards(rc, hqLocation);
            rc.setIndicatorString(state.toString() + " TO " + hqLocation);
        }
    }

    private static void returningToHQ(RobotController rc) throws GameActionException {
        if (reportingEnemy && rc.canWriteSharedArray(0, 0)) {
            reportEnemy(rc, Launcher.target, false);
        }

        if (reportingWell) {
            rc.setIndicatorString("Reporting Well");
            if (isWellDiscovered(rc, targetType, hqID)) {
                targetWellLocation = readWellLocation(rc, targetType, hqID);
                reportingWell = false;

                state = CarrierState.MOVING;
                moveTowardsTargetWell(rc);
                return;
            } else if (rc.canWriteSharedArray(0, 1)) {
                writeWell(rc, targetType, targetWellLocation, hqID);
                reportingWell = false;

                state = CarrierState.MOVING;
                moveTowardsTargetWell(rc);
                return;
            }
        }

        // if we are already at hq, transfer and set state to moving
        if (checkHQAdjacencyAndTransfer(rc)) {
            return;
        }

        rc.setIndicatorString(state.toString() + " TO " + hqLocation);
        if (checkIfBlocked(rc, hqLocation)) {
            return;
        }

        moveTowards(rc, hqLocation);
        checkHQAdjacencyAndTransfer(rc);
    }

    private static void islands(RobotController rc) throws GameActionException {
        rc.setIndicatorString("ISLANDS");

        //Camp on an island to destroy anchors or protect yours.
        if (rc.getAnchor() == null && rc.senseIsland(rc.getLocation()) != -1) {
            //System.out.println("Camping");
            rc.disintegrate();
            return;
        }

        //Default code provided, just picks up anchors moves towards islands we know about.
        int[] islands = rc.senseNearbyIslands();
        Set<MapLocation> islandLocs = new HashSet<>();
        for (int id : islands) {
            MapLocation[] thisIslandLocs = rc.senseNearbyIslandLocations(id);
            islandLocs.addAll(Arrays.asList(thisIslandLocs));
        }
        if (rc.canSenseLocation(hqLocation) && rc.canTakeAnchor(hqLocation, Anchor.STANDARD) && rc.getAnchor() == null) {
            if (rc.canTakeAnchor(hqLocation, Anchor.STANDARD)) rc.takeAnchor(hqLocation, Anchor.STANDARD);
            rc.setIndicatorString("Acquiring anchor");
        }
        if (islandLocs.size() > 0) {
            rc.setIndicatorString("Moving to island");
            int index = 0;
            MapLocation islandLocation = islandLocs.iterator().next();
            if (rc.getAnchor() == null) {
                rc.setIndicatorString("Moving my anchor towards " + islandLocation);
                moveTowards(rc, islandLocation);
            } else {
                while(++index < islands.length &&  rc.senseAnchor(islands[index]) != null) islandLocation = islandLocs.iterator().next();
                if (!islandLocs.iterator().hasNext()) moveAway(rc, corner);
                moveTowards(rc, islandLocation);
                if (rc.canPlaceAnchor()) {
                    rc.placeAnchor();
                }
            }

        } else if (rc.isMovementReady()) {
            rc.setIndicatorString("Moving to center");
            moveTowards(rc, new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2));
        }
    }

    private static void senseEnemies(RobotController rc) throws GameActionException {
        // If a headquarters is detected, report it back to HQ
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, opponentTeam);
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == RobotType.LAUNCHER || enemy.getType() == RobotType.DESTABILIZER) {
                //If a fighting enemy is detected, report it back to HQ
                Launcher.target = enemy.getLocation();
                state = CarrierState.RETURNING;
                reportingEnemy = checkEnemy(rc, Launcher.target);
                return;
            }
        }
    }

    private static boolean checkIfBlocked(RobotController rc, MapLocation target) throws GameActionException {
        rc.setIndicatorString("Blocked!");
        MapLocation rcLocation = rc.getLocation();
        Direction targetDir = (pathBlocked) ? blockedTargetDirection: rcLocation.directionTo(target);
        MapLocation front = rcLocation.add(targetDir);

        boolean senseable = rc.canSenseLocation(front);

        //Consider currents that point towards you and adjacent tiles to be impassable.
        Direction current = senseable ? rc.senseMapInfo(front).getCurrentDirection() : null;

        boolean passable = senseable && rc.sensePassability(front) && (current == Direction.CENTER || dist(rcLocation, front.add(current)) > 1);

        if (senseable && !passable && !rc.canSenseRobotAtLocation(front)) {
            Direction[] wallFollow = {
                    targetDir.rotateRight().rotateRight(),
                    targetDir.rotateLeft().rotateLeft()};

            // Move in the same direction as we previously were when blocked
            if (pathBlocked) {
                if (rc.canMove(blockedTraverseDirection)) {
                    rc.move(blockedTraverseDirection);
                    return true;
                } else {
                    blockedTraverseDirection = blockedTraverseDirection.opposite();
                    if (rc.canMove(blockedTraverseDirection)) {
                        rc.move(blockedTraverseDirection);
                        return true;
                    }
                }
            } else {
                // Call moveTowards again to see if we are near well/still stuck
                for (Direction wallDir : wallFollow) {
                    if (rc.canMove(wallDir)) {
                        pathBlocked = true;
                        blockedTargetDirection = rcLocation.directionTo(target);
                        blockedTraverseDirection = wallDir;

                        rc.move(wallDir);
                        return true;
                    }
                }
            }
        } else {
            pathBlocked = false;
        }
        return false;
    }

    private static boolean checkHQAdjacencyAndTransfer(RobotController rc) throws GameActionException{
        if (rc.getLocation().isAdjacentTo(hqLocation)) {
            if (rc.canTransferResource(hqLocation, targetType, 1)) {
                rc.transferResource(hqLocation, targetType, rc.getResourceAmount(targetType));
            }

            if (!reportingWell) {
                state = CarrierState.MOVING;
                if(targetWellLocation == null) targetWellLocation = readWellLocation(rc, targetType, hqID);
                moveTowardsTargetWell(rc);
            }

            return true;
        }
        return false;
    }

    private static boolean canFarm(RobotController rc) throws GameActionException {
        if (rc.canCollectResource(targetWellLocation, -1)) {
            state = CarrierState.FARMING;
            numCycles = 0;
            farm(rc);

            return true;
        }
        return false;
    }

    private static boolean checkAndCollectResources(RobotController rc) throws GameActionException {
        if (rc.canCollectResource(targetWellLocation, -1)) {
            rc.collectResource(targetWellLocation, -1);
            numCycles++;
            rc.setIndicatorString(state.toString() + " CYCLE " + numCycles + "/" + maxCollectionCycles);
            return true;
        }
        return false;
    }

    // Determines which HQ spawned us by finding our ID amongst a list of IDs spawned
    private static int getHQNum(RobotController rc) throws GameActionException {
        int[] IDs = readCarrierSpawnIDs(rc);
        int rcID = rc.getID();

        for (int i = 0; i < IDs.length; i++) {
            if (IDs[i] == rcID) {
                return i;
            }
        }

        throw new GameActionException(GameActionExceptionType.OUT_OF_RANGE, "Could not find HQ ID!");
    }
}
