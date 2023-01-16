package FarmFirst;

import Utilities.HQSync;
import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static FarmFirst.RobotPlayer.directions;
import static FarmFirst.RobotPlayer.rng;
import static Utilities.CarrierSync.*;
import static Utilities.HQSync.hqMinIndex;
import static Utilities.Util.locToInt;

public class Headquarters {
    static boolean stateLock = false;
    static MapLocation hqLocation = null;
    public static ResourceType carrierAssignment = null;
    static List<Direction> shuffledDir = null;
    static List<Direction> shuffledCardinalDir = null;

    static void run(RobotController rc) throws GameActionException {
        if (!stateLock) {
            // runs on hq creation
            hqLocation = rc.getLocation();
            if (rc.canWriteSharedArray(hqMinIndex, locToInt(hqLocation))) {
                rc.writeSharedArray(hqMinIndex, locToInt(hqLocation));
            }

            carrierAssignment = ResourceType.ADAMANTIUM;

            shuffledDir = new ArrayList<>(Arrays.asList(directions));
            shuffledCardinalDir = new ArrayList<>(Arrays.asList(Direction.cardinalDirections()));
            Collections.shuffle(shuffledDir);
            Collections.shuffle(shuffledCardinalDir);

            stateLock = true;
        }
        // If there are opponents within radius of headquarters, spawn launchers in direction of opponents
        //TODO : spawn/move away from borders on random spawn
        RobotInfo[] opponents = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (opponents.length > 0) {
            MapLocation loc = rc.getLocation().add(rc.getLocation().directionTo(opponents[0].location));
            if (rc.canBuildRobot(RobotType.LAUNCHER, loc)) {
                rc.buildRobot(RobotType.LAUNCHER, loc);
            } else {
                // if direction towards opponent is blocked, pick another one
                Collections.shuffle(shuffledDir);
                for (Direction dir : shuffledDir) {
                    MapLocation randomLoc = rc.getLocation().add(dir);
                    if (rc.canBuildRobot(RobotType.LAUNCHER, randomLoc)) {
                        rc.buildRobot(RobotType.LAUNCHER, randomLoc);
                        break;
                    }
                }
            }
        }
        else {
             // If not all wells have been found, spawn carrier in random direction
            if (getNumWellsFound(rc) < numWellsStored) {
                Collections.shuffle(shuffledDir);
                for (Direction dir : shuffledDir) {
                    MapLocation randomLoc = rc.getLocation().add(dir);
                    if (rc.canBuildRobot(RobotType.CARRIER, randomLoc)) {
                        rc.buildRobot(RobotType.CARRIER, randomLoc);
                    }
                }
            } else {
                if (rc.getResourceAmount(ResourceType.ADAMANTIUM) > GameConstants.UPGRADE_WELL_AMOUNT) {

                } else if (rc.getResourceAmount(ResourceType.MANA) > GameConstants.UPGRADE_WELL_AMOUNT) {

                }
                // Spawn carriers towards random well
                // TODO: In direction of closest well with carrierAssignment type
                Direction dir = hqLocation.directionTo(getWellLocation(rc, wellIndexMin + rng.nextInt(numWellsStored)));
                MapLocation newLoc = hqLocation.add(dir);
                if (rc.canBuildRobot(RobotType.CARRIER, newLoc)) {
                    rc.buildRobot(RobotType.CARRIER, newLoc);
                }
            }
            // Alternate next carrier spawn between Ad and Mn target resources
            if (carrierAssignment == ResourceType.ADAMANTIUM) {
                setCarrierAssignment(rc, ResourceType.MANA);
                carrierAssignment = ResourceType.MANA;
            } else {
                setCarrierAssignment(rc, ResourceType.ADAMANTIUM);
                carrierAssignment = ResourceType.ADAMANTIUM;
            }
        }
    }
}
