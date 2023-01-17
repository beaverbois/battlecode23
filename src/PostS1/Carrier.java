package PostS1;

import battlecode.common.*;

import java.util.*;

import static BeaverBois_S1.CarrierSync.*;
import static BeaverBois_S1.HQSync.hqMinIndex;
import static BeaverBois_S1.RobotPlayer.*;
import static BeaverBois_S1.Util.*;

public class Carrier {
    enum CarrierState {
        SCOUTING,
        MOVING,
        FARMING,
        RETURNING,
        ISLANDS
    }

    static boolean reportingWell = false;

    static Direction scoutDirection = null;
    static CarrierState state = null;
    static boolean stateLock = false;
    static MapLocation hqLocation = null;
    static MapLocation targetWellLocation = null;
    static final int maxCollectionCycles = 4; // Max is 6 for 1 move/turn after collecting
    static int numCycles = 0;
    static List<Direction> shuffledDir;
    public static ResourceType targetType = null;

    //Robot's current position
    static MapLocation pos;

    static boolean reportingEnemy = false;

    static void run(RobotController rc) throws GameActionException {
        if (state == null) {
            // this will run when the bot is created
            //TODO: Upgrades
            state = CarrierState.SCOUTING;
            hqLocation = intToLoc(rc.readSharedArray(hqMinIndex));
            targetType = getCarrierAssignment(rc);

            shuffledDir = new ArrayList<>(Arrays.asList(directions));
            Collections.shuffle(shuffledDir);

            //Do islands if instructed to.
            if(rc.readSharedArray(islandIndex) == 1) {
                state = CarrierState.ISLANDS;
            }
        }

        senseEnemies(rc);
        hqLocation = closest(rc.getLocation(), headquarters);

        switch (state) {
            case SCOUTING:
                // if we have not discovered all wells, pick a random direction to go in and discover them
                if (!stateLock) {
                    if (getNumWellsFound(rc) < numWellsStored) {
                        scoutDirection = directions[rng.nextInt(directions.length)];
                        stateLock = true;
                        scout(rc);
                    }
                    else {
                        // if we have discovered all wells, assemble a list of wells with our targetType and pick a random one
                        discoveredAllWells(rc);
                        break;
                    }
                }

                else {
                    scout(rc);
                }

                //Check for enemies and enemy HQ
                senseEnemies(rc);

                break;

            case MOVING:

                // move towards square closest to target well
                MapLocation rcLocation = rc.getLocation();
                MapLocation closestSquare = targetWellLocation.subtract(rcLocation.directionTo(targetWellLocation));
                Direction closestSquareDir = rcLocation.directionTo(closestSquare);
                if (rc.canMove(closestSquareDir)) {
                    rc.move(closestSquareDir);
                } else {
                    // if path is blocked, move to different square around well including center
                    List<Direction> shuffledDirWell = new ArrayList<>(Arrays.asList(Direction.allDirections()));
                    Collections.shuffle(shuffledDirWell) ;

                    //TODO: Randomized movements with priority towards well/hq location
                    for (Direction dir : shuffledDirWell) {
                        closestSquare = targetWellLocation.add(dir);
                        closestSquareDir = rcLocation.directionTo(closestSquare);
                        if (rc.canMove(closestSquareDir)) {
                            rc.move(closestSquareDir);
                            break;
                        }
                    }

                    // if we are still blocked, pick a random square around us
                    if (rc.isMovementReady()) {
                        for (Direction dir : closestDirectionsTo(rcLocation, targetWellLocation)) {
                            if (rc.canMove(dir)) {
                                rc.move(dir);
                                break;
                            }
                        }
                    }
                }

                rc.setIndicatorString(state.toString() + " " + closestSquareDir + " TO " + closestSquare);

                rcLocation = rc.getLocation();
                if (rcLocation.isAdjacentTo(targetWellLocation)) {
                    state = CarrierState.FARMING;
                    if (rc.canCollectResource(targetWellLocation, -1)) {
                        rc.collectResource(targetWellLocation, -1);
                        numCycles++;
                    }
                    rc.setIndicatorString(state.toString() + " CYCLE " + numCycles);
                }

                break;

            case FARMING:
                // if we can collect resources, do so numCycles times
                if (rc.canCollectResource(targetWellLocation, -1)) {
                    rc.collectResource(targetWellLocation, -1);
                    numCycles++;
                }

                rc.setIndicatorString(state.toString() + " CYCLE " + numCycles);

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
                        for (Direction dir : closestDirectionsTo(rcLocation, hqLocation)) {
                            if (rc.canMove(dir)) {
                                rc.move(dir);
                                break;
                            }
                        }
                    }

                    rc.setIndicatorString(state.toString() + " TO " + hqLocation);
                }

