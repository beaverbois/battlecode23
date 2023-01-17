package Sprint2;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.ResourceType;
import battlecode.common.RobotController;

import static Sprint2.Util.intToLoc;
import static Sprint2.Util.locToInt;

public class CarrierSync {

    public static int numWellsStored = 4;
    public static int wellIndexMin = 56;
    //TODO: This eventually needs to be (numHQ)*2 up to 63
    public static int wellIndexMax = 59;
    public static int carrierAssignmentIndex = 55;
    public static int islandIndex = 54;

    public static void writeWell(RobotController rc, ResourceType type, MapLocation loc) throws GameActionException {

        // if we are too far to write
        if (!rc.canWriteSharedArray(wellIndexMin, 1)) {
            System.out.println("Could not write to shared array!");
            return;
        }
        // search until we find an available index to write
        int index;
        for (index = wellIndexMin; index <= wellIndexMax; index++) {
            if (rc.readSharedArray(index) == 0) {
                rc.writeSharedArray(index, type.resourceID * 10000 + locToInt(loc));
                return;
            }
        }
    }

    public static MapLocation getWellLocation(RobotController rc, int index) throws IndexOutOfBoundsException, GameActionException {
        if (index < wellIndexMin || index > wellIndexMax)
            throw new IndexOutOfBoundsException("Well index out of bounds");
        else if (rc.readSharedArray(index) == 0) {
            System.out.println("Shared array is empty at index " + index);
        }

        return intToLoc(rc.readSharedArray(index) % 10000);
    }

    public static ResourceType getWellType(RobotController rc, int index) throws GameActionException {
        if (index < wellIndexMin || index > wellIndexMax)
            throw new IndexOutOfBoundsException("Well index out of bounds");
        else if (rc.readSharedArray(index) == 0) {
            System.out.println("Shared array is empty at index " + index);
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

    public static ResourceType getCarrierAssignment(RobotController rc) throws GameActionException {
        return ResourceType.values()[rc.readSharedArray(carrierAssignmentIndex) / 10000];
    }

    public static void setCarrierAssignment(RobotController rc, ResourceType type) throws GameActionException {
        if (rc.canWriteSharedArray(carrierAssignmentIndex, 1)) {
            // copy into string and modify first index
            //rc.writeSharedArray(carrierAssignmentIndex, type.resourceID * 10000 + rc.readSharedArray(carrierAssignmentIndex) % 10000);
            int read = rc.readSharedArray(carrierAssignmentIndex);
            if (read == 0) {
                rc.writeSharedArray(carrierAssignmentIndex, type.resourceID * 10000);
            } else {
                rc.writeSharedArray(carrierAssignmentIndex, type.resourceID * 10000 + read % 10000);
            }
        } else {
            System.out.println(rc.getID() + " Could not write to shared array!");
        }
    }
}
