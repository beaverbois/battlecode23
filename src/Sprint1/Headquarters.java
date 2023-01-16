package Sprint1;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static Sprint1.RobotPlayer.*;
import static Utilities.CarrierSync.*;
import static Utilities.CarrierSync.setCarrierAssignment;
import static Utilities.HQSync.hqMinIndex;
import static Utilities.Util.locToInt;

public class Headquarters {

    static boolean stateLock = false;
    static MapLocation hqLocation = null;
    public static ResourceType carrierAssignment = null;
    static List<Direction> shuffledDir = null;
    static List<Direction> shuffledCardinalDir = null;

    static void run(RobotController rc) throws GameActionException {
        //
        if (!stateLock) {
            // runs on hq creation
            //This is accounted for in RobotPlayer for me.
//            hqLocation = rc.getLocation();
//            if (rc.canWriteSharedArray(hqMinIndex, locToInt(hqLocation))) {
//                rc.writeSharedArray(hqMinIndex, locToInt(hqLocation));
//            }

            carrierAssignment = ResourceType.ADAMANTIUM;

            shuffledDir = new ArrayList<>(Arrays.asList(directions));
            shuffledCardinalDir = new ArrayList<>(Arrays.asList(Direction.cardinalDirections()));
            Collections.shuffle(shuffledDir);
            Collections.shuffle(shuffledCardinalDir);

            stateLock = true;
        }
        // If there are opponents within radius of headquarters, spawn launchers in direction of opponents
        //Sticking to spawning launchers when able, should make little difference.
        //TODO : spawn/move away from borders on random spawn
//        RobotInfo[] opponents = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
//        if (opponents.length > 0) {
//            MapLocation loc = rc.getLocation().add(rc.getLocation().directionTo(opponents[0].location));
//            if (rc.canBuildRobot(RobotType.LAUNCHER, loc)) {
//                rc.buildRobot(RobotType.LAUNCHER, loc);
//            } else {
//                // if direction towards opponent is blocked, pick another one
//                Collections.shuffle(shuffledDir);
//                for (Direction dir : shuffledDir) {
//                    MapLocation randomLoc = rc.getLocation().add(dir);
//                    if (rc.canBuildRobot(RobotType.LAUNCHER, randomLoc)) {
//                        rc.buildRobot(RobotType.LAUNCHER, randomLoc);
//                        break;
//                    }
//                }
//            }
//        }
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
//            if (rc.getResourceAmount(ResourceType.ADAMANTIUM) > GameConstants.UPGRADE_WELL_AMOUNT) {
//
//            } else if (rc.getResourceAmount(ResourceType.MANA) > GameConstants.UPGRADE_WELL_AMOUNT) {
//
//            }
            // Spawn carriers towards random well
            // TODO: In direction of closest well with carrierAssignment type
            System.out.println(getWellLocation(rc, wellIndexMin + rng.nextInt(numWellsStored)));
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

        //Make island scouts late-game.
        if(rc.getRobotCount() > rc.getMapHeight() * rc.getMapWidth() / 6) rc.writeSharedArray(55, 2);

        //Don't need this because of CarrierSync
//        else if(turnCount % 5 == 1) rc.writeSharedArray(31, 0);
//        else if(turnCount % 5 == 0) rc.writeSharedArray(31, 1);

        // Pick a direction to build in.
        int i = rng.nextInt(directions.length);
        //Direction dir = directions[i++];
        MapLocation newLoc = rc.getLocation().add(directions[i++%directions.length]);
        while(rc.canSenseRobotAtLocation(newLoc) && i < directions.length) {
            newLoc = rc.getLocation().add(directions[i++%directions.length]);
        }

        boolean bloated = false;

        if (rc.getRobotCount() > rc.getMapHeight() * rc.getMapWidth() / 8 && rc.canBuildAnchor(Anchor.STANDARD) && rc.getNumAnchors(Anchor.STANDARD) < 2) {
            // If we can build an anchor do it!
            rc.buildAnchor(Anchor.STANDARD);
        }

        if(rc.getRobotCount() > rc.getMapHeight() * rc.getMapWidth() / 3) {
            rc.setIndicatorString("Bloated");
            bloated = true;
            return;
        }

        rc.setIndicatorString("About to try and build, " + rc.canBuildRobot(RobotType.LAUNCHER, newLoc));

        //Trying prioritizing launchers. Will likely want a state machine
        //to determine the ratio in which they should be built.
        if ((rc.getRobotCount() < rc.getMapHeight() * rc.getMapWidth() / 8 || rc.getResourceAmount(ResourceType.MANA) > 200) && rc.canBuildRobot(RobotType.LAUNCHER, newLoc)) {
            // Let's try to build a carrier.
            rc.setIndicatorString("Building a launcher");
            rc.buildRobot(RobotType.LAUNCHER, newLoc);
        } else if ((rc.getRobotCount() < rc.getMapHeight() * rc.getMapWidth() / 6 || rc.getNumAnchors(Anchor.STANDARD) > 0) && rc.canBuildRobot(RobotType.CARRIER, newLoc)){
            // Let's try to build a launcher.
            rc.setIndicatorString("Building a carrier");
            rc.buildRobot(RobotType.CARRIER, newLoc);
        }
    }
}
