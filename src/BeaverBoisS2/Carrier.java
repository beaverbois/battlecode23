package BeaverBoisS2;

import battlecode.common.*;

import java.util.*;

import static BeaverBoisS2.CarrierSync.*;
import static BeaverBoisS2.HQSync.readHQLocation;
import static BeaverBoisS2.LauncherSync.reportEnemy;
import static BeaverBoisS2.RobotPlayer.*;
import static BeaverBoisS2.RobotSync.readIsland;
import static BeaverBoisS2.Util.*;

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
    static MapLocation rcLocation = null;
    static final int maxCollectionCycles = 10; // Max is 6 for 1 move/turn after collecting
    static int numCycles = 0;
    static List<Direction> shuffledDir;
    public static ResourceType targetType = null;

    static boolean reportingEnemy = false;
    static boolean pathBlocked = false;
    static Direction blockedTraverseDirection = null;
    static Direction blockedTargetDirection = null;
    static int numMoves = 0;
    // TODO: Remove
    static MapLocation corner = new MapLocation(-1, -1);
    static void run(RobotController rc) throws GameActionException {
        if (state == null) {
            // this will run when the bot is created
            state = CarrierState.SCOUTING;
            rcLocation = rc.getLocation();
            hqID = getHQNum(rc);
            hqLocation = readHQLocation(rc, hqID);
            targetType = readCarrierAssignment(rc, hqID);

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
                        rcLocation = rc.getLocation();
                        scoutDirection = hqLocation.directionTo(rcLocation);
                        stateLock = true;
                        scout(rc);
                    } else {
                        // if we have discovered all wells, set our targetWell
                        targetWellLocation = readWellLocation(rc, targetType, hqID);
                        state = CarrierState.MOVING;
                        moveTowards(rc, targetWellLocation);
                        break;
                    }
                } else {
                    scout(rc);
                }

                //Check for enemies and enemy HQ
