package FarmFirst;

import battlecode.common.*;

import static FarmFirst.RobotPlayer.directions;
import static FarmFirst.RobotPlayer.rng;
import static Util.Util.getWellLocation;
import static Util.Util.locToInt;

public class Headquarters {
    public static int hqIndex = 4;
    static boolean stateLock = false;
    static MapLocation hqLocation = null;
    static void run(RobotController rc) throws GameActionException {
        if (!stateLock) {
            hqLocation = rc.getLocation();
            if (rc.canWriteSharedArray(hqIndex, locToInt(hqLocation))) {
                rc.writeSharedArray(hqIndex, locToInt(hqLocation));
            }

            stateLock = true;
        }
        // If there are opponents within radius of headquarters, spawn launchers in direction of opponents
        RobotInfo[] opponents = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (opponents.length > 0) {
            MapLocation loc = rc.getLocation().add(rc.getLocation().directionTo(opponents[0].location));
            if (rc.canBuildRobot(RobotType.LAUNCHER, loc)) {
                rc.buildRobot(RobotType.LAUNCHER, loc);
            } else {
                // if direction towards opponent is blocked, pick another one
                //TODO: Use randoms
                for (Direction dir : directions) {
                    MapLocation randomLoc = rc.getLocation().add(dir);
                    if (rc.canBuildRobot(RobotType.LAUNCHER, randomLoc)) {
                        rc.buildRobot(RobotType.LAUNCHER, randomLoc);
                        break;
                    }
                }
            }
        }
        else {
             // If no wells have been found, spawn carrier in random direction
            if (getWellLocation(rc, 0) == null) {
                Direction dir = directions[rng.nextInt(directions.length)];
                MapLocation newLoc = rc.getLocation().add(dir);
                if (rc.canBuildRobot(RobotType.CARRIER, newLoc)) {
                    rc.buildRobot(RobotType.CARRIER, newLoc);
                }
            } else {
                // Spawn carriers towards well
                // TODO: alternating well types
                Direction dir = hqLocation.directionTo(getWellLocation(rc, 0));
                MapLocation newLoc = hqLocation.add(dir);
                if (rc.canBuildRobot(RobotType.CARRIER, newLoc)) {
                    rc.buildRobot(RobotType.CARRIER, newLoc);
                }
            }
        }
    }
}
