package AmpMasterServant;

import battlecode.common.*;

import static RushWithAnchors.RobotPlayer.*;
import static Utilities.Util.locToInt;

public class Headquarters {

    static int numHQ = 0;

    static int newAlpha = 0;

    static int numAlpha = 0;

    static int[] alphaIDs = new int[4];

    static void run(RobotController rc) throws GameActionException {
        //Make scout carriers every 4 turns.
        rc.setIndicatorString(((Integer) numAlpha).toString());

        numAlpha = 0;

        for(int i = 24; i < 31; i += 2) {
            if(rc.readSharedArray(i) != 0) {
                alphaIDs[numAlpha] = rc.readSharedArray(i);
                numAlpha += 1;
            }
        }

        if(turnCount > 1000) rc.writeSharedArray(10, 2);
        else if(turnCount % 4 == 0) rc.writeSharedArray(10, 1);
        if(turnCount % 4 == 1) rc.writeSharedArray(10, 0);

        int read = rc.readSharedArray(8);

        //Begin alpha-beta setup
        if(turnCount > 50 && turnCount % 2 == 0) rc.writeSharedArray(8, read % 10 + read / 100 * 100 + 10);
        //else if(turnCount > 100) rc.writeSharedArray(8, read % 10 + read / 100 * 100);

        // Pick a direction to build in.
        int i = rng.nextInt(directions.length);
        //Direction dir = directions[i++];
        MapLocation newLoc = rc.getLocation().add(directions[i++%directions.length]);
        while(rc.canSenseRobotAtLocation(newLoc) && i < directions.length) {
            newLoc = rc.getLocation().add(directions[i++%directions.length]);
        }

        if (turnCount > 1000 && rc.canBuildAnchor(Anchor.STANDARD)) {
            // If we can build an anchor do it!
            rc.buildAnchor(Anchor.STANDARD);
        }

        if(turnCount > 50 && read / 100 % 10 == 0) {
            for (int j = 24; j < 31; j += 2) {
                if (rc.readSharedArray(j) == 0) {
                    //1 through 4
                    newAlpha = j / 2 - 11;
                    rc.writeSharedArray(8, read % 100 + read / 1000 + newAlpha * 100);
                    break;
                }
            }
        }

        //Stop if there are enemies nearby.
        int radius = rc.getType().visionRadiusSquared;
        Team opponent = rc.getTeam().opponent();
        RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
        if(enemies.length > 1) {
            rc.setIndicatorString("Under Attack!");
            rc.writeSharedArray(numHQ, locToInt(rc.getLocation()) + 10000);
            writeToEmptyTarget(rc, locToInt(rc.getLocation()), false);
            return;
        }

        boolean waitingAlpha = false;

        for(int j = 0; j < 4; j++) {
            int id = alphaIDs[j];
            if(id != 0 && rc.canSenseRobot(id))
                waitingAlpha = true;
        }

        if(!waitingAlpha && turnCount > 50 && numAlpha < turnCount / 300 + 1 && numAlpha < 4) {
            //Check if an alpha is nearby.
            if(rc.canBuildRobot(RobotType.AMPLIFIER, newLoc)) {
                rc.buildRobot(RobotType.AMPLIFIER, newLoc);
            }
        } else if ((turnCount < 1000 || rc.getNumAnchors(Anchor.STANDARD) > 0) && rc.canBuildRobot(RobotType.LAUNCHER, newLoc)) {
            // Let's try to build a carrier.
            rc.setIndicatorString("Building a launcher");
            rc.buildRobot(RobotType.LAUNCHER, newLoc);
        } else if ((turnCount < 1000 || rc.getNumAnchors(Anchor.STANDARD) > 0) && rc.canBuildRobot(RobotType.CARRIER, newLoc)){
            // Let's try to build a launcher.
            rc.setIndicatorString("Building a carrier");
            rc.buildRobot(RobotType.CARRIER, newLoc);
        }
    }


    private static void writeToEmptyTarget(RobotController rc, int write, boolean hq) throws GameActionException {
        for(int i = 33; i < 44; i += 2)
            if(rc.readSharedArray(i) == 0) {
                rc.writeSharedArray(i, write);
                return;
            }

        //We know there was no open slot to write in. If hq, should override slot with no alpha assigned.
        if(hq) {
            for (int i = 32; i < 43; i += 2) {
                if (rc.readSharedArray(i) == 0) {
                    rc.writeSharedArray(i + 1, write);
                    return;
                }
            }
        }
    }
}
