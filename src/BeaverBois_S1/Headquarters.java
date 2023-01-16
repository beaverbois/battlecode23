package BeaverBois_S1;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static BeaverBois_S1.RobotPlayer.*;
import static BeaverBois_S1.CarrierSync.*;
import static BeaverBois_S1.CarrierSync.setCarrierAssignment;
import static BeaverBois_S1.Util.*;

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

        //Make island carriers late-game.
        if(rc.getRobotCount() > rc.getMapHeight() * rc.getMapWidth() / 12) rc.writeSharedArray(54, 1);

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

        //Here, start building anchors once the robot count is high enough. Increase the number to start building anchors earlier, decrease for later.
        if (rc.getRobotCount() > rc.getMapHeight() * rc.getMapWidth() / 12 && rc.canBuildAnchor(Anchor.STANDARD) && rc.getNumAnchors(Anchor.STANDARD) < 2) {
            // If we can build an anchor do it!
            rc.buildAnchor(Anchor.STANDARD);
        }

        // Pick a direction to build in.
        int i = rng.nextInt(directions.length);
        //Direction dir = directions[i++];
        MapLocation newLoc = rc.getLocation().add(directions[i++%directions.length]);
        while(rc.canSenseRobotAtLocation(newLoc) && i < directions.length) {
            newLoc = rc.getLocation().add(directions[i++%directions.length]);
        }

        //Spawn launchers if there are enemies in vision.
        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam().opponent());
        if(enemies.length > 0) {
            if(rc.canBuildRobot(RobotType.LAUNCHER, newLoc)) rc.buildRobot(RobotType.LAUNCHER, newLoc);
            rc.setIndicatorString("Under Attack!");
            for (int j = 32; j < 44; j++) {
                int read = rc.readSharedArray(j);
                int dist = distance(enemies[0].location, intToLoc(read));
                if (dist < 3) {
                    return;
                }
            }
            for (int j = 32; j < 44; j++) {
                if (rc.readSharedArray(j) == 0) {
                    rc.writeSharedArray(j, locToInt(enemies[0].location));
                    return;
                }
            }
            return;
        }

        //Stop making robots entirely once you cover an quarter of the map. Ideally don't get to bloated too early, since you need to build ISLANDS carriers.
        if(rc.getRobotCount() > rc.getMapHeight() * rc.getMapWidth() / 4) {
            rc.setIndicatorString("Bloated");
            return;
        }

        // If not all wells have been found, spawn carrier in random direction
        if (getNumWellsFound(rc) < numWellsStored) {
            Collections.shuffle(shuffledDir);
            for (Direction dir : shuffledDir) {
                MapLocation randomLoc = rc.getLocation().add(dir);
                if ((rc.getRobotCount() < rc.getMapHeight() * rc.getMapWidth() / 12 || rc.getNumAnchors(Anchor.STANDARD) > 0) && rc.canBuildRobot(RobotType.CARRIER, randomLoc)) {
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
            Direction dir = hqLocation.directionTo(getWellLocation(rc, wellIndexMin + rng.nextInt(numWellsStored)));
            MapLocation newLocat = hqLocation.add(dir);
            if ((rc.getRobotCount() < rc.getMapHeight() * rc.getMapWidth() / 12 || rc.getNumAnchors(Anchor.STANDARD) > 0) && rc.canBuildRobot(RobotType.CARRIER, newLocat)) {
                rc.buildRobot(RobotType.CARRIER, newLocat);
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

        //Don't need this because of CarrierSync
//        else if(turnCount % 5 == 1) rc.writeSharedArray(31, 0);
//        else if(turnCount % 5 == 0) rc.writeSharedArray(31, 1);

        //Again, bloating numbers. This time, larger number = stop building earlier. Prolly wanna increase for launchers, and maybe for carriers.
        //Make sure that we build anchors FAR BEFORE we stop building carriers, as otherwise the anchors will be stranded.
        //Alternatively, you can start turning farming carriers into ISLANDS carriers once rc.getRobotCount() is high enough.
        if ((rc.getRobotCount() < rc.getMapHeight() * rc.getMapWidth() / 12 || rc.getResourceAmount(ResourceType.MANA) > 200) && rc.canBuildRobot(RobotType.LAUNCHER, newLoc)) {
            // Let's try to build a carrier.
            rc.setIndicatorString("Building a launcher");
            rc.buildRobot(RobotType.LAUNCHER, newLoc);
        } else if ((rc.getRobotCount() < rc.getMapHeight() * rc.getMapWidth() / 12 || rc.getNumAnchors(Anchor.STANDARD) > 0) && rc.canBuildRobot(RobotType.CARRIER, newLoc)){
            // Let's try to build a launcher.
            rc.setIndicatorString("Building a carrier");
            rc.buildRobot(RobotType.CARRIER, newLoc);
        }
    }
}
