package FarmFirst;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static FarmFirst.Headquarters.hqIndex;
import static FarmFirst.RobotPlayer.directions;
import static FarmFirst.RobotPlayer.rng;
import static Util.Util.*;

public class Carrier {
    enum CarrierState {
        SCOUTING,
        MOVING,
        FARMING,
        RETURNING
    }

    static Direction scoutDirection = null;
    static CarrierState state = null;
    static boolean stateLock = false;
    static MapLocation hqLocation = null;
    static MapLocation targetWellLocation = null;
    static ResourceType targetWellType = null;
    static final int maxCollectionCycles = 4; // Max is 6 for 1 move/turn after collecting
    static int numCycles = 0;
    static List<Direction> shuffledDir;


    static void run(RobotController rc) throws GameActionException {
        if (state == null) {
            state = CarrierState.SCOUTING;
            hqLocation = intToLoc(rc.readSharedArray(hqIndex)); //TODO: eventually make nicer
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
                    }
                    else {
                        // if we have discovered all wells, pick a random well from our list
                        //TODO: This should be more distributed, not random
                        int index = wellIndexMin + rng.nextInt(numWellsStored);
                        targetWellLocation = getWellLocation(rc, index);
                        targetWellType = getWellType(rc, index);
                        state = CarrierState.MOVING;
                        break;
                    }
                }

                //TODO: Do not move towards borders

                else {
                    rc.setIndicatorString(state.toString());
                    // once we have picked an initial direction, go in that direction till we can no longer
                    if (rc.canMove(scoutDirection)) {
                        rc.move(scoutDirection);
                    } else {
                        // if we can't go that way, randomly pick another direction until one is found
                        for (Direction dir : shuffledDir) {
                            if (rc.canMove(dir)) {
                                scoutDirection = dir;
                                rc.move(scoutDirection);
                                break;
                            }
                        }
                    }
                }

                if (rc.senseNearbyWells().length > 0) {
                    targetWellLocation = rc.senseNearbyWells()[0].getMapLocation();
                    targetWellType = rc.senseNearbyWells()[0].getResourceType();
                    state = CarrierState.MOVING;
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
                        for (Direction dir : shuffledDir) {
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
                        for (Direction dir : shuffledDir) {
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

                rcLocation = rc.getLocation();
                Direction hqDirection = rcLocation.directionTo(hqLocation);
                if (rc.canMove(hqDirection)) {
                    rc.move(hqDirection);
                } else {
                    // if path is blocked, move to different square around hq
                    for (Direction dir : shuffledDir) {
                        closestSquare = hqLocation.add(dir);
                        closestSquareDir = rcLocation.directionTo(closestSquare);
                        if (rc.canMove(closestSquareDir)) {
                            rc.move(closestSquareDir);
                            break;
                        }
                    }

                    // if we are still blocked, pick a random square around us to move to
                    if (rc.isMovementReady()) {
                        for (Direction dir : shuffledDir) {
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
                    if (rc.canTransferResource(hqLocation, targetWellType, rc.getResourceAmount(targetWellType))) {
                        rc.transferResource(hqLocation, targetWellType,rc.getResourceAmount(targetWellType));
                    }

                    state = CarrierState.MOVING;
                }
                break;
        }
    }
}
