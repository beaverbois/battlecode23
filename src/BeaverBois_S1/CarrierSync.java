package BeaverBois_S1;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.ResourceType;
import battlecode.common.RobotController;

import static BeaverBois_S1.Util.intToLoc;
import static BeaverBois_S1.Util.locToInt;

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

    public static ResourceType getCarrierAssignment(RobotController rc) throws GameActionException {
        System.out.println("Got carrier assignment: " + ResourceType.values()[Integer.parseInt(String.valueOf(String.valueOf(rc.readSharedArray(carrierAssignmentIndex)).charAt(0)))].toString());
        return ResourceType.values()[Integer.parseInt(String.valueOf(String.valueOf(rc.readSharedArray(carrierAssignmentIndex)).charAt(0)))];
    }

    public static void setCarrierAssignment(RobotController rc, ResourceType type) throws GameActionException {
        if (rc.canWriteSharedArray(carrierAssignmentIndex, 1)) {
            // copy into string and modify first index
            if (rc.readSharedArray(carrierAssignmentIndex) == 0) {
                rc.writeSharedArray(carrierAssignmentIndex, Integer.parseInt(String.valueOf(type.resourceID).concat("0000")));
            } else {
                String val = String.valueOf(rc.readSharedArray(carrierAssignmentIndex)).substring(1);
                rc.writeSharedArray(carrierAssignmentIndex, Integer.parseInt(String.valueOf(type.resourceID).concat(val)));
            }
        } else {
            System.out.println(rc.getID() + " Could not write to shared array!");
        }
    }
}
