package Sprint2;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.ResourceType;
import battlecode.common.RobotController;

import java.util.ArrayList;

import static Sprint2.CarrierSync.*;
import static Sprint2.Launcher.*;
import static Sprint2.RobotPlayer.*;
import static Sprint2.Util.*;

public class LauncherSync {

    static int minReportDist = 3, maxSwarmDist = 20;
    static int enemyLocMin = 32, enemyLocMax = 44, suspectedHQMin = 9, suspectedHQMax = 12;

    static boolean foundHQ = false;

    public static void readOppHeadquarters(RobotController rc) throws GameActionException {
        int count = 0;
        for (int i = 0; i < allOpposingHQ.length; i++) {
            int read = rc.readSharedArray(i + 4) % 10000;
            if (read != 0 && read != locToInt(allOpposingHQ[i])) {
                if (locToInt(allOpposingHQ[i]) == 0) {
                    allOpposingHQ[i] = intToLoc(read);
                }
                else if (i < allOpposingHQ.length - 1) {
                    allOpposingHQ[i + 1] = allOpposingHQ[i];
                    allOpposingHQ[i] = intToLoc(read);
                }
            }
            if(read != 0) count++;
        }
        if(count == hqList.length) foundHQ = true;
    }

    public static MapLocation[] closestTargetHQ(RobotController rc) throws GameActionException {
        MapLocation rcLocation = rc.getLocation();

        MapLocation[] untakenHQ = new MapLocation[allOpposingHQ.length];
        int count = 0;
        for (int i = 0; i < allOpposingHQ.length; i++) {
            if (locToInt(allOpposingHQ[i]) == 0) break;
            if (rc.readSharedArray(i + 4) / 10000 == 0) untakenHQ[count++] = allOpposingHQ[i];
        }
        if (count > 0) {
            for(int i = 1; i < count; i++) {
                double dist = dist(untakenHQ[i], rcLocation);
                for(int j = i; j > 0; j--) {
                    if(dist < dist(untakenHQ[j-1], rcLocation)) {
                        MapLocation temp = untakenHQ[j-1];
                        untakenHQ[j-1] = untakenHQ[j];
                        untakenHQ[j] = temp;
                    } else break;
                }
            }

            MapLocation[] close = new MapLocation[count];
            System.arraycopy(untakenHQ, 0, close, 0, count);

            return close;
        }

        return null;
    }

    public static void reportHQ(RobotController rc) throws GameActionException {
        //Update suspectedHQ with the correct info.
        if(!foundHQ) {
            for (int i = 0; i < hqList.length; i++) {
                for (int j = 0; j < 3; j++) {
                    if (suspectedOppHQ[3 * i + j].equals(newKnownHQ)) {
                        suspectCount = j;
                        writeSuspected(rc, true);
                        break;
                    }
                }
            }
            foundHQ = true;
        }

        //Update shared array with status updates on suppressed HQ.
        for (int i = 0; i < allOpposingHQ.length; i++) {
            int hq = locToInt(allOpposingHQ[i]);
            if (target != null && target.equals(allOpposingHQ[i])) {
                rc.writeSharedArray(4 + i, 10000 * oppHQStatus + hq);
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
        for(int i = 0; i < hqList.length; i++) {
            int width = rc.getMapWidth() - 1, height = rc.getMapHeight() - 1;
            //Rotated
            suspectedOppHQ[3 * i] = new MapLocation(width - hqList[i].x, height - hqList[i].y);
            //Reflected over x
            suspectedOppHQ[3 * i + 1] = new MapLocation(hqList[i].x, height - hqList[i].y);
            //Reflected over y
            suspectedOppHQ[3 * i + 2] = new MapLocation(width - hqList[i].x, hqList[i].y);
        }

        //Now, replace any confirmed location with [120, 120], as they will never be pathed to.
        MapLocation nonexistent = new MapLocation(120, 120);
        for (int i = 0; i < hqList.length; i++) {
            int read = rc.readSharedArray(i + suspectedHQMin);
            if(read % 10 == 2) suspectedOppHQ[3 * i] = nonexistent;
            if(read / 10 % 10 == 2) suspectedOppHQ[3 * i + 1] = nonexistent;
            if(read / 100 % 10 == 2) suspectedOppHQ[3 * i + 2] = nonexistent;
        }
    }

    public static void updateSuspected(RobotController rc) throws GameActionException {
        //Iterate through suspectedOppHQ and set any locations confirmed to not exist to [120, 120].
        MapLocation nonexistent = new MapLocation(120, 120);
        for (int i = 0; i < hqList.length; i++) {
            //Assuming we will never incorrectly identify where the HQ is.
            int read = rc.readSharedArray(i + suspectedHQMin);
            if(read % 10 == 2) suspectedOppHQ[3 * i] = nonexistent;
            if(read / 10 % 10 == 2) suspectedOppHQ[3 * i + 1] = nonexistent;
            if(read / 100 % 10 == 2) suspectedOppHQ[3 * i + 2] = nonexistent;
        }
    }

    public static void writeSuspected(RobotController rc, boolean exists) throws GameActionException {
        //First, make sure we can write.
        if (!rc.canWriteSharedArray(0, 0)) {
            System.out.println("Trying to write when unable.");
            return;
        }
        if(foundHQ) {
            System.out.println("Trying to write when already found.");
            return;
        }
        System.out.println("Writing " + suspectCount + ", " + exists);

        //If exists, then we know for certain the location of EVERY headquarters.
        int div = (int) (Math.pow(10, suspectCount % 3));
        if(exists) {
            foundHQ = true;

            //If suspectCount % 3 = 0, we know the map is rotated. If 1, then reflected over x, and if 2, then over y.
            int write = 222 - div;
            for(int i = suspectedHQMin; i < suspectedHQMax; i++) {
                rc.writeSharedArray(i, write);
            }

            //Update known enemy HQ location.
            for(int i = 0; i < hqList.length; i++) {
                int read = rc.readSharedArray(i + 4);
                rc.writeSharedArray(i + 4, read / 10000 + locToInt(suspectedOppHQ[3 * i + suspectCount % 3]));
            }
            return;
        }

        //Otherwise, update the correct HQ using suspectCount.
        int read = rc.readSharedArray(suspectCount / 3 + suspectedHQMin);
        //Modifiers to test our specific index.
        if(read / div % 10 == 0) {
            //Build an int with the updated value, preserving the other data if !exists.
            int write = read % div + read / (div * 100) + 2 * div;
            rc.writeSharedArray(suspectCount / 3 + suspectedHQMin, write);
        }
    }
}
