package EfficientRush;

import battlecode.common.*;

import static EfficientRush.RobotPlayer.*;

public class Headquarters {
    static void run(RobotController rc) throws GameActionException {
        //Make scout carriers every 5 turns.
        if(rc.getRobotCount() > rc.getMapHeight() * rc.getMapWidth() / 6) rc.writeSharedArray(31, 2);
        else if(turnCount % 5 == 1) rc.writeSharedArray(31, 0);
        else if(turnCount % 5 == 0) rc.writeSharedArray(31, 1);
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