//                senseEnemies(rc);
                break;

            case MOVING:

                moveTowards(rc, targetWellLocation);
                break;

            case FARMING:
                farm(rc);
                break;

            case RETURNING:
                //TODO: double moves on return
                returning(rc);
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
            rc.setIndicatorLine(rcLocation, rcLocation.add(scoutDirection), 100,100,0);
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
            moveTowards(rc, targetWellLocation);
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

    private static void moveTowards(RobotController rc, MapLocation location) throws GameActionException {
        boolean moved = false;

        // check if we are already adjacent to a well or if we cannot move
        if (checkWellAdjacencyAndCollect(rc) || !rc.isMovementReady()) {
            return;
        }

        // move towards square around target well closest to us
        rcLocation = rc.getLocation();
        numMoves = 0;

        if (checkIfBlocked(rc, location)) {
            return;
        }

        for (Direction dir : closestDirections(rc, location, rcLocation)) {
            MapLocation closestSquare = location.add(dir);
            Direction closestSquareDir = rcLocation.directionTo(closestSquare);

            // ensure we do not move towards a wall/impassible square
            if (rc.canSenseLocation(closestSquare) && !rc.sensePassability(closestSquare)) {
                continue;
            }

            if (rc.canMove(closestSquareDir)) {
                rc.move(closestSquareDir);
                moved = true;
                rc.setIndicatorString(state.toString() + " TO " + closestSquare + " DESTINATION " + location);
                numMoves++;
                break;
            }
        }

        if (checkIfBlocked(rc, location)) {
            return;
        }

        // robot has not moved, so move to a random square around us closest to well
        if (numMoves == 0) {
            rcLocation = rc.getLocation();
            for (Direction dir : closestDirections(rc, rcLocation, location)) {
                if (rc.canMove(dir)) {
                    rc.move(dir);
                    moved = true;
                    rc.setIndicatorString(state.toString() + " TO " + rcLocation.add(dir) + " DESTINATION " + location);
                    break;
                }
            }
        }

        // check if we are adjacent to a well and change state accordingly
        if (checkWellAdjacencyAndCollect(rc)) {
            return;
        }

        // move a second time if we can
        if (rc.isMovementReady() && moved) {
            moveTowards(rc, location);
        }
    }

    private static void farm(RobotController rc) throws GameActionException {
        // if we can collect resources, do so, if we can no longer move back to well
        if (!checkAndCollectResources(rc)) {
            state = CarrierState.MOVING;
            moveTowards(rc, targetWellLocation);
            return;
        }

        // once we reach maxCollectionCycles, return and move towards hq
        if (numCycles == maxCollectionCycles) {
            state = CarrierState.RETURNING;
            numCycles = 0;

            rcLocation = rc.getLocation();
            Direction hqDirection = rcLocation.directionTo(hqLocation);
            if (rc.canMove(hqDirection)) {
                rc.move(hqDirection);
            } else {
                // if path towards hq is blocked, find another random direction
                for (Direction dir : closestDirections(rc, rcLocation, hqLocation)) {
                    if (rc.canMove(dir)) {
                        rc.move(dir);
                        break;
                    }
                }
            }

            rc.setIndicatorString(state.toString() + " TO " + hqLocation);
        }
    }

    //TODO: Max 9 carriers per well

    private static void returning(RobotController rc) throws GameActionException {
        if (reportingWell) {
            rc.setIndicatorString("1");
            if (isWellDiscovered(rc, targetType, hqID)) {
                targetWellLocation = readWellLocation(rc, targetType, hqID);
                reportingWell = false;

                state = CarrierState.MOVING;
                moveTowards(rc, targetWellLocation);
                return;
            } else if (rc.canWriteSharedArray(0, 1)) {
                writeWell(rc, targetType, targetWellLocation, hqID);
                reportingWell = false;

                state = CarrierState.MOVING;
                moveTowards(rc, targetWellLocation);
                return;
            }
        } else {
            // if we are already at hq, transfer and set state to moving
            if (checkHQAdjacencyAndTransfer(rc)) {
                return;
            }
        }

        numMoves = 0;
        rc.setIndicatorString(state.toString() + " TO " + hqLocation);

        if (reportingEnemy && rc.canWriteSharedArray(0, 0)) reportEnemy(rc, Launcher.target, false);

        rcLocation = rc.getLocation();
        if (checkIfBlocked(rc, hqLocation)) {
            return;
        }

        Direction hqDirection = rcLocation.directionTo(hqLocation);
        if (rc.canMove(hqDirection)) {
            rc.move(hqDirection);
        } else {
            // if path is blocked, move to different square around hq
            int numMoves = 0;
            for (Direction dir : closestDirections(rc, hqLocation, rcLocation)) {
                MapLocation closestSquare = hqLocation.add(dir);
                Direction closestSquareDir = rcLocation.directionTo(closestSquare);
                if (rc.canMove(closestSquareDir)) {
                    rc.move(closestSquareDir);
                    numMoves++;
                    break;
                }
            }

            // if we are still blocked, pick a random square around us to move to
            if (numMoves == 0 && rc.isMovementReady()) {
                for (Direction dir : closestDirections(rc, rcLocation, hqLocation)) {
                    if (rc.canMove(dir)) {
                        rc.move(dir);
                        break;
                    }
                }
            }
        }

        checkHQAdjacencyAndTransfer(rc);
    }

    private static void islands(RobotController rc) throws GameActionException {
        rc.setIndicatorString("ISLANDS");
        rcLocation = rc.getLocation();

        //Camp on an island to destroy anchors or protect yours.
        if (rc.getAnchor() == null && rc.senseIsland(rcLocation) != -1) {
            //System.out.println("Camping");
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
                Util.moveTowards(rc, islandLocation);
            } else {
                while(++index < islands.length &&  rc.senseAnchor(islands[index]) != null) islandLocation = islandLocs.iterator().next();
                if (!islandLocs.iterator().hasNext()) moveAway(rc, corner);
                Util.moveTowards(rc, islandLocation);
                if (rc.canPlaceAnchor()) {
                    rc.placeAnchor();
                }
            }

        } else if (rc.isMovementReady()) {
            rc.setIndicatorString("Moving to center");
            Util.moveTowards(rc, new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2));
        }
    }

    private static void senseEnemies(RobotController rc) throws GameActionException {
        // If a headquarters is detected, report it back to HQ
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == RobotType.LAUNCHER || enemy.getType() == RobotType.DESTABILIZER) {
                //If a fighting enemy is detected, report it back to HQ
                Launcher.target = enemy.getLocation();
                state = CarrierState.RETURNING;
                reportingEnemy = true;
                return;
            }
        }
    }

