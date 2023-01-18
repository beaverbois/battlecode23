package Sprint2;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.ResourceType;
import battlecode.common.RobotController;

import static Sprint2.Util.intToLoc;
import static Sprint2.Util.locToInt;

public class CarrierSync {

    public static final int NUM_WELLS_STORED = 4;
    public static final int WELL_INDEX_MIN = 56;
    //TODO: This eventually needs to be (numHQ)*2 up to 63
    public static final int WELL_INDEX_MAX = 59;
    public static final int CARRIER_ASSIGNMENT_INDEX = 55;
    public static final int ISLAND_INDEX = 54;

    public static void writeWell(RobotController rc, ResourceType type, MapLocation loc) throws GameActionException {

        // if we are too far to write
        if (!rc.canWriteSharedArray(WELL_INDEX_MIN, 1)) {
            System.out.println("Could not write to shared array!");
            return;
        }
        // search until we find an available index to write
        int index;
        for (index = WELL_INDEX_MIN; index <= WELL_INDEX_MAX; index++) {
            if (rc.readSharedArray(index) == 0) {
                rc.writeSharedArray(index, type.resourceID * 10000 + locToInt(loc));
                return;
            }
        }
    }

    public static MapLocation getWellLocation(RobotController rc, int index) throws IndexOutOfBoundsException, GameActionException {
        if (index < WELL_INDEX_MIN || index > WELL_INDEX_MAX)
            throw new IndexOutOfBoundsException("Well index out of bounds");
        else if (rc.readSharedArray(index) == 0) {
            System.out.println("Shared array is empty at index " + index);
        }

        return intToLoc(rc.readSharedArray(index) % 10000);
    }

    public static ResourceType getWellType(RobotController rc, int index) throws GameActionException {
        if (index < WELL_INDEX_MIN || index > WELL_INDEX_MAX)
            throw new IndexOutOfBoundsException("Well index out of bounds");
        else if (rc.readSharedArray(index) == 0) {
            System.out.println("Shared array is empty at index " + index);
        }

        return ResourceType.values()[rc.readSharedArray(index) / 10000];
    }

    public static int getNumWellsFound(RobotController rc) throws GameActionException {
        // sum the total number of non-0 value wells
        int total = 0;
        for (int i = WELL_INDEX_MIN; i <= WELL_INDEX_MAX; i++) {
            if (rc.readSharedArray(i) != 0) {
                total++;
            }
        }
        return total;
    }

    public static ResourceType getCarrierAssignment(RobotController rc) throws GameActionException {
        return ResourceType.values()[rc.readSharedArray(CARRIER_ASSIGNMENT_INDEX) / 10000];
    }

    public static void setCarrierAssignment(RobotController rc, ResourceType type) throws GameActionException {
        if (rc.canWriteSharedArray(CARRIER_ASSIGNMENT_INDEX, 1)) {
            //Accounts for both cases, as if readSharedArray returns 0 then modding by 10000 is still 0.
            rc.writeSharedArray(CARRIER_ASSIGNMENT_INDEX, type.resourceID * 10000 + rc.readSharedArray(CARRIER_ASSIGNMENT_INDEX) % 10000);
        } else {
            System.out.println(rc.getID() + " Could not write to shared array!");
        }
    }
}
