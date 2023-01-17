package PostS1;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

import static PostS1.RobotPlayer.allOpposingHQ;
import static PostS1.Util.*;

public class LauncherSync {

    public static MapLocation[] readOppHeadquarters(RobotController rc, MapLocation[] knownOppHQ) throws GameActionException {
        for (int i = 0; i < knownOppHQ.length; i++) {
            int read = rc.readSharedArray(i + 4) % 10000;
            if (read != 0 && read != locToInt(knownOppHQ[i])) {
                if (locToInt(knownOppHQ[i]) == 0) {
                    knownOppHQ[i] = intToLoc(read);
                }
                else if (i < knownOppHQ.length - 1) {
                    knownOppHQ[i + 1] = knownOppHQ[i];
                    knownOppHQ[i] = intToLoc(read);
                }
            }
        }

        return knownOppHQ;
    }

    public static MapLocation closestTargetHQ(RobotController rc, MapLocation[] knownOppHQ) throws GameActionException {
        int count = 0;
        int maxDist = 10; //Max distance to swarm towards.

        MapLocation rcLocation = rc.getLocation();

        MapLocation[] untakenHQ = new MapLocation[4];
        for (int i = 0; i < allOpposingHQ.length; i++) {
            if (locToInt(allOpposingHQ[i]) == 0) break;
            if (rc.readSharedArray(i + 4) / 10000 == 0) untakenHQ[count++] = allOpposingHQ[i];
        }
        if(count != 0) {
            MapLocation close = closest(rcLocation, knownOppHQ);
            if(distance(rcLocation, close) < maxDist) {
                return close;
            }
        }

        return null;
    }
}
