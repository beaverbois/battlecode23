package Utilities;

import battlecode.common.*;

/**
 * Public utility class
 */
public class Util {

    public static int numWellsStored = 4;
    public static int wellIndexMin = 0;
    public static int wellIndexMax = 3;

    public static final int codingMultiplier = 100; // originally GameConstants.MAP_MAX_WIDTH
    public static MapLocation intToLoc(int raw) {
        return new MapLocation(raw / codingMultiplier, raw % codingMultiplier);
    }

    // Maps (x,y) to "xy"
    public static int locToInt(MapLocation pos) {
        return pos.x * codingMultiplier + pos.y;
    }

    public static void writeWell(RobotController rc, ResourceType type, MapLocation loc) throws GameActionException {
        // search until we find an available index to write
        if (!rc.canWriteSharedArray(wellIndexMin, 1)) {
            System.out.println(rc.getID() + " Could not write to shared array!");
            return;
        }
        int index;
        for (index = wellIndexMin; index <= wellIndexMax; index++) {
            if (rc.readSharedArray(index) == 0) {
                String val = "" + type.resourceID + locToInt(loc);
                rc.writeSharedArray(index, Integer.parseInt(val));
                return;
            }
        }
    }

    public static MapLocation getWellLocation(RobotController rc, int index) throws IndexOutOfBoundsException, GameActionException {
        if (index < wellIndexMin || index > wellIndexMax)
            throw new IndexOutOfBoundsException("Well index out of bounds");
        //TODO: This will cause errors when well is at 0,0
        else if (rc.readSharedArray(index) == 0) {
            return null;
        }

        String val = String.valueOf(rc.readSharedArray(index));
        MapLocation loc = intToLoc(Integer.parseInt(val.substring(1)));

        return loc;
    }

    public static ResourceType getWellType(RobotController rc, int index) throws GameActionException {
        if (index < wellIndexMin || index > wellIndexMax)
            throw new IndexOutOfBoundsException("Well index out of bounds");
        //TODO: This will cause errors when well is at 0,0
        else if (rc.readSharedArray(index) == 0) {
            return null;
        }

        String val = String.valueOf(rc.readSharedArray(index));
        int type = Integer.parseInt(String.valueOf(val.charAt(0)));

        return ResourceType.values()[type];
    }

    public static int getNumWellsFound(RobotController rc) throws GameActionException {
        // sum the total number of non-0 value wells
        int total = 0;
        for (int i = wellIndexMin; i <= wellIndexMax; i++) {
            if (getWellLocation(rc, i) != null) {
                total++;
            }
        }
        return total;
    }

    //Movement methods
    public static int distance(MapLocation pos, MapLocation target) {
        //Does not take into account impassable terrain.
        return Math.max(Math.abs(target.x - pos.x), Math.abs(target.y - pos.y));
    }

    public static MapLocation closest(MapLocation pos, MapLocation... targets) {
        //Assuming at least 1 target, and ignoring terrain.
        if(targets.length == 0) {
            System.out.println("Calling closest with no targets.");
            return pos;
        }
        int minDistance = distance(pos, targets[0]);
        MapLocation close = targets[0];
        for(MapLocation target : targets) {
            if(distance(pos, target) < minDistance) {
                minDistance = distance(pos, target);
                close = target;
            }
        }
        return close;
    }

    public static int away(MapLocation pos, MapLocation target) {
        //Returns the INDEX in directions of the direction away from target.
        if(pos == target) return -1;

        int xgap, ygap;

        xgap = target.x - pos.x;
        ygap = target.y - pos.y;

        if(xgap == 0) return ygap > 0 ? 4 : 0;
        if(ygap == 0) return xgap > 0 ? 6 : 2;
        if(xgap > 0) return ygap > 0 ? 5 : 7;
        return ygap > 0 ? 1 : 3;
    }

    public static int towards(MapLocation pos, MapLocation target) {
        //Returns the INDEX in directions of the direction away from target.
        if(pos == target) return -1;

        int xgap, ygap;

        xgap = target.x - pos.x;
        ygap = target.y - pos.y;

        if(xgap == 0) return ygap > 0 ? 0 : 4;
        if(ygap == 0) return xgap > 0 ? 2 : 6;
        if(xgap > 0) return ygap > 0 ? 1 : 3;
        return ygap > 0 ? 7 : 5;
    }

    /**
     * Storage Layout
     * Largest loc is 3,599
     * Largest storage value is 2^16 = 65535
     *
     * 0 .. 3 | Resource Type | Well Location
     */
}
