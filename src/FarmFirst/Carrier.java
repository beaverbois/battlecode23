package FarmFirst;

import battlecode.common.*;

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


    static void run(RobotController rc) throws GameActionException {
        if (state == null) {
            state = CarrierState.SCOUTING;
            hqLocation = intToLoc(rc.readSharedArray(hqIndex)); //TODO: eventually make nicer
        }

        switch (state) {
            case SCOUTING:
                // if we have not discovered all wells, pick a random direction to go in and discover them
                if (!stateLock) {
                    if (numWellsFound < numWellsStored) {
                        scoutDirection = directions[rng.nextInt(directions.length)];
                        stateLock = true; //TODO: this may cause a bug when numWellsFound >
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
                //TODO: random dir selection

                else {
                    rc.setIndicatorString(state.toString());
                    // once we have picked an initial direction, go in that direction till we can no longer
                    if (rc.canMove(scoutDirection)) {
                        rc.move(scoutDirection);
                    } else {
                        // if we can't go that way, choose another random direction to go in
                        for (Direction dir : directions) {
                            if (rc.canMove(dir)) {
                                scoutDirection = dir;
                                rc.move(scoutDirection);
                            }
                        }

                        // if we are still blocked, pick a random square around us
                        if (rc.isMovementReady()) {
                            for (Direction dir : directions) {
                                if (rc.canMove(dir)) {
                                    rc.move(dir);
                                }
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
                    // if path is blocked, move to different square around well
                    //TODO: This needs to be random not sequenced, including center
                    for (Direction dir : Direction.allDirections()) {
                        closestSquare = targetWellLocation.add(dir);
                        closestSquareDir = rcLocation.directionTo(closestSquare);
                        if (rc.canMove(closestSquareDir)) {
                            rc.move(closestSquareDir);
                        }
                    }

                    // if we are still blocked, pick a random square around us
                    if (rc.isMovementReady()) {
                        Direction dir = directions[rng.nextInt(directions.length)];
                        if (rc.canMove(dir)) {
                            rc.move(dir);
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
                    //TODO: Clean this implementation up
                }

                break;

            case FARMING:

                if (rc.canCollectResource(targetWellLocation, -1)) {
                    rc.collectResource(targetWellLocation, -1);
                    numCycles++;
                }

                rc.setIndicatorString(state.toString() + " CYCLE " + numCycles);

                if (numCycles == maxCollectionCycles) {
                    state = CarrierState.RETURNING;
                    numCycles = 0;

                    rcLocation = rc.getLocation();
                    Direction hqDirection = rcLocation.directionTo(hqLocation);
                    if (rc.canMove(hqDirection)) {
                        rc.move(hqDirection);
                    }

                    rc.setIndicatorString(state.toString() + " TO " + hqLocation);
                }

                break;

            case RETURNING:

                rcLocation = rc.getLocation();
                Direction hqDirection = rcLocation.directionTo(hqLocation);
                if (rc.canMove(hqDirection)) {
                    rc.move(hqDirection);
                }
                // if we are still blocked, pick a random square around us
                else if (rc.isMovementReady()) {
                    Direction dir = directions[rng.nextInt(directions.length)];
                    if (rc.canMove(dir)) {
                        rc.move(dir);
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