                break;

            case RETURNING:
                if (reportingWell) {
                    ArrayList<MapLocation> targetWellLocations = new ArrayList<>();
                    for (int i = wellIndexMin; i <= wellIndexMax; i++) {
                        if (getWellType(rc, i) == targetType) targetWellLocations.add(getWellLocation(rc, i));
                    }

                    if (targetWellLocations.contains(targetWellLocation)) {
                        reportingWell = false;
                    } else if (rc.canWriteSharedArray(0, 1)) {
                        writeWell(rc, targetType, targetWellLocation);

                        System.out.println(targetType + " at " + targetWellLocation);
                        reportingWell = false;
                        state = CarrierState.MOVING;
                        break;
                    }
                }

                if(reportingEnemy) report(rc);

                rcLocation = rc.getLocation();
                Direction hqDirection = rcLocation.directionTo(hqLocation);
                if (rc.canMove(hqDirection)) {
                    rc.move(hqDirection);
                } else {
                    // if path is blocked, move to different square around hq
                    for (Direction dir : closestDirectionsTo(hqLocation, rcLocation)) {
                        closestSquare = hqLocation.add(dir);
                        closestSquareDir = rcLocation.directionTo(closestSquare);
                        if (rc.canMove(closestSquareDir)) {
                            rc.move(closestSquareDir);
                            break;
                        }
                    }

                    // if we are still blocked, pick a random square around us to move to
                    if (rc.isMovementReady()) {
                        for (Direction dir : closestDirectionsTo(rcLocation, hqLocation)) {
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
                        rc.transferResource(hqLocation, targetType,rc.getResourceAmount(targetType));
                    }

                    if (!reportingWell && !reportingEnemy) state = CarrierState.MOVING;
                }
                break;
            case ISLANDS: islands(rc); break;
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
        if (getNumWellsFound(rc) >= numWellsStored) {
            discoveredAllWells(rc);
            return;
        }

        // when we discover a nearby well, make sure it is the right type and not already stored before we write it
        WellInfo[] wells = rc.senseNearbyWells((targetType));
        if (wells.length > 0) {
            // make a location list of all stored wells of our type
            ArrayList<MapLocation> targetWellLocations = new ArrayList<>();
            for (int i = wellIndexMin; i <= wellIndexMax; i++) {
                if (getWellType(rc, i) == targetType) targetWellLocations.add(getWellLocation(rc, i));
            }

            // we only want to store numWellsStored/2 wells per type, not elixir yet
            if (targetWellLocations.size() >= numWellsStored / 2) {
                return;
            }

            // check if any wells we found are new and not stored
            for (WellInfo well : wells) {
                MapLocation loc = well.getMapLocation();
                if (!targetWellLocations.contains(loc)) {
                    targetWellLocation = loc;
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

    private static void discoveredAllWells(RobotController rc) throws GameActionException {
        ArrayList<Integer> targetWellIndices = new ArrayList<>();
        for (int i = wellIndexMin; i <= wellIndexMax; i++) {
            if (getWellType(rc, i) == targetType) targetWellIndices.add(i);
        }

        int targetIndex = targetWellIndices.get(rng.nextInt(targetWellIndices.size()));

        targetWellLocation = getWellLocation(rc, targetIndex);
        targetType = getWellType(rc, targetIndex);
        state = CarrierState.MOVING;
    }

    //Stuff added for integration purposes:
    private static void islands(RobotController rc) throws GameActionException {
        rc.setIndicatorString("ISLANDS");
        pos = rc.getLocation();

        //Camp on an island to destroy anchors or protect yours.
        if(rc.getAnchor() == null && rc.senseIsland(pos) != -1) {
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
        if(pos.isAdjacentTo(headquarters) && rc.getAnchor() == null) {
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

            state = CarrierState.SCOUTING;
            stateLock = false;
        }
    }

    private static void moveAway(RobotController rc, MapLocation from) throws GameActionException {
        int dirIn = away(pos, from);
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
            corner = pos;
            moveRandom(rc);
        }
    }

    private static void moveTowards(RobotController rc, MapLocation target) throws GameActionException {
        int dirIn = towards(pos, target);
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
}