//    private static void reportEnemy(RobotController rc) throws GameActionException {
//        for (int i = 0; i < hqList.length; i++) {
//            int read = rc.readSharedArray(4 + i);
//            if (read != 0 && read != locToInt(allOpposingHQ[i])) {
//                if (locToInt(allOpposingHQ[i]) == 0) allOpposingHQ[i] = intToLoc(read);
//                    //Doesn't account for the case of 3+ HQ where the
//                    //robot has 2 new known HQ and another reports an HQ.
//                else if (i < hqList.length - 1) {
//                    allOpposingHQ[i+1] = allOpposingHQ[i];
//                    allOpposingHQ[i] = intToLoc(read);
//                }
//            }
//        }
//        if (rc.canWriteSharedArray(0, 0)) {
//            //Update shared array with enemy HQ, currently may have problems with overwriting HQ.
//            for (int i = 0; i < hqList.length; i++) {
//                int read = rc.readSharedArray(4+i);
//                int oppHQ = locToInt(allOpposingHQ[i]);
//                if (oppHQ != 0 && read == 0) {
//                    rc.writeSharedArray(4 + i, oppHQ);
//                }
//            }
//
//            //If no slot is close to the reported enemy, update first free slot with enemy location.
//            if (enemyLoc != null) {
//                boolean exists = false;
//                for (int i = 32; i < 44; i++) {
//                    int read = rc.readSharedArray(i);
//                    int dist = distance(enemyLoc, intToLoc(read));
//                    if (dist < 3) {
//                        exists = true;
//                        break;
//                    }
//                }
//                if (!exists) {
//                    for (int j = 32; j < 44; j++) {
//                        if (rc.readSharedArray(j) == 0) {
//                            rc.writeSharedArray(j, locToInt(enemyLoc));
//                            break;
//                        }
//                    }
//                }
//                enemyLoc = null;
//            }
//
//            if (targetWellLocation == null) {
//                state = CarrierState.SCOUTING;
//            } else {
//                state = CarrierState.MOVING;
//            }
//        }
//    }

    private static boolean checkIfBlocked(RobotController rc, MapLocation target) throws GameActionException {
        rc.setIndicatorString("Blocked!");
        rcLocation = rc.getLocation();
        Direction targetDir = (pathBlocked) ? blockedTargetDirection: rcLocation.directionTo(target);
        MapLocation front = rcLocation.add(targetDir);

        if (rc.canSenseLocation(front) && !rc.canSenseRobotAtLocation(front) && !rc.sensePassability(front)) {
            Direction[] wallFollow = {
                    targetDir.rotateRight().rotateRight(),
                    targetDir.rotateLeft().rotateLeft()};

            // Move in the same direction as we previously were when blocked
            if (pathBlocked) {
                if (rc.canMove(blockedTraverseDirection)) {
                    rc.move(blockedTraverseDirection);
                    numMoves++;
                    return true;
                } else {
                    blockedTraverseDirection = blockedTraverseDirection.opposite();
                    if (rc.canMove(blockedTraverseDirection)) {
                        rc.move(blockedTraverseDirection);
                        numMoves++;
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
                        numMoves++;
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
        rcLocation = rc.getLocation();
        if (rcLocation.isAdjacentTo(hqLocation)) {
            if (rc.canTransferResource(hqLocation, targetType, 1)) {
                rc.transferResource(hqLocation, targetType, rc.getResourceAmount(targetType));
            }

            if (!reportingWell) {
                state = CarrierState.MOVING;
                if(targetWellLocation == null) targetWellLocation = readWellLocation(rc, targetType, hqID);
                moveTowards(rc, targetWellLocation);
            }

            return true;
        }
        return false;
    }

    private static boolean checkWellAdjacencyAndCollect(RobotController rc) throws GameActionException {
        rcLocation = rc.getLocation();
        if (rcLocation.isAdjacentTo(targetWellLocation) && rc.canCollectResource(targetWellLocation, -1)) {
            state = CarrierState.FARMING;
            numCycles = 0;

            // if we can collect resources, do so
            farm(rc);
            return true;
        }
        return false;
    }

    private static boolean checkAndCollectResources(RobotController rc) throws GameActionException {
        rcLocation = rc.getLocation();
        if (rcLocation.isAdjacentTo(targetWellLocation) && rc.canCollectResource(targetWellLocation, -1)) {
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
