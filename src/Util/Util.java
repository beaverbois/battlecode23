package Util;

import battlecode.common.*;
import battlecode.world.Well;

import java.util.HashMap;
import java.util.Map;

/**
 * Public utility class
 */
public class Util {

    public static int wellIndexMin = 0;
    public static int wellIndexMax = 3;
    public static MapLocation intToLoc(int raw) {
        return new MapLocation(raw / GameConstants.MAP_MAX_WIDTH, raw % GameConstants.MAP_MAX_HEIGHT);
    }

    public static int locToInt(MapLocation pos) {
        return pos.x * GameConstants.MAP_MAX_WIDTH + pos.y;
    }

    public static void writeWell(RobotController rc, ResourceType type, MapLocation loc) throws GameActionException {
        // search until we find an available index to write
        int index;
        for (index = wellIndexMin; index <= wellIndexMax; index++) {
            if (rc.readSharedArray(index) == 0) {
                String val = "" + type.resourceID + locToInt(loc);
                rc.writeSharedArray(index, Integer.parseInt(val));
                return;
            }
        }
    }

    public static Well readWell(RobotController rc, int index) throws IndexOutOfBoundsException, GameActionException {
        if (index < wellIndexMin || index > wellIndexMax) throw new IndexOutOfBoundsException("Well index out of bounds");
        String val = String.valueOf(rc.readSharedArray(index));
        int type = Integer.parseInt(String.valueOf(val.charAt(0)));
        MapLocation loc = intToLoc(Integer.parseInt(val.substring(1)));

        return new Well(loc, ResourceType.values()[type]);
    }

    /**
     * Storage Layout
     * Largest loc is 3,599
     * Largest storage value is 2^16 = 65535
     *
     * 0 .. 3 | Resource Type | Well Location
     */
}
