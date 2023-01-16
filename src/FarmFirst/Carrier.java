package FarmFirst;

import battlecode.common.*;

import java.util.*;

import static FarmFirst.RobotPlayer.directions;
import static FarmFirst.RobotPlayer.rng;
import static Utilities.CarrierSync.*;
import static Utilities.HQSync.hqMinIndex;
import static Utilities.Util.closestDirections;
import static Utilities.Util.intToLoc;

public class Carrier {
    enum CarrierState {
        SCOUTING,
        MOVING,
        FARMING,
        RETURNING
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

    static void run(RobotController rc) throws GameActionException {
        if (state == null) {
            // this will run when the bot is created
            //TODO: check if hq is upgradable, shared array must have what hqs have enough resources to be upgradable boolean
            state = CarrierState.SCOUTING;
            hqLocation = intToLoc(rc.readSharedArray(hqMinIndex));
            targetType = getCarrierAssignment(rc);

            shuffledDir = new ArrayList<>(Arrays.asList(directions));
            Collections.shuffle(shuffledDir) ;
        }

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
//                    Collections.shuffle(shuffledDir);
                    if (rc.isMovementReady()) {
                        for (Direction dir : closestDirections(rcLocation, targetWellLocation)) {
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
                        for (Direction dir : closestDirections(rcLocation, hqLocation)) {
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
                        System.out.println("Wells Discovered: " + getNumWellsFound(rc));
                        reportingWell = false;
                        state = CarrierState.MOVING;
                        break;
                    }
                }

                rcLocation = rc.getLocation();
                Direction hqDirection = rcLocation.directionTo(hqLocation);
                if (rc.canMove(hqDirection)) {
                    rc.move(hqDirection);
                } else {
                    // if path is blocked, move to different square around hq
//                    Collections.shuffle(shuffledDir);
                    for (Direction dir : closestDirections(hqLocation, rcLocation)) {
                        closestSquare = hqLocation.add(dir);
                        closestSquareDir = rcLocation.directionTo(closestSquare);
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
                        rc.transferResource(hqLocation, targetType,rc.getResourceAmount(targetType));
                    }

                    if (!reportingWell) state = CarrierState.MOVING;
                }
                break;
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
                    System.out.println("writing well");
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
}
