package Sprint2;

import battlecode.common.*;

import java.util.*;

import static Sprint2.CarrierSync.*;
import static Sprint2.HQSync.readHQLocation;
import static Sprint2.RobotPlayer.*;
import static Sprint2.Util.*;

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
    static int hqNum = 0;
    static MapLocation hqLocation = null;
    static MapLocation targetWellLocation = null;
    static MapLocation rcLocation = null;
    static final int maxCollectionCycles = 10; // Max is 6 for 1 move/turn after collecting
    static int numCycles = 0;
    static List<Direction> shuffledDir;
    public static ResourceType targetType = null;

    static boolean reportingEnemy = false;

    static void run(RobotController rc) throws GameActionException {
        if (state == null) {
            // this will run when the bot is created
            state = CarrierState.SCOUTING;
            rcLocation = rc.getLocation();
            hqNum = getHQNum(rc);
            System.out.println("MY NUMBER IS " + hqNum);
            hqLocation = readHQLocation(rc, hqNum);
            targetType = readCarrierAssignment(rc, hqNum);

            shuffledDir = new ArrayList<>(Arrays.asList(directions));
            Collections.shuffle(shuffledDir);

            //Do islands if instructed to.
//            if (rc.readSharedArray(ISLAND_INDEX) == 1) {
//                state = CarrierState.ISLAND;
//            }
        }

//        senseEnemies(rc);

        switch (state) {
            case SCOUTING:
                // if we have not discovered all wells, pick a random direction to go in and discover them
                if (!stateLock) {
                    if (readNumWellsFound(rc, hqNum) < 2) {
                        rcLocation = rc.getLocation();
                        scoutDirection = hqLocation.directionTo(rcLocation);
                        stateLock = true;
                        scout(rc);
                    } else {
                        // if we have discovered all wells, set our targetWell
                        targetWellLocation = readWellLocation(rc, targetType, hqNum);
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
//                islands(rc);
                break;
            }
        }
    }

    private static void scout(RobotController rc) throws GameActionException {
        rc.setIndicatorString(state.toString() + " " + targetType);
        // once we have picked an initial direction, go in that direction till we can no longer
        if (rc.canMove(scoutDirection)) {
            rc.move(scoutDirection);
        } else {
            // if we can't go that way, randomly pick another direction until one is found
            Collections.shuffle(shuffledDir) ;
            for (Direction dir : shuffledDir) {
                if (rc.canMove(dir)) {
                    scoutDirection = dir;
                    rc.move(scoutDirection);
                    break;
                }
            }
        }

        // if all wells are discovered while scouting, set our target well and move towards it
        if (isWellDiscovered(rc, targetType, hqNum)) {
            targetWellLocation = readWellLocation(rc, targetType, hqNum);
            state = CarrierState.MOVING;
            moveTowards(rc, targetWellLocation);
            return;
        }

        // when we discover a nearby well, make sure it is the right type and not already stored before we write it
        WellInfo[] wells = rc.senseNearbyWells(targetType);
        if (wells.length > 0) {
            targetWellLocation = wells[0].getMapLocation();
            if (rc.canWriteSharedArray(0, 1)) {
                writeWell(rc, targetType, targetWellLocation, hqNum);
            } else {
                reportingWell = true;
                state = CarrierState.RETURNING;
            }
        }
    }

    private static void moveTowards(RobotController rc, MapLocation location) throws GameActionException {
        // check if we are already adjacent to a well or if we cannot move
        if (checkWellAdjacencyAndCollect(rc) || !rc.isMovementReady()) {
            return;
        }

        // move towards square around target well closest to us
        rcLocation = rc.getLocation();
        int numMoves = 0;
        for (Direction dir : closestDirections(targetWellLocation, rcLocation, true)) {
            MapLocation closestSquare = targetWellLocation.add(dir);
            Direction closestSquareDir = rcLocation.directionTo(closestSquare);

            // ensure we do not move towards a wall/impassible square
            if (rc.canSenseLocation(closestSquare) && !rc.sensePassability(closestSquare)) {
                continue;
            }

            // if there is a wall in front of us, traverse the wall right/left
            // TODO: Better pathfinding around walls
            MapLocation front = rcLocation.add(closestSquareDir);
            if (!rc.canSenseRobotAtLocation(front) && !rc.sensePassability(front)) {
                Direction[] wallFollow = {
                        closestSquareDir.rotateRight().rotateRight(),
                        closestSquareDir.rotateLeft().rotateLeft()
                };

                // Call moveTowards again to see if we are near well/still stuck
                for (Direction wallDir : wallFollow) {
                    if (rc.canMove(wallDir)) {
                        rc.move(wallDir);
                        numMoves++;
                        moveTowards(rc, targetWellLocation);
                        break;
                    }
                }
            }

            if (rc.canMove(closestSquareDir)) {
                rc.move(closestSquareDir);
                rc.setIndicatorString(state.toString() + " TO " + closestSquare + " FOR " + targetType.toString() + " AT " + targetWellLocation);
                numMoves++;
                break;
            }
        }

        // robot has not moved, so move to a random square around us closest to well
        if (numMoves == 0) {
            rcLocation = rc.getLocation();
            for (Direction dir : closestDirections(rcLocation, targetWellLocation)) {
                if (rc.canMove(dir)) {
                    rc.move(dir);
                    rc.setIndicatorString(state.toString() + " TO " + rcLocation.add(dir) + " FOR " + targetType.toString() + " AT " + targetWellLocation);
                    break;
                }
            }
        }

        // check if we are adjacent to a well and change state accordingly
        if (checkWellAdjacencyAndCollect(rc)) {
            return;
        };

        // move a second time if we can
        if (rc.isMovementReady()) {
            moveTowards(rc, targetWellLocation);
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
                for (Direction dir : closestDirections(rcLocation, hqLocation)) {
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
            if (isWellDiscovered(rc, targetType, hqNum)) {
                targetWellLocation = readWellLocation(rc, targetType, hqNum);
                reportingWell = false;

                state = CarrierState.MOVING;
                moveTowards(rc, targetWellLocation);
                return;
            } else if (rc.canWriteSharedArray(0, 1)) {
                writeWell(rc, targetType, targetWellLocation, hqNum);
                reportingWell = false;

                state = CarrierState.MOVING;
                moveTowards(rc, targetWellLocation);
            }
        } else {
            // if we are already at hq, transfer and set state to moving
            if (checkHQAdjacencyAndTransfer(rc)) {
                return;
            }
        }

        rc.setIndicatorString(state.toString() + " TO " + hqLocation);

//        if (reportingEnemy) report(rc);

        rcLocation = rc.getLocation();
        Direction hqDirection = rcLocation.directionTo(hqLocation);
        if (rc.canMove(hqDirection)) {
            rc.move(hqDirection);
        } else {
            // if path is blocked, move to different square around hq
            int numMoves = 0;
            for (Direction dir : closestDirections(hqLocation, rcLocation)) {
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
                for (Direction dir : closestDirections(rcLocation, hqLocation)) {
                    if (rc.canMove(dir)) {
                        rc.move(dir);
                        break;
                    }
                }
            }
        }

        checkHQAdjacencyAndTransfer(rc);
    }

    //Stuff added for integration purposes:
//    private static void islands(RobotController rc) throws GameActionException {
//        rc.setIndicatorString("ISLANDS");
//        rcLocation = rc.getLocation();
//
//        //Camp on an island to destroy anchors or protect yours.
//        if (rc.getAnchor() == null && rc.senseIsland(rcLocation) != -1) {
//            //System.out.println("Camping");
//            return;
//        }
//
//        //Default code provided, just picks up anchors moves towards islands we know about.
//        int[] islands = rc.senseNearbyIslands();
//        Set<MapLocation> islandLocs = new HashSet<>();
//        for (int id : islands) {
//            MapLocation[] thisIslandLocs = rc.senseNearbyIslandLocations(id);
//            islandLocs.addAll(Arrays.asList(thisIslandLocs));
//        }
//        if (rcLocation.isAdjacentTo(headquarters) && rc.getAnchor() == null) {
//            if (rc.canTakeAnchor(headquarters, Anchor.STANDARD)) rc.takeAnchor(headquarters, Anchor.STANDARD);
//        }
//        if (islandLocs.size() > 0) {
//            int index = 0;
//            MapLocation islandLocation = islandLocs.iterator().next();
//            if (rc.getAnchor() == null) {
//                rc.setIndicatorString("Moving my anchor towards " + islandLocation);
//                Util.moveTowards(rc, islandLocation);
//            } else {
//                while(++index < islands.length &&  rc.senseAnchor(islands[index]) != null) islandLocation = islandLocs.iterator().next();
//                if (!islandLocs.iterator().hasNext()) moveAway(rc, corner);
//                Util.moveTowards(rc, islandLocation);
//                if (rc.canPlaceAnchor()) {
//                    rc.placeAnchor();
//                }
//            }
//
//        } else if (rc.isMovementReady()) {
//            moveAway(rc, corner);
//        }
//    }

//    private static void senseEnemies(RobotController rc) throws GameActionException {
//        // If a headquarters is detected, report it back to HQ
//        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
//        for (RobotInfo enemy : enemies) {
//            if (enemy.getType() == RobotType.HEADQUARTERS) {
//                int pos = locToInt(enemy.getLocation());
//                int numHQ = hqList.length;
//                for (int i = 0; i < numHQ; i++) {
//                    int val = locToInt(allOpposingHQ[i]);
//                    if (val == pos) {
//                        break;
//                    } else if (val == 0) {
//                        allOpposingHQ[i] = intToLoc(pos);
//                        state = CarrierState.RETURNING;
//                        reportingEnemy = true;
//                        return;
//                    }
//                }
//            } else if (enemy.getType() == RobotType.LAUNCHER || enemy.getType() == RobotType.DESTABILIZER) {
//                //If a fighting enemy is detected, report it back to HQ
//                enemyLoc = enemy.getLocation();
//                state = CarrierState.RETURNING;
//                reportingEnemy = true;
//                return;
//            }
//        }
//    }

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

    private static boolean checkHQAdjacencyAndTransfer(RobotController rc) throws GameActionException{
        rcLocation = rc.getLocation();
        if (rcLocation.isAdjacentTo(hqLocation)) {
            if (rc.canTransferResource(hqLocation, targetType, rc.getResourceAmount(targetType))) {
                rc.transferResource(hqLocation, targetType, rc.getResourceAmount(targetType));

                if (!reportingWell) {
                    state = CarrierState.MOVING;
                    moveTowards(rc, targetWellLocation);
                }
            }

            return true;
        }
        return false;
    }

    private static boolean checkWellAdjacencyAndCollect(RobotController rc) throws GameActionException {
        rcLocation = rc.getLocation();
        if (rcLocation.isAdjacentTo(targetWellLocation)) {
            state = CarrierState.FARMING;
            numCycles = 0;

            // if we can collect resources, do so
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
