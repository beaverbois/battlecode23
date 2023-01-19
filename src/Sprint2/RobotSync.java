package Sprint2;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

import java.util.ArrayList;

import static Sprint2.Launcher.oppHQStatus;
//import static Sprint2.Launcher.target;
import static Sprint2.Util.*;

public class RobotSync {

    static int minReportDist = 3, maxSwarmDist = 10;
    static int enemyLocMin = 32, enemyLocMax = 44;

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
        MapLocation rcLocation = rc.getLocation();

        ArrayList<MapLocation> untakenHQ = new ArrayList<>();
        for (int i = 0; i < knownOppHQ.length; i++) {
            if (locToInt(knownOppHQ[i]) == 0) break;
            if (rc.readSharedArray(i + 4) / 10000 == 0) untakenHQ.add(knownOppHQ[i]);
        }
        if (untakenHQ.size() != 0) {
            MapLocation close = closest(rcLocation, untakenHQ.toArray(new MapLocation[0]));
            if (distance(rcLocation, close) < maxSwarmDist) {
                return close;
            }
        }

        return null;
    }

//    public static void reportHQ(RobotController rc, MapLocation[] oppHQ) throws GameActionException {
//        //Update shared array with enemy HQ and status updates on suppressed HQ.
//        for (int i = 0; i < oppHQ.length; i++) {
//            int read = rc.readSharedArray(4+i);
//            int hq = locToInt(oppHQ[i]);
//            if (hq != 0 && (read == 0 || (target != null && target.equals(oppHQ[i])))) {
//                if (target != null && target.equals(oppHQ[i])) rc.writeSharedArray(4 + i, 10000 * oppHQStatus + hq);
//                else rc.writeSharedArray(4 + i, hq);
//            }
//        }
//    }

    public static void reportEnemy(RobotController rc, MapLocation enemyLoc) throws GameActionException {
        for (int i = enemyLocMin; i < enemyLocMax; i++) {
            int read = rc.readSharedArray(i);
            int dist = distance(enemyLoc, intToLoc(read));
            if (dist < minReportDist) {
                rc.writeSharedArray(i, 0);
            }
        }
    }
}
