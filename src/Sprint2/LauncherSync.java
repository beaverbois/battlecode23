package Sprint2;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.ResourceType;
import battlecode.common.RobotController;

import java.util.ArrayList;

import static Sprint2.CarrierSync.*;
import static Sprint2.Launcher.*;
import static Sprint2.RobotPlayer.hqList;
import static Sprint2.Util.*;

public class LauncherSync {

    static int minReportDist = 3, maxSwarmDist = 10;
    static int enemyLocMin = 32, enemyLocMax = 44, suspectedHQMin = 9, suspectedHQMax = 12;

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

    public static void reportHQ(RobotController rc, MapLocation[] oppHQ) throws GameActionException {
        //Update shared array with enemy HQ and status updates on suppressed HQ.
        for (int i = 0; i < oppHQ.length; i++) {
            int read = rc.readSharedArray(4+i);
            int hq = locToInt(oppHQ[i]);
            if (hq != 0 && (read == 0 || (target != null && target.equals(oppHQ[i])))) {
                if (target != null && target.equals(oppHQ[i])) rc.writeSharedArray(4 + i, 10000 * oppHQStatus + hq);
                else rc.writeSharedArray(4 + i, hq);
            }
        }
    }

    public static void reportEnemy(RobotController rc, MapLocation enemyLoc) throws GameActionException {
        for (int i = enemyLocMin; i < enemyLocMax; i++) {
            int read = rc.readSharedArray(i);
            int dist = distance(enemyLoc, intToLoc(read));
            if (dist < minReportDist) {
                rc.writeSharedArray(i, 0);
            }
        }
    }

    public static void reportWell(RobotController rc, MapLocation targetWellLocation, ResourceType targetWellType) throws GameActionException {
        //ONLY CALL IF CAN WRITE
        ArrayList<MapLocation> targetWellLocations = new ArrayList<>();

        for (int i = WELL_INDEX_MIN; i <= WELL_INDEX_MAX; i++) {
            if (getWellType(rc, i) == targetWellType) targetWellLocations.add(getWellLocation(rc, i));
        }

        if (targetWellLocations.contains(targetWellLocation)) {
            return;
        }

        writeWell(rc, targetWellType, targetWellLocation);
    }

    public static void setSuspected(RobotController rc) throws GameActionException {
        suspectedOppHQ = new MapLocation[hqList.length * 3];

        //First, store each possible location.
        for (int i = 0; i < hqList.length; i++) {
            int width = rc.getMapWidth(), height = rc.getMapHeight();
            //Rotated
            suspectedOppHQ[0] = new MapLocation(width - hqList[0].x, height - hqList[0].y);
            //Reflected over x
            suspectedOppHQ[1] = new MapLocation(hqList[0].x, height - hqList[0].y);
            //Reflected over y
            suspectedOppHQ[2] = new MapLocation(width - hqList[0].x, hqList[0].y);
        }

        //Now, replace any confirmed location with [120, 120], as it will never be pathed to.
        MapLocation nonexistent = new MapLocation(120, 120);
        for (int i = 0; i < hqList.length; i++) {
            int read = rc.readSharedArray(i + suspectedHQMin);
            if (read % 10 != 0) suspectedOppHQ[3 * i] = nonexistent;
            if (read / 10 % 10 != 0) suspectedOppHQ[3 * i + 1] = nonexistent;
            if (read / 100 % 10 != 0) suspectedOppHQ[3 * i + 2] = nonexistent;
        }
    }

    public static void updateSuspected(RobotController rc) throws GameActionException {
        //Iterate through suspectedOppHQ and set any confirmed locations to [120, 120].
        MapLocation nonexistent = new MapLocation(120, 120);
        for (int i = 0; i < hqList.length; i++) {
            //Assuming we will never incorrectly identify where the HQ is.
            int read = rc.readSharedArray(i + suspectedHQMin);
            if (read % 10 != 0) suspectedOppHQ[3 * i] = nonexistent;
            if (read / 10 % 10 != 0) suspectedOppHQ[3 * i + 1] = nonexistent;
            if (read / 100 % 10 != 0) suspectedOppHQ[3 * i + 2] = nonexistent;
        }
    }

    public static void writeSuspected(RobotController rc, boolean exists) throws GameActionException {
        //First, make sure we can write.
        if (!rc.canWriteSharedArray(0, 0)) {
            System.out.println("Trying to write when unable.");
            return;
        }

        //Next, update the correct HQ using suspectCount.
        int read = rc.readSharedArray(suspectCount / 3 + suspectedHQMin);
        //Modifiers to test our specific index.
        int div = (int) Math.pow(10, suspectCount % 3 + 1);
        if (read / div % 10 == 0) {
            //Build an int with the updated value, preserving the other data.
            int write = read % div + read / (div * 100) + (exists ? 1 : 2) * div;
            rc.writeSharedArray(suspectCount / 3 + suspectedHQMin, write);
        }
    }
}
