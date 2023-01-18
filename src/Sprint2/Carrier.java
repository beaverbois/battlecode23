package Sprint2;

import battlecode.common.*;

import java.util.*;

import static Sprint2.CarrierSync.*;
import static Sprint2.HQSync.hqMinIndex;
import static Sprint2.RobotPlayer.*;
import static Sprint2.Util.*;
import static Utilities.Util.away;
import static Utilities.Util.towards;

public class Carrier {
    enum CarrierState {
        SCOUTING,
        MOVING,
        FARMING,
        RETURNING,
        ISLANDS
    }

    static boolean reportingWell = false;

    private static Direction scoutDirection = null;
    static CarrierState state = null;
    static boolean stateLock = false;
    static MapLocation hqLocation = null;
    static MapLocation targetWellLocation = null;
    static MapLocation rcLocation = null;
    static final int maxCollectionCycles = 15; // Max is 6 for 1 move/turn after collecting
    static int numCycles = 0;
    static List<Direction> shuffledDir;
    public static ResourceType targetType = null;

    static boolean reportingEnemy = false;

    static void run(RobotController rc) throws GameActionException {
        if (state == null) {
            // this will run when the bot is created
            //TODO: Upgrades
            state = CarrierState.SCOUTING;
            rcLocation = rc.getLocation();
            targetType = getCarrierAssignment(rc);

            shuffledDir = new ArrayList<>(Arrays.asList(directions));
            Collections.shuffle(shuffledDir);

            //Do islands if instructed to.
            if (rc.readSharedArray(ISLAND_INDEX) == 1) {
                state = CarrierState.ISLANDS;
            }
        }

        senseEnemies(rc);
        hqLocation = closest(rc.getLocation(), headquarters);

        switch (state) {
            case SCOUTING:
                // if we have not discovered all wells, pick a random direction to go in and discover them
                if (!stateLock) {
                    if (getNumWellsFound(rc) < NUM_WELLS_STORED) {
                        rcLocation = rc.getLocation();
                        scoutDirection = hqLocation.directionTo(rcLocation);
                        stateLock = true;
                        scout(rc);
                    } else {
                        // if we have discovered all wells, assemble a list of wells with our targetType and pick a random one
                        discoveredAllWells(rc);
                        break;
                    }
                } else {
                    scout(rc);
                }

                //Check for enemies and enemy HQ
                senseEnemies(rc);
                break;

            case MOVING:
                move(rc);
                break;

            case FARMING:
                farm(rc);
                break;

            case RETURNING:
                //TODO: double moves on return
                returning(rc);
                break;
            case ISLANDS: {
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

        // if all wells are discovered while scouting, move towards one
        if (getNumWellsFound(rc) >= NUM_WELLS_STORED) {
            discoveredAllWells(rc);
            return;
        }

        // when we discover a nearby well, make sure it is the right type and not already stored before we write it
        WellInfo[] wells = rc.senseNearbyWells(targetType);
        if (wells.length > 0) {
            //TODO: Temp fix
            targetWellLocation = wells[0].getMapLocation();
            // make a location list of all stored wells of our type
            ArrayList<MapLocation> targetWellLocations = new ArrayList<>();
            for (int i = WELL_INDEX_MIN; i <= WELL_INDEX_MAX; i++) {
                if (getWellType(rc, i) == targetType) targetWellLocations.add(getWellLocation(rc, i));
            }

            // we only want to store numWellsStored/2 wells per type, not elixir yet
            if (targetWellLocations.size() >= NUM_WELLS_STORED / 2) {
                return;
            }

            // check if any wells we found are new and not stored
            for (WellInfo well : wells) {
                MapLocation loc = well.getMapLocation();
                if (!targetWellLocations.contains(loc)) {
                    // if we can write new well, do so
                    if (rc.canWriteSharedArray(0, 1)) {
                        writeWell(rc, targetType, loc);
                    } else {
                        // otherwise, return to hq to report
                        reportingWell = true;
                        state = CarrierState.RETURNING;
                        break;
                    }
                }
            }
        }
    }

    private static void move(RobotController rc) throws GameActionException {
        // check if we are already adjacent to a well
        if (checkWellAdjacency(rc)) {
            return;
        };

        // move towards square around target well closest to us
        rcLocation = rc.getLocation();
        int numAttempts = 0;
        for (Direction dir : closestDirections(targetWellLocation, rcLocation, true)) {
            MapLocation closestSquare = targetWellLocation.add(dir);
            Direction closestSquareDir = rcLocation.directionTo(closestSquare);

            // ensure we do not move towards a wall/impassible square
            if (rc.canSenseLocation(closestSquare) && !rc.sensePassability(closestSquare)) {
                continue;
            }

            if (rc.canMove(closestSquareDir)) {
                rc.move(closestSquareDir);
                rc.setIndicatorString(state.toString() + " TO " + closestSquare + " FOR " + targetType.toString() + " AT " + targetWellLocation);
                numAttempts++;
                break;
            }
        }

        // robot has not moved, so move to a random square around us closest to well
        if (numAttempts == 0) {
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
        if (checkWellAdjacency(rc)) {
            return;
        };

        // move a second time if we can
        if (rc.isMovementReady()) {
            move(rc);
        }
    }

    private static void farm(RobotController rc) throws GameActionException {
        // if we can collect resources, do so
        checkAndCollectResources(rc);

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

    private static void returning(RobotController rc) throws GameActionException {
        if (reportingWell) {
            ArrayList<MapLocation> targetWellLocations = new ArrayList<>();
            for (int i = WELL_INDEX_MIN; i <= WELL_INDEX_MAX; i++) {
                if (getWellType(rc, i) == targetType) targetWellLocations.add(getWellLocation(rc, i));
            }

            if (targetWellLocations.contains(targetWellLocation)) {
                reportingWell = false;
            } else if (rc.canWriteSharedArray(0, 1)) {
                writeWell(rc, targetType, targetWellLocation);

                System.out.println(targetType + " at " + targetWellLocation);
                reportingWell = false;
                state = CarrierState.MOVING;
                move(rc);
            }
        }

        // TODO: fix null crashes here
//        if (reportingEnemy) report(rc);

        rcLocation = rc.getLocation();
        Direction hqDirection = rcLocation.directionTo(hqLocation);
        if (rc.canMove(hqDirection)) {
            rc.move(hqDirection);
        } else {
            // if path is blocked, move to different square around hq
            for (Direction dir : closestDirections(hqLocation, rcLocation)) {
                MapLocation closestSquare = hqLocation.add(dir);
                Direction closestSquareDir = rcLocation.directionTo(closestSquare);
                if (rc.canMove(closestSquareDir)) {
                    rc.move(closestSquareDir);
                    break;
                }
            }

            // if we are still blocked, pick a random square around us to move to
            if (rc.isMovementReady()) {
                for (Direction dir : closestDirections(rcLocation, hqLocation)) {
                    if (rc.canMove(dir)) {
                        rc.move(dir);
                        break;
                    }
                }
            }
        }

        rc.setIndicatorString(state.toString() + " TO " + hqLocation);

        rcLocation = rc.getLocation();
        if (rcLocation.isAdjacentTo(hqLocation)) {
            if (rc.canTransferResource(hqLocation, targetType, rc.getResourceAmount(targetType))) {
                rc.transferResource(hqLocation, targetType, rc.getResourceAmount(targetType));
            }

            if (!reportingWell) {
                state = CarrierState.MOVING;
                move(rc);
            }
        }
    }
    private static void discoveredAllWells(RobotController rc) throws GameActionException {
        // assemble a list of the indices of wells of our type
        // TODO: do not use random wells after scouting, use closest instead
        ArrayList<Integer> targetWellIndices = new ArrayList<>();
        for (int i = WELL_INDEX_MIN; i <= WELL_INDEX_MAX; i++) {
            if (getWellType(rc, i).equals(targetType)) targetWellIndices.add(i);
        }

        // Pick a random well from our list of wells with the correct type
        Collections.shuffle(targetWellIndices);
        int targetIndex = targetWellIndices.get(0);

        targetWellLocation = getWellLocation(rc, targetIndex);
        targetType = getWellType(rc, targetIndex);
        state = CarrierState.MOVING;
    }

    //Stuff added for integration purposes:
    private static void islands(RobotController rc) throws GameActionException {
        rc.setIndicatorString("ISLANDS");
        rcLocation = rc.getLocation();

        //Camp on an island to destroy anchors or protect yours.
        if(rc.getAnchor() == null && rc.senseIsland(rcLocation) != -1) {
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
        if(rcLocation.isAdjacentTo(headquarters) && rc.getAnchor() == null) {
            if(rc.canTakeAnchor(headquarters, Anchor.STANDARD)) rc.takeAnchor(headquarters, Anchor.STANDARD);
        }
        if (islandLocs.size() > 0) {
            int index = 0;
            MapLocation islandLocation = islandLocs.iterator().next();
            if(rc.getAnchor() == null) {
                rc.setIndicatorString("Moving my anchor towards " + islandLocation);
                moveTowards(rc, islandLocation);
            } else {
                while(++index < islands.length &&  rc.senseAnchor(islands[index]) != null) islandLocation = islandLocs.iterator().next();
                if(!islandLocs.iterator().hasNext()) moveAway(rc, corner);
                moveTowards(rc, islandLocation);
                if (rc.canPlaceAnchor()) {
                    rc.placeAnchor();
                }
            }

        } else if (rc.isMovementReady()){
            moveAway(rc, corner);
        }
    }

    private static void senseEnemies(RobotController rc) throws GameActionException {
        // If a headquarters is detected, report it back to HQ
        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == RobotType.HEADQUARTERS) {
                int pos = locToInt(enemy.getLocation());
                int numHQ = allHQ.length;
                for (int i = 0; i < numHQ; i++) {
                    int val = locToInt(allOpposingHQ[i]);
                    if (val == pos) {
                        break;
                    } else if (val == 0) {
                        allOpposingHQ[i] = intToLoc(pos);
                        state = CarrierState.RETURNING;
                        reportingEnemy = true;
                        return;
                    }
                }
            } else if(enemy.getType() == RobotType.LAUNCHER || enemy.getType() == RobotType.DESTABILIZER){
                //If a fighting enemy is detected, report it back to HQ
                enemyLoc = enemy.getLocation();
                state = CarrierState.RETURNING;
                reportingEnemy = true;
                return;
            }
        }
    }

    private static void report(RobotController rc) throws GameActionException {
        for (int i = 0; i < allHQ.length; i++) {
            int read = rc.readSharedArray(4 + i);
            if(read != 0 && read != locToInt(allOpposingHQ[i])) {
                if(locToInt(allOpposingHQ[i]) == 0) allOpposingHQ[i] = intToLoc(read);
                    //Doesn't account for the case of 3+ HQ where the
                    //robot has 2 new known HQ and another reports an HQ.
                else if(i < allHQ.length - 1) {
                    allOpposingHQ[i+1] = allOpposingHQ[i];
                    allOpposingHQ[i] = intToLoc(read);
                }
            }
        }

        if(rc.canWriteSharedArray(0, 0)) {
            //Update shared array with enemy HQ, currently may have problems with overwriting HQ.
            for(int i = 0; i < allHQ.length; i++) {
                int read = rc.readSharedArray(4+i);
                int oppHQ = locToInt(allOpposingHQ[i]);
                if (oppHQ != 0 && read == 0) {
                    rc.writeSharedArray(4 + i, oppHQ);
                }
            }

            //If no slot is close to the reported enemy, update first free slot with enemy location.
            if(enemyLoc != null) {
                boolean exists = false;
                for (int i = 32; i < 44; i++) {
                    int read = rc.readSharedArray(i);
                    int dist = distance(enemyLoc, intToLoc(read));
                    if (dist < 3) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    for (int j = 32; j < 44; j++) {
                        if (rc.readSharedArray(j) == 0) {
                            rc.writeSharedArray(j, locToInt(enemyLoc));
                            break;
                        }
                    }
                }
                enemyLoc = null;
            }

            if (targetWellLocation == null) {
                state = CarrierState.SCOUTING;
            } else {
                state = CarrierState.MOVING;
            }
        }
    }

    private static void moveAway(RobotController rc, MapLocation from) throws GameActionException {
        rcLocation = rc.getLocation();
        int dirIn = away(rcLocation, from);
        if(dirIn == -1) {
            moveRandom(rc);
            return;
        }
        int randInt = rng.nextInt(3);
        Direction dir = directions[(dirIn + (randInt - 1) + directions.length) % directions.length];
        if (rc.canMove(dir)) rc.move(dir);
        else if (rc.canMove(directions[(dirIn + (randInt % 2) + directions.length - 1) % directions.length]))
            rc.move(directions[(dirIn + (randInt % 2) + directions.length - 1) % directions.length]);
        else if(rc.canMove(directions[(dirIn + (randInt + 1 % 2) + directions.length - 1) % directions.length]))
            rc.move(directions[(dirIn + (randInt + 1 % 2) + directions.length - 1) % directions.length]);
        else {
            corner = rcLocation;
            moveRandom(rc);
        }
    }

    private static void moveTowards(RobotController rc, MapLocation target) throws GameActionException {
        rcLocation = rc.getLocation();
        int dirIn = towards(rcLocation, target);
        if(dirIn == -1) {
            moveRandom(rc);
            return;
        }
        Direction dir = directions[dirIn];
        if (rc.canMove(dir)) rc.move(dir);
        else if (rc.canMove(directions[(dirIn + 1) % directions.length]))
            rc.move(directions[(dirIn + 1) % directions.length]);
        else if(rc.canMove(directions[((dirIn - 1) + directions.length - 1) % directions.length]))
            rc.move(directions[((dirIn - 1) + directions.length - 1) % directions.length]);
        else {
            moveRandom(rc);
        }
    }

    private static void moveRandom(RobotController rc) throws GameActionException {
        int randDir = rng.nextInt(directions.length);
        Direction dir = directions[randDir++ % directions.length];
        for (int i = 0; i < directions.length && !rc.canMove(dir); i++) {
            dir = directions[randDir++ % directions.length];
        }
        if (rc.canMove(dir)) rc.move(dir);
    }

    private static boolean checkWellAdjacency(RobotController rc) throws GameActionException {
        rcLocation = rc.getLocation();
        if (rcLocation.isAdjacentTo(targetWellLocation)) {
            state = CarrierState.FARMING;

            // if we can collect resources, do so
            checkAndCollectResources(rc);
            return true;
        }
        return false;
    }

    private static void checkAndCollectResources(RobotController rc) throws GameActionException {
        if (rc.canCollectResource(targetWellLocation, -1)) {
            rc.collectResource(targetWellLocation, -1);
            numCycles++;
            rc.setIndicatorString(state.toString() + " CYCLE " + numCycles + "/" + maxCollectionCycles);
        }
    }
}
