package EfficientRush;

import battlecode.common.*;

import static EfficientRush.RobotPlayer.*;

public class Headquarters {
    static void run(RobotController rc) throws GameActionException {
        //Make scout carriers every 4 turns.
        if(turnCount > 1000) rc.writeSharedArray(31, 2);
        else if(turnCount % 5 == 0) rc.writeSharedArray(31, 1);
        if(turnCount % 4 == 1) rc.writeSharedArray(31, 0);
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
        //Trying prioritizing launchers. Will likely want a state machine
        //to determine the ratio in which they should be built.
        if ((turnCount < 1000 || rc.getNumAnchors(Anchor.STANDARD) > 0) && rc.canBuildRobot(RobotType.LAUNCHER, newLoc)) {
            // Let's try to build a carrier.
            rc.setIndicatorString("Building a launcher");
            rc.buildRobot(RobotType.LAUNCHER, newLoc);
        } else if ((turnCount < 1000 || rc.getNumAnchors(Anchor.STANDARD) > 0) && rc.canBuildRobot(RobotType.CARRIER, newLoc)){
            // Let's try to build a launcher.
            rc.setIndicatorString("Building a carrier");
            rc.buildRobot(RobotType.CARRIER, newLoc);
        }
    }
}
