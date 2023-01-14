package Utilities;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.ResourceType;
import battlecode.common.RobotController;

import static Utilities.Util.intToLoc;
import static Utilities.Util.locToInt;

public class CarrierSync {

    public static int numWellsStored = 4;
    public static int wellIndexMin = 0;
    public static int wellIndexMax = 3;
    public static int wellAssignmentIndex = 4;

    public static void writeWell(RobotController rc, ResourceType type, MapLocation loc) throws GameActionException {
        // if we are too far to write
        if (!rc.canWriteSharedArray(wellIndexMin, 1)) {
            System.out.println(rc.getID() + " Could not write to shared array!");
            return;
        }
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

    public static MapLocation getWellLocation(RobotController rc, int index) throws IndexOutOfBoundsException, GameActionException {
        if (index < wellIndexMin || index > wellIndexMax)
            throw new IndexOutOfBoundsException("Well index out of bounds");
        else if (rc.readSharedArray(index) == 0) {
            return null;
        }

        String val = String.valueOf(rc.readSharedArray(index));
        return intToLoc(Integer.parseInt(val.substring(1)));
    }

    public static ResourceType getWellType(RobotController rc, int index) throws GameActionException {
        if (index < wellIndexMin || index > wellIndexMax)
            throw new IndexOutOfBoundsException("Well index out of bounds");
        else if (rc.readSharedArray(index) == 0) {
            return null;
        }

        String val = String.valueOf(rc.readSharedArray(index));
        return ResourceType.values()[Integer.parseInt(String.valueOf(val.charAt(0)))];
    }

    public static int getNumWellsFound(RobotController rc) throws GameActionException {
        // sum the total number of non-0 value wells
        int total = 0;
        for (int i = wellIndexMin; i <= wellIndexMax; i++) {
            if (rc.readSharedArray(i) != 0) {
                total++;
            }
        }
        return total;
    }

    public static ResourceType getCarrierAssignment(RobotController rc) {
        return null;
    }

    public static void assignCarrier(RobotController rc, ResourceType type) {

    }
}
