package EnhancedRush;

import battlecode.common.*;

import static EnhancedRush.RobotPlayer.*;
import static Util.Util.intToLoc;

public class Headquarters {
    static void run(RobotController rc) throws GameActionException {
        //Make scout carriers every 5 turns.
        if(turnCount % 2 == 0) rc.writeSharedArray(31, 1);
        else if(turnCount % 2 == 1) rc.writeSharedArray(31, 0);
        // Pick a direction to build in.
        int i = rng.nextInt(directions.length);
        //Direction dir = directions[i++];
        MapLocation newLoc = rc.getLocation().add(directions[i++%directions.length]);
        while(rc.canSenseRobotAtLocation(newLoc) && i < directions.length) {
            newLoc = rc.getLocation().add(directions[i++%directions.length]);
        }
        //Testing no anchors
        /*if (rc.canBuildAnchor(Anchor.STANDARD)) {
            // If we can build an anchor do it!
            rc.buildAnchor(Anchor.STANDARD);
            rc.setIndicatorString("Building anchor! " + rc.getAnchor());
        }
        */
        //Trying prioritizing launchers. Will likely want a state machine
        //to determine the ratio in which they should be built.
        if (rc.canBuildRobot(RobotType.LAUNCHER, newLoc)) {
            // Let's try to build a carrier.
            rc.setIndicatorString("Building a launcher");
            rc.buildRobot(RobotType.LAUNCHER, newLoc);
        } else if (rc.canBuildRobot(RobotType.CARRIER, newLoc)){
            // Let's try to build a launcher.
            rc.setIndicatorString("Building a carrier");
            rc.buildRobot(RobotType.CARRIER, newLoc);
        }
    }
}
